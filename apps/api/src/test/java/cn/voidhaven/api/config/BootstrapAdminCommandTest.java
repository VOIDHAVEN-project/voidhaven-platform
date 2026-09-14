package cn.voidhaven.api.config;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.RoleRepository;
import cn.voidhaven.api.service.ActivationService;
import cn.voidhaven.api.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminCommandTest {
    @Test
    void requiresExplicitBootstrapAdminProfile() {
        Profile profile = BootstrapAdminCommand.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[]{"bootstrap-admin"}, profile.value());
    }

    @Test
    void acceptsProductionAndTestServerButRejectsLocal() {
        assertTrue(BootstrapAdminCommand.isApprovedEnvironment(new String[]{"production", "bootstrap-admin"}));
        assertTrue(BootstrapAdminCommand.isApprovedEnvironment(new String[]{"test-server", "bootstrap-admin"}));
        assertFalse(BootstrapAdminCommand.isApprovedEnvironment(new String[]{"local", "bootstrap-admin"}));
        assertFalse(BootstrapAdminCommand.isApprovedEnvironment(new String[]{"test-server" + "-lookalike", "bootstrap-admin"}));
    }

    @Test
    void rejectsUnsupportedEnvironmentBeforeOpeningATransaction() {
        Fixture fixture = fixture("local");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.command.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(error.getMessage().contains("production or test-server"));
        verifyNoInteractions(fixture.transactions);
    }

    @Test
    void refusesToBootstrapWhenAnAccountAlreadyExists() {
        Fixture fixture = fixture("test-server", "bootstrap-admin");
        when(fixture.accounts.count()).thenReturn(1L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.command.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(error.getMessage().contains("accounts table is not empty"));
        verifyNoInteractions(fixture.activations);
    }

    @Test
    void testServerBootstrapPrintsTheOneTimeActivationUrlAndAuditsWithASyntheticRequestId(CapturedOutput output) {
        Fixture fixture = fixture("test-server", "bootstrap-admin");
        when(fixture.accounts.count()).thenReturn(0L);
        when(fixture.roles.findByCode("ADMIN")).thenReturn(Optional.empty());
        when(fixture.roles.findByCode("SUPER_ADMIN")).thenReturn(Optional.empty());
        when(fixture.roles.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.activations.issueLocked(any(Account.class))).thenReturn(
                new ActivationService.IssuedActivation("single-use-test-token", Instant.now().plusSeconds(3600)));

        fixture.command.run(new DefaultApplicationArguments(new String[0]));

        assertTrue(output.getOut().contains("http://119.45.221.195/auth/activate?token=single-use-test-token"));
        verify(fixture.audit).record(any(), any(), any(), any(), any(), any(), any(), isNull(),
                argThat(requestId -> requestId != null && requestId.length() == 26));
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(String... profiles) {
        AccountRepository accounts = mock(AccountRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        ActivationService activations = mock(ActivationService.class);
        AuditService audit = mock(AuditService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        BootstrapAdminCommand command = new BootstrapAdminCommand(accounts, roles, activations, audit, environment, transactions);
        ReflectionTestUtils.setField(command, "adminLogin", "release-admin");
        ReflectionTestUtils.setField(command, "publicBaseUrl", "http://119.45.221.195");
        return new Fixture(command, accounts, roles, activations, audit, transactions);
    }

    private record Fixture(BootstrapAdminCommand command, AccountRepository accounts, RoleRepository roles,
                           ActivationService activations, AuditService audit, TransactionTemplate transactions) { }
}
