package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAdministrationService {
    private final OrganizationAccessService organizations;
    private final AdminAuthorizationService authorization;
    private final AccountRepository accounts;
    private final AccountLockService locks;
    private final SecurityRevocationService revocations;
    private final AuditService audit;
    public record State(String id, boolean active, int memberQuota, int memberCount, long version) { }
    public OrganizationAdministrationService(OrganizationAccessService organizations, AdminAuthorizationService authorization,
                                            AccountRepository accounts, AccountLockService locks, SecurityRevocationService revocations, AuditService audit) {
        this.organizations = organizations; this.authorization = authorization; this.accounts = accounts; this.locks = locks; this.revocations = revocations; this.audit = audit;
    }
    @Transactional
    public State quota(String id, int quota, long expected, String reason, String actor, String role, String requestId) {
        PartnerProfile profile = organizations.lock(id); locks.requireActiveActor(actor); authorization.requireOrganization("QUOTA_CONFIG", id);
        checkVersion(profile, expected);
        if (quota < 0 || quota > 1000) throw new ApiException(HttpStatus.BAD_REQUEST, "MEMBER_QUOTA_INVALID", "成员限额必须在 0 到 1000 之间。" );
        int before = profile.getMemberQuota(); profile.setMemberQuota(quota);
        audit.record(actor, role, "ORGANIZATION_MEMBER_QUOTA_CHANGED", "PARTNER_PROFILE", id, "{\"memberQuota\":" + before + "}", "{\"memberQuota\":" + quota + "}", reason, requestId);
        return state(profile, before == quota ? expected : expected + 1);
    }
    @Transactional
    public State changeActive(String id, boolean active, long expected, String reason, String actor, String role, String requestId) {
        PartnerProfile profile = organizations.lock(id); authorization.requireOrganization("ACCOUNT_OPERATE", id);
        checkVersion(profile, expected);
        // All account locks precede every token/session update, including simultaneous credential consumption.
        for (String accountId : organizations.accounts(id)) accounts.findByIdForUpdate(accountId).orElseThrow();
        locks.requireActiveActor(actor);
        if (profile.isActive() == active) return state(profile, expected);
        profile.changeActive(active);
        for (String accountId : organizations.accounts(id)) revocations.revoke(accounts.findById(accountId).orElseThrow());
        audit.record(actor, role, active ? "ORGANIZATION_REACTIVATED" : "ORGANIZATION_SUSPENDED", "PARTNER_PROFILE", id,
                "{\"active\":" + !active + "}", "{\"active\":" + active + ",\"credentialsRevoked\":true}", reason, requestId);
        return state(profile, expected + 1);
    }
    private State state(PartnerProfile p, long version) { return new State(p.getId(), p.isActive(), p.getMemberQuota(), organizations.occupied(p.getId()), version); }
    private void checkVersion(PartnerProfile p, long expected) { if (p.getVersion() != expected) throw new ApiException(HttpStatus.CONFLICT, "ORGANIZATION_VERSION_CONFLICT", "组织设置已变化，请刷新后重试。"); }
}
