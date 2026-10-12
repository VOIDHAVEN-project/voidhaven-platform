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

class CatalogOrderServiceTest {
    @Test void rejects_stale_version_and_duplicate_content() {
        Fixture fixture = fixture();
        when(fixture.jdbc.queryForObject(contains("for update"), eq(Long.class), any())).thenReturn(4L);

        assertThatThrownBy(() -> fixture.service.replace(
                ContentKind.COMPETITION, "default",
                List.of(new CatalogOrderService.EntryInput(fixture.contentId, 0)),
                3, "01JADMIN000000000000000000", "ADMIN", "request-1"))
                .hasFieldOrPropertyWithValue("code", "CATALOG_VERSION_CONFLICT");

        when(fixture.jdbc.queryForObject(contains("for update"), eq(Long.class), any())).thenReturn(4L);
        assertThatThrownBy(() -> fixture.service.replace(
                ContentKind.COMPETITION, "default",
                List.of(
                        new CatalogOrderService.EntryInput(fixture.contentId, 0),
                        new CatalogOrderService.EntryInput(fixture.contentId, 1)),
                4, "01JADMIN000000000000000000", "ADMIN", "request-1"))
                .hasFieldOrPropertyWithValue("code", "CATALOG_ORDER_DUPLICATE");
    }

    @Test void rejects_deadline_default_for_resources() {
        Fixture fixture = fixture();
        when(fixture.jdbc.queryForObject(contains("for update"), eq(Long.class), any())).thenReturn(1L);
        assertThatThrownBy(() -> fixture.service.replace(
                ContentKind.RESOURCE, "deadline", List.of(),
                1, "01JADMIN000000000000000000", "ADMIN", "request-1"))
                .hasFieldOrPropertyWithValue("code", "CATALOG_SORT_UNSUPPORTED");
    }

    @Test void reuses_existing_row_instead_of_inserting() {
        Fixture fixture = fixture();
        when(fixture.jdbc.queryForObject(contains("for update"), eq(Long.class), any())).thenReturn(1L);
        when(fixture.jdbc.queryForObject(eq("select version from catalog_sort_settings where kind = ?"), eq(Long.class), any()))
                .thenReturn(2L);
        when(fixture.jdbc.queryForObject(eq("select default_sort from catalog_sort_settings where kind = ?"), eq(String.class), any()))
                .thenReturn("DEFAULT");
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            var row = mock(java.sql.ResultSet.class);
            when(row.getString("id")).thenReturn("01JCATPLACE000000000000001");
            when(row.getString("content_item_id")).thenReturn(fixture.contentId);
            handler.processRow(row);
            return null;
        }).when(fixture.jdbc).query(startsWith("select id, content_item_id from catalog_order_entries"), any(RowCallbackHandler.class), any());
        when(fixture.jdbc.query(contains("active = true order by sort_order"), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(List.of());

        CatalogOrderService.Replacement result = fixture.service.replace(
                ContentKind.COMPETITION, "updated",
                List.of(new CatalogOrderService.EntryInput(fixture.contentId, 0)),
                1, "01JADMIN000000000000000000", "ADMIN", "request-1");

        verify(fixture.jdbc).update(startsWith("update catalog_order_entries set sort_order"), eq(0), eq("01JCATPLACE000000000000001"));
        verify(fixture.jdbc, never()).update(startsWith("insert into catalog_order_entries"), any(), any(), any(), any(), any());
        assertThat(result.version()).isEqualTo(2L);
        assertThat(result.defaultSort()).isEqualTo("default");
    }

    private static Fixture fixture() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentService content = mock(ContentService.class);
        when(content.sourcedForPublic(any())).thenReturn(true);
        String contentId = "01JCONTENT0000000000000001";
        ContentItem item = new ContentItem(contentId, ContentKind.COMPETITION, WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE);
        ContentRevision revision = new ContentRevision(
                "01JREV000000000000000000001", item, 1, "测试竞赛", "摘要", "{}", "zh", null,
                "https://example.com/source", "来源", "授权", "作者", "NOT_ALLOWED", "NOT_ALLOWED", "{}", "01JADMIN000000000000000000");
        item.setCurrentRevision(revision);
        item.setPublishedRevision(revision);
        when(items.findGraphByIds(any())).thenReturn(List.of(item));
        CatalogOrderService service = new CatalogOrderService(jdbc, audit, items, content, JsonMapper.builder().build());
        return new Fixture(jdbc, service, contentId);
    }

    private record Fixture(JdbcTemplate jdbc, CatalogOrderService service, String contentId) { }
}
