package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaReadServiceTest {
    @Test void public_action_qr_requires_an_open_published_public_entry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000000")))
                .thenReturn("content-action/asset.png");

        new MediaReadService(jdbc, mock(MediaStorage.class)).loadPublic("01JMEDIA00000000000000000");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000000"));
        assertThat(sql.getValue()).contains("ca.access_level = 'PUBLIC'");
        assertThat(sql.getValue()).contains("c.published_revision_id = ca.revision_id");
        assertThat(sql.getValue()).contains("c.operational_status = 'OPEN'");
        assertThat(sql.getValue()).contains("c.visibility = 'PUBLIC'");
        assertThat(sql.getValue()).contains("c.record_state = 'ACTIVE'");
        assertThat(sql.getValue()).contains("ce.access_level = 'PUBLIC'");
    }

    @Test void community_action_qr_stays_on_the_gated_path_and_still_requires_an_open_item() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000001")))
                .thenReturn("content-action/gated.png");

        new MediaReadService(jdbc, mock(MediaStorage.class)).loadCommunity("01JMEDIA00000000000000001");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000001"));
        assertThat(sql.getValue()).contains("ca.access_level = 'COMMUNITY_GATED'");
        assertThat(sql.getValue()).contains("c.operational_status = 'OPEN'");
        assertThat(sql.getValue()).contains("c.published_revision_id = ca.revision_id");
        assertThat(sql.getValue()).doesNotContain("ca.access_level = 'PUBLIC'");
    }

    @Test void managed_preview_includes_attached_action_images_after_reservation_expiry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000002"),
                eq(true), eq("01JADMIN000000000000000000"), eq(true), eq("01JADMIN000000000000000000"),
                eq(true), eq("01JADMIN000000000000000000"))).thenReturn("content-action/preview.png");

        new MediaReadService(jdbc, mock(MediaStorage.class))
                .loadManaged("01JMEDIA00000000000000002", "01JADMIN000000000000000000", true);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000002"),
                eq(true), eq("01JADMIN000000000000000000"), eq(true), eq("01JADMIN000000000000000000"),
                eq(true), eq("01JADMIN000000000000000000"));
        assertThat(sql.getValue()).contains("content_action_entries");
        assertThat(sql.getValue()).contains("c.current_revision_id = ca.revision_id");
        assertThat(sql.getValue()).contains("content_action_media_reservations");
    }

    @Test void project_join_public_qr_is_readable_through_the_public_media_path() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000003")))
                .thenReturn("community/project-join.png");

        new MediaReadService(jdbc, mock(MediaStorage.class)).loadPublic("01JMEDIA00000000000000003");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000003"));
        assertThat(sql.getValue()).contains("project_join_entries pje");
        assertThat(sql.getValue()).contains("pje.access_level = 'PUBLIC'");
    }

    @Test void project_join_gated_qr_stays_on_the_community_path() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000004")))
                .thenReturn("community/project-join-gated.png");

        new MediaReadService(jdbc, mock(MediaStorage.class)).loadCommunity("01JMEDIA00000000000000004");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(ResultSetExtractor.class), eq("01JMEDIA00000000000000004"));
        assertThat(sql.getValue()).contains("project_join_entries pje");
        assertThat(sql.getValue()).doesNotContain("pje.access_level");
    }
}
