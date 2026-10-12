package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.ActivationToken;
import cn.voidhaven.api.persistence.AccountRepository;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CredentialSecurityService {
    private final AccountRepository accounts;
    private AdminAuthorizationService authorization;
    private OrganizationAccessService organizations;
    @org.springframework.beans.factory.annotation.Autowired
    void organizations(OrganizationAccessService organizations) { this.organizations = organizations; }
    public void bind(ActivationToken token, Account target) {
        token.bind(target, issuer());
        if (organizations != null && target.hasRole("PARTNER")) token.bindOrganization(organizations.require(target.getId()));
    }
    @org.springframework.beans.factory.annotation.Autowired
    void authorization(AdminAuthorizationService authorization) { this.authorization = authorization; }
    public CredentialSecurityService(AccountRepository accounts) { this.accounts = accounts; }
    public Account issuer() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : accounts.findByLoginId(authentication.getName()).orElse(null);
    }
    /** Lock all accounts before locking the credential; ordering also covers reciprocal recovery. */
    public Account lockTarget(ActivationToken preview) {
        TreeSet<String> ids = new TreeSet<>(); ids.add(preview.getAccountId());
        if (preview.getIssuerAccountId() != null) ids.add(preview.getIssuerAccountId());
        if (organizations != null) {
            TreeSet<String> profileIds = new TreeSet<>();
            for (String id : ids) { String profile = organizations.organizationId(id); if (profile != null) profileIds.add(profile); }
            for (String id : profileIds) organizations.lock(id);
        }
        Map<String, Account> locked = new HashMap<>();
        for (String id : ids) accounts.findByIdForUpdate(id).ifPresent(account -> locked.put(id, account));
        return locked.get(preview.getAccountId());
    }
    public boolean valid(ActivationToken token, Account target) {
        if (target == null || token.getTargetSecurityEpoch() == null
                || token.getTargetSecurityEpoch() != target.getSecurityEpoch()) return false;
        if (organizations != null && target.hasRole("PARTNER") && organizations.profile(target.getId())
                .filter(p -> p.isActive() && p.getId().equals(token.getOrganizationId()) && Objects.equals(token.getOrganizationSecurityEpoch(), p.getSecurityEpoch())).isEmpty()) return false;
        if (token.getIssuerAccountId() == null) return token.getIssuerSecurityEpoch() == null;
        return accounts.findById(token.getIssuerAccountId()).filter(Account::isActive)
                .filter(issuer -> Objects.equals(token.getIssuerSecurityEpoch(), issuer.getSecurityEpoch()))
                .filter(issuer -> authorization == null || issuer.hasRole("SUPER_ADMIN")
                        || (organizations != null && organizations.canManageMember(issuer.getId(), target.getId()))
                        || ("ACTIVATION".equals(token.getTokenType()) && authorization.canIssueActivation(issuer.getId(), target.getId()))).isPresent();
    }
}
