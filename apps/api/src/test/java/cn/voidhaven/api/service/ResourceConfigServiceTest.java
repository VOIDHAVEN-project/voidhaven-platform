package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ResourceConfigServiceTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final ResourceConfigService service = new ResourceConfigService(jdbc, mapper, mock(AuditService.class));
    final ContentModuleRegistry registry = new ContentModuleRegistry(mapper);
    ResourceConfigService.Config config(boolean enabled) {
        return new ResourceConfigService.Config(2,
                List.of(new ResourceConfigService.Category("GENERAL", "通用分享", true), new ResourceConfigService.Category("EVENT", "活动", enabled)),
                List.of(new ResourceConfigService.Field("date", "活动日期", "date", "", List.of(), enabled),
                        new ResourceConfigService.Field("link", "参考链接", "url", "", List.of(), true)), List.of());
    }
    @Test void accepts_optional_metadata_but_rejects_unversioned_or_unknown_custom_fields() {
        service.validateContent(config(true), null, mapper.readTree("{}"), mapper.readTree("{}"), registry);
        var values = mapper.readTree("{\"customFields\":{\"date\":\"2030-10-01\"}}");
        service.validateContent(config(true), 2L, values, mapper.readTree("{}"), registry);
        assertThatThrownBy(() -> service.validateContent(config(true), null, values, mapper.readTree("{}"), registry)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.validateContent(config(true), 1L, values, mapper.readTree("{}"), registry)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.validateContent(config(true), 2L, mapper.readTree("{\"customFields\":{\"sourceMode\":\"ORIGINAL\"}}"), mapper.readTree("{}"), registry)).isInstanceOf(ApiException.class);
    }
    @Test void enforces_disabled_fields_and_categories_without_breaking_existing_content() {
        var values = mapper.readTree("{\"resourceType\":\"EVENT\",\"customFields\":{\"date\":\"2030-10-01\"}}");
        assertThatThrownBy(() -> service.validateContent(config(false), 2L, values, mapper.readTree("{}"), registry)).isInstanceOf(ApiException.class);
        service.validateContent(config(false), 2L, values, values, registry);
        var snapshot = mapper.createObjectNode(); snapshot.set("resourceConfig", mapper.valueToTree(config(true)));
        var retained = service.forContent(config(false), snapshot, values);
        assertThat(retained.fields().get(0).label()).isEqualTo("活动日期");
        assertThat(retained.fields().get(0).enabled()).isFalse();
        service.validateContent(retained, 2L, values, values, registry);
    }
    @Test void rejects_invalid_values_and_dangerous_links() {
        for (String input : List.of("{\"date\":\"yesterday\"}", "{\"date\":12}", "{\"link\":\"javascript:alert(1)\"}", "{\"link\":\"https:relative\"}")) {
            var details = mapper.createObjectNode(); details.set("customFields", mapper.readTree(input));
            assertThatThrownBy(() -> service.validateContent(config(true), 2L, details, mapper.createObjectNode(), registry)).isInstanceOf(ApiException.class);
        }
    }
    @Test void rejects_deletion_type_changes_and_disabling_general() {
        var current = config(true);
        service.validateConfig(current, current);
        assertThatThrownBy(() -> service.validateConfig(new ResourceConfigService.Config(2, current.categories(), List.of(), List.of()), current)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.validateConfig(new ResourceConfigService.Config(2, List.of(), current.fields(), List.of()), current)).isInstanceOf(ApiException.class);
        var changed = new ResourceConfigService.Config(2, current.categories(), List.of(new ResourceConfigService.Field("date", "日期", "number", "", List.of(), true), current.fields().get(1)), List.of());
        assertThatThrownBy(() -> service.validateConfig(changed, current)).isInstanceOf(ApiException.class);
    }
    @Test void refuses_stale_writes_before_mutating_database() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);
        assertThatThrownBy(() -> service.replace(config(true), 2, "actor", "ADMIN", "request")).isInstanceOf(ApiException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void private_definitions_contain_templates_but_public_definitions_do_not() {
        var configs = mock(ResourceConfigService.class);
        when(configs.current()).thenReturn(config(true)); registry.setResourceConfigs(configs);
        var editor = registry.definitions(true).path("modules").get(2);
        assertThat(editor.has("resourceConfig")).isTrue();
        assertThat(registry.publicDefinitions().path("modules").get(2).has("resourceConfig")).isFalse();
        assertThat(registry.requireActive("resource").allowsOriginalSource()).isTrue();
        assertThat(registry.requireFilterable(cn.voidhaven.api.domain.ContentKind.RESOURCE, "resourceType").options()).contains("EVENT");
    }
}
