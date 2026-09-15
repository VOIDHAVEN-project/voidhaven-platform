package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.support.SecureTokens;
import cn.voidhaven.api.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class CommunityService {
    public record Grant(String grant, Instant expiresAt) { }
    public record Entry(String id, String name, String description, String presentationType, String contactChannel,
                        String accessLevel, String value, String mediaUrl, boolean primary, int sortOrder) { }
    public record EntrySummary(String id, String name, String description, String presentationType,
                               String contactChannel, String accessLevel, String mediaAssetId,
                               boolean primary, boolean active, int sortOrder, long version) { }
    public record InviteBatch(String id, String label, Instant validFrom, Instant expiresAt, int maxUses,
                              int useCount, String state, Instant createdAt) { }
    public record CreatedInviteBatch(String id, String label, String inviteCode, Instant expiresAt, int maxUses) { }

    private static final Set<String> PRESENTATIONS = Set.of("LINK", "QR_CODE", "TEXT");
    private static final Set<String> CHANNELS = Set.of("URL", "EMAIL", "WECHAT", "QQ", "PHONE", "OTHER");
    private static final Set<String> ACCESS = Set.of("PUBLIC", "COMMUNITY_GATED");
    private static final Set<String> SENSITIVE_CHANNELS = Set.of("EMAIL", "WECHAT", "QQ", "PHONE");

    private final JdbcTemplate jdbc;
    private final PrivateValueCipher privateValues;
    private final ContentSourcePolicy sources;
    private final AuditService audit;
    private final boolean enabled;
    private final int grantMinutes;
    private final String hmacSecret;

    public CommunityService(JdbcTemplate jdbc, PrivateValueCipher privateValues, ContentSourcePolicy sources,
            AuditService audit, @Value("${voidhaven.community-gate-enabled:false}") boolean enabled,
            @Value("${voidhaven.community-grant-minutes:30}") int grantMinutes,
            @Value("${voidhaven.community-invite-hmac-secret:}") String hmacSecret,
            Environment environment) {
        if (grantMinutes < 1 || grantMinutes > 120) throw new IllegalStateException("Community grant duration must be between 1 and 120 minutes");
        if (isProduction(environment) && enabled && (hmacSecret == null || hmacSecret.isBlank() || hmacSecret.contains("local-development"))) {
            throw new IllegalStateException("A production community invite HMAC secret is required");
        }
        this.jdbc = jdbc;
        this.privateValues = privateValues;
        this.sources = sources;
        this.audit = audit;
        this.enabled = enabled;
        this.grantMinutes = grantMinutes;
        this.hmacSecret = hmacSecret == null || hmacSecret.isBlank() ? "local-development-community-secret" : hmacSecret;
    }

    @Transactional
    public Grant verify(String inviteCode, String networkSubject) {
        requireOperational();
        String code = inviteCode == null ? "" : inviteCode.trim();
        String subjectHash = SecureTokens.hmacSha256(hmacSecret, networkSubject);
        Map<String, Object> failures = jdbc.queryForMap("select count(*) failures, max(created_at) latest from community_gate_events where anonymous_subject_hash = ? and outcome = 'FAILURE' and expires_at > current_timestamp(6)", subjectHash);
        int failureCount = ((Number) failures.get("failures")).intValue();
        Timestamp latestFailure = (Timestamp) failures.get("latest");
        if (failureCount >= 8 && latestFailure != null && latestFailure.toInstant().isAfter(Instant.now().minus(30, ChronoUnit.MINUTES))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "COMMUNITY_GATE_COOLDOWN", "邀请码尝试次数过多，请 30 分钟后再试。");
        }
        int claimed = jdbc.update("update community_invite_batches set use_count = use_count + 1 where code_hmac = ? and state = 'ACTIVE' and valid_from <= current_timestamp(6) and expires_at > current_timestamp(6) and use_count < max_uses",
                SecureTokens.hmacSha256(hmacSecret, code));
        if (claimed != 1) {
            jdbc.update("insert into community_gate_events (id, anonymous_subject_hash, outcome, expires_at) values (?, ?, 'FAILURE', ?)",
                    Ids.ulid(), subjectHash, Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "COMMUNITY_INVITE_INVALID", "邀请码无效、已过期、已撤销或已用尽。");
        }
        String batchId = jdbc.queryForObject("select id from community_invite_batches where code_hmac = ?", String.class,
                SecureTokens.hmacSha256(hmacSecret, code));
        String rawGrant = SecureTokens.randomUrlToken();
        Instant expiresAt = Instant.now().plus(grantMinutes, ChronoUnit.MINUTES);
        jdbc.update("insert into community_access_grants (id, invite_batch_id, grant_hash, anonymous_subject_hash, expires_at) values (?, ?, ?, ?, ?)",
                Ids.ulid(), batchId, SecureTokens.sha256(rawGrant), subjectHash, Timestamp.from(expiresAt));
        jdbc.update("insert into community_gate_events (id, anonymous_subject_hash, outcome, expires_at) values (?, ?, 'SUCCESS', ?)",
                Ids.ulid(), subjectHash, Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
        return new Grant(rawGrant, expiresAt);
    }

    @Transactional(readOnly = true)
    public List<Entry> publicEntries(String rawGrant) {
        requireEnabled();
        boolean includeGated = rawGrant != null && !rawGrant.isBlank();
        if (includeGated) requireGrant(rawGrant);
        String sql = "select id, name, description, presentation_type, contact_channel, access_level, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, is_primary, sort_order from community_entries where active = true "
                + (includeGated ? "" : "and access_level = 'PUBLIC' ")
                + "order by is_primary desc, sort_order asc, id asc";
        return jdbc.query(sql, (row, index) -> {
            String id = row.getString("id");
            String access = row.getString("access_level");
            String value = row.getString("public_value");
            if ("COMMUNITY_GATED".equals(access) && row.getString("private_value_ciphertext") != null) {
                value = privateValues.decrypt(row.getString("private_value_ciphertext"), row.getString("private_value_key_id"), "COMMUNITY_ENTRY:" + id);
            }
            String mediaId = row.getString("media_asset_id");
            String mediaUrl = mediaId == null ? null : "COMMUNITY_GATED".equals(access)
                    ? "/api/v1/public/community/media/" + mediaId : "/api/v1/public/media/" + mediaId;
            return new Entry(id, row.getString("name"), row.getString("description"), row.getString("presentation_type"),
                    row.getString("contact_channel"), access, value, mediaUrl, row.getBoolean("is_primary"), row.getInt("sort_order"));
        });
    }

    @Transactional(readOnly = true)
    public void requireGrant(String rawGrant) {
        requireOperational();
        if (rawGrant == null || rawGrant.isBlank()) throw invalidGrant();
        Integer count = jdbc.queryForObject("select count(*) from community_access_grants g join community_invite_batches b on b.id = g.invite_batch_id where g.grant_hash = ? and g.expires_at > current_timestamp(6) and b.state = 'ACTIVE' and b.expires_at > current_timestamp(6)", Integer.class, SecureTokens.sha256(rawGrant));
        if (count == null || count != 1) throw invalidGrant();
    }

    @Transactional
    public EntrySummary createEntry(String name, String description, String presentation, String channel, String access,
                                    String value, String mediaAssetId, boolean publicExposureConfirmed,
                                    boolean primary, int sortOrder, String actorId, String role, String requestId) {
        requireEnabled();
        PreparedEntry prepared = prepare(null, presentation, channel, access, value, mediaAssetId,
                publicExposureConfirmed, actorId);
        String id = Ids.ulid();
        StoredValue stored = store(id, prepared.access(), prepared.value());
        if (primary) jdbc.update("update community_entries set is_primary = false, version = version + 1 where active = true and is_primary = true");
        jdbc.update("insert into community_entries (id, name, description, presentation_type, contact_channel, access_level, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, is_primary, sort_order, created_by_account_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, requireName(name), blankToNull(description), prepared.presentation(), prepared.channel(), prepared.access(),
                stored.publicValue(), stored.ciphertext(), stored.keyId(), prepared.mediaId(), primary, sortOrder, actorId);
        audit.record(actorId, role, "COMMUNITY_ENTRY_CREATED", "COMMUNITY_ENTRY", id, "{}",
                "{\"presentationType\":\"" + prepared.presentation() + "\",\"contactChannel\":\"" + prepared.channel()
                        + "\",\"accessLevel\":\"" + prepared.access() + "\",\"primary\":" + primary + "}", null, requestId);
        return summary(id);
    }

    @Transactional
    public EntrySummary updateEntry(String id, long expectedVersion, String name, String description,
                                    String presentation, String channel, String access, String value,
                                    String mediaAssetId, boolean publicExposureConfirmed, boolean primary, int sortOrder,
                                    String actorId, String role, String requestId) {
        requireEnabled();
        Map<String, Object> current = lockEntry(id);
        if (((Number) current.get("version")).longValue() != expectedVersion) throw new ApiException(HttpStatus.CONFLICT, "COMMUNITY_ENTRY_VERSION_CONFLICT", "社群入口已被其他操作更新，请刷新后重试。");
        if (!Boolean.TRUE.equals(current.get("active"))) throw new ApiException(HttpStatus.CONFLICT, "COMMUNITY_ENTRY_INACTIVE", "已停用的社群入口不能再编辑。");
        PreparedEntry prepared = prepare(current, presentation, channel, access, value, mediaAssetId,
                publicExposureConfirmed, actorId);
        StoredValue stored = storedValue(id, current, prepared);
        if (primary) jdbc.update("update community_entries set is_primary = false, version = version + 1 where active = true and is_primary = true and id <> ?", id);
        int updated = jdbc.update("""
                update community_entries
                set name = ?, description = ?, presentation_type = ?, contact_channel = ?, access_level = ?,
                    public_value = ?, private_value_ciphertext = ?, private_value_key_id = ?, media_asset_id = ?,
                    is_primary = ?, sort_order = ?, version = version + 1
                where id = ? and version = ? and active = true
                """, requireName(name), blankToNull(description), prepared.presentation(), prepared.channel(), prepared.access(),
                stored.publicValue(), stored.ciphertext(), stored.keyId(), prepared.mediaId(), primary, sortOrder, id, expectedVersion);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "COMMUNITY_ENTRY_VERSION_CONFLICT", "社群入口已被其他操作更新，请刷新后重试。");
        audit.record(actorId, role, "COMMUNITY_ENTRY_UPDATED", "COMMUNITY_ENTRY", id, "{}",
                "{\"presentationType\":\"" + prepared.presentation() + "\",\"contactChannel\":\"" + prepared.channel()
                        + "\",\"accessLevel\":\"" + prepared.access() + "\",\"primary\":" + primary + "}", null, requestId);
        return summary(id);
    }

    @Transactional
    public EntrySummary deactivateEntry(String id, long expectedVersion, String reason, String actorId, String role, String requestId) {
        lockEntry(id);
        int updated = jdbc.update("update community_entries set active = false, is_primary = false, version = version + 1 where id = ? and version = ? and active = true", id, expectedVersion);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "COMMUNITY_ENTRY_VERSION_CONFLICT", "社群入口不存在、已停用或版本已变化。");
        audit.record(actorId, role, "COMMUNITY_ENTRY_DEACTIVATED", "COMMUNITY_ENTRY", id, "{\"active\":true}", "{\"active\":false}", reason, requestId);
        return summary(id);
    }

    @Transactional(readOnly = true)
    public List<EntrySummary> entries() {
        return jdbc.query("select id, name, description, presentation_type, contact_channel, access_level, media_asset_id, is_primary, active, sort_order, version from community_entries order by active desc, is_primary desc, sort_order asc", (row, index) -> mapSummary(row));
    }

    @Transactional
    public CreatedInviteBatch createInviteBatch(String label, int expiresInDays, int maxUses, String actorId, String role, String requestId) {
        requireOperational();
        int days = expiresInDays == 0 ? 30 : expiresInDays;
        int uses = maxUses == 0 ? 500 : maxUses;
        if (days < 1 || days > 90 || uses < 1 || uses > 10000) throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_INVITE_LIMIT_INVALID", "邀请码有效期或使用次数超出允许范围。");
        String id = Ids.ulid();
        String rawCode = SecureTokens.randomUrlToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(days, ChronoUnit.DAYS);
        jdbc.update("insert into community_invite_batches (id, label, code_hmac, valid_from, expires_at, max_uses, created_by_account_id) values (?, ?, ?, ?, ?, ?, ?)",
                id, label.trim(), SecureTokens.hmacSha256(hmacSecret, rawCode), Timestamp.from(now), Timestamp.from(expiresAt), uses, actorId);
        audit.record(actorId, role, "COMMUNITY_INVITE_CREATED", "COMMUNITY_INVITE_BATCH", id, "{}", "{\"maxUses\":" + uses + "}", null, requestId);
        return new CreatedInviteBatch(id, label.trim(), rawCode, expiresAt, uses);
    }

    @Transactional(readOnly = true)
    public List<InviteBatch> inviteBatches() {
        return jdbc.query("select id, label, valid_from, expires_at, max_uses, use_count, case when state = 'ACTIVE' and expires_at <= current_timestamp(6) then 'EXPIRED' else state end display_state, created_at from community_invite_batches order by created_at desc", (row, index) ->
                new InviteBatch(row.getString("id"), row.getString("label"), row.getTimestamp("valid_from").toInstant(), row.getTimestamp("expires_at").toInstant(), row.getInt("max_uses"), row.getInt("use_count"), row.getString("display_state"), row.getTimestamp("created_at").toInstant()));
    }

    @Transactional
    public InviteBatch revokeInviteBatch(String id, String reason, String actorId, String role, String requestId) {
        int updated = jdbc.update("update community_invite_batches set state = 'REVOKED', revoked_at = current_timestamp(6) where id = ? and state = 'ACTIVE'", id);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "COMMUNITY_INVITE_NOT_ACTIVE", "邀请码批次不存在或已失效。");
        jdbc.update("delete from community_access_grants where invite_batch_id = ?", id);
        audit.record(actorId, role, "COMMUNITY_INVITE_REVOKED", "COMMUNITY_INVITE_BATCH", id, "{\"state\":\"ACTIVE\"}", "{\"state\":\"REVOKED\"}", reason, requestId);
        return inviteBatches().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private PreparedEntry prepare(Map<String, Object> current, String rawPresentation, String rawChannel,
                                  String rawAccess, String rawValue, String rawMediaId,
                                  boolean publicExposureConfirmed, String actorId) {
        String presentation = normalized(rawPresentation);
        String channel = normalized(rawChannel);
        String access = normalized(rawAccess);
        if (!PRESENTATIONS.contains(presentation) || !CHANNELS.contains(channel) || !ACCESS.contains(access)) throw invalidType();
        if ("URL".equals(channel) && !"LINK".equals(presentation)) throw invalidType();
        if ("LINK".equals(presentation) && !Set.of("URL", "OTHER").contains(channel)) throw invalidType();
        String value = blankToNull(rawValue);
        String mediaId = blankToNull(rawMediaId);
        if (mediaId == null && current != null && "QR_CODE".equals(presentation)
                && "QR_CODE".equals(Objects.toString(current.get("presentation_type")))) {
            mediaId = Objects.toString(current.get("media_asset_id"), null);
        }
        if ("QR_CODE".equals(presentation) && mediaId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_ENTRY_MEDIA_REQUIRED", "二维码入口必须选择图片。");
        if (!"QR_CODE".equals(presentation) && value == null && !canPreserveValue(current, presentation, channel, access)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_ENTRY_VALUE_REQUIRED", "请填写社群入口内容。");
        }
        if ("LINK".equals(presentation) && value != null) sources.requireAllowed(value);
        validateChannel(channel, value);
        if ("PUBLIC".equals(access) && (SENSITIVE_CHANNELS.contains(channel) || "QR_CODE".equals(presentation))
                && !publicExposureConfirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_ENTRY_PUBLIC_EXPOSURE_CONFIRMATION_REQUIRED", "公开个人联系方式或二维码前必须确认隐私风险。");
        }
        if (mediaId != null) {
            Integer owned = jdbc.queryForObject("select count(*) from media_assets where id = ? and lifecycle_state = 'ACTIVE' and owner_account_id = ? and storage_key like 'community/%'", Integer.class, mediaId, actorId);
            boolean alreadyAttached = current != null && mediaId.equals(Objects.toString(current.get("media_asset_id"), null));
            if (!alreadyAttached && (owned == null || owned != 1)) throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_ENTRY_MEDIA_INVALID", "二维码图片不存在或不属于当前账号。");
        }
        return new PreparedEntry(presentation, channel, access, value, mediaId);
    }

    private StoredValue storedValue(String id, Map<String, Object> current, PreparedEntry prepared) {
        if ("QR_CODE".equals(prepared.presentation())) return new StoredValue(null, null, null);
        if (prepared.value() == null && canPreserveValue(current, prepared.presentation(), prepared.channel(), prepared.access())) {
            return new StoredValue(Objects.toString(current.get("public_value"), null),
                    Objects.toString(current.get("private_value_ciphertext"), null),
                    Objects.toString(current.get("private_value_key_id"), null));
        }
        return store(id, prepared.access(), prepared.value());
    }

    private StoredValue store(String id, String access, String value) {
        if (value == null) return new StoredValue(null, null, null);
        if ("PUBLIC".equals(access)) return new StoredValue(value, null, null);
        privateValues.requireKeys();
        PrivateValueCipher.EncryptedValue encrypted = privateValues.encrypt(value, "COMMUNITY_ENTRY:" + id);
        return new StoredValue(null, encrypted.ciphertext(), encrypted.keyId());
    }

    private boolean canPreserveValue(Map<String, Object> current, String presentation, String channel, String access) {
        return current != null && presentation.equals(Objects.toString(current.get("presentation_type")))
                && channel.equals(Objects.toString(current.get("contact_channel")))
                && access.equals(Objects.toString(current.get("access_level")));
    }

    private void validateChannel(String channel, String value) {
        if (value == null) return;
        if ("EMAIL".equals(channel) && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_ENTRY_EMAIL_INVALID", "邮箱格式无效。");
        if ("PHONE".equals(channel) && !value.matches("^[+0-9()\\-\\s]{5,32}$")) throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_ENTRY_PHONE_INVALID", "电话号码格式无效。");
    }

    private Map<String, Object> lockEntry(String id) {
        try {
            return jdbc.queryForMap("select id, version, active, presentation_type, contact_channel, access_level, public_value, private_value_ciphertext, private_value_key_id, media_asset_id from community_entries where id = ? for update", id);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_ENTRY_NOT_FOUND", "社群入口不存在。");
        }
    }

    private EntrySummary summary(String id) {
        return jdbc.queryForObject("select id, name, description, presentation_type, contact_channel, access_level, media_asset_id, is_primary, active, sort_order, version from community_entries where id = ?",
                (row, index) -> mapSummary(row), id);
    }

    private EntrySummary mapSummary(java.sql.ResultSet row) throws java.sql.SQLException {
        return new EntrySummary(row.getString("id"), row.getString("name"), row.getString("description"),
                row.getString("presentation_type"), row.getString("contact_channel"), row.getString("access_level"),
                row.getString("media_asset_id"), row.getBoolean("is_primary"), row.getBoolean("active"),
                row.getInt("sort_order"), row.getLong("version"));
    }

    public boolean isOperational() { return enabled && privateValues.isKeyRingOperational(); }
    public void requireOperational() { requireEnabled(); privateValues.requireKeys(); }
    private void requireEnabled() { if (!enabled) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "COMMUNITY_NOT_CONFIGURED", "校内社群入口尚未启用。"); }
    private ApiException invalidGrant() { return new ApiException(HttpStatus.UNAUTHORIZED, "COMMUNITY_GRANT_INVALID", "社群授权无效或已过期，请重新验证邀请码。"); }
    private ApiException invalidType() { return new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_ENTRY_TYPE_INVALID", "社群入口展示形式、联系渠道或访问级别无效。"); }
    private String requireName(String value) { String result = value == null ? "" : value.trim(); if (result.isBlank() || result.length() > 120) throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_ENTRY_NAME_INVALID", "请填写有效的入口名称。"); return result; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalized(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private boolean isProduction(Environment environment) { for (String profile : environment.getActiveProfiles()) if ("production".equals(profile)) return true; return false; }

    private record PreparedEntry(String presentation, String channel, String access, String value, String mediaId) { }
    private record StoredValue(String publicValue, String ciphertext, String keyId) { }
}
