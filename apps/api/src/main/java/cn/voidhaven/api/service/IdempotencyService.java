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
        List<Map<String, Object>> existing = jdbc.queryForList("select request_hash, response_body from idempotency_records where idempotency_key = ?", key);
        if (!existing.isEmpty()) return restore(existing.get(0), requestHash, responseType);
        T result = action.get(); String response = write(result);
        try { jdbc.update("insert into idempotency_records (id, idempotency_key, request_hash, response_status, response_body, expires_at) values (?, ?, ?, 200, cast(? as json), ?)", Ids.ulid(), key, requestHash, response, Instant.now().plusSeconds(86400)); }
        catch (DuplicateKeyException ignored) { return restore(jdbc.queryForMap("select request_hash, response_body from idempotency_records where idempotency_key = ?", key), requestHash, responseType); }
        return result;
    }
    private <T> T restore(Map<String, Object> row, String hash, Class<T> type) {
        if (!Objects.equals(row.get("request_hash"), hash)) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "该请求标识已用于不同内容，请生成新的请求后重试。");
        try { return json.readValue(row.get("response_body").toString(), type); } catch (Exception error) { throw new IllegalStateException("Stored idempotency response cannot be restored", error); }
    }
    private String write(Object object) { try { return json.writeValueAsString(object); } catch (Exception error) { throw new IllegalStateException("Response cannot be serialized", error); } }
    private String hash(String input) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); } catch (Exception error) { throw new IllegalStateException(error); } }
}
