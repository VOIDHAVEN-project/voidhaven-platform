package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualDeliveryServiceTest {
    @Test void records_activation_delivery_and_marks_partner_application() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        ManualDeliveryService service = new ManualDeliveryService(jdbc, audit);
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq("01JAPPLICATION000000000000"))).thenReturn(1);

        service.record("PARTNER_APPLICATION", "01JAPPLICATION000000000000", "ACTIVATION",
                "01JADMIN000000000000000000", "当面交付", "SUPER_ADMIN", "request-1");

        verify(jdbc).update(contains("insert into manual_delivery_records"), any(), eq("PARTNER_APPLICATION"),
                eq("01JAPPLICATION000000000000"), eq("ACTIVATION"), eq("01JADMIN000000000000000000"), eq("当面交付"));
        verify(jdbc).update(contains("activation_delivered_at"), eq("01JAPPLICATION000000000000"));
        verify(audit).record(eq("01JADMIN000000000000000000"), eq("SUPER_ADMIN"), eq("MANUAL_DELIVERY_RECORDED"),
                eq("PARTNER_APPLICATION"), eq("01JAPPLICATION000000000000"), any(), any(), eq("当面交付"), eq("request-1"));
    }

    @Test void rejects_unknown_subject_or_type() {
        ManualDeliveryService service = new ManualDeliveryService(mock(JdbcTemplate.class), mock(AuditService.class));
        assertThatThrownBy(() -> service.record("CONTENT", "01JAPPLICATION000000000000", "ACTIVATION",
                "01JADMIN000000000000000000", "x", "SUPER_ADMIN", "r"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MANUAL_DELIVERY_SUBJECT_INVALID");
    }

    @Test void records_submission_status_result_after_public_result_exists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("public_result is not null"), eq(Integer.class), eq("01JSUBMISSION0000000000000"))).thenReturn(1);
        ManualDeliveryService service = new ManualDeliveryService(jdbc, mock(AuditService.class));

        service.record("SUBMISSION", "01JSUBMISSION0000000000000", "STATUS_RESULT",
                "01JADMIN000000000000000000", "电话通知", "SUPER_ADMIN", "request-2");

        verify(jdbc).update(contains("insert into manual_delivery_records"), any(), eq("SUBMISSION"),
                eq("01JSUBMISSION0000000000000"), eq("STATUS_RESULT"),
                eq("01JADMIN000000000000000000"), eq("电话通知"));
    }
}
