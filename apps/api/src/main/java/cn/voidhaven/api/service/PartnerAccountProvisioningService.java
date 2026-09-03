package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.PartnerProfile;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import cn.voidhaven.api.persistence.RoleRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerAccountProvisioningService {
    public record Provisioned(String partnerProfileId, String loginId, String activationToken) { }
    public record PendingApplication(String id, String displayName, String introduction, String portfolioUrl) { }
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final PartnerProfileRepository profiles;
    private final RoleRepository roles;
    private final ActivationService activations;
    private final AuditService audit;

    public PartnerAccountProvisioningService(JdbcTemplate jdbc, AccountRepository accounts, PartnerProfileRepository profiles, RoleRepository roles, ActivationService activations, AuditService audit) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.profiles = profiles;
        this.roles = roles;
        this.activations = activations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public java.util.List<PendingApplication> pendingApplications() {
        return jdbc.query("select id, display_name, introduction, portfolio_url from partner_applications where state = 'PENDING_REVIEW' order by created_at asc", (result, row) -> new PendingApplication(result.getString("id"), result.getString("display_name"), result.getString("introduction"), result.getString("portfolio_url")));
    }

    @Transactional
    public Provisioned approveApplication(String applicationId, String loginId, String actorId, String actorRole, String requestId) {
        String displayName;
        try {
            displayName = jdbc.queryForObject("select display_name from partner_applications where id = ? and state = 'PENDING_REVIEW'", String.class, applicationId);
        } catch (EmptyResultDataAccessException missing) {
            throw new ApiException(HttpStatus.CONFLICT, "PARTNER_APPLICATION_NOT_ACTIONABLE", "该合作方申请不存在或已被处理。");
        }
        if (accounts.findByLoginId(loginId).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_ALREADY_EXISTS", "该受控登录名已被使用。");
        Role partnerRole = roles.findByCode("PARTNER").orElseGet(() -> roles.save(new Role("PARTNER")));
        Account account = new Account(Ids.ulid(), loginId, "!ACTIVATION_REQUIRED!", "PENDING");
        account.addRole(partnerRole);
        accounts.saveAndFlush(account);
        PartnerProfile profile = profiles.saveAndFlush(new PartnerProfile(Ids.ulid(), account.getId(), displayName, 3));
        int updated = jdbc.update("update partner_applications set state = 'APPROVED' where id = ? and state = 'PENDING_REVIEW'", applicationId);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "PARTNER_APPLICATION_NOT_ACTIONABLE", "该合作方申请已被其他操作处理。 ");
        String token = activations.issue(account.getId());
        audit.record(actorId, actorRole, "PARTNER_ACCOUNT_CREATED", "PARTNER_PROFILE", profile.getId(), "{}", "{\"applicationId\":\"" + applicationId + "\",\"qualificationStatus\":\"PENDING\"}", null, requestId);
        audit.record(actorId, actorRole, "ACTIVATION_ISSUED", "ACCOUNT", account.getId(), "{}", "{\"qualificationStatus\":\"PENDING\"}", null, requestId);
        return new Provisioned(profile.getId(), account.getLoginId(), token);
    }
}
