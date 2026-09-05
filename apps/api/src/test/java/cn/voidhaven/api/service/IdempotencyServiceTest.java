package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {
    record Result(String value) { }

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final IdempotencyReservationService reservations = mock(IdempotencyReservationService.class);
    private final IdempotencyService service = new IdempotencyService(jdbc, JsonMapper.builder().build(), reservations);

    @Test
    void rejects_a_matching_request_while_the_first_request_owns_the_key() {
        when(reservations.reserve(eq("same-key"), anyString())).thenAnswer(invocation ->
                new IdempotencyReservationService.Existing(invocation.getArgument(1), 102, "{\"state\":\"IN_PROGRESS\"}"));
        AtomicInteger actions = new AtomicInteger();

        assertThatThrownBy(() -> service.execute("same-key", new Result("request"), Result.class,
                () -> { actions.incrementAndGet(); return new Result("response"); }))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("REQUEST_IN_PROGRESS"));
        assertThat(actions).hasValue(0);
    }

    @Test
    void rejects_reusing_a_key_for_a_different_payload() {
        when(reservations.reserve(eq("reused-key"), anyString())).thenReturn(
                new IdempotencyReservationService.Existing("different-request-hash", 200, "{\"value\":\"old\"}"));

        assertThatThrownBy(() -> service.execute("reused-key", new Result("new"), Result.class, () -> new Result("ignored")))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void releases_the_reservation_when_the_business_action_fails() {
        when(reservations.reserve(eq("failed-key"), anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.execute("failed-key", new Result("request"), Result.class,
                () -> { throw new IllegalStateException("synthetic failure"); }))
                .isInstanceOf(IllegalStateException.class);
        verify(reservations).release(eq("failed-key"), anyString());
    }
}
