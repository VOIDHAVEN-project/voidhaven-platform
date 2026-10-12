package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.security.AccountPrincipal;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** One business organization, one main account, and one layer of controlled members. */
@Service
public class OrganizationAccessService {
    public record Identity(String id, String displayName, String accountKind, int memberQuota, int memberCount, long version) { }
    private final PartnerProfileRepository profiles;
    private final JdbcTemplate jdbc;
    public OrganizationAccessService(PartnerProfileRepository profiles, JdbcTemplate jdbc) { this.profiles = profiles; this.jdbc = jdbc; }
    public String organizationId(String accountId) {
        return jdbc.query("select id from partner_profiles where account_id = ? union all select partner_profile_id from partner_members where account_id = ?",
                row -> row.next() ? row.getString(1) : null, accountId, accountId);
    }
    public Optional<PartnerProfile> profile(String accountId) {
        String id = organizationId(accountId); return id == null ? Optional.empty() : profiles.findById(id);
    }
    public PartnerProfile require(String accountId) {
        return profile(accountId).filter(PartnerProfile::isActive).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_INACTIVE", "组织当前不可用。"));
    }
    public PartnerProfile requireMain(String accountId) {
        PartnerProfile profile = require(accountId);
        if (!profile.getAccountId().equals(accountId)) throw new ApiException(HttpStatus.FORBIDDEN, "ORGANIZATION_MAIN_REQUIRED", "只有组织主账号可以管理成员。" );
        return profile;
    }
    public boolean matches(AccountPrincipal principal, Account account) {
        if (!account.hasRole("PARTNER")) return principal.organizationId() == null;
        return profile(account.getId()).filter(p -> p.isActive() && p.getId().equals(principal.organizationId())
                && Objects.equals(principal.organizationEpoch(), p.getSecurityEpoch())).isPresent();
    }
    public int occupied(String profileId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive() && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            return jdbc.queryForList("select a.id from partner_members m join accounts a on a.id = m.account_id where m.partner_profile_id = ? and a.qualification_status <> 'REVOKED' order by a.id for share", String.class, profileId).size();
        return jdbc.queryForObject("select count(*) from partner_members m join accounts a on a.id = m.account_id where m.partner_profile_id = ? and a.qualification_status <> 'REVOKED'", Integer.class, profileId);
    }
    public List<String> accounts(String profileId) {
        String lock = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive() && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? " for share" : "";
        List<String> result = new ArrayList<>(jdbc.queryForList("select account_id from partner_profiles where id = ?" + lock, String.class, profileId));
        result.addAll(jdbc.queryForList("select account_id from partner_members where partner_profile_id = ? order by account_id" + lock, String.class, profileId));
        return result.stream().sorted().toList();
    }
    public Identity identity(String accountId) {
        var profile = profile(accountId).orElse(null); if (profile == null) return null;
        return new Identity(profile.getId(), profile.getDisplayName(), profile.getAccountId().equals(accountId) ? "MAIN" : "MEMBER", profile.getMemberQuota(), occupied(profile.getId()), profile.getVersion());
    }
    public boolean canManageMember(String issuer, String target) {
        return profile(issuer).filter(p -> p.isActive() && p.getAccountId().equals(issuer)
                && !target.equals(issuer) && p.getId().equals(organizationId(target))).isPresent();
    }
    public PartnerProfile lock(String id) {
        return profiles.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "组织不存在。"));
    }
}
