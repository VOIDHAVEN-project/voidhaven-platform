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
                new IdempotencyReservationService.Existing(invocation.getArgument(1), 102, "{\"state\":\"IN_PROGRESS\"}", true));
        AtomicInteger actions = new AtomicInteger();

        assertThatThrownBy(() -> service.execute("same-key", new Result("request"), Result.class,
                () -> { actions.incrementAndGet(); return new Result("response"); }))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("REQUEST_IN_PROGRESS"));
        assertThat(actions).hasValue(0);
    }

    @Test
    void rejects_reusing_a_key_for_a_different_payload() {
        when(reservations.reserve(eq("reused-key"), anyString())).thenReturn(
                new IdempotencyReservationService.Existing("different-request-hash", 200, "{\"value\":\"old\"}", true));

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

    @Test
    void refuses_to_replay_a_sensitive_single_delivery_result() {
        when(reservations.reserve(eq("secret-key"), anyString())).thenAnswer(invocation ->
                new IdempotencyReservationService.Existing(invocation.getArgument(1), 200, "{\"state\":\"DELIVERED_NO_REPLAY\"}", false));

        assertThatThrownBy(() -> service.executeSingleDelivery("secret-key", new Result("request"), () -> new Result("secret")))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("SENSITIVE_RESULT_NOT_REPLAYABLE"));
    }

    @Test
    void restores_homepage_replacement_as_typed_records_instead_of_raw_lists() throws Exception {
        HomepageAdministrationService.PlacementReplacement stored = new HomepageAdministrationService.PlacementReplacement(
                4,
                java.util.List.of(new HomepageAdministrationService.PlacementItem(
                        "01JPLACE000000000000000001", "FOCUS", "01JCONTENT0000000000000001", 0, null, null, 2,
                        "测试竞赛", cn.voidhaven.api.domain.ContentKind.COMPETITION, "OPEN", null, "2030-01-01", true, null)));
        String body = JsonMapper.builder().build().writeValueAsString(stored);
        when(reservations.reserve(eq("home-key"), anyString())).thenAnswer(invocation ->
                new IdempotencyReservationService.Existing(invocation.getArgument(1), 200, body, true));

        HomepageAdministrationService.PlacementReplacement replayed = service.execute(
                "home-key", new Result("request"), HomepageAdministrationService.PlacementReplacement.class,
                () -> { throw new IllegalStateException("must not re-run"); });
        assertThat(replayed.placements()).hasSize(1);
        assertThat(replayed.placements().get(0).slot()).isEqualTo("FOCUS");
        assertThat(replayed.placements().get(0).contentItemId()).isEqualTo("01JCONTENT0000000000000001");
    }
}
