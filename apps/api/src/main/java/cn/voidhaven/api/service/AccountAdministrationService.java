package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AccountAdministrationService {
    public record AccountStatus(String id, String loginId, String qualificationStatus) { }

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

    private AccountStatus view(Account account) {
        return new AccountStatus(account.getId(), account.getLoginId(), account.getQualificationStatus());
    }
}
