package cn.voidhaven.api.service;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import tools.jackson.databind.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Transactional
    public <T> T execute(String key, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank() || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "请携带有效的幂等请求标识后重试。");
        String requestHash = hash(write(request));
        try {
            jdbc.update("insert into idempotency_records (id, idempotency_key, request_hash, response_status, response_body, expires_at) values (?, ?, ?, 102, cast(? as json), ?)", Ids.ulid(), key, requestHash, "{\"state\":\"IN_PROGRESS\"}", Instant.now().plusSeconds(86400));
        } catch (DuplicateKeyException ignored) {
            return restore(jdbc.queryForMap("select request_hash, response_status, response_body from idempotency_records where idempotency_key = ?", key), requestHash, responseType);
        }
        T result = action.get(); String response = write(result);
        jdbc.update("update idempotency_records set response_status = 200, response_body = cast(? as json) where idempotency_key = ? and request_hash = ?", response, key, requestHash);
        return result;
    }
    /**
     * Applies the same duplicate-write protection without ever persisting a one-time secret.
     * A duplicate request deliberately requires an administrator to issue a new credential.
     */
    @Transactional
    public <T> T executeSingleDelivery(String key, Object request, Supplier<T> action) {
        if (key == null || key.isBlank() || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "请携带有效的幂等请求标识后重试。");
        String requestHash = hash(write(request));
        try {
            jdbc.update("insert into idempotency_records (id, idempotency_key, request_hash, response_status, response_body, expires_at) values (?, ?, ?, 102, cast(? as json), ?)", Ids.ulid(), key, requestHash, "{\"state\":\"IN_PROGRESS\"}", Instant.now().plusSeconds(86400));
        } catch (DuplicateKeyException ignored) {
            Map<String, Object> row = jdbc.queryForMap("select request_hash, response_status from idempotency_records where idempotency_key = ?", key);
            if (!Objects.equals(row.get("request_hash"), requestHash)) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "该请求标识已用于不同内容，请生成新的请求后重试。");
            if (Integer.parseInt(row.get("response_status").toString()) == 102) throw new ApiException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "相同请求仍在处理，请稍后使用相同请求标识重试。");
            throw new ApiException(HttpStatus.CONFLICT, "ONE_TIME_CREDENTIAL_REDELIVERY_REQUIRED", "一次性激活凭据不会被重复返回，请由管理员重新生成。");
        }
        T result = action.get();
        jdbc.update("update idempotency_records set response_status = 200, response_body = cast(? as json) where idempotency_key = ? and request_hash = ?", "{\"state\":\"DELIVERED_NO_REPLAY\"}", key, requestHash);
        return result;
    }
    private <T> T restore(Map<String, Object> row, String hash, Class<T> type) {
        if (!Objects.equals(row.get("request_hash"), hash)) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "该请求标识已用于不同内容，请生成新的请求后重试。");
        if (Integer.parseInt(row.get("response_status").toString()) == 102) throw new ApiException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "相同请求仍在处理，请稍后使用相同请求标识重试。");
        try { return json.readValue(row.get("response_body").toString(), type); } catch (Exception error) { throw new IllegalStateException("Stored idempotency response cannot be restored", error); }
    }
    private String write(Object object) { try { return json.writeValueAsString(object); } catch (Exception error) { throw new IllegalStateException("Response cannot be serialized", error); } }
    private String hash(String input) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); } catch (Exception error) { throw new IllegalStateException(error); } }
}
