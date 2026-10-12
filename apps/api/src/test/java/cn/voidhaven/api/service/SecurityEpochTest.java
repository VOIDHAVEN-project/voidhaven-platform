package cn.voidhaven.api.service;
import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.security.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SecurityEpochTest {
    @Test void suspended_then_restored_account_cannot_revive_an_unvisited_session() {
        Account account = new Account("a", "member", "hash");
        AccountPrincipal old = new AccountPrincipal(account);
        var revocations = new SecurityRevocationService(mock(ActivationTokenRepository.class), mock(JdbcTemplate.class));
        account.suspend(); revocations.revoke(account); account.reactivate();
        assertThat(old.matches(account)).isFalse();
        assertThat(new AccountPrincipal(account).matches(account)).isTrue();
    }
    @Test void restoring_a_never_activated_account_still_requires_recipient_activation() {
        Account account = new Account("pending", "pending-member", "!ACTIVATION_REQUIRED!", "PENDING");
        account.suspend(); account.reactivate();
        assertThat(account.isPending()).isTrue();
        assertThat(account.isActive()).isFalse();
    }
    @Test void changed_password_revokes_an_already_issued_reset_token() {
        var accounts = mock(AccountRepository.class); var tokens = mock(ActivationTokenRepository.class);
        var passwords = mock(PasswordEncoder.class);
        Account account = new Account("a", "member", "old");
        when(accounts.findByIdForUpdate("a")).thenReturn(Optional.of(account));
        when(accounts.findByLoginId("member")).thenReturn(Optional.of(account));
        when(passwords.matches("current-password", "old")).thenReturn(true);
        when(passwords.encode(anyString())).thenReturn("new");
        var service = new PasswordService(accounts, tokens, passwords, mock(JdbcTemplate.class), mock(AuditService.class));
        var issued = service.issueReset("a", "test", "root", "SUPER_ADMIN", "r");
        var token = new ActivationToken("t", "a", "PASSWORD_RESET", "hash", Instant.now().plusSeconds(60));
        token.bind(account, null);
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(tokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
        service.change("member", "current-password", "new-password-value", "r");
        assertThatThrownBy(() -> service.consumeReset(issued.resetToken(), "another-password-value"))
                .hasFieldOrPropertyWithValue("code", "PASSWORD_RESET_INVALID");
    }
    @Test void legacy_credentials_and_credentials_from_revoked_issuers_are_rejected() {
        var accounts = mock(AccountRepository.class);
        var security = new CredentialSecurityService(accounts);
        Account target = new Account("a", "member", "hash"), issuer = new Account("b", "root", "hash");
        var token = new ActivationToken("t", "a", "hash", Instant.now().plusSeconds(60));
        assertThat(security.valid(token, target)).isFalse();
        token.bind(target, issuer); when(accounts.findById("b")).thenReturn(Optional.of(issuer));
        assertThat(security.valid(token, target)).isTrue();
        issuer.advanceSecurityEpoch(); assertThat(security.valid(token, target)).isFalse();
    }
    @Test void super_administrator_never_receives_a_web_recovery_token() {
        var accounts = mock(AccountRepository.class); var tokens = mock(ActivationTokenRepository.class);
        Account root = new Account("root", "root", "hash"); root.addRole(new Role("SUPER_ADMIN"));
        when(accounts.findByIdForUpdate("root")).thenReturn(Optional.of(root));
        var service = new PasswordService(accounts, tokens, mock(PasswordEncoder.class), mock(JdbcTemplate.class), mock(AuditService.class));
        assertThatThrownBy(() -> service.issueReset("root", "test", "other", "SUPER_ADMIN", "r"))
                .hasFieldOrPropertyWithValue("code", "SUPER_ADMIN_WEB_RECOVERY_FORBIDDEN");
        verify(tokens, never()).save(any());
    }
}
