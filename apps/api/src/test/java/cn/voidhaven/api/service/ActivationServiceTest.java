package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.ActivationToken;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.ActivationTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        ActivationService service = new ActivationService(tokens, accounts, passwords);
        ArgumentCaptor<ActivationToken> saved = ArgumentCaptor.forClass(ActivationToken.class);
        when(tokens.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        String raw = service.issue("01JACCOUNT00000000000000000");
        ActivationToken token = saved.getValue();
        assertThat(raw).hasSizeGreaterThan(32);
        assertThat(token.getTokenHash()).isNotEqualTo(raw).hasSize(64);

        Account account = new Account("01JACCOUNT00000000000000000", "partner-one", "!ACTIVATION_REQUIRED!", "PENDING");
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(passwords.encode("sufficient-local-password")).thenReturn("argon2-hash");

        service.consume(raw, "sufficient-local-password");
        assertThat(account.isActive()).isTrue();
        assertThat(account.getPasswordHash()).isEqualTo("argon2-hash");
        assertThatThrownBy(() -> service.consume(raw, "sufficient-local-password")).hasMessageContaining("已使用");
    }
}
