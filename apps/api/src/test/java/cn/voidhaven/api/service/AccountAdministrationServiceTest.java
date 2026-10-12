package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.RoleRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountAdministrationServiceTest {
    private static final String ACTOR = "01JADMIN000000000000000000";
    private static final String TARGET = "01JACCOUNT00000000000000000";

    @Test void suspends_an_account_and_writes_a_minimal_audit_event() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditService audit = mock(AuditService.class);
        Account account = new Account(TARGET, "partner-one", "hash");
        AccountAdministrationService service = service(accounts, audit, account);

        var result = service.disable(account.getId(), "SUSPENDED", "资格复核未通过", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000000");

        assertThat(result.qualificationStatus()).isEqualTo("SUSPENDED");
        assertThat(account.isActive()).isFalse();
        assertThat(account.getSecurityEpoch()).isEqualTo(1);
        verify(audit).record(eq(ACTOR), eq("SUPER_ADMIN"), eq("ACCOUNT_QUALIFICATION_CHANGED"), eq("ACCOUNT"), eq(account.getId()), anyString(), anyString(), eq("资格复核未通过"), eq("01JREQUEST0000000000000000"));
    }

    @Test void rejects_self_disable_and_invalid_target_status() {
        AccountAdministrationService service = service(mock(AccountRepository.class), mock(AuditService.class), new Account(TARGET, "partner-one", "hash"));
        assertThatThrownBy(() -> service.disable(ACTOR, "SUSPENDED", "x", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000000"))
                .hasMessageContaining("自身账号");
        assertThatThrownBy(() -> service.disable(TARGET, "ACTIVE", "x", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000000"))
                .hasMessageContaining("只能调整");
    }

    @Test void suspended_accounts_can_be_reactivated_but_revoked_accounts_are_final() {
        AccountRepository accounts = mock(AccountRepository.class);
        Account account = new Account(TARGET, "partner-one", "hash");
        AccountAdministrationService service = service(accounts, mock(AuditService.class), account);

        assertThat(service.suspend(account.getId(), "暂停资格复核", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000000").qualificationStatus()).isEqualTo("SUSPENDED");
        assertThat(service.reactivate(account.getId(), "复核完成", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000001").qualificationStatus()).isEqualTo("ACTIVE");
        assertThat(service.revoke(account.getId(), "永久撤销", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000002").qualificationStatus()).isEqualTo("REVOKED");
        assertThatThrownBy(() -> service.reactivate(account.getId(), "尝试恢复", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000003"))
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_REVOKED_FINAL");
    }

    @Test void creates_a_pending_admin_without_a_partner_profile() {
        AccountRepository accounts = mock(AccountRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        ActivationService activations = mock(ActivationService.class);
        AccountLockService locks = mock(AccountLockService.class);
        AuditService audit = mock(AuditService.class);
        when(roles.findByCode("ADMIN")).thenReturn(Optional.of(new Role("ADMIN")));
        when(accounts.findByLoginId("admin.example")).thenReturn(Optional.empty());
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activations.issueLocked(any(Account.class))).thenReturn(new ActivationService.IssuedActivation("one-time-token", Instant.parse("2026-09-10T12:00:00Z")));
        when(locks.requireActiveActor(ACTOR)).thenReturn(new Account(ACTOR, "super-admin", "hash"));
        AccountAdministrationService service = new AccountAdministrationService(accounts, roles, activations, locks, audit);

        var created = service.createAdmin(" admin.example ", "新增内容审核管理员", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000000");

        assertThat(created.loginId()).isEqualTo("admin.example");
        assertThat(created.qualificationStatus()).isEqualTo("PENDING");
        assertThat(created.roles()).containsExactly("ADMIN");
        assertThat(created.activationToken()).isEqualTo("one-time-token");
        verify(accounts, never()).findByLoginId(" SUPER_ADMIN ");
    }

    @Test void refuses_to_reissue_activation_for_a_non_pending_account() {
        Account account = new Account(TARGET, "admin.example", "hash");
        AccountAdministrationService service = service(mock(AccountRepository.class), mock(AuditService.class), account);
        assertThatThrownBy(() -> service.reissueActivation(TARGET, "补发", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000000"))
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_NOT_PENDING");
    }

    @Test void refuses_to_mutate_the_super_admin_account() {
        Account account = new Account(TARGET, "root", "hash");
        account.addRole(new Role("SUPER_ADMIN"));
        AccountAdministrationService service = service(mock(AccountRepository.class), mock(AuditService.class), account);
        assertThatThrownBy(() -> service.revoke(TARGET, "误操作", ACTOR, "SUPER_ADMIN", "01JREQUEST0000000000000000"))
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_SUPER_ADMIN_IMMUTABLE");
    }

    private AccountAdministrationService service(AccountRepository accounts, AuditService audit, Account target) {
        RoleRepository roles = mock(RoleRepository.class);
        ActivationService activations = mock(ActivationService.class);
        AccountLockService locks = mock(AccountLockService.class);
        when(locks.requireActiveActor(anyString())).thenReturn(new Account(ACTOR, "super-admin", "hash"));
        when(locks.lockTarget(target.getId())).thenReturn(target);
        var service = new AccountAdministrationService(accounts, roles, activations, locks, audit);
        service.security(new SecurityRevocationService(mock(cn.voidhaven.api.persistence.ActivationTokenRepository.class), mock(org.springframework.jdbc.core.JdbcTemplate.class)));
        return service;
    }
}
