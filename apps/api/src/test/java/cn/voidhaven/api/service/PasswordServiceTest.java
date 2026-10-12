package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.ActivationTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordServiceTest {
    @Test void reset_tokens_are_invalidated_only_by_password_reset_type() {
        AccountRepository accounts = mock(AccountRepository.class);
        ActivationTokenRepository tokens = mock(ActivationTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        PasswordService service = new PasswordService(accounts, tokens, passwords, jdbc, audit);
        Account account = new Account("01JACCOUNT00000000000000000", "admin.example", "hash", "ACTIVE");
        account.addRole(new Role("ADMIN"));
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PasswordService.IssuedReset issued = service.issueReset(account.getId(), "人工重置", "01JADMIN000000000000000000", "SUPER_ADMIN", "request-1");

        assertThat(issued.resetToken()).isNotBlank();
        verify(tokens).invalidateUnusedByType(eq(account.getId()), eq("PASSWORD_RESET"), any());
        verify(tokens, never()).invalidateUnused(any(), any());
        verify(tokens, never()).invalidateUnusedByType(eq(account.getId()), eq("ACTIVATION"), any());
    }

    @Test void consuming_manual_reset_invalidates_remaining_manual_tokens_and_sessions() {
        AccountRepository accounts = mock(AccountRepository.class);
        ActivationTokenRepository tokens = mock(ActivationTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        PasswordService service = new PasswordService(accounts, tokens, passwords, jdbc, audit);
        Account account = new Account("01JACCOUNT00000000000000000", "user.example", "old-hash", "ACTIVE");
        AtomicReference<cn.voidhaven.api.domain.ActivationToken> saved = new AtomicReference<>();
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(tokens.save(any())).thenAnswer(invocation -> {
            cn.voidhaven.api.domain.ActivationToken value = invocation.getArgument(0);
            saved.set(value);
            return value;
        });
        when(passwords.matches(any(), eq("old-hash"))).thenReturn(false);
        when(passwords.encode("new-password-value")).thenReturn("new-hash");

        account.addRole(new Role("ADMIN"));
        PasswordService.IssuedReset issued = service.issueReset(account.getId(), "人工重置",
                "01JADMIN000000000000000000", "SUPER_ADMIN", "request-3");
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(saved.get()));
        when(tokens.findByTokenHashForUpdate(any())).thenReturn(Optional.of(saved.get()));

        assertThat(service.consumeReset(issued.resetToken(), "new-password-value")).isEqualTo("user.example");
        verify(tokens).invalidateUnused(eq(account.getId()), any());
        assertThat(account.getSecurityEpoch()).isEqualTo(1);
        verify(jdbc).update("delete from SPRING_SESSION where PRINCIPAL_NAME = ?", "user.example");
    }
}
