package cn.voidhaven.api.service;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ContentVerificationService {
    public record Verification(String id, String contentItemId, String outcome, String detailSummary,
                               String checkedByAccountId, Instant checkedAt, Instant nextCheckAt) { }
    public record PublicVerification(String outcome, Instant checkedAt) { }

    private static final Set<String> OUTCOMES = Set.of("VALID", "INVALID", "NEEDS_REVIEW");
    private final JdbcTemplate jdbc;
    private final ContentItemRepository items;
    private final AccountRepository accounts;
    private final AuditService audit;

    public ContentVerificationService(JdbcTemplate jdbc, ContentItemRepository items,
            AccountRepository accounts, AuditService audit) {
        this.jdbc = jdbc;
        this.items = items;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Verification> history(String contentItemId) {
        requireContent(contentItemId);
        return jdbc.query("select id, content_item_id, outcome, detail_summary, checked_by_account_id, checked_at, next_check_at "
                        + "from content_verification_records where content_item_id = ? order by checked_at desc, id desc limit 20",
                (row, index) -> map(row), contentItemId);
    }

    @Transactional(readOnly = true)
    public PublicVerification latestPublic(String contentItemId) {
        List<Verification> values = jdbc.query("select id, content_item_id, outcome, detail_summary, checked_by_account_id, checked_at, next_check_at "
                        + "from content_verification_records where content_item_id = ? order by checked_at desc, id desc limit 1",
                (row, index) -> map(row), contentItemId);
        return values.isEmpty() ? null : new PublicVerification(values.get(0).outcome(), values.get(0).checkedAt());
    }

    @Transactional
    public Verification record(String contentItemId, String outcome, String detailSummary, Instant nextCheckAt,
                               String actorLoginId, String requestId) {
        requireContent(contentItemId);
        String normalizedOutcome = outcome == null ? "" : outcome.trim().toUpperCase();
        if (!OUTCOMES.contains(normalizedOutcome)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_VERIFICATION_OUTCOME_INVALID", "核验结果无效。");
        }
        String detail = detailSummary == null ? null : detailSummary.trim();
        if (detail != null && detail.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_VERIFICATION_DETAIL_TOO_LONG", "核验说明不能超过 1000 字。");
        }
        if (!"VALID".equals(normalizedOutcome) && (detail == null || detail.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_VERIFICATION_DETAIL_REQUIRED", "失效或待复核时必须填写说明。");
        }
        var actor = accounts.findByLoginId(actorLoginId).orElseThrow();
        String actorId = actor.getId();
        String actorRole = actor.hasRole("SUPER_ADMIN") ? "SUPER_ADMIN" : "ADMIN";
        String id = Ids.ulid();
        Instant checkedAt = Instant.now();
        if (nextCheckAt != null && !nextCheckAt.isAfter(checkedAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_VERIFICATION_NEXT_CHECK_INVALID", "下次复核时间必须晚于本次核验时间。");
        }
        jdbc.update("insert into content_verification_records (id, content_item_id, outcome, detail_summary, checked_by_account_id, checked_at, next_check_at) values (?, ?, ?, ?, ?, ?, ?)",
                id, contentItemId, normalizedOutcome, detail, actorId, Timestamp.from(checkedAt),
                nextCheckAt == null ? null : Timestamp.from(nextCheckAt));
        audit.record(actorId, actorRole, "CONTENT_SOURCE_VERIFIED", "CONTENT_ITEM", contentItemId, "{}",
                "{\"outcome\":\"" + normalizedOutcome + "\"}", detail, requestId);
        return new Verification(id, contentItemId, normalizedOutcome, detail, actorId, checkedAt, nextCheckAt);
    }

    private void requireContent(String id) {
        if (!items.existsById(id)) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。");
    }

    private Verification map(java.sql.ResultSet row) throws java.sql.SQLException {
        Timestamp next = row.getTimestamp("next_check_at");
        return new Verification(row.getString("id"), row.getString("content_item_id"), row.getString("outcome"),
                row.getString("detail_summary"), row.getString("checked_by_account_id"),
                row.getTimestamp("checked_at").toInstant(), next == null ? null : next.toInstant());
    }
}
