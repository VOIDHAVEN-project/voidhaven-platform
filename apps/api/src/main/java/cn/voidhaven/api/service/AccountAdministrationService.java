package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import cn.voidhaven.api.support.PageResult;

@Service
public class AccountAdministrationService {
    public record AccountStatus(String id, String loginId, String qualificationStatus, List<String> roles) { }

    private static final Set<String> NON_ACTIVE_STATUSES = Set.of("SUSPENDED", "REVOKED");
    private final AccountRepository accounts;
    private final AuditService audit;

    public AccountAdministrationService(AccountRepository accounts, AuditService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional
    public AccountStatus disable(String accountId, String status, String reason, String actorId, String role, String requestId) {
        if (!NON_ACTIVE_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_QUALIFICATION_INVALID", "账号资格只能调整为已停用或已撤销。");
        }
        if (accountId.equals(actorId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_SELF_DISABLE_FORBIDDEN", "不能通过当前会话停用或撤销自身账号。");
        }
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在。"));
        String before = account.getQualificationStatus();
        if (status.equals(before)) return view(account);
        account.changeQualificationStatus(status);
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
    public PageResult<AccountStatus> page(String cursor, String query, int requestedSize) {
        int pageSize = Math.max(1, Math.min(requestedSize, 50));
        String normalized = query == null ? "" : query.trim().toLowerCase();
        List<AccountStatus> values = accounts.findAll().stream().map(this::view)
                .filter(value -> cursor == null || cursor.isBlank() || value.id().compareTo(cursor) < 0)
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
        Account account = requireTarget(accountId, actorId);
        if (!"SUSPENDED".equals(account.getQualificationStatus())) throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_REACTIVATABLE", "只有已停用账号可以恢复；已撤销账号不可恢复。");
        String normalizedReason = requireReason(reason);
        account.reactivate();
        audit.record(actorId, role, "ACCOUNT_REACTIVATED", "ACCOUNT", accountId,
                "{\"qualificationStatus\":\"SUSPENDED\"}", "{\"qualificationStatus\":\"ACTIVE\"}", normalizedReason, requestId);
        return view(account);
    }

    private AccountStatus transition(String accountId, String target, String reason, String actorId, String role, String requestId) {
        Account account = requireTarget(accountId, actorId);
        String before = account.getQualificationStatus();
        if (target.equals(before)) return view(account);
        if ("REVOKED".equals(before)) throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_REVOKED_FINAL", "已撤销账号不可再次变更资格。");
        if ("SUSPENDED".equals(target) && !"ACTIVE".equals(before)) throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_SUSPENDABLE", "只有正常账号可以停用。");
        String normalizedReason = requireReason(reason);
        account.changeQualificationStatus(target);
        audit.record(actorId, role, "ACCOUNT_" + target, "ACCOUNT", accountId,
                "{\"qualificationStatus\":\"" + before + "\"}", "{\"qualificationStatus\":\"" + target + "\"}", normalizedReason, requestId);
        return view(account);
    }

    private Account requireTarget(String accountId, String actorId) {
        if (accountId.equals(actorId)) throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_SELF_DISABLE_FORBIDDEN", "不能通过当前会话停用、恢复或撤销自身账号。");
        return accounts.findById(accountId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在。"));
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "ACTION_REASON_REQUIRED", "请填写操作原因。");
        return normalized;
    }

    private AccountStatus view(Account account) {
        return new AccountStatus(account.getId(), account.getLoginId(), account.getQualificationStatus(),
                account.getRoles().stream().map(value -> value.getCode()).sorted().toList());
    }
}
