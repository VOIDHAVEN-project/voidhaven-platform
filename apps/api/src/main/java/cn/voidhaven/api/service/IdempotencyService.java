package cn.voidhaven.api.service;
import cn.voidhaven.api.web.ApiException;
import tools.jackson.databind.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final IdempotencyReservationService reservations;
    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper json, IdempotencyReservationService reservations) { this.jdbc = jdbc; this.json = json; this.reservations = reservations; }
    @Transactional
    public <T> T execute(String key, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank() || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "请携带有效的幂等请求标识后重试。");
        String requestHash = hash(write(request));
        IdempotencyReservationService.Existing existing = reservations.reserve(key, requestHash);
        if (existing != null) return restore(existing, requestHash, responseType);
        try {
            T result = action.get(); String response = write(result);
            int completed = jdbc.update("update idempotency_records set response_status = 200, response_body = cast(? as json), expires_at = ? where idempotency_key = ? and request_hash = ? and response_status = 102",
                    response, Timestamp.from(Instant.now().plusSeconds(86400)), key, requestHash);
            if (completed != 1) throw new IllegalStateException("Idempotency reservation was lost before completion");
            return result;
        } catch (RuntimeException | Error error) {
            reservations.release(key, requestHash);
            throw error;
        }
    }
    /**
     * Applies the same duplicate-write protection without ever persisting a one-time secret.
     * A duplicate request deliberately requires an administrator to issue a new credential.
     */
    @Transactional
    public <T> T executeSingleDelivery(String key, Object request, Supplier<T> action) {
        if (key == null || key.isBlank() || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "请携带有效的幂等请求标识后重试。");
        String requestHash = hash(write(request));
        IdempotencyReservationService.Existing existing = reservations.reserve(key, requestHash);
        if (existing != null) {
            if (!Objects.equals(existing.requestHash(), requestHash)) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "该请求标识已用于不同内容，请生成新的请求后重试。");
            if (existing.responseStatus() == 102) throw new ApiException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "相同请求仍在处理，请稍后使用相同请求标识重试。");
            throw new ApiException(HttpStatus.CONFLICT, "ONE_TIME_CREDENTIAL_REDELIVERY_REQUIRED", "一次性激活凭据不会被重复返回，请由管理员重新生成。");
        }
        try {
            T result = action.get();
            int completed = jdbc.update("update idempotency_records set response_status = 200, response_body = cast(? as json), expires_at = ? where idempotency_key = ? and request_hash = ? and response_status = 102",
                    "{\"state\":\"DELIVERED_NO_REPLAY\"}", Timestamp.from(Instant.now().plusSeconds(86400)), key, requestHash);
            if (completed != 1) throw new IllegalStateException("Idempotency reservation was lost before completion");
            return result;
        } catch (RuntimeException | Error error) {
            reservations.release(key, requestHash);
            throw error;
        }
    }
    private <T> T restore(IdempotencyReservationService.Existing existing, String hash, Class<T> type) {
        if (!Objects.equals(existing.requestHash(), hash)) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "该请求标识已用于不同内容，请生成新的请求后重试。");
        if (existing.responseStatus() == 102) throw new ApiException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "相同请求仍在处理，请稍后使用相同请求标识重试。");
        try { return json.readValue(existing.responseBody(), type); } catch (Exception error) { throw new IllegalStateException("Stored idempotency response cannot be restored", error); }
    }
    private String write(Object object) { try { return json.writeValueAsString(object); } catch (Exception error) { throw new IllegalStateException("Response cannot be serialized", error); } }
    private String hash(String input) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); } catch (Exception error) { throw new IllegalStateException(error); } }
}
