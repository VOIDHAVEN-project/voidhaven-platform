package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.persistence.ActivationTokenRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class SecurityRevocationService {
    private final ActivationTokenRepository tokens;
    private final JdbcTemplate jdbc;
    public SecurityRevocationService(ActivationTokenRepository tokens, JdbcTemplate jdbc) {
        this.tokens = tokens; this.jdbc = jdbc;
    }
    /** Caller holds the account write lock in this transaction. */
    @Transactional
    public void revoke(Account account) {
        account.advanceSecurityEpoch();
        tokens.invalidateUnused(account.getId(), Instant.now());
        jdbc.update("update activation_tokens set consumed_at = current_timestamp(6) where issuer_account_id = ? and consumed_at is null", account.getId());
        jdbc.update("delete from SPRING_SESSION where PRINCIPAL_NAME = ?", account.getLoginId());
    }
}
