package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.PartnerProfile;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import cn.voidhaven.api.support.PageResult;

@Service
public class PartnerAdministrationService {
    private OrganizationAccessService organizations;
    @org.springframework.beans.factory.annotation.Autowired
    void organizations(OrganizationAccessService organizations) { this.organizations = organizations; }
    private AdminAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    void authorization(AdminAuthorizationService authorization) { this.authorization = authorization; }
    public record ProfileView(String id, String accountId, String displayName, int contentQuota, boolean active, String qualificationStatus, int memberQuota, int memberCount, long version) { }
    private final PartnerProfileRepository profiles;
    private final ContentItemRepository items;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final AccountRepository accounts;

    private AccountLockService accountLocks;

    public PartnerAdministrationService(PartnerProfileRepository profiles, ContentItemRepository items, JdbcTemplate jdbc, AuditService audit, AccountRepository accounts) {
        this.profiles = profiles;
        this.items = items;
        this.jdbc = jdbc;
        this.audit = audit;
        this.accounts = accounts;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAccountLocks(AccountLockService accountLocks) { this.accountLocks = accountLocks; }

    @Transactional(readOnly = true)
    public List<ProfileView> listProfiles() {
        return profiles.findAll().stream().filter(profile -> authorization == null || authorization.canSeeOrganization(profile.getId())).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<ProfileView> page(String cursor, String query, int requestedSize) {
        int pageSize = Math.max(1, Math.min(requestedSize, 50));
        String normalized = query == null ? "" : query.trim().toLowerCase();
        List<ProfileView> values = profiles.findAll().stream().filter(profile -> authorization == null || authorization.canSeeOrganization(profile.getId())).map(this::view)
                .filter(value -> cursor == null || cursor.isBlank() || value.id().compareTo(cursor) < 0)
                .filter(value -> normalized.isBlank() || value.displayName().toLowerCase().contains(normalized) || value.accountId().toLowerCase().contains(normalized))
                .sorted(java.util.Comparator.comparing(ProfileView::id).reversed()).limit(pageSize + 1L).toList();
        return PageResult.from(values, pageSize, ProfileView::id);
    }

    @Transactional
    public ProfileView setQuota(String profileId, int quota, String reason, String actorId, String role, String requestId) {
        if (accountLocks != null) accountLocks.requireActiveActor(actorId);
        if (quota < 0 || quota > 5) throw new ApiException(HttpStatus.BAD_REQUEST, "PARTNER_QUOTA_INVALID", "公开内容配额必须在 0 到 5 之间。");
        PartnerProfile profile = profiles.findByIdForUpdate(profileId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PARTNER_PROFILE_NOT_FOUND", "合作方档案不存在。"));
        int before = profile.getContentQuota();
        profile.setContentQuota(quota);
        jdbc.update("insert into partner_quota_overrides (id, partner_profile_id, quota, reason, operator_account_id) values (?, ?, ?, ?, ?)", Ids.ulid(), profileId, quota, reason, actorId);
        audit.record(actorId, role, "PARTNER_QUOTA_UPDATED", "PARTNER_PROFILE", profileId, "{\"quota\":" + before + "}", "{\"quota\":" + quota + "}", reason, requestId);
        return view(profile);
    }

    @Transactional
    public void transferOwnership(String contentId, String toProfileId, String reason, String actorId, String role, String requestId) {
        if (accountLocks != null) accountLocks.requireActiveActor(actorId);
        ContentItem item = items.findByIdForUpdate(contentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。"));
        PartnerProfile destination = profiles.findByIdForUpdate(toProfileId).filter(PartnerProfile::isActive).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PARTNER_PROFILE_INACTIVE", "目标合作方档案不可用。"));
        if (authorization != null) { authorization.requireItem("OWNERSHIP_TRANSFER", item); authorization.requireOrganization("OWNERSHIP_TRANSFER", toProfileId); }
        String fromProfileId = item.getOwnerPartnerProfileId();
        if (toProfileId.equals(fromProfileId)) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_OWNERSHIP_UNCHANGED", "内容已归属该合作方。");
        item.setOwnerPartnerProfileId(destination.getId());
        jdbc.update("insert into content_ownership_transfers (id, content_item_id, from_partner_profile_id, to_partner_profile_id, reason, operator_account_id) values (?, ?, ?, ?, ?, ?)", Ids.ulid(), contentId, fromProfileId, toProfileId, reason, actorId);
        audit.record(actorId, role, "CONTENT_OWNERSHIP_TRANSFERRED", "CONTENT_ITEM", contentId, "{\"ownerPartnerProfileId\":" + quote(fromProfileId) + "}", "{\"ownerPartnerProfileId\":\"" + toProfileId + "\"}", reason, requestId);
    }

    private String quote(String value) { return value == null ? "null" : "\"" + value + "\""; }
    private ProfileView view(PartnerProfile profile) {
        String status = accounts.findById(profile.getAccountId()).map(value -> value.getQualificationStatus()).orElse("UNKNOWN");
        return new ProfileView(profile.getId(), profile.getAccountId(), profile.getDisplayName(), profile.getContentQuota(), profile.isActive(), status, profile.getMemberQuota(), organizations == null ? 0 : organizations.occupied(profile.getId()), profile.getVersion());
    }
}
