package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.PartnerProfile;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PartnerAdministrationService {
    public record ProfileView(String id, String displayName, int contentQuota, boolean active) { }
    private final PartnerProfileRepository profiles;
    private final ContentItemRepository items;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PartnerAdministrationService(PartnerProfileRepository profiles, ContentItemRepository items, JdbcTemplate jdbc, AuditService audit) {
        this.profiles = profiles;
        this.items = items;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProfileView> listProfiles() {
        return profiles.findAll().stream().map(profile -> new ProfileView(profile.getId(), profile.getDisplayName(), profile.getContentQuota(), profile.isActive())).toList();
    }

    @Transactional
    public ProfileView setQuota(String profileId, int quota, String reason, String actorId, String role, String requestId) {
        if (quota < 0 || quota > 5) throw new ApiException(HttpStatus.BAD_REQUEST, "PARTNER_QUOTA_INVALID", "公开内容配额必须在 0 到 5 之间。");
        PartnerProfile profile = profiles.findByIdForUpdate(profileId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PARTNER_PROFILE_NOT_FOUND", "合作方档案不存在。"));
        int before = profile.getContentQuota();
        profile.setContentQuota(quota);
        jdbc.update("insert into partner_quota_overrides (id, partner_profile_id, quota, reason, operator_account_id) values (?, ?, ?, ?, ?)", Ids.ulid(), profileId, quota, reason, actorId);
        audit.record(actorId, role, "PARTNER_QUOTA_UPDATED", "PARTNER_PROFILE", profileId, "{\"quota\":" + before + "}", "{\"quota\":" + quota + "}", reason, requestId);
        return new ProfileView(profile.getId(), profile.getDisplayName(), profile.getContentQuota(), profile.isActive());
    }

    @Transactional
    public void transferOwnership(String contentId, String toProfileId, String reason, String actorId, String role, String requestId) {
        ContentItem item = items.findByIdForUpdate(contentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。"));
        PartnerProfile destination = profiles.findByIdForUpdate(toProfileId).filter(PartnerProfile::isActive).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PARTNER_PROFILE_INACTIVE", "目标合作方档案不可用。"));
        String fromProfileId = item.getOwnerPartnerProfileId();
        if (toProfileId.equals(fromProfileId)) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_OWNERSHIP_UNCHANGED", "内容已归属该合作方。");
        item.setOwnerPartnerProfileId(destination.getId());
        jdbc.update("insert into content_ownership_transfers (id, content_item_id, from_partner_profile_id, to_partner_profile_id, reason, operator_account_id) values (?, ?, ?, ?, ?, ?)", Ids.ulid(), contentId, fromProfileId, toProfileId, reason, actorId);
        audit.record(actorId, role, "CONTENT_OWNERSHIP_TRANSFERRED", "CONTENT_ITEM", contentId, "{\"ownerPartnerProfileId\":" + quote(fromProfileId) + "}", "{\"ownerPartnerProfileId\":\"" + toProfileId + "\"}", reason, requestId);
    }

    private String quote(String value) { return value == null ? "null" : "\"" + value + "\""; }
}
