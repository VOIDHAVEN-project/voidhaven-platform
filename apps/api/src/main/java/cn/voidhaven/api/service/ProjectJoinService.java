package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 项目组行动入口：与社群入口逐条增删改对称，但不复用社群入口表，也不设唯一主入口。
 * 受控入口的读取权限复用社群邀请码授权表。
 */
@Service
public class ProjectJoinService {
    public record Entry(String id, String name, String description, String presentationType, String contactChannel,
                        String accessLevel, String value, String mediaUrl, int sortOrder) { }
    public record EntrySummary(String id, String name, String description, String presentationType,
                               String contactChannel, String accessLevel, String mediaAssetId,
                               boolean active, int sortOrder, long version) { }

    private static final Set<String> PRESENTATIONS = Set.of("LINK", "QR_CODE", "TEXT");
    private static final Set<String> CHANNELS = Set.of("URL", "EMAIL", "WECHAT", "QQ", "PHONE", "OTHER");
    private static final Set<String> ACCESS = Set.of("PUBLIC", "COMMUNITY_GATED");
    private static final Set<String> SENSITIVE_CHANNELS = Set.of("EMAIL", "WECHAT", "QQ", "PHONE");

    private final JdbcTemplate jdbc;
    private final PrivateValueCipher privateValues;
    private final ContentSourcePolicy sources;
    private final AuditService audit;
    private final CommunityService community;

    public ProjectJoinService(JdbcTemplate jdbc, PrivateValueCipher privateValues, ContentSourcePolicy sources,
                              AuditService audit, CommunityService community) {
        this.jdbc = jdbc;
        this.privateValues = privateValues;
        this.sources = sources;
        this.audit = audit;
        this.community = community;
    }

    @Transactional(readOnly = true)
    public List<Entry> publicEntries(String rawGrant) {
        boolean includeGated = rawGrant != null && !rawGrant.isBlank();
        if (includeGated) community.requireGrant(rawGrant);
        String sql = "select id, name, description, presentation_type, contact_channel, access_level, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, sort_order from project_join_entries where active = true "
                + (includeGated ? "" : "and access_level = 'PUBLIC' ")
                + "order by sort_order asc, id asc";
        return jdbc.query(sql, (row, index) -> {
            String id = row.getString("id");
            String access = row.getString("access_level");
            String value = row.getString("public_value");
            if ("COMMUNITY_GATED".equals(access) && row.getString("private_value_ciphertext") != null) {
                value = privateValues.decrypt(row.getString("private_value_ciphertext"),
                        row.getString("private_value_key_id"), "PROJECT_JOIN_ENTRY:" + id);
            }
            String mediaId = row.getString("media_asset_id");
            String mediaUrl = mediaId == null ? null : "COMMUNITY_GATED".equals(access)
                    ? "/api/v1/public/community/media/" + mediaId : "/api/v1/public/media/" + mediaId;
            return new Entry(id, row.getString("name"), row.getString("description"),
                    row.getString("presentation_type"), row.getString("contact_channel"), access, value,
                    mediaUrl, row.getInt("sort_order"));
        });
    }

    @Transactional(readOnly = true)
    public boolean hasActiveEntries() {
        Integer count = jdbc.queryForObject("select count(*) from project_join_entries where active = true", Integer.class);
        return count != null && count > 0;
    }

    @Transactional
    public EntrySummary createEntry(String name, String description, String presentation, String channel, String access,
                                    String value, String mediaAssetId, boolean publicExposureConfirmed, int sortOrder,
                                    String actorId, String role, String requestId) {
        PreparedEntry prepared = prepare(null, presentation, channel, access, value, mediaAssetId,
                publicExposureConfirmed, actorId);
        String id = Ids.ulid();
        StoredValue stored = store(id, prepared.access(), prepared.value());
        jdbc.update("insert into project_join_entries (id, name, description, presentation_type, contact_channel, access_level, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, sort_order, created_by_account_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, requireName(name), blankToNull(description), prepared.presentation(), prepared.channel(),
                prepared.access(), stored.publicValue(), stored.ciphertext(), stored.keyId(), prepared.mediaId(),
                sortOrder, actorId);
        audit.record(actorId, role, "PROJECT_JOIN_ENTRY_CREATED", "PROJECT_JOIN_ENTRY", id, "{}",
                "{\"presentationType\":\"" + prepared.presentation() + "\",\"contactChannel\":\""
                        + prepared.channel() + "\",\"accessLevel\":\"" + prepared.access() + "\"}", null, requestId);
        return summary(id);
    }

