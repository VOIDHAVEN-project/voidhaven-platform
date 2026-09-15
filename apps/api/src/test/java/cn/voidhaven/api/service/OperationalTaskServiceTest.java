package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalTaskServiceTest {
    @Test void verification_tasks_use_only_the_latest_record_and_have_matching_filters() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        List<OperationalTaskService.Task> tasks = new OperationalTaskService(jdbc).tasks();

        assertThat(tasks).anySatisfy(task -> {
            assertThat(task.code()).isEqualTo("VERIFICATION_ATTENTION");
            assertThat(task.href()).endsWith("verification=attention");
        }).anySatisfy(task -> {
            assertThat(task.code()).isEqualTo("VERIFICATION_DUE");
            assertThat(task.href()).endsWith("verification=due");
        });
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(9)).queryForObject(sql.capture(), eq(Integer.class));
        String attention = sql.getAllValues().stream().filter(value -> value.contains("v.outcome in")).findFirst().orElseThrow();
        String due = sql.getAllValues().stream().filter(value -> value.contains("v.next_check_at")).findFirst().orElseThrow();
        assertThat(attention).contains("not exists", "newer.checked_at", "record_state = 'ACTIVE'");
        assertThat(due).contains("not exists", "newer.checked_at", "next_check_at <= current_timestamp(6)", "v.outcome = 'VALID'");
    }
}
