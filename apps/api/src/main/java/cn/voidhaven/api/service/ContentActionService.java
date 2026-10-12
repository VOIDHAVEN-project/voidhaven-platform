package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentRevision;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
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
    public record ActionInput(String presentationType, String contactChannel, String accessLevel, String label,
                              String value, String mediaAssetId, boolean publicExposureConfirmed,
                              boolean enabled, int sortOrder) { }
    public record PublicAction(String id, String presentationType, String contactChannel, String label, String value,
                               String mediaUrl, String accessLevel) { }
    public record ManagedAction(String id, String presentationType, String contactChannel, String accessLevel,
                                String label, String value, String mediaAssetId, boolean enabled, int sortOrder) { }
    public record ActionSet(String contentId, long version, String workflowStatus, boolean candidateCreated,
                            List<ManagedAction> actions) { }

    private static final Set<String> PRESENTATIONS = Set.of("LINK", "QR_CODE", "TEXT");
    private static final Set<String> CHANNELS = Set.of("URL", "EMAIL", "WECHAT", "QQ", "PHONE", "OTHER");
    private static final Set<String> ACCESS = Set.of("PUBLIC", "COMMUNITY_GATED");
    private static final Set<String> SENSITIVE_CHANNELS = Set.of("EMAIL", "WECHAT", "QQ", "PHONE");

    private final JdbcTemplate jdbc;
    private final ContentItemRepository items;
    private final ContentRevisionCandidateService candidates;
    private final MediaService media;
    private final PrivateValueCipher privateValues;
    private final ContentSourcePolicy sources;
    private final CommunityService community;
    private final AuditService audit;
    private AdminAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    void authorization(AdminAuthorizationService authorization) { this.authorization = authorization; }

    public ContentActionService(JdbcTemplate jdbc, ContentItemRepository items,
                                ContentRevisionCandidateService candidates, MediaService media,
                                PrivateValueCipher privateValues, ContentSourcePolicy sources,
                                CommunityService community, AuditService audit) {
        this.jdbc = jdbc;
        this.items = items;
        this.candidates = candidates;
        this.media = media;
        this.privateValues = privateValues;
        this.sources = sources;
        this.community = community;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PublicAction> publicActions(String contentId) {
        List<Map<String, Object>> content = jdbc.queryForList("select published_revision_id, operational_status from content_items where id = ? and kind = 'RECRUITMENT' and visibility = 'PUBLIC' and record_state = 'ACTIVE'", contentId);
        if (content.isEmpty() || !"OPEN".equals(Objects.toString(content.get(0).get("operational_status"), "OPEN"))) return List.of();
        return readPublicActions(Objects.toString(content.get(0).get("published_revision_id")), "PUBLIC");
    }

    @Transactional(readOnly = true)
    public List<ManagedAction> managedActions(String contentId, String ownerProfileId) {
        ContentItem item = requireOwned(contentId, ownerProfileId, false);
        boolean privateAllowed = ownerProfileId != null || authorization == null || authorization.allows("PRIVATE_DATA_READ", item.getModuleKey(), item.getOwnerPartnerProfileId());
        return readManagedActions(item.getCurrentRevision().getId(), privateAllowed);
    }

    @Transactional(readOnly = true)
    public List<PublicAction> gatedActions(String contentId, String grant) {
        community.requireGrant(grant);
        List<Map<String, Object>> content = jdbc.queryForList("select published_revision_id, operational_status from content_items where id = ? and kind = 'RECRUITMENT' and visibility = 'PUBLIC' and record_state = 'ACTIVE'", contentId);
        if (content.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。");
        if (!"OPEN".equals(Objects.toString(content.get(0).get("operational_status"), "OPEN"))) return List.of();
        return readPublicActions(Objects.toString(content.get(0).get("published_revision_id")), "COMMUNITY_GATED");
    }

    @Transactional
    public ActionSet replace(String contentId, String ownerProfileId, long expectedVersion, List<ActionInput> inputs,
                             String actorId, String role, String requestId) {
        ContentItem item = requireOwned(contentId, ownerProfileId, true);
        if (ownerProfileId == null && authorization != null) {
            authorization.requireContent("CONTENT_EDIT", contentId);
            Integer gated = jdbc.queryForObject("select count(*) from content_action_entries where revision_id = ? and access_level = 'COMMUNITY_GATED'", Integer.class, item.getCurrentRevision().getId());
            if ((gated != null && gated > 0) || (inputs != null && inputs.stream().anyMatch(input -> "COMMUNITY_GATED".equals(input.accessLevel()))))
                authorization.requireContent("PRIVATE_DATA_READ", contentId);
        }
        if (!Objects.equals(item.getVersion(), expectedVersion)) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "内容已被其他操作更新，请刷新后重试。");
        if (!"RECRUITMENT".equals(item.getKind().name())) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTIONS_UNSUPPORTED", "只有招募内容可以配置行动入口。");
        WorkflowStatus status = item.getWorkflowStatus();
        if (status != WorkflowStatus.DRAFT && status != WorkflowStatus.CHANGES_REQUESTED && status != WorkflowStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTENT_ACTIONS_NOT_EDITABLE", "审核中或已审核的行动入口不能修改，请等待当前流程完成。");
        }
        List<PreparedAction> prepared = new ArrayList<>();
        int index = 0;
        for (ActionInput input : inputs == null ? List.<ActionInput>of() : inputs) prepared.add(prepare(input, index++));

        ContentRevision from = item.getCurrentRevision();
        int beforeCount = countActions(from.getId());
        boolean candidateCreated = status == WorkflowStatus.PUBLISHED;
        ContentRevision target = candidateCreated ? candidates.cloneCurrent(item, actorId) : from;
        boolean administrator = "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
        for (PreparedAction action : prepared) {
            if (action.mediaId() != null) media.requireActionMedia(contentId, target.getId(), action.mediaId(), actorId, administrator);
        }

        jdbc.update("delete from content_action_entries where revision_id = ?", target.getId());
        for (PreparedAction action : prepared) {
            jdbc.update("insert into content_action_entries (id, revision_id, presentation_type, contact_channel, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    action.id(), target.getId(), action.presentation(), action.channel(), action.access(), action.label(),
                    action.publicValue(), action.ciphertext(), action.keyId(), action.mediaId(), action.enabled(), action.sortOrder());
            if (action.mediaId() != null) media.consumeActionMedia(contentId, action.mediaId());
        }
        item.touch();
        item = items.saveAndFlush(item);
        String before = "{\"revisionId\":\"" + from.getId() + "\",\"count\":" + beforeCount + "}";
        String after = "{\"revisionId\":\"" + target.getId() + "\",\"count\":" + prepared.size()
                + ",\"candidateCreated\":" + candidateCreated + "}";
        audit.record(actorId, role, "CONTENT_ACTIONS_REPLACED", "CONTENT_ITEM", contentId, before, after, null, requestId);
        return new ActionSet(contentId, item.getVersion(), item.getWorkflowStatus().name(), candidateCreated,
                readManagedActions(target.getId(), true));
    }

    public boolean hasEnabledAction(String revisionId) {
        Integer count = jdbc.queryForObject("select count(*) from content_action_entries where revision_id = ? and enabled = true", Integer.class, revisionId);
        return count != null && count > 0;
    }

    public void copyActions(String fromRevisionId, String toRevisionId) {
        for (Map<String, Object> row : jdbc.queryForList("select presentation_type, contact_channel, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order from content_action_entries where revision_id = ?", fromRevisionId)) {
            jdbc.update("insert into content_action_entries (id, revision_id, presentation_type, contact_channel, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Ids.ulid(), toRevisionId, row.get("presentation_type"), row.get("contact_channel"), row.get("access_level"),
                    row.get("label"), row.get("public_value"), row.get("private_value_ciphertext"), row.get("private_value_key_id"),
                    row.get("media_asset_id"), row.get("enabled"), row.get("sort_order"));
        }
    }

    private List<ManagedAction> readManagedActions(String revisionId, boolean privateAllowed) {
        return jdbc.query("select id, presentation_type, contact_channel, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order from content_action_entries where revision_id = ? order by sort_order asc, id asc", (row, index) -> {
            String access = row.getString("access_level");
            String value = row.getString("public_value");
            if (privateAllowed && "COMMUNITY_GATED".equals(access) && row.getString("private_value_ciphertext") != null) {
                value = privateValues.decrypt(row.getString("private_value_ciphertext"), row.getString("private_value_key_id"), "CONTENT_ACTION");
            }
            return new ManagedAction(row.getString("id"), row.getString("presentation_type"), row.getString("contact_channel"),
                    access, row.getString("label"), !privateAllowed && "COMMUNITY_GATED".equals(access) ? null : value,
                    !privateAllowed && "COMMUNITY_GATED".equals(access) ? null : row.getString("media_asset_id"), row.getBoolean("enabled"), row.getInt("sort_order"));
        }, revisionId);
    }

    private List<PublicAction> readPublicActions(String revisionId, String accessLevel) {
        return jdbc.query("select id, presentation_type, contact_channel, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id from content_action_entries where revision_id = ? and enabled = true and access_level = ? order by sort_order asc, id asc", (row, index) -> {
            String value = row.getString("public_value");
            if ("COMMUNITY_GATED".equals(accessLevel) && row.getString("private_value_ciphertext") != null) {
                value = privateValues.decrypt(row.getString("private_value_ciphertext"), row.getString("private_value_key_id"), "CONTENT_ACTION");
            }
            String mediaId = row.getString("media_asset_id");
            String mediaUrl = mediaId == null ? null : "COMMUNITY_GATED".equals(accessLevel)
                    ? "/api/v1/public/community/media/" + mediaId : "/api/v1/public/media/" + mediaId;
            return new PublicAction(row.getString("id"), row.getString("presentation_type"), row.getString("contact_channel"),
                    row.getString("label"), value, mediaUrl, accessLevel);
        }, revisionId, accessLevel);
    }

    private PreparedAction prepare(ActionInput input, int fallbackOrder) {
        if (input == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_INVALID", "行动入口不能为空。");
        String presentation = normalized(input.presentationType());
        String channel = normalized(input.contactChannel());
        String access = normalized(input.accessLevel());
        if (!PRESENTATIONS.contains(presentation) || !CHANNELS.contains(channel) || !ACCESS.contains(access)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_INVALID", "行动入口展示形式、联系渠道或访问级别无效。");
        }
        if ("URL".equals(channel) && !"LINK".equals(presentation)) throw invalidCombination();
        if ("LINK".equals(presentation) && !Set.of("URL", "OTHER").contains(channel)) throw invalidCombination();
        String label = input.label() == null ? "" : input.label().trim();
        if (label.isBlank() || label.length() > 120) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_LABEL_INVALID", "请填写有效的行动入口名称。");
        String value = blankToNull(input.value());
        String mediaId = blankToNull(input.mediaAssetId());
        if ("QR_CODE".equals(presentation) && mediaId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_MEDIA_REQUIRED", "二维码行动入口必须选择专用图片。");
        if (!"QR_CODE".equals(presentation) && value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_VALUE_REQUIRED", "请填写行动入口内容。");
        if ("LINK".equals(presentation)) sources.requireAllowed(value);
        validateChannel(channel, value);
        if ("PUBLIC".equals(access) && (SENSITIVE_CHANNELS.contains(channel) || "QR_CODE".equals(presentation))
                && !input.publicExposureConfirmed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_PUBLIC_EXPOSURE_CONFIRMATION_REQUIRED", "公开个人联系方式或二维码前必须确认隐私风险。");
        }
        String publicValue = null;
        String ciphertext = null;
        String keyId = null;
        if ("PUBLIC".equals(access)) {
            publicValue = value;
        } else {
            community.requireOperational();
            if (value != null) {
                PrivateValueCipher.EncryptedValue encrypted = privateValues.encrypt(value, "CONTENT_ACTION");
                ciphertext = encrypted.ciphertext();
                keyId = encrypted.keyId();
            }
        }
        return new PreparedAction(Ids.ulid(), presentation, channel, access, label, publicValue, ciphertext, keyId,
                mediaId, input.enabled(), input.sortOrder() < 0 ? fallbackOrder : input.sortOrder());
    }

    private void validateChannel(String channel, String value) {
        if (value == null) return;
        if ("EMAIL".equals(channel) && !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_EMAIL_INVALID", "邮箱格式无效。");
        }
        if ("PHONE".equals(channel) && !value.matches("^[+0-9()\\-\\s]{5,32}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_PHONE_INVALID", "电话号码格式无效。");
        }
    }

    private ContentItem requireOwned(String contentId, String ownerProfileId, boolean lock) {
        ContentItem item = lock
                ? items.findByIdForUpdate(contentId).orElseThrow(this::missing)
                : items.findWithCurrentRevisionById(contentId).orElseThrow(this::missing);
        if (ownerProfileId != null && !Objects.equals(item.getOwnerPartnerProfileId(), ownerProfileId)) throw missing();
        if (item.getRecordState() != RecordState.ACTIVE || item.getCurrentRevision() == null) throw missing();
        return item;
    }

    private int countActions(String revisionId) {
        Integer value = jdbc.queryForObject("select count(*) from content_action_entries where revision_id = ?", Integer.class, revisionId);
        return value == null ? 0 : value;
    }

    private ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "招募内容不存在或无权编辑。"); }
    private ApiException invalidCombination() { return new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_ACTION_COMBINATION_INVALID", "展示形式与联系渠道组合无效。"); }
    private String normalized(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record PreparedAction(String id, String presentation, String channel, String access, String label,
                                  String publicValue, String ciphertext, String keyId, String mediaId,
                                  boolean enabled, int sortOrder) { }
}
