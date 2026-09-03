package cn.voidhaven.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuditQueryService {
    public record Entry(Instant createdAt, String actorRole, String action, String objectType, String objectId, String reason, String requestId) { }
    private final JdbcTemplate jdbc;
    public AuditQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<Entry> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query("select created_at, actor_role, action, object_type, object_id, reason, request_id from audit_logs order by created_at desc limit ?", (result, row) -> new Entry(result.getTimestamp("created_at").toInstant(), result.getString("actor_role"), result.getString("action"), result.getString("object_type"), result.getString("object_id"), result.getString("reason"), result.getString("request_id")), safeLimit);
    }
}
