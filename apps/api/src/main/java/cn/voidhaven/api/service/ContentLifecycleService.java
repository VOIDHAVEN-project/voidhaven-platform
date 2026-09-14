package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.PublicationStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.support.Ids;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ContentLifecycleService {
    private static final String TASK_LOCK = "content-lifecycle";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final ContentItemRepository items;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final MysqlAdvisoryLock advisoryLocks;

    ContentLifecycleService(ContentItemRepository items, JdbcTemplate jdbc, AuditService audit) {
        this(items, jdbc, audit, null, null);
    }

    @Autowired
    public ContentLifecycleService(ContentItemRepository items, JdbcTemplate jdbc, AuditService audit,
                                   PlatformTransactionManager transactionManager, MysqlAdvisoryLock advisoryLocks) {
        this.items = items;
        this.jdbc = jdbc;
        this.audit = audit;
        this.transactions = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.advisoryLocks = advisoryLocks;
    }

    @Scheduled(cron = "${voidhaven.lifecycle.cron:0 10 1 * * *}", zone = "Asia/Shanghai")
    public void scheduledRun() {
        if (advisoryLocks == null) {
            run(Instant.now());
            return;
        }
        advisoryLocks.runExclusive(TASK_LOCK, () -> run(Instant.now()));
    }

    public void run(Instant now) {
        String requestId = Ids.ulid();
        for (ContentItem candidate : items.findTimedPublicItems()) {
            try {
                if (transactions == null) processOne(candidate.getId(), now, requestId);
                else transactions.executeWithoutResult(status -> processOne(candidate.getId(), now, requestId));
            } catch (RuntimeException ignored) {
                // A single item conflict must not roll back the rest of the run.
            }
        }
    }

    private void processOne(String id, Instant now, String requestId) {
        ContentItem item = items.findByIdForUpdate(id).orElse(null);
        if (item == null) return;
        if (item.getPublicationStatus() == PublicationStatus.ACTIVE && hasPassedDeadline(item, now)) {
            item.closePublic(now);
            if (item.getKind() == ContentKind.RECRUITMENT) item.setOperationalStatus("EXPIRED");
            audit.record(null, "SYSTEM", "CONTENT_CLOSED", "CONTENT_ITEM", item.getId(), "{\"publicationStatus\":\"ACTIVE\"}", "{\"publicationStatus\":\"CLOSED\"}", "截止日期已到", requestId);
        }
        if (item.getKind() != ContentKind.RECRUITMENT && item.getPublicationStatus() == PublicationStatus.CLOSED && item.getClosedAt() != null && !item.getClosedAt().plusSeconds(3 * 24 * 60 * 60).isAfter(now)) {
            item.archive();
            audit.record(null, "SYSTEM", "CONTENT_ARCHIVED", "CONTENT_ITEM", item.getId(), "{\"recordState\":\"ACTIVE\"}", "{\"recordState\":\"ARCHIVED\",\"visibility\":\"HIDDEN\"}", "关闭三天后自动归档", requestId);
        }
    }

    private boolean hasPassedDeadline(ContentItem item, Instant now) {
        if (item.getKind() == ContentKind.RESOURCE || item.getPublishedRevision() == null) return false;
        List<Map<String, Object>> rows = jdbc.queryForList(deadlineSql(item.getKind()), item.getPublishedRevision().getId());
        if (rows.isEmpty()) return false;
        Map<String, Object> detail = rows.get(0);
        Timestamp deadlineAt = (Timestamp) detail.get("deadline_at");
        if (deadlineAt != null) return deadlineAt.toInstant().isBefore(now);
        Date deadlineDate = (Date) detail.get("deadline_date");
        return deadlineDate != null && deadlineDate.toLocalDate().isBefore(ZonedDateTime.ofInstant(now, SHANGHAI).toLocalDate());
    }

    private String deadlineSql(ContentKind kind) {
        return kind == ContentKind.COMPETITION
                ? "select deadline_at, deadline_date from competition_revision_details where revision_id = ?"
                : "select deadline_at, deadline_date from recruitment_revision_details where revision_id = ?";
    }
}
