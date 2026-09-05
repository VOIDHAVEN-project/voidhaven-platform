package cn.voidhaven.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import cn.voidhaven.api.support.PageResult;

@Service
public class AuditQueryService {
    public record Entry(String id, Instant createdAt, String actorAccountId, String actorRole, String action,
                        String objectType, String objectId, String reason, String requestId) { }
    private final JdbcTemplate jdbc;
    public AuditQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<Entry> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query("select id, created_at, actor_account_id, actor_role, action, object_type, object_id, reason, request_id from audit_logs order by id desc limit ?", (result, row) -> entry(result), safeLimit);
    }

    @Transactional(readOnly = true)
    public PageResult<Entry> page(String cursor, String query, String action, String actorAccountId,
                                  String objectType, Instant from, Instant to, int requestedSize) {
        int pageSize = Math.max(1, Math.min(requestedSize, 50));
        String normalized = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        String normalizedAction = action == null || action.isBlank() ? null : action.trim();
        String normalizedActor = actorAccountId == null || actorAccountId.isBlank() ? null : actorAccountId.trim();
        String normalizedObjectType = objectType == null || objectType.isBlank() ? null : objectType.trim();
        Timestamp fromTimestamp = from == null ? null : Timestamp.from(from);
        Timestamp toTimestamp = to == null ? null : Timestamp.from(to);
        List<Entry> values = jdbc.query("select id, created_at, actor_account_id, actor_role, action, object_type, object_id, reason, request_id from audit_logs where (? is null or id < ?) and (? is null or action = ?) and (? is null or actor_account_id = ?) and (? is null or object_type = ?) and (? is null or created_at >= ?) and (? is null or created_at <= ?) and (? is null or lower(object_type) like ? or lower(object_id) like ? or lower(actor_role) like ? or lower(action) like ?) order by id desc limit ?",
                (result, row) -> entry(result), cursor, cursor, normalizedAction, normalizedAction,
                normalizedActor, normalizedActor, normalizedObjectType, normalizedObjectType,
                fromTimestamp, fromTimestamp, toTimestamp, toTimestamp,
                normalized, normalized, normalized, normalized, normalized, pageSize + 1);
        return PageResult.from(values, pageSize, Entry::id);
    }

    private Entry entry(java.sql.ResultSet result) throws java.sql.SQLException {
        return new Entry(result.getString("id"), result.getTimestamp("created_at").toInstant(),
                result.getString("actor_account_id"), result.getString("actor_role"), result.getString("action"),
                result.getString("object_type"), result.getString("object_id"), result.getString("reason"),
                result.getString("request_id"));
    }
}