    @Transactional
    public EntrySummary updateEntry(String id, long expectedVersion, String name, String description,
                                    String presentation, String channel, String access, String value,
                                    String mediaAssetId, boolean publicExposureConfirmed, int sortOrder,
                                    String actorId, String role, String requestId) {
        Map<String, Object> current = lockEntry(id);
        if (((Number) current.get("version")).longValue() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_JOIN_ENTRY_VERSION_CONFLICT",
                    "项目组入口已被其他操作更新，请刷新后重试。");
        }
        if (!Boolean.TRUE.equals(current.get("active"))) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_JOIN_ENTRY_INACTIVE", "已停用的项目组入口不能再编辑。");
        }
        PreparedEntry prepared = prepare(current, presentation, channel, access, value, mediaAssetId,
                publicExposureConfirmed, actorId);
        StoredValue stored = storedValue(id, current, prepared);
        int updated = jdbc.update("""
                update project_join_entries
                set name = ?, description = ?, presentation_type = ?, contact_channel = ?, access_level = ?,
                    public_value = ?, private_value_ciphertext = ?, private_value_key_id = ?, media_asset_id = ?,
                    sort_order = ?, version = version + 1
                where id = ? and version = ? and active = true
                """, requireName(name), blankToNull(description), prepared.presentation(), prepared.channel(),
                prepared.access(), stored.publicValue(), stored.ciphertext(), stored.keyId(), prepared.mediaId(),
                sortOrder, id, expectedVersion);
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_JOIN_ENTRY_VERSION_CONFLICT",
                    "项目组入口已被其他操作更新，请刷新后重试。");
        }
        audit.record(actorId, role, "PROJECT_JOIN_ENTRY_UPDATED", "PROJECT_JOIN_ENTRY", id, "{}",
                "{\"presentationType\":\"" + prepared.presentation() + "\",\"contactChannel\":\""
                        + prepared.channel() + "\",\"accessLevel\":\"" + prepared.access() + "\"}", null, requestId);
        return summary(id);
    }

    @Transactional
    public EntrySummary deactivateEntry(String id, long expectedVersion, String reason, String actorId, String role,
                                        String requestId) {
        lockEntry(id);
        int updated = jdbc.update("update project_join_entries set active = false, version = version + 1 where id = ? and version = ? and active = true",
                id, expectedVersion);
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_JOIN_ENTRY_VERSION_CONFLICT",
                    "项目组入口不存在、已停用或版本已变化。");
        }
        audit.record(actorId, role, "PROJECT_JOIN_ENTRY_DEACTIVATED", "PROJECT_JOIN_ENTRY", id,
                "{\"active\":true}", "{\"active\":false}", reason, requestId);
        return summary(id);
    }

    @Transactional(readOnly = true)
    public List<EntrySummary> entries() {
        return jdbc.query("select id, name, description, presentation_type, contact_channel, access_level, media_asset_id, active, sort_order, version from project_join_entries order by active desc, sort_order asc",
                (row, index) -> mapSummary(row));
    }

    private PreparedEntry prepare(Map<String, Object> current, String rawPresentation, String rawChannel,
                                  String rawAccess, String rawValue, String rawMediaId,
                                  boolean publicExposureConfirmed, String actorId) {
        String presentation = normalized(rawPresentation);
        String channel = normalized(rawChannel);
        String access = normalized(rawAccess);
        if (!PRESENTATIONS.contains(presentation) || !CHANNELS.contains(channel) || !ACCESS.contains(access)) {
            throw invalidType();
        }
        if ("URL".equals(channel) && !"LINK".equals(presentation)) throw invalidType();
        if ("LINK".equals(presentation) && !Set.of("URL", "OTHER").contains(channel)) throw invalidType();
        String value = blankToNull(rawValue);
        String mediaId = blankToNull(rawMediaId);
        if (mediaId == null && current != null && "QR_CODE".equals(presentation)
                && "QR_CODE".equals(Objects.toString(current.get("presentation_type")))) {
            mediaId = Objects.toString(current.get("media_asset_id"), null);
        }
        if ("QR_CODE".equals(presentation) && mediaId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_JOIN_ENTRY_MEDIA_REQUIRED", "二维码入口必须选择图片。");
        }
        if (!"QR_CODE".equals(presentation) && value == null && !canPreserveValue(current, presentation, channel, access)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_JOIN_ENTRY_VALUE_REQUIRED", "请填写项目组入口内容。");
        }
        if ("LINK".equals(presentation) && value != null) sources.requireAllowed(value);
        validateChannel(channel, value);
        if ("PUBLIC".equals(access) && (SENSITIVE_CHANNELS.contains(channel) || "QR_CODE".equals(presentation))
                && !publicExposureConfirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_JOIN_ENTRY_PUBLIC_EXPOSURE_CONFIRMATION_REQUIRED",
                    "公开个人联系方式或二维码前必须确认隐私风险。");
        }
        if (mediaId != null) {
            Integer owned = jdbc.queryForObject("select count(*) from media_assets where id = ? and lifecycle_state = 'ACTIVE' and owner_account_id = ? and storage_key like 'community/%'",
                    Integer.class, mediaId, actorId);
            boolean alreadyAttached = current != null && mediaId.equals(Objects.toString(current.get("media_asset_id"), null));
            if (!alreadyAttached && (owned == null || owned != 1)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_JOIN_ENTRY_MEDIA_INVALID",
                        "二维码图片不存在或不属于当前账号。");
            }
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
        PrivateValueCipher.EncryptedValue encrypted = privateValues.encrypt(value, "PROJECT_JOIN_ENTRY:" + id);
        return new StoredValue(null, encrypted.ciphertext(), encrypted.keyId());
    }

    private boolean canPreserveValue(Map<String, Object> current, String presentation, String channel, String access) {
        return current != null && presentation.equals(Objects.toString(current.get("presentation_type")))
                && channel.equals(Objects.toString(current.get("contact_channel")))
                && access.equals(Objects.toString(current.get("access_level")));
    }

    private void validateChannel(String channel, String value) {
        if (value == null) return;
        if ("EMAIL".equals(channel) && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_JOIN_ENTRY_EMAIL_INVALID", "邮箱格式无效。");
        }
        if ("PHONE".equals(channel) && !value.matches("^[+0-9()\\-\\s]{5,32}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_JOIN_ENTRY_PHONE_INVALID", "电话号码格式无效。");
        }
    }

    private Map<String, Object> lockEntry(String id) {
        try {
            return jdbc.queryForMap("select id, version, active, presentation_type, contact_channel, access_level, public_value, private_value_ciphertext, private_value_key_id, media_asset_id from project_join_entries where id = ? for update", id);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_JOIN_ENTRY_NOT_FOUND", "项目组入口不存在。");
        }
    }

    private EntrySummary summary(String id) {
        return jdbc.queryForObject("select id, name, description, presentation_type, contact_channel, access_level, media_asset_id, active, sort_order, version from project_join_entries where id = ?",
                (row, index) -> mapSummary(row), id);
    }

    private EntrySummary mapSummary(java.sql.ResultSet row) throws java.sql.SQLException {
        return new EntrySummary(row.getString("id"), row.getString("name"), row.getString("description"),
                row.getString("presentation_type"), row.getString("contact_channel"), row.getString("access_level"),
                row.getString("media_asset_id"), row.getBoolean("active"), row.getInt("sort_order"),
                row.getLong("version"));
    }

    private ApiException invalidType() {
        return new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_JOIN_ENTRY_TYPE_INVALID",
                "项目组入口展示形式、联系渠道或访问级别无效。");
    }

    private String requireName(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank() || result.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_JOIN_ENTRY_NAME_INVALID", "请填写有效的入口名称。");
        }
        return result;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PreparedEntry(String presentation, String channel, String access, String value, String mediaId) { }
    private record StoredValue(String publicValue, String ciphertext, String keyId) { }
}
