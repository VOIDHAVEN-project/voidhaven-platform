package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.ActivationToken;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.ActivationTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ActivationServiceTest {
    @Test void stores_only_a_hash_and_allows_the_token_to_be_consumed_once() {
        ActivationTokenRepository tokens = mock(ActivationTokenRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        ActivationService service = new ActivationService(tokens, accounts, passwords, mock(JdbcTemplate.class));
        ArgumentCaptor<ActivationToken> saved = ArgumentCaptor.forClass(ActivationToken.class);
        Account pending = new Account("01JACCOUNT00000000000000000", "partner-one", "!ACTIVATION_REQUIRED!", "PENDING");
        when(accounts.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(tokens.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        String raw = service.issue(pending.getId()).token();
        ActivationToken token = saved.getValue();
        assertThat(raw).hasSizeGreaterThan(32);
        assertThat(token.getTokenHash()).isNotEqualTo(raw).hasSize(64);
        assertThat(token.getExpiresAt()).isNotNull();

        Account account = new Account("01JACCOUNT00000000000000000", "partner-one", "!ACTIVATION_REQUIRED!", "PENDING");
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(passwords.encode("sufficient-local-password")).thenReturn("argon2-hash");

        service.consume(raw, "sufficient-local-password");
        assertThat(account.isActive()).isTrue();
        assertThat(account.getPasswordHash()).isEqualTo("argon2-hash");
        InOrder activationOrder = inOrder(passwords, tokens);
        activationOrder.verify(passwords).encode("sufficient-local-password");
        activationOrder.verify(tokens).invalidateUnused(eq(account.getId()), any());
        verify(tokens).invalidateUnused(eq(account.getId()), any());
        assertThat(account.getSecurityEpoch()).isEqualTo(1);
        verify(tokens, never()).invalidateUnusedExcept(anyString(), anyString(), any());
        assertThatThrownBy(() -> service.consume(raw, "sufficient-local-password")).hasMessageContaining("已使用");
    }

    @Test void refuses_to_activate_a_revoked_account() {
        ActivationTokenRepository tokens = mock(ActivationTokenRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        ActivationService service = new ActivationService(tokens, accounts, mock(PasswordEncoder.class), mock(JdbcTemplate.class));
        Account revoked = new Account("01JACCOUNT00000000000000000", "admin.example", "hash", "REVOKED");
        ActivationToken token = new ActivationToken("01JTOKEN000000000000000000", revoked.getId(), "abc", java.time.Instant.now().plusSeconds(3600));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(accounts.findByIdForUpdate(revoked.getId())).thenReturn(Optional.of(revoked));
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.consume("raw-token", "sufficient-local-password"))
                .hasFieldOrPropertyWithValue("code", "ACTIVATION_TOKEN_INVALID");
        assertThat(revoked.getQualificationStatus()).isEqualTo("REVOKED");
    }

    @Test void issuing_or_consuming_activation_does_not_invalidate_password_reset_tokens() {
        ActivationTokenRepository tokens = mock(ActivationTokenRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        ActivationService service = new ActivationService(tokens, accounts, mock(PasswordEncoder.class), mock(JdbcTemplate.class));
        Account pending = new Account("01JACCOUNT00000000000000000", "partner-one", "!ACTIVATION_REQUIRED!", "PENDING");
        when(accounts.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.issue(pending.getId());
        verify(tokens).invalidateUnusedByType(eq(pending.getId()), eq("ACTIVATION"), any());
        verify(tokens, never()).invalidateUnusedByType(eq(pending.getId()), eq("PASSWORD_RESET"), any());

        ActivationToken reset = new ActivationToken("01JTOKENRESET0000000000000", pending.getId(), "PASSWORD_RESET", "abc", java.time.Instant.now().plusSeconds(3600));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(reset));
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(reset));
        assertThatThrownBy(() -> service.consume("password-reset-raw", "sufficient-local-password"))
                .hasFieldOrPropertyWithValue("code", "ACTIVATION_TOKEN_INVALID");
        verify(tokens, never()).invalidateUnusedByTypeExcept(anyString(), eq("PASSWORD_RESET"), anyString(), any());
    }
}
