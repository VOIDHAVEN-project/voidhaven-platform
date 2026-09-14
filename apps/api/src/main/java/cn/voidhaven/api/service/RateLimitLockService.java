package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class RateLimitLockService {
    public static final String PUBLIC_WRITE_NETWORK = "PUBLIC_WRITE_NETWORK";
    public static final String PUBLIC_STATUS_QUERY = "PUBLIC_STATUS_QUERY";
    public static final String LOGIN_ID = "LOGIN_ID";
    public static final String LOGIN_NETWORK = "LOGIN_NETWORK";
    public static final String ACTIVATION_NETWORK = "ACTIVATION_NETWORK";
    public static final String PASSWORD_RESET_LOGIN = "PASSWORD_RESET_LOGIN";
    public static final String PASSWORD_RESET_NETWORK = "PASSWORD_RESET_NETWORK";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;

    public RateLimitLockService(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void reservePublicWrite(String subjectHash, int maximum, Duration ttl) {
        independent.executeWithoutResult(status -> {
            lockSubject(PUBLIC_WRITE_NETWORK, subjectHash);
            Integer count = jdbc.queryForObject(
                    "select count(*) from public_write_rate_limit_events where scope = ? and subject_hash = ? and expires_at > current_timestamp(6)",
                    Integer.class, PUBLIC_WRITE_NETWORK, subjectHash);
            if (count != null && count >= maximum) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PUBLIC_WRITE_RATE_LIMITED", "提交过于频繁，请稍后再试。");
            }
            jdbc.update("insert into public_write_rate_limit_events (id, scope, subject_hash, expires_at) values (?, ?, ?, ?)",
                    Ids.ulid(), PUBLIC_WRITE_NETWORK, subjectHash, Timestamp.from(Instant.now().plus(ttl)));
        });
    }

    public void reservePublicStatusQuery(String subjectHash, int maximum, Duration ttl) {
        independent.executeWithoutResult(status -> {
            lockSubject(PUBLIC_STATUS_QUERY, subjectHash);
            Integer count = jdbc.queryForObject(
                    "select count(*) from public_status_query_rate_limit_events where subject_hash = ? and expires_at > current_timestamp(6)",
                    Integer.class, subjectHash);
            if (count != null && count >= maximum) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PUBLIC_STATUS_QUERY_RATE_LIMITED", "查询过于频繁，请稍后再试。");
            }
            jdbc.update("insert into public_status_query_rate_limit_events (id, subject_hash, expires_at) values (?, ?, ?)",
                    Ids.ulid(), subjectHash, Timestamp.from(Instant.now().plus(ttl)));
        });
    }

    public String reserveAuth(String scope, String subjectHash, int maximum, Duration ttl, String outcome) {
        return independent.execute(status -> {
            lockSubject(scope, subjectHash);
            Integer count = jdbc.queryForObject(
                    "select count(*) from auth_rate_limit_events where scope = ? and subject_hash = ? and outcome in ('ATTEMPT','FAILURE') and expires_at > current_timestamp(6)",
                    Integer.class, scope, subjectHash);
            if (count != null && count >= maximum) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", "尝试过于频繁，请在 15 分钟后重试。");
            }
            String id = Ids.ulid();
            jdbc.update("insert into auth_rate_limit_events (id, scope, subject_hash, outcome, expires_at) values (?, ?, ?, ?, ?)",
                    id, scope, subjectHash, outcome, Timestamp.from(Instant.now().plus(ttl)));
            return id;
        });
    }

    public void completeLoginAttempt(String attemptId, boolean succeeded) {
        if (attemptId == null || attemptId.isBlank()) return;
        independent.executeWithoutResult(status -> {
            List<String> subjects = jdbc.query(
                    "select subject_hash from auth_rate_limit_events where id = ? and scope = ?",
                    (row, index) -> row.getString("subject_hash"), attemptId, LOGIN_ID);
            if (subjects.isEmpty()) return;

            String subjectHash = subjects.get(0);
            lockSubject(LOGIN_ID, subjectHash);
            int updated = jdbc.update(
                    "update auth_rate_limit_events set outcome = ? where id = ? and scope = ? and subject_hash = ? and outcome = 'ATTEMPT'",
                    succeeded ? "SUCCESS" : "FAILURE", attemptId, LOGIN_ID, subjectHash);
            if (succeeded && updated == 1) {
                jdbc.update("delete from auth_rate_limit_events where scope = ? and subject_hash = ? and outcome = 'FAILURE'",
                        LOGIN_ID, subjectHash);
            }
        });
    }

    private void lockSubject(String scope, String subjectHash) {
        jdbc.update("insert into rate_limit_locks (scope, subject_hash) values (?, ?) on duplicate key update subject_hash = subject_hash",
                scope, subjectHash);
        jdbc.queryForObject("select scope from rate_limit_locks where scope = ? and subject_hash = ? for update",
                String.class, scope, subjectHash);
    }
}
