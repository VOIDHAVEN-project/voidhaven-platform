package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RelatedContentIdsTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void unions_single_field_and_tags_without_duplicates() {
        var details = json.readTree("{\"relatedCompetitionId\":\"01HZXC0V4K7N9PQRSTVWXY0001\",\"relatedCompetitionIds\":[\"01HZXC0V4K7N9PQRSTVWXY0001\",\"01HZXC0V4K7N9PQRSTVWXY0002\"]}");
        assertThat(RelatedContentIds.fromDetails(details)).containsExactly(
                "01HZXC0V4K7N9PQRSTVWXY0001", "01HZXC0V4K7N9PQRSTVWXY0002");
    }

    @Test void accepts_comma_separated_legacy_values_and_skips_blanks() {
        var details = json.readTree("{\"relatedCompetitionId\":\"  \",\"relatedCompetitionIds\":\"01HZXC0V4K7N9PQRSTVWXY0003, 01HZXC0V4K7N9PQRSTVWXY0004\"}");
        assertThat(RelatedContentIds.fromDetails(details)).containsExactly(
                "01HZXC0V4K7N9PQRSTVWXY0003", "01HZXC0V4K7N9PQRSTVWXY0004");
        assertThat(RelatedContentIds.fromDetails(json.readTree("{}"))).isEmpty();
        assertThat(RelatedContentIds.fromDetails(null)).isEmpty();
    }
}
