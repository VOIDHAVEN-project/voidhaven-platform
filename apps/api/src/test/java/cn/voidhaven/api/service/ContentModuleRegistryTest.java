package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentModuleRegistryTest {
    private final ContentModuleRegistry registry = new ContentModuleRegistry(JsonMapper.builder().build());

    @Test void exposes_versioned_resource_article_without_creating_a_new_core_workflow() {
        var article = registry.requireActive("resource.article");
        assertThat(article.allowsOriginalSource()).isTrue();
        assertThat(article.legacyKind().name()).isEqualTo("RESOURCE");
        assertThat(article.fields()).extracting(ContentModuleRegistry.Field::key).contains("sourceMode", "resourceType");
        assertThat(registry.publicDefinitions().path("modules").valueStream().map(node -> node.path("key").asText()).toList())
                .containsExactly("competition", "recruitment", "resource", "resource.article");
    }

    @Test void exposes_only_declared_public_filters_and_validates_typed_metadata() throws Exception {
        var competition = registry.requireActive("competition");
        assertThat(registry.requireFilterable(competition, "deadlineDate").type()).isEqualTo("date");
        assertThatThrownBy(() -> registry.requireFilterable(competition, "officialRulesUrl")).isInstanceOf(ApiException.class);
        var invalid = JsonMapper.builder().build().readTree("{\"officialRulesUrl\":\"https://voidhaven.cn/rules\",\"deadlineDate\":\"not-a-date\"}");
        assertThatThrownBy(() -> registry.requireValidMetadata(competition, invalid)).isInstanceOf(ApiException.class);
    }
}
