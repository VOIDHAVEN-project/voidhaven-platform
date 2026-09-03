package cn.voidhaven.api.service;
import cn.voidhaven.api.support.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void record(String actorId, String role, String action, String type, String objectId, String before, String after, String reason, String requestId) {
        jdbc.update("insert into audit_logs (id, actor_account_id, actor_role, action, object_type, object_id, before_summary, after_summary, reason, request_id) values (?, ?, ?, ?, ?, ?, cast(? as json), cast(? as json), ?, ?)", Ids.ulid(), actorId, role, action, type, objectId, before, after, reason, requestId);
    }
    public void recordReview(String contentId, String revisionId, String reviewerId, String decision, String reason) {
        jdbc.update("insert into review_records (id, content_item_id, revision_id, reviewer_account_id, decision, reason) values (?, ?, ?, ?, ?, ?)", Ids.ulid(), contentId, revisionId, reviewerId, decision, reason);
    }
}
