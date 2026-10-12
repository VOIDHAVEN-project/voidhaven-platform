package cn.voidhaven.api.security;

import cn.voidhaven.api.domain.Account;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Serializable authentication snapshot; never refresh its epoch after password verification. */
public final class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final String accountId;
    private final long securityEpoch;
    private final String organizationId;
    private final Long organizationEpoch;
    public AccountPrincipal(Account account) { this(account, null); }
    public AccountPrincipal(Account account, cn.voidhaven.api.domain.PartnerProfile organization) {
        super(account.getLoginId(), account.getPasswordHash(), account.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode())).toList());
        accountId = account.getId();
        securityEpoch = account.getSecurityEpoch();
        organizationId = organization == null ? null : organization.getId();
        organizationEpoch = organization == null ? null : organization.getSecurityEpoch();
    }
    public String organizationId() { return organizationId; }
    public Long organizationEpoch() { return organizationEpoch; }
    public String accountId() { return accountId; }
    public long securityEpoch() { return securityEpoch; }
    public boolean matches(Account account) {
        return account.isActive() && accountId.equals(account.getId()) && securityEpoch == account.getSecurityEpoch();
    }
}
