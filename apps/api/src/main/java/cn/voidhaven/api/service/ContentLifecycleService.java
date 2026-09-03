package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.support.Ids;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class ContentLifecycleService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final ContentItemRepository items;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public ContentLifecycleService(ContentItemRepository items, ObjectMapper mapper, AuditService audit) {
        this.items = items;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Scheduled(cron = "${voidhaven.lifecycle.cron:0 10 1 * * *}", zone = "Asia/Shanghai")
    public void scheduledRun() {
        run(Instant.now());
    }

    @Transactional
    public void run(Instant now) {
        String requestId = Ids.ulid();
        for (ContentItem item : items.findTimedPublicItems()) {
            if (item.getWorkflowStatus() == WorkflowStatus.PUBLISHED && hasPassedDeadline(item, now)) {
                item.close(now);
                audit.record(null, "SYSTEM", "CONTENT_CLOSED", "CONTENT_ITEM", item.getId(), "{\"workflowStatus\":\"PUBLISHED\"}", "{\"workflowStatus\":\"CLOSED\"}", "截止日期已到", requestId);
            }
            if (item.getWorkflowStatus() == WorkflowStatus.CLOSED && item.getClosedAt() != null && !item.getClosedAt().plusSeconds(3 * 24 * 60 * 60).isAfter(now)) {
                item.archive();
                audit.record(null, "SYSTEM", "CONTENT_ARCHIVED", "CONTENT_ITEM", item.getId(), "{\"recordState\":\"ACTIVE\"}", "{\"recordState\":\"ARCHIVED\",\"visibility\":\"HIDDEN\"}", "关闭三天后自动归档", requestId);
            }
        }
    }

    private boolean hasPassedDeadline(ContentItem item, Instant now) {
        try {
            JsonNode details = mapper.readTree(item.getPublishedRevision().getDetailsJson());
            if (details.hasNonNull("deadlineAt")) return Instant.parse(details.get("deadlineAt").asText()).isBefore(now);
            if (details.hasNonNull("deadlineDate")) return LocalDate.parse(details.get("deadlineDate").asText()).isBefore(ZonedDateTime.ofInstant(now, SHANGHAI).toLocalDate());
        } catch (Exception ignored) {
            // A malformed optional deadline must never make a public item disappear automatically.
        }
        return false;
    }
}
