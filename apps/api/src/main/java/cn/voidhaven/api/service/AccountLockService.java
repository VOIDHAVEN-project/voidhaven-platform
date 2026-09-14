package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccountLockService {
    private final AccountRepository accounts;

    public AccountLockService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Account requireActiveActor(String actorId) {
        Account actor = accounts.findByIdForShare(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号已停用或资格不可用。"));
        if (!actor.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号已停用或资格不可用。");
        }
        return actor;
    }

    public Account lockTarget(String accountId) {
        return accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在。"));
    }
}
