package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentLanguagePolicyTest {
    private final ContentLanguagePolicy policy = new ContentLanguagePolicy();

    @Test void maps_only_the_approved_language_values_to_public_chinese_labels() {
        assertThat(policy.label("zh-CN")).isEqualTo("中文");
        assertThat(policy.label("en")).isEqualTo("英文");
        assertThat(policy.label("zh-CN,en")).isEqualTo("中英双语");
        assertThat(policy.label("other:Japanese")).isEqualTo("其他语言（Japanese）");
        assertThat(policy.requiresOriginalTitle("en")).isTrue();
        assertThat(policy.requiresOriginalTitle("zh-CN")).isFalse();
    }

    @Test void rejects_unbounded_or_ambiguous_language_codes() {
        assertThatThrownBy(() -> policy.normalize("fr"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CONTENT_LANGUAGE");
    }
}
