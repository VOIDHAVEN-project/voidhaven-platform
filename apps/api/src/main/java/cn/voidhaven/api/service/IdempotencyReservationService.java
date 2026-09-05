package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/** Commits the short-lived ownership marker independently from the protected business transaction. */
@Service
public class IdempotencyReservationService {
    public record Existing(String requestHash, int responseStatus, String responseBody) { }

    private final JdbcTemplate jdbc;
    private final long inProgressSeconds;

    public IdempotencyReservationService(JdbcTemplate jdbc,
            @Value("${voidhaven.idempotency-in-progress-seconds:86400}") long inProgressSeconds) {
        if (inProgressSeconds < 1) throw new IllegalStateException("Idempotency in-progress timeout must be positive");
        this.jdbc = jdbc;
        this.inProgressSeconds = inProgressSeconds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Existing reserve(String key, String requestHash) {
        jdbc.update("delete from idempotency_records where idempotency_key = ? and response_status = 102 and expires_at < current_timestamp(6)", key);
        try {
            jdbc.update("insert into idempotency_records (id, idempotency_key, request_hash, response_status, response_body, expires_at) values (?, ?, ?, 102, cast(? as json), ?)",
                    Ids.ulid(), key, requestHash, "{\"state\":\"IN_PROGRESS\"}",
                    Timestamp.from(Instant.now().plusSeconds(inProgressSeconds)));
            return null;
        } catch (DuplicateKeyException ignored) {
            Map<String, Object> row = jdbc.queryForMap(
                    "select request_hash, response_status, response_body from idempotency_records where idempotency_key = ?", key);
            return new Existing(row.get("request_hash").toString(), Integer.parseInt(row.get("response_status").toString()),
                    row.get("response_body").toString());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String key, String requestHash) {
        jdbc.update("delete from idempotency_records where idempotency_key = ? and request_hash = ? and response_status = 102", key, requestHash);
    }
}
