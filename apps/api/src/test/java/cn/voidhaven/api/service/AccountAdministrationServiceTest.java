package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.persistence.AccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountAdministrationServiceTest {
    @Test void suspends_an_account_and_writes_a_minimal_audit_event() {
        AccountRepository accounts = mock(AccountRepository.class);
        AuditService audit = mock(AuditService.class);
        Account account = new Account("01JACCOUNT00000000000000000", "partner-one", "hash");
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        AccountAdministrationService service = new AccountAdministrationService(accounts, audit);

        var result = service.disable(account.getId(), "SUSPENDED", "资格复核未通过", "01JADMIN000000000000000000", "SUPER_ADMIN", "01JREQUEST0000000000000000");

        assertThat(result.qualificationStatus()).isEqualTo("SUSPENDED");
        assertThat(account.isActive()).isFalse();
        verify(audit).record(eq("01JADMIN000000000000000000"), eq("SUPER_ADMIN"), eq("ACCOUNT_QUALIFICATION_CHANGED"), eq("ACCOUNT"), eq(account.getId()), anyString(), anyString(), eq("资格复核未通过"), eq("01JREQUEST0000000000000000"));
    }

    @Test void rejects_self_disable_and_invalid_target_status() {
        AccountAdministrationService service = new AccountAdministrationService(mock(AccountRepository.class), mock(AuditService.class));
        assertThatThrownBy(() -> service.disable("01JADMIN000000000000000000", "SUSPENDED", "x", "01JADMIN000000000000000000", "SUPER_ADMIN", "01JREQUEST0000000000000000"))
                .hasMessageContaining("自身账号");
        assertThatThrownBy(() -> service.disable("01JACCOUNT00000000000000000", "ACTIVE", "x", "01JADMIN000000000000000000", "SUPER_ADMIN", "01JREQUEST0000000000000000"))
                .hasMessageContaining("只能调整");
    }

    @Test void suspended_accounts_can_be_reactivated_but_revoked_accounts_are_final() {
        AccountRepository accounts = mock(AccountRepository.class);
        Account account = new Account("01JACCOUNT00000000000000000", "partner-one", "hash");
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        AccountAdministrationService service = new AccountAdministrationService(accounts, mock(AuditService.class));

        assertThat(service.suspend(account.getId(), "暂停资格复核", "01JADMIN000000000000000000", "SUPER_ADMIN", "01JREQUEST0000000000000000").qualificationStatus()).isEqualTo("SUSPENDED");
        assertThat(service.reactivate(account.getId(), "复核完成", "01JADMIN000000000000000000", "SUPER_ADMIN", "01JREQUEST0000000000000001").qualificationStatus()).isEqualTo("ACTIVE");
        assertThat(service.revoke(account.getId(), "永久撤销", "01JADMIN000000000000000000", "SUPER_ADMIN", "01JREQUEST0000000000000002").qualificationStatus()).isEqualTo("REVOKED");
        assertThatThrownBy(() -> service.reactivate(account.getId(), "尝试恢复", "01JADMIN000000000000000000", "SUPER_ADMIN", "01JREQUEST0000000000000003"))
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_NOT_REACTIVATABLE");
    }
}
