package cn.voidhaven.api.config;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoverSuperAdminCommandTest {
    @Test void web_mode_and_password_arguments_are_rejected_before_console_access() {
        var env = new MockEnvironment().withProperty("spring.main.web-application-type", "servlet");
        env.setActiveProfiles("production", "recover-super-admin");
        var command = new RecoverSuperAdminCommand(mock(AccountRepository.class), mock(PasswordEncoder.class),
                mock(SecurityRevocationService.class), mock(AuditService.class), env, mock(TransactionTemplate.class));
        assertThatThrownBy(() -> command.run(new DefaultApplicationArguments("--login=root", "--operator=ops", "--reason=incident")))
                .hasMessageContaining("non-Web");
        env.setProperty("spring.main.web-application-type", "none");
        assertThatThrownBy(() -> command.run(new DefaultApplicationArguments("--login=root", "--operator=ops", "--reason=incident", "--password=forbidden")))
                .hasMessageContaining("passwords must never be arguments");
    }
    @Test void recovery_activates_only_existing_pending_super_admin_and_revokes_all_old_access() {
        var accounts = mock(AccountRepository.class); var encoder = mock(PasswordEncoder.class);
        var revocations = mock(SecurityRevocationService.class); var audit = mock(AuditService.class);
        var transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> { ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>) invocation.getArgument(0))
                .accept(new SimpleTransactionStatus()); return null; }).when(transactions).executeWithoutResult(any());
        var account = new Account("existing", "root", "!pending!", "PENDING"); account.addRole(new Role("SUPER_ADMIN"));
        when(accounts.findByLoginId("root")).thenReturn(Optional.of(account)); when(accounts.findByIdForUpdate("existing")).thenReturn(Optional.of(account));
        when(encoder.encode("synthetic-password")).thenReturn("encoded");
        var command = new RecoverSuperAdminCommand(accounts, encoder, revocations, audit, new MockEnvironment(), transactions);
        command.recover("root", "synthetic-password", "isolated-operator", "test-only");
        assertThat(account.isActive()).isTrue(); assertThat(account.getPasswordHash()).isEqualTo("encoded");
        verify(revocations).revoke(account); verify(accounts, never()).save(any());
        verify(audit).record(eq("SYSTEM"), eq("SYSTEM"), eq("SUPER_ADMIN_RECOVERED"), eq("ACCOUNT"), eq("existing"), anyString(), anyString(), contains("isolated-operator"), anyString());
        when(accounts.findByLoginId("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> command.recover("missing", "synthetic-password", "ops", "test")).hasMessageContaining("existing");
    }
}
