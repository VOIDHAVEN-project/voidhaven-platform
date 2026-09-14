package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import cn.voidhaven.api.support.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class PublicInputAdministrationService {
    public record Submission(String id, String submissionType, String title, String message, String linkUrl,
                             String imageDescription, String state, String publicResult, Instant dueAt,
                             long version, boolean contactAvailable) { }
    public record DecisionResult(String id, String state) { }

    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT");
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private AccountLockService accountLocks;

    public PublicInputAdministrationService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAccountLocks(AccountLockService accountLocks) { this.accountLocks = accountLocks; }

    @Transactional(readOnly = true)
    public List<Submission> pendingSubmissions() {
        return jdbc.query("select s.id, s.submission_type, s.title, s.message, s.link_url, s.image_description, s.state, s.public_result, s.due_at, s.version, exists(select 1 from private_contact_records p where p.subject_type = 'SUBMISSION' and p.subject_id = s.id and p.delete_after > current_timestamp(6)) contact_available from content_submissions s where s.state in ('RECEIVED','PENDING_REVIEW') order by s.created_at asc",
                (row, index) -> submission(row));
    }

    @Transactional(readOnly = true)
    public PageResult<Submission> page(String cursor, String query, String status, int requestedSize) {
        int pageSize = Math.max(1, Math.min(requestedSize, 50));
        String normalized = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        String normalizedStatus = normalizeStatus(status);
        List<Submission> values = jdbc.query("select s.id, s.submission_type, s.title, s.message, s.link_url, s.image_description, s.state, s.public_result, s.due_at, s.version, exists(select 1 from private_contact_records p where p.subject_type = 'SUBMISSION' and p.subject_id = s.id and p.delete_after > current_timestamp(6)) contact_available from content_submissions s where (? is null or s.state = ?) and (? is null or lower(s.title) like ? or lower(s.message) like ?) and (? is null or s.id < ?) order by s.id desc limit ?",
                (row, index) -> submission(row),
                normalizedStatus, normalizedStatus, normalized, normalized, normalized, cursor, cursor, pageSize + 1);
        return PageResult.from(values, pageSize, Submission::id);
    }

    @Transactional
    public DecisionResult decideSubmission(String id, String decision, String reason, String actorId, String role, String requestId) {
        if (accountLocks != null) accountLocks.requireActiveActor(actorId);
        String state = targetState(decision);
        String from = jdbc.query("select state from content_submissions where id = ? for update",
                result -> result.next() ? result.getString(1) : null, id);
        int updated = jdbc.update("update content_submissions set state = ?, public_result = ?, review_reason = ?, reviewed_by_account_id = ?, reviewed_at = current_timestamp(6), resolved_at = current_timestamp(6), version = version + 1 where id = ? and state in ('RECEIVED','PENDING_REVIEW','IN_PROGRESS')", state, reason, reason, actorId, id);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_ACTIONABLE", "该投稿、反馈或版权请求不存在或已被处理。");
        audit.record(actorId, role, "PUBLIC_SUBMISSION_" + decision, "CONTENT_SUBMISSION", id,
                "{\"state\":\"" + from + "\"}", "{\"state\":\"" + state + "\"}", reason, requestId);
        jdbc.update("insert into public_case_status_history (id, case_type, case_id, from_state, to_state, public_message, actor_account_id) values (?, 'SUBMISSION', ?, ?, ?, ?, ?)",
                cn.voidhaven.api.support.Ids.ulid(), id, from, state, reason, actorId);
        return new DecisionResult(id, state);
    }

    static String targetState(String decision) {
        if (!DECISIONS.contains(decision)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_DECISION", "审核决定无效。");
        return "APPROVE".equals(decision) ? "RESOLVED" : "REJECTED";
    }

    private String normalizeStatus(String status) {
        return PublicCaseStates.normalizeSubmissionFilter(status);
    }

    private Submission submission(java.sql.ResultSet row) throws java.sql.SQLException {
        java.sql.Timestamp due = row.getTimestamp("due_at");
        return new Submission(row.getString("id"), row.getString("submission_type"), row.getString("title"), row.getString("message"),
                row.getString("link_url"), row.getString("image_description"), row.getString("state"), row.getString("public_result"),
                due == null ? null : due.toInstant(), row.getLong("version"), row.getBoolean("contact_available"));
    }
}
