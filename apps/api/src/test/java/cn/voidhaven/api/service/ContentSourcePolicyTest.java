package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentSourcePolicyTest {
    @Test void accepts_a_real_https_source_in_every_profile() {
        ContentSourcePolicy policy = new ContentSourcePolicy(false, "https://voidhaven.example");
        assertThatCode(() -> policy.requireAllowed("https://contest.example.edu/rules")).doesNotThrowAnyException();
    }

    @Test void rejects_local_and_reserved_sources_outside_the_local_profile() {
        ContentSourcePolicy policy = new ContentSourcePolicy(false, "https://voidhaven.example");
        assertThatThrownBy(() -> policy.requireAllowed("http://localhost:8080/local-fixtures/source"))
                .isInstanceOf(ApiException.class).hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> policy.requireAllowed("https://example.invalid/rules"))
                .isInstanceOf(ApiException.class).hasMessageContaining("HTTPS");
        assertThat(policy.isAllowed("http://localhost:8080/local-fixtures/source")).isFalse();
        assertThat(policy.isAllowed("https://example.invalid/rules")).isFalse();
    }

    @Test void accepts_only_the_configured_local_fixture_source_when_explicitly_enabled() {
        ContentSourcePolicy policy = new ContentSourcePolicy(true, "http://localhost:18080");
        assertThatCode(() -> policy.requireAllowed("http://localhost:18080/local-fixtures/source")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireAllowed("http://localhost:8080/local-fixtures/source"))
                .isInstanceOf(ApiException.class);
        assertThat(policy.isAllowed("http://localhost:18080/local-fixtures/source")).isTrue();
    }
}
