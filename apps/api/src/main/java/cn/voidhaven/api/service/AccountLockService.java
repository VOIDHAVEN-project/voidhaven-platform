package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccountLockService {
    private final AccountRepository accounts;
    private AdminAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    void authorization(AdminAuthorizationService authorization) { this.authorization = authorization; }

    public AccountLockService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Account requireActiveActor(String actorId) {
        Account actor = accounts.findByIdForShare(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号已停用或资格不可用。"));
        if (!actor.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号已停用或资格不可用。");
        }
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && (!(authentication.getPrincipal() instanceof cn.voidhaven.api.security.AccountPrincipal principal) || !principal.matches(actor)))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "会话已失效，请重新登录。" );
        if (authorization != null && authentication != null) authorization.recheckWrite();
        return actor;
    }

    public Account lockTarget(String accountId) {
        return accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在。"));
    }
}
