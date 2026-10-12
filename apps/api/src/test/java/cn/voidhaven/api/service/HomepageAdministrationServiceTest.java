package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.ContentRevision;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.Visibility;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomepageAdministrationServiceTest {
    @Test void reuses_inactive_row_instead_of_inserting_a_conflicting_unique_key() {
        Fixture fixture = fixture();
        when(fixture.jdbc.queryForObject(contains("for update"), eq(Long.class), any())).thenReturn(3L);
        when(fixture.jdbc.queryForObject(eq("select version from homepage_curation where id = ?"), eq(Long.class), any()))
                .thenReturn(4L);
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            when(row.getString("id")).thenReturn("01JPLACE000000000000000001");
            when(row.getString("slot")).thenReturn("FOCUS");
            when(row.getString("content_item_id")).thenReturn(fixture.contentId);
            handler.processRow(row);
            return null;
        }).when(fixture.jdbc).query(eq("select id, slot, content_item_id from homepage_placements"), any(RowCallbackHandler.class));
        when(fixture.jdbc.query(contains("where active = true"), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of());

        HomepageAdministrationService.PlacementReplacement result = fixture.service.replace(
                List.of(new HomepageAdministrationService.PlacementInput("FOCUS", fixture.contentId, 2, null, null)),
                3, "01JADMIN000000000000000000", "ADMIN", "request-1");

        verify(fixture.jdbc).update(startsWith("update homepage_placements set sort_order"), eq(2), isNull(), isNull(), eq("01JPLACE000000000000000001"));
        verify(fixture.jdbc, never()).update(startsWith("insert into homepage_placements"), any(), any(), any(), any(), any(), any(), any());
        verify(fixture.jdbc).update(startsWith("update homepage_placements set active = false"), eq("01JPLACE000000000000000001"));
        assertThat(result.version()).isEqualTo(4L);
    }

    @Test void rejects_duplicate_slot_and_content_in_the_same_request() {
        Fixture fixture = fixture();
        when(fixture.jdbc.queryForObject(contains("for update"), eq(Long.class), any())).thenReturn(1L);

        assertThatThrownBy(() -> fixture.service.replace(List.of(
                new HomepageAdministrationService.PlacementInput("FOCUS", fixture.contentId, 0, null, null),
                new HomepageAdministrationService.PlacementInput("FOCUS", fixture.contentId, 1, null, null)
        ), 1, "01JADMIN000000000000000000", "ADMIN", "request-1"))
                .hasFieldOrPropertyWithValue("code", "HOMEPAGE_PLACEMENT_DUPLICATE");
    }

    @Test void rejects_stale_curation_version() {
        Fixture fixture = fixture();
        when(fixture.jdbc.queryForObject(contains("for update"), eq(Long.class), any())).thenReturn(8L);

        assertThatThrownBy(() -> fixture.service.replace(
                List.of(new HomepageAdministrationService.PlacementInput("FOCUS", fixture.contentId, 0, null, null)),
                7, "01JADMIN000000000000000000", "ADMIN", "request-1"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "HOMEPAGE_VERSION_CONFLICT");
    }

    @Test void rejects_a_typed_slot_with_the_wrong_kind() {
        Fixture fixture = fixture();
        when(fixture.jdbc.queryForObject(contains("for update"), eq(Long.class), any())).thenReturn(1L);

        assertThatThrownBy(() -> fixture.service.replace(
                List.of(new HomepageAdministrationService.PlacementInput("RESOURCE", fixture.contentId, 0, null, null)),
                1, "01JADMIN000000000000000000", "ADMIN", "request-1"))
                .hasFieldOrPropertyWithValue("code", "HOMEPAGE_CONTENT_INVALID");
    }

    private static Fixture fixture() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentService content = mock(ContentService.class);
        HomepageProjectionService projection = mock(HomepageProjectionService.class);
        when(projection.detailsOf(any())).thenReturn(JsonMapper.builder().build().nullNode());
        when(content.sourcedForPublic(any())).thenReturn(true);
        String contentId = "01JCONTENT0000000000000001";
        ContentItem item = new ContentItem(contentId, ContentKind.COMPETITION, WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE);
        ContentRevision revision = new ContentRevision(
                "01JREV000000000000000000001", item, 1, "测试竞赛", "摘要", "{}", "zh", null,
                "https://example.com/source", "来源", "授权", "作者", "NOT_ALLOWED", "NOT_ALLOWED", "{}", "01JADMIN000000000000000000");
        item.setCurrentRevision(revision);
        item.setPublishedRevision(revision);
        when(items.findGraphByIds(any())).thenReturn(List.of(item));
        HomepageAdministrationService service = new HomepageAdministrationService(jdbc, audit, items, content, projection);
        return new Fixture(jdbc, service, contentId);
    }

    private record Fixture(JdbcTemplate jdbc, HomepageAdministrationService service, String contentId) { }
}
