package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.support.*;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class PartnerMemberService {
    public record Member(String id, String loginId, String qualificationStatus) { }
    public record Members(OrganizationAccessService.Identity organization, List<Member> items) { }
    private final OrganizationAccessService organizations;
    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final AccountLockService locks;
    public PartnerMemberService(OrganizationAccessService organizations, AccountRepository accounts, RoleRepository roles,
                                JdbcTemplate jdbc, AuditService audit, AccountLockService locks) {
        this.organizations = organizations; this.accounts = accounts; this.roles = roles; this.jdbc = jdbc; this.audit = audit; this.locks = locks;
    }
    @Transactional(readOnly = true)
    public Members list(String actor) {
        var profile = organizations.requireMain(actor);
        var members = jdbc.query("select a.id, a.login_id, a.qualification_status from partner_members m join accounts a on a.id = m.account_id where m.partner_profile_id = ? order by a.id desc",
                (row, index) -> new Member(row.getString(1), row.getString(2), row.getString(3)), profile.getId());
        return new Members(organizations.identity(actor), members);
    }
    @Transactional
    public Member create(String login, String reason, String actor, String requestId) {
        String profileId = organizations.requireMain(actor).getId(); var profile = organizations.lock(profileId);
        locks.requireActiveActor(actor); organizations.requireMain(actor);
        if (organizations.occupied(profileId) >= profile.getMemberQuota()) throw new ApiException(HttpStatus.CONFLICT, "MEMBER_QUOTA_EXCEEDED", "组织成员名额已用完，请联系平台调整限额。");
        String normalized = LoginIds.normalize(login);
        if (accounts.findByLoginId(normalized).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_ALREADY_EXISTS", "该受控登录名已被使用。");
        Account account = new Account(Ids.ulid(), normalized, "!ACTIVATION_REQUIRED!", "PENDING");
        account.addRole(roles.findByCode("PARTNER").orElseThrow());
        try { accounts.saveAndFlush(account); }
        catch (DataIntegrityViolationException error) { throw new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_ALREADY_EXISTS", "该受控登录名已被使用。"); }
        jdbc.update("insert into partner_members (account_id, partner_profile_id, created_by_account_id) values (?, ?, ?)", account.getId(), profileId, actor);
        audit.record(actor, "PARTNER", "ORGANIZATION_MEMBER_CREATED", "ACCOUNT", account.getId(), "{}", "{\"organizationId\":\"" + profileId + "\",\"qualificationStatus\":\"PENDING\"}", reason, requestId);
        return new Member(account.getId(), account.getLoginId(), account.getQualificationStatus());
    }
    public void requireMember(String actor, String target) {
        organizations.requireMain(actor);
        if (!organizations.canManageMember(actor, target)) throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "成员不存在或不属于当前组织。");
    }
}
