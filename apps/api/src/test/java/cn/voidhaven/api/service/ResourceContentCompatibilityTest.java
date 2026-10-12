package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ResourceContentCompatibilityTest {
    final JsonMapper json = JsonMapper.builder().build();
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final ContentItemRepository items = mock(ContentItemRepository.class);
    final ContentRevisionRepository revisions = mock(ContentRevisionRepository.class);
    final ContentModuleRegistry registry = new ContentModuleRegistry(json);
    final ResourceConfigService configs = spy(new ResourceConfigService(jdbc, json, mock(AuditService.class)));
    final ContentSourcePolicy sourcePolicy = new ContentSourcePolicy(false, "https://voidhaven.example");
    ResourceConfigService.Config config(long version, String label, boolean enabled) {
        return new ResourceConfigService.Config(version,
                List.of(new ResourceConfigService.Category("GENERAL", "通用分享", true)),
                List.of(new ResourceConfigService.Field("location", label, "text", "", List.of(), enabled)), List.of());
    }
    ContentManagementService service() {
        doReturn(config(1, "地点", true)).when(configs).current();
        registry.setResourceConfigs(configs);
        when(items.saveAndFlush(any(ContentItem.class))).thenAnswer(call -> call.getArgument(0));
        when(revisions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        return new ContentManagementService(items, revisions, mock(PartnerProfileRepository.class), mock(AuditService.class), json,
                sourcePolicy, new RichTextPolicy(), new ContentLanguagePolicy(), registry, new MarkdownDocumentService(json), jdbc);
    }
    ContentManagementService.Draft draft(String details, Long version) {
        return new ContentManagementService.Draft("RESOURCE", "隔离笔记", "用于兼容验证的合成正文", json.readTree("{\"version\":1,\"blocks\":[]}"),
                "zh-CN", null, null, null, "原创", "测试作者", "NOT_ALLOWED", "NOT_ALLOWED", json.readTree(details), "resource", 1, "# 正文\n\n合成内容", version);
    }
    @Test void original_unclassified_resource_can_be_published_and_snapshot_survives_configuration_change() {
        var service = service();
        var request = draft("{\"sourceMode\":\"ORIGINAL\",\"customFields\":{\"location\":\"合成教室\"}}", 1L);
        var created = service.create(request, null, "actor", "ADMIN", "request");
        var capture = org.mockito.ArgumentCaptor.forClass(ContentItem.class);
        verify(items, atLeastOnce()).saveAndFlush(capture.capture());
        ContentItem item = capture.getValue();
        ReflectionTestUtils.setField(item, "version", 0L);
        when(items.findWithCurrentRevisionById(created.id())).thenReturn(Optional.of(item));
        when(items.findByIdForUpdate(created.id())).thenReturn(Optional.of(item));
        service.submitAsAdmin(item.getId(), "actor", "ADMIN", "request");
        service.decide(item.getId(), "APPROVE", null, 0L, "actor", "ADMIN", "request");
        service.publish(item.getId(), "actor", "ADMIN", "request");
        assertThat(item.getPublishedRevision().getSourceUrl()).isNull();
        assertThat(json.readTree(item.getPublishedRevision().getMetadataJson()).path("resourceType").asText()).isEqualTo("GENERAL");
        verify(jdbc).update(startsWith("insert into resource_revision_details"), anyString(), eq("GENERAL"));

        doReturn(config(2, "已经改名", false)).when(configs).current();
        ContentService reader = new ContentService(items, json, sourcePolicy, mock(MediaReadService.class), new ContentLanguagePolicy(), registry, jdbc);
        var published = reader.publicCard(item);
        assertThat(published.resourcePresentation().path("fields").get(0).path("label").asText()).isEqualTo("地点");
        assertThat(published.resourcePresentation().has("templates")).isFalse();
        assertThat(published.details().path("customFields").path("location").asText()).isEqualTo("合成教室");
        assertThat(published.resourcePresentation().has("category")).isFalse();
    }
    @Test void external_resources_still_require_a_source_and_new_custom_fields_require_a_version() {
        var service = service();
        assertThatThrownBy(() -> service.create(draft("{}", null), null, "actor", "ADMIN", "request")).isInstanceOf(ApiException.class).hasMessageContaining("来源");
        assertThatThrownBy(() -> service.create(draft("{\"sourceMode\":\"ORIGINAL\",\"customFields\":{\"location\":\"教室\"}}", null), null, "actor", "ADMIN", "request")).isInstanceOf(ApiException.class).hasMessageContaining("配置");
        verify(items, never()).saveAndFlush(any());
    }
}
