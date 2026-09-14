package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ContentActionService {
    public record ActionInput(String actionType, String accessLevel, String label, String value,
                              String mediaAssetId, boolean enabled, int sortOrder) { }
    public record PublicAction(String id, String actionType, String label, String value, String mediaUrl,
                               String accessLevel) { }
    public record ActionSet(String contentId, long version, List<PublicAction> actions) { }
    public record ManagedAction(String id, String actionType, String accessLevel, String label, String value,
                                String mediaAssetId, boolean enabled, int sortOrder) { }

    private static final Set<String> TYPES = Set.of("APPLICATION_URL", "PUBLIC_EMAIL", "QQ", "WECHAT", "QQ_GROUP", "WECHAT_GROUP", "QR_CODE");
    private static final Set<String> ACCESS = Set.of("PUBLIC", "COMMUNITY_GATED");
    private final JdbcTemplate jdbc;
    private final PrivateValueCipher privateValues;
    private final ContentSourcePolicy sources;
    private final CommunityService community;
    private final AuditService audit;

    public ContentActionService(JdbcTemplate jdbc, PrivateValueCipher privateValues, ContentSourcePolicy sources,
                                CommunityService community, AuditService audit) {
        this.jdbc = jdbc;
        this.privateValues = privateValues;
        this.sources = sources;
        this.community = community;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PublicAction> publicActions(String contentId) {
        List<Map<String, Object>> content = jdbc.queryForList("select published_revision_id, operational_status from content_items where id = ? and kind = 'RECRUITMENT' and visibility = 'PUBLIC' and record_state = 'ACTIVE'", contentId);
        if (content.isEmpty()) return List.of();
        String status = Objects.toString(content.get(0).get("operational_status"), "OPEN");
        if (!"OPEN".equals(status)) return List.of();
        return readActions(Objects.toString(content.get(0).get("published_revision_id")), false);
    }

    @Transactional(readOnly = true)
    public List<ManagedAction> managedActions(String contentId, String ownerProfileId) {
        List<Map<String, Object>> contents = ownerProfileId == null
                ? jdbc.queryForList("select current_revision_id from content_items where id = ? and kind = 'RECRUITMENT' and record_state = 'ACTIVE'", contentId)
                : jdbc.queryForList("select current_revision_id from content_items where id = ? and kind = 'RECRUITMENT' and record_state = 'ACTIVE' and owner_partner_profile_id = ?", contentId, ownerProfileId);
        if (contents.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "招募内容不存在或无权编辑。");
        String revisionId = Objects.toString(contents.get(0).get("current_revision_id"));
        return jdbc.query("select id, action_type, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order from content_action_entries where revision_id = ? order by sort_order asc, id asc", (row, index) -> {
            String access = row.getString("access_level");
            String value = row.getString("public_value");
            if ("COMMUNITY_GATED".equals(access) && row.getString("private_value_ciphertext") != null) value = privateValues.decrypt(row.getString("private_value_ciphertext"), row.getString("private_value_key_id"), "CONTENT_ACTION");
            return new ManagedAction(row.getString("id"), row.getString("action_type"), access, row.getString("label"), value,
                    row.getString("media_asset_id"), row.getBoolean("enabled"), row.getInt("sort_order"));
        }, revisionId);
    }

    @Transactional(readOnly = true)
    public List<PublicAction> gatedActions(String contentId, String grant) {
        community.requireGrant(grant);
        List<Map<String, Object>> content = jdbc.queryForList("select published_revision_id, operational_status from content_items where id = ? and kind = 'RECRUITMENT' and visibility = 'PUBLIC' and record_state = 'ACTIVE'", contentId);
        if (content.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。");
        if (!"OPEN".equals(Objects.toString(content.get(0).get("operational_status"), "OPEN"))) return List.of();
        return readActions(Objects.toString(content.get(0).get("published_revision_id")), true).stream()
                .filter(action -> "COMMUNITY_GATED".equals(action.accessLevel())).toList();
    }

    @Transactional
    public ActionSet replace(String contentId, String ownerProfileId, long expectedVersion, List<ActionInput> inputs,
                             String actorId, String role, String requestId) {
        Map<String, Object> content = requireEditable(contentId, ownerProfileId);
        long currentVersion = ((Number) content.get("version")).longValue();
        if (currentVersion != expectedVersion) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "内容已被其他操作更新，请刷新后重试。");
        if (!"RECRUITMENT".equals(Objects.toString(content.get("kind")))) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTIONS_UNSUPPORTED", "只有招募内容可以配置行动入口。");
        String revisionId = Objects.toString(content.get("current_revision_id"));
        List<PreparedAction> prepared = new ArrayList<>();
        int index = 0;
        for (ActionInput input : inputs == null ? List.<ActionInput>of() : inputs) prepared.add(prepare(input, index++));
        jdbc.update("delete from content_action_entries where revision_id = ?", revisionId);
        for (PreparedAction action : prepared) {
            jdbc.update("insert into content_action_entries (id, revision_id, action_type, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    action.id(), revisionId, action.type(), action.access(), action.label(), action.publicValue(), action.ciphertext(), action.keyId(), action.mediaId(), action.enabled(), action.sortOrder());
        }
        int updated = jdbc.update("update content_items set version = version + 1, updated_at = current_timestamp(6) where id = ? and version = ?", contentId, expectedVersion);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "内容已被其他操作更新，请刷新后重试。");
        audit.record(actorId, role, "CONTENT_ACTIONS_REPLACED", "CONTENT_ITEM", contentId, "{}", "{\"count\":" + prepared.size() + "}", null, requestId);
        return new ActionSet(contentId, expectedVersion + 1, readActions(revisionId, true));
    }

    public boolean hasEnabledAction(String revisionId) {
        Integer count = jdbc.queryForObject("select count(*) from content_action_entries where revision_id = ? and enabled = true", Integer.class, revisionId);
        return count != null && count > 0;
    }

    public void copyActions(String fromRevisionId, String toRevisionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select action_type, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order from content_action_entries where revision_id = ?", fromRevisionId);
        for (Map<String, Object> row : rows) {
            jdbc.update("insert into content_action_entries (id, revision_id, action_type, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Ids.ulid(), toRevisionId, row.get("action_type"), row.get("access_level"), row.get("label"), row.get("public_value"), row.get("private_value_ciphertext"), row.get("private_value_key_id"), row.get("media_asset_id"), row.get("enabled"), row.get("sort_order"));
        }
    }

    private List<PublicAction> readActions(String revisionId, boolean includeGated) {
        return jdbc.query("select id, action_type, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id from content_action_entries where revision_id = ? and enabled = true " + (includeGated ? "" : "and access_level = 'PUBLIC' ") + "order by sort_order asc, id asc", (row, index) -> {
            String id = row.getString("id");
            String access = row.getString("access_level");
            String value = row.getString("public_value");
            if (includeGated && "COMMUNITY_GATED".equals(access) && row.getString("private_value_ciphertext") != null) {
                value = privateValues.decrypt(row.getString("private_value_ciphertext"), row.getString("private_value_key_id"), "CONTENT_ACTION");
            }
            String mediaId = row.getString("media_asset_id");
            return new PublicAction(id, row.getString("action_type"), row.getString("label"), value,
                    mediaId == null ? null : "/api/v1/public/community/media/" + mediaId, access);
        }, revisionId);
    }

    private PreparedAction prepare(ActionInput input, int fallbackOrder) {
        String type = input.actionType() == null ? "" : input.actionType().trim();
        String access = input.accessLevel() == null ? "" : input.accessLevel().trim();
        if (!TYPES.contains(type) || !ACCESS.contains(access)) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_INVALID", "行动入口类型或访问级别无效。");
        String label = input.label() == null ? "" : input.label().trim();
        if (label.isBlank() || label.length() > 120) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_LABEL_INVALID", "请填写有效的行动入口名称。");
        String value = blankToNull(input.value());
        String mediaId = blankToNull(input.mediaAssetId());
        if ("QR_CODE".equals(type) && mediaId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_MEDIA_REQUIRED", "二维码行动入口必须选择图片。");
        if (!"QR_CODE".equals(type) && value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_VALUE_REQUIRED", "请填写行动入口内容。");
        if ("PUBLIC".equals(access)) {
            if (!Set.of("APPLICATION_URL", "PUBLIC_EMAIL").contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_ACCESS_INVALID", "个人账号、群号和二维码必须通过社群验证后展示。");
            if ("APPLICATION_URL".equals(type)) sources.requireAllowed(value);
            if ("PUBLIC_EMAIL".equals(type) && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_EMAIL_INVALID", "公开邮箱格式无效。");
            return new PreparedAction(Ids.ulid(), type, access, label, value, null, null, null, input.enabled(), input.sortOrder() < 0 ? fallbackOrder : input.sortOrder());
        }
        community.requireOperational();
        PrivateValueCipher.EncryptedValue encrypted = value == null ? null : privateValues.encrypt(value, "CONTENT_ACTION");
        return new PreparedAction(Ids.ulid(), type, access, label, null, encrypted == null ? null : encrypted.ciphertext(), encrypted == null ? null : encrypted.keyId(), mediaId, input.enabled(), input.sortOrder() < 0 ? fallbackOrder : input.sortOrder());
    }

    private Map<String, Object> requireEditable(String contentId, String ownerProfileId) {
        List<Map<String, Object>> rows = ownerProfileId == null
                ? jdbc.queryForList("select id, kind, current_revision_id, version, record_state from content_items where id = ? for update", contentId)
                : jdbc.queryForList("select id, kind, current_revision_id, version, record_state from content_items where id = ? and owner_partner_profile_id = ? for update", contentId, ownerProfileId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或无权编辑。");
        if (!"ACTIVE".equals(Objects.toString(rows.get(0).get("record_state")))) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_NOT_EDITABLE", "该内容当前不可编辑。");
        return rows.get(0);
    }

    private record PreparedAction(String id, String type, String access, String label, String publicValue,
                                  String ciphertext, String keyId, String mediaId, boolean enabled, int sortOrder) { }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
