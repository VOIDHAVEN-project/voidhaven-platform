package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicCaseAdministrationServiceTest {
    private static final String ID = "01JSUBMISSION0000000000001";

    @Test void omitted_due_at_preserves_the_locked_database_value() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant existing = Instant.parse("2030-01-08T10:00:00Z");
        when(jdbc.queryForList(startsWith("select state, due_at"), eq(ID)))
                .thenReturn(List.of(Map.of("state", "RECEIVED", "due_at", Timestamp.from(existing), "version", 3L)));
        when(jdbc.update(startsWith("update content_submissions"), any(Object[].class))).thenReturn(1);
        PublicCaseAdministrationService service = new PublicCaseAdministrationService(
                jdbc, mock(PrivateValueCipher.class), mock(AuditService.class));

        PublicCaseAdministrationService.TransitionResult result = service.transitionSubmission(
                ID, "IN_PROGRESS", null, null, false, 3, "开始处理",
                "01JADMIN000000000000000000", "ADMIN", "request-1");

        assertThat(result.dueAt()).isEqualTo(existing);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(startsWith("update content_submissions"), arguments.capture());
        assertThat(arguments.getValue()[2]).isEqualTo(Timestamp.from(existing));
    }

    @Test void due_at_is_cleared_only_when_explicitly_requested() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant existing = Instant.parse("2030-01-08T10:00:00Z");
        when(jdbc.queryForList(startsWith("select state, due_at"), eq(ID)))
                .thenReturn(List.of(Map.of("state", "RECEIVED", "due_at", Timestamp.from(existing), "version", 3L)));
        when(jdbc.update(startsWith("update content_submissions"), any(Object[].class))).thenReturn(1);
        PublicCaseAdministrationService service = new PublicCaseAdministrationService(
                jdbc, mock(PrivateValueCipher.class), mock(AuditService.class));

        PublicCaseAdministrationService.TransitionResult result = service.transitionSubmission(
                ID, "IN_PROGRESS", null, null, true, 3, "明确清除",
                "01JADMIN000000000000000000", "ADMIN", "request-2");

        assertThat(result.dueAt()).isNull();
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(startsWith("update content_submissions"), arguments.capture());
        assertThat(arguments.getValue()[2]).isNull();
    }

    @Test void setting_and_clearing_due_at_in_the_same_request_is_rejected() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("select state, due_at"), eq(ID)))
                .thenReturn(List.of(Map.of("state", "RECEIVED", "due_at", Timestamp.from(Instant.now()), "version", 3L)));
        PublicCaseAdministrationService service = new PublicCaseAdministrationService(
                jdbc, mock(PrivateValueCipher.class), mock(AuditService.class));

        assertThatThrownBy(() -> service.transitionSubmission(ID, "IN_PROGRESS", null,
                Instant.parse("2030-01-09T10:00:00Z"), true, 3, "冲突请求",
                "01JADMIN000000000000000000", "ADMIN", "request-3"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CASE_DUE_AT_CONFLICT");
        verify(jdbc, never()).update(startsWith("update content_submissions"), any(Object[].class));
    }

    @Test void an_explicit_due_at_replaces_the_previous_value() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant existing = Instant.parse("2030-01-08T10:00:00Z");
        Instant next = Instant.parse("2030-02-01T12:00:00Z");
        when(jdbc.queryForList(startsWith("select state, due_at"), eq(ID)))
                .thenReturn(List.of(Map.of("state", "RECEIVED", "due_at", Timestamp.from(existing), "version", 3L)));
        when(jdbc.update(startsWith("update content_submissions"), any(Object[].class))).thenReturn(1);
        PublicCaseAdministrationService service = new PublicCaseAdministrationService(
                jdbc, mock(PrivateValueCipher.class), mock(AuditService.class));

        PublicCaseAdministrationService.TransitionResult result = service.transitionSubmission(
                ID, "IN_PROGRESS", null, next, false, 3, "改期",
                "01JADMIN000000000000000000", "ADMIN", "request-4");

        assertThat(result.dueAt()).isEqualTo(next);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(startsWith("update content_submissions"), arguments.capture());
        assertThat(arguments.getValue()[2]).isEqualTo(Timestamp.from(next));
    }

    @Test void a_stale_version_does_not_write_status_history() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("select state, due_at"), eq(ID)))
                .thenReturn(List.of(Map.of("state", "RECEIVED", "due_at", Timestamp.from(Instant.now()), "version", 4L)));
        when(jdbc.update(startsWith("update content_submissions"), any(Object[].class))).thenReturn(0);
        PublicCaseAdministrationService service = new PublicCaseAdministrationService(
                jdbc, mock(PrivateValueCipher.class), mock(AuditService.class));

        assertThatThrownBy(() -> service.transitionSubmission(ID, "IN_PROGRESS", null, null, false, 3, "过期版本",
                "01JADMIN000000000000000000", "ADMIN", "request-5"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CASE_VERSION_CONFLICT");
        verify(jdbc, never()).update(startsWith("insert into public_case_status_history"), any(), any(), any(), any(), any(), any(), any());
    }
}
