package cn.voidhaven.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomepageAdministrationServiceTest {
    @Test void reuses_inactive_row_instead_of_inserting_a_conflicting_unique_key() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        HomepageAdministrationService service = new HomepageAdministrationService(jdbc, audit);
        String contentId = "01JCONTENT0000000000000001";
        when(jdbc.queryForObject(startsWith("select count(*) from content_items"), eq(Integer.class), eq(contentId))).thenReturn(1);
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            when(row.getString("id")).thenReturn("01JPLACE000000000000000001");
            when(row.getString("slot")).thenReturn("FOCUS");
            when(row.getString("content_item_id")).thenReturn(contentId);
            handler.processRow(row);
            return null;
        }).when(jdbc).query(eq("select id, slot, content_item_id from homepage_placements"), any(RowCallbackHandler.class));
        when(jdbc.query(contains("where active = true"), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of(new HomepageAdministrationService.Placement("01JPLACE000000000000000001", "FOCUS", contentId, 2, null, null, 4)));

        HomepageAdministrationService.PlacementReplacement result = service.replace(
                List.of(new HomepageAdministrationService.PlacementInput("FOCUS", contentId, 2, null, null)),
                "01JADMIN000000000000000000", "ADMIN", "request-1");

        verify(jdbc).update(startsWith("update homepage_placements set sort_order"), eq(2), isNull(), isNull(), eq("01JPLACE000000000000000001"));
        verify(jdbc, never()).update(startsWith("insert into homepage_placements"), any(), any(), any(), any(), any(), any(), any());
        verify(jdbc).update(startsWith("update homepage_placements set active = false"), eq("01JPLACE000000000000000001"));
        assertThat(result.placements()).hasSize(1);
        assertThat(result.placements().get(0).contentItemId()).isEqualTo(contentId);
    }

    @Test void rejects_duplicate_slot_and_content_in_the_same_request() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HomepageAdministrationService service = new HomepageAdministrationService(jdbc, mock(AuditService.class));
        String contentId = "01JCONTENT0000000000000001";
        when(jdbc.queryForObject(startsWith("select count(*) from content_items"), eq(Integer.class), eq(contentId))).thenReturn(1);

        assertThatThrownBy(() -> service.replace(List.of(
                new HomepageAdministrationService.PlacementInput("FOCUS", contentId, 0, null, null),
                new HomepageAdministrationService.PlacementInput("FOCUS", contentId, 1, null, null)
        ), "01JADMIN000000000000000000", "ADMIN", "request-1"))
                .hasFieldOrPropertyWithValue("code", "HOMEPAGE_PLACEMENT_DUPLICATE");
    }
}
