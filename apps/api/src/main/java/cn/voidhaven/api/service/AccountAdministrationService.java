package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.RoleRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.support.LoginIds;
import cn.voidhaven.api.support.PageResult;
import cn.voidhaven.api.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class AccountAdministrationService {
    public record AccountStatus(String id, String loginId, String qualificationStatus, List<String> roles) { }
    public record CreatedAccount(String id, String loginId, String qualificationStatus, List<String> roles,
                                 String activationToken, Instant activationExpiresAt) { }
    public record IssuedActivationLink(String id, String loginId, String qualificationStatus, List<String> roles,
                                       String activationToken, Instant activationExpiresAt) { }

    private static final Set<String> NON_ACTIVE_STATUSES = Set.of("SUSPENDED", "REVOKED");
    private static final Set<String> QUALIFICATION_STATUSES = Set.of("PENDING", "ACTIVE", "SUSPENDED", "REVOKED");
    private AdminAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    void authorization(AdminAuthorizationService authorization) { this.authorization = authorization; }
    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final ActivationService activations;
    private final AccountLockService locks;
    private final AuditService audit;
    private SecurityRevocationService revocations;
    @org.springframework.beans.factory.annotation.Autowired
    void security(SecurityRevocationService revocations) { this.revocations = revocations; }

    public AccountAdministrationService(AccountRepository accounts, RoleRepository roles, ActivationService activations,
                                        AccountLockService locks, AuditService audit) {
        this.accounts = accounts;
        this.roles = roles;
        this.activations = activations;
        this.locks = locks;
        this.audit = audit;
    }

    @Transactional
    public CreatedAccount createAdmin(String loginId, String reason, String actorId, String role, String requestId) {
        return createAdmin(loginId, reason, actorId, role, requestId, null);
    }

    @Transactional
    public CreatedAccount createAdmin(String loginId, String reason, String actorId, String role, String requestId, AdminAuthorizationService.Input authorizationInput) {
        locks.requireActiveActor(actorId);
        String normalizedLogin = LoginIds.normalize(loginId);
        String normalizedReason = requireReason(reason);
        if (accounts.findByLoginId(normalizedLogin).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_ALREADY_EXISTS", "该受控登录名已被使用。");
        }
        Role adminRole = roles.findByCode("ADMIN").orElseGet(() -> roles.save(new Role("ADMIN")));
        Account account = new Account(Ids.ulid(), normalizedLogin, "!ACTIVATION_REQUIRED!", "PENDING");
        account.addRole(adminRole);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException error) {
            throw new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_ALREADY_EXISTS", "该受控登录名已被使用。");
        }
        if (authorizationInput != null) authorization.replace(account.getId(), 0, authorizationInput, requestId);
        ActivationService.IssuedActivation issued = activations.issueLocked(account);
        audit.record(actorId, role, "ADMIN_ACCOUNT_CREATED", "ACCOUNT", account.getId(),
                "{}", "{\"qualificationStatus\":\"PENDING\",\"roles\":[\"ADMIN\"]}", normalizedReason, requestId);
        audit.record(actorId, role, "ACTIVATION_ISSUED", "ACCOUNT", account.getId(),
                "{}", "{\"qualificationStatus\":\"PENDING\"}", normalizedReason, requestId);
        AccountStatus view = view(account);
        return new CreatedAccount(view.id(), view.loginId(), view.qualificationStatus(), view.roles(), issued.token(), issued.expiresAt());
    }

    @Transactional
    public IssuedActivationLink reissueActivation(String accountId, String reason, String actorId, String role, String requestId) {
        locks.requireActiveActor(actorId);
        Account account = locks.lockTarget(accountId);
        if (!account.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_PENDING", "只有待激活账号可以重新签发激活链接。");
        }
        String normalizedReason = requireReason(reason);
        ActivationService.IssuedActivation issued = activations.issueLocked(account);
        audit.record(actorId, role, "ACTIVATION_REISSUED", "ACCOUNT", account.getId(),
                "{}", "{\"qualificationStatus\":\"PENDING\"}", normalizedReason, requestId);
        AccountStatus view = view(account);
        return new IssuedActivationLink(view.id(), view.loginId(), view.qualificationStatus(), view.roles(), issued.token(), issued.expiresAt());
    }

    @Transactional
    public AccountStatus disable(String accountId, String status, String reason, String actorId, String role, String requestId) {
        if (!NON_ACTIVE_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_QUALIFICATION_INVALID", "账号资格只能调整为已停用或已撤销。");
        }
        Account account = requireMutableTarget(accountId, actorId);
        String before = account.getQualificationStatus();
        if (status.equals(before)) return view(account);
        account.changeQualificationStatus(status);
        revocations.revoke(account);
        audit.record(actorId, role, "ACCOUNT_QUALIFICATION_CHANGED", "ACCOUNT", accountId,
                "{\"qualificationStatus\":\"" + before + "\"}",
                "{\"qualificationStatus\":\"" + status + "\"}", reason, requestId);
        return view(account);
    }

    @Transactional(readOnly = true)
    public List<AccountStatus> list() {
        return accounts.findAll().stream().map(this::view).sorted(Comparator.comparing(AccountStatus::loginId)).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<AccountStatus> page(String cursor, String query, String status, int requestedSize) {
        int pageSize = Math.max(1, Math.min(requestedSize, 50));
        String normalized = query == null ? "" : query.trim().toLowerCase();
        String requiredStatus = normalizeStatus(status);
        List<AccountStatus> values = accounts.findAll().stream().map(this::view)
                .filter(value -> authorization == null || authorization.isSuper() || partnerVisible(value))
                .filter(value -> cursor == null || cursor.isBlank() || value.id().compareTo(cursor) < 0)
                .filter(value -> requiredStatus == null || requiredStatus.equals(value.qualificationStatus()))
                .filter(value -> normalized.isBlank() || value.loginId().toLowerCase().contains(normalized) || value.qualificationStatus().toLowerCase().contains(normalized))
                .sorted(Comparator.comparing(AccountStatus::id).reversed()).limit(pageSize + 1L).toList();
        return PageResult.from(values, pageSize, AccountStatus::id);
    }

    @Transactional
    public AccountStatus suspend(String accountId, String reason, String actorId, String role, String requestId) {
        return transition(accountId, "SUSPENDED", reason, actorId, role, requestId);
    }

    @Transactional
    public AccountStatus revoke(String accountId, String reason, String actorId, String role, String requestId) {
        return transition(accountId, "REVOKED", reason, actorId, role, requestId);
    }

    @Transactional
    public AccountStatus reactivate(String accountId, String reason, String actorId, String role, String requestId) {
        Account account = requireMutableTarget(accountId, actorId);
        if ("REVOKED".equals(account.getQualificationStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_REVOKED_FINAL", "已撤销账号不可再次变更资格。");
        }
        if (!"SUSPENDED".equals(account.getQualificationStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_REACTIVATABLE", "只有已停用账号可以恢复；已撤销账号不可恢复。");
        }
        String normalizedReason = requireReason(reason);
        account.reactivate();
        revocations.revoke(account);
        audit.record(actorId, role, "ACCOUNT_REACTIVATED", "ACCOUNT", accountId,
                "{\"qualificationStatus\":\"SUSPENDED\"}", "{\"qualificationStatus\":\"" + account.getQualificationStatus() + "\"}", normalizedReason, requestId);
        return view(account);
    }

    private AccountStatus transition(String accountId, String target, String reason, String actorId, String role, String requestId) {
        Account account = requireMutableTarget(accountId, actorId);
        String before = account.getQualificationStatus();
        if (target.equals(before)) return view(account);
        if ("REVOKED".equals(before)) throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_REVOKED_FINAL", "已撤销账号不可再次变更资格。");
        if ("SUSPENDED".equals(target) && !"ACTIVE".equals(before) && !"PENDING".equals(before)) throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_SUSPENDABLE", "只有正常或待激活账号可以停用。");
        String normalizedReason = requireReason(reason);
        account.changeQualificationStatus(target);
        revocations.revoke(account);
        audit.record(actorId, role, "ACCOUNT_" + target, "ACCOUNT", accountId,
                "{\"qualificationStatus\":\"" + before + "\"}", "{\"qualificationStatus\":\"" + target + "\"}", normalizedReason, requestId);
        return view(account);
    }

    private Account requireMutableTarget(String accountId, String actorId) {
        locks.requireActiveActor(actorId);
        if (accountId.equals(actorId)) throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_SELF_DISABLE_FORBIDDEN", "不能通过当前会话停用、恢复或撤销自身账号。");
        Account account = locks.lockTarget(accountId);
        if (account.hasRole("SUPER_ADMIN")) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_SUPER_ADMIN_IMMUTABLE", "不能停用、恢复或撤销超级管理员账号。");
        }
        return account;
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "ACTION_REASON_REQUIRED", "请填写操作原因。");
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim();
        if (!QUALIFICATION_STATUSES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_STATUS_INVALID", "账号资格状态筛选无效。");
        }
        return normalized;
    }

    private boolean partnerVisible(AccountStatus value) {
        try { authorization.requirePartnerAccount(value.id()); return true; } catch (ApiException error) { return false; }
    }

    private AccountStatus view(Account account) {
        return new AccountStatus(account.getId(), account.getLoginId(), account.getQualificationStatus(),
                account.getRoles().stream().map(value -> value.getCode()).sorted().toList());
    }
}
