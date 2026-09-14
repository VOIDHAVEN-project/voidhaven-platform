package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PublicCaseServiceTest {
    @Test void copyright_and_partner_contacts_fail_closed_without_calling_a_probe_encrypt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        PrivateValueCipher cipher = spy(new PrivateValueCipher(false, "", "", environment));
        PublicCaseService service = new PublicCaseService(jdbc, idempotency, cipher);

        assertThatThrownBy(() -> service.createSubmission("key-1",
                new PublicCaseService.SubmissionCommand(PublicCaseService.SubmissionType.COPYRIGHT, "标题", "说明", null, null, "wechat:example")))
                .hasFieldOrPropertyWithValue("code", "PRIVATE_CONTACT_DISABLED");
        assertThatThrownBy(() -> service.createPartnerApplication("key-2",
                new PublicCaseService.PartnerApplicationCommand("名称", "介绍", null, "wechat:example")))
                .hasFieldOrPropertyWithValue("code", "PRIVATE_CONTACT_DISABLED");
        verify(cipher, never()).encrypt(anyString(), anyString());
        verifyNoInteractions(idempotency, jdbc);
    }

    @Test void optional_feedback_without_contact_does_not_require_the_cipher() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        PrivateValueCipher cipher = spy(new PrivateValueCipher(false, "", "", environment));
        PublicCaseService service = new PublicCaseService(jdbc, idempotency, cipher);
        when(idempotency.executeSingleDelivery(any(), any(), any())).thenReturn(new PublicCaseService.CreatedCase("01JCASE0000000000000000001", "query-code"));

        service.createSubmission("key-3",
                new PublicCaseService.SubmissionCommand(PublicCaseService.SubmissionType.FEEDBACK, "标题", "说明", null, null, null));
        verify(cipher, never()).requireEnabled();
        verify(cipher, never()).encrypt(anyString(), anyString());
        verify(idempotency).executeSingleDelivery(eq("key-3"), any(), any());
    }
}
