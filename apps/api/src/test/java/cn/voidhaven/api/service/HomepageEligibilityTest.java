package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.PublicationStatus;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.Visibility;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HomepageEligibilityTest {
    private final Instant now = Instant.parse("2026-09-15T10:00:00Z");

    @Test void focus_accepts_every_content_kind_and_typed_slots_do_not() {
        assertThat(HomepageEligibility.kindMatchesSlot("FOCUS", ContentKind.RESOURCE)).isTrue();
        assertThat(HomepageEligibility.kindMatchesSlot("COMPETITION", ContentKind.RECRUITMENT)).isFalse();
        assertThat(HomepageEligibility.kindMatchesSlot("RESOURCE", ContentKind.RESOURCE)).isTrue();
    }

    @Test void display_window_treats_null_bounds_as_open() {
        assertThat(HomepageEligibility.inDisplayWindow(null, null, now)).isTrue();
        assertThat(HomepageEligibility.inDisplayWindow(now.plusSeconds(60), null, now)).isFalse();
        assertThat(HomepageEligibility.inDisplayWindow(null, now, now)).isFalse();
    }

    @Test void date_deadline_uses_shanghai_calendar_day() throws Exception {
        var details = JsonMapper.builder().build().readTree("{\"deadlineDate\":\"2026-09-14\"}");
        assertThat(HomepageEligibility.deadlinePassed(ContentKind.COMPETITION, details, now)).isTrue();
        var today = JsonMapper.builder().build().readTree("{\"deadlineDate\":\"" + LocalDate.of(2026, 9, 15) + "\"}");
        assertThat(HomepageEligibility.deadlinePassed(ContentKind.COMPETITION, today, now)).isFalse();
        assertThat(HomepageEligibility.deadlinePassed(ContentKind.RESOURCE, details, now)).isFalse();
    }

    @Test void currently_listable_rejects_closed_expired_and_non_public_rows() throws Exception {
        var details = JsonMapper.builder().build().readTree("{}");
        assertThat(HomepageEligibility.currentlyListable(
                Visibility.PUBLIC, RecordState.ACTIVE, PublicationStatus.ACTIVE, true, true,
                ContentKind.RECRUITMENT, "OPEN", details, now)).isTrue();
        assertThat(HomepageEligibility.currentlyListable(
                Visibility.PUBLIC, RecordState.ACTIVE, PublicationStatus.CLOSED, true, true,
                ContentKind.COMPETITION, null, details, now)).isFalse();
        assertThat(HomepageEligibility.currentlyListable(
                Visibility.PUBLIC, RecordState.ACTIVE, PublicationStatus.ACTIVE, true, true,
                ContentKind.RECRUITMENT, "FILLED", details, now)).isFalse();
    }

    @Test void issue_explains_window_and_kind_mismatches() {
        String issue = HomepageEligibility.issue(
                Visibility.PUBLIC, RecordState.ACTIVE, PublicationStatus.ACTIVE, true, true, false,
                null, null, ContentKind.RESOURCE, null, null, now);
        assertThat(issue).contains("类型");
        String window = HomepageEligibility.issue(
                Visibility.PUBLIC, RecordState.ACTIVE, PublicationStatus.ACTIVE, true, true, true,
                now.plusSeconds(3600), null, ContentKind.RESOURCE, null, null, now);
        assertThat(window).contains("展示时间");
    }
}
