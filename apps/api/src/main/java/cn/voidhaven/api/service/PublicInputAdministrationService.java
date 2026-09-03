package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class PublicInputAdministrationService {
    public record Submission(String id, String submissionType, String title, String message, String linkUrl, String imageDescription, String state) { }
    public record DecisionResult(String id, String state) { }

    private static final Set<String> DECISIONS = Set.of("APPROVE", "CHANGES_REQUESTED", "REJECT");
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PublicInputAdministrationService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Submission> pendingSubmissions() {
        return jdbc.query("select id, submission_type, title, message, link_url, image_description, state from content_submissions where state = 'PENDING_REVIEW' order by created_at asc",
                (row, index) -> new Submission(row.getString("id"), row.getString("submission_type"), row.getString("title"), row.getString("message"), row.getString("link_url"), row.getString("image_description"), row.getString("state")));
    }

    @Transactional
    public DecisionResult decideSubmission(String id, String decision, String reason, String actorId, String role, String requestId) {
        String state = targetState(decision);
        int updated = jdbc.update("update content_submissions set state = ?, review_reason = ?, reviewed_by_account_id = ?, reviewed_at = current_timestamp(6) where id = ? and state = 'PENDING_REVIEW'", state, reason, actorId, id);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_NOT_ACTIONABLE", "该投稿、反馈或版权请求不存在或已被处理。");
        audit.record(actorId, role, "PUBLIC_SUBMISSION_" + decision, "CONTENT_SUBMISSION", id,
                "{\"state\":\"PENDING_REVIEW\"}", "{\"state\":\"" + state + "\"}", reason, requestId);
        return new DecisionResult(id, state);
    }

    static String targetState(String decision) {
        if (!DECISIONS.contains(decision)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_DECISION", "审核决定无效。");
        return switch (decision) { case "APPROVE" -> "APPROVED"; case "CHANGES_REQUESTED" -> "CHANGES_REQUESTED"; default -> "REJECTED"; };
    }
}
