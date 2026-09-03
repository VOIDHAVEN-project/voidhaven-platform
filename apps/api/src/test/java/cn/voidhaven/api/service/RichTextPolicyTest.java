package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RichTextPolicyTest {
    private final RichTextPolicy policy = new RichTextPolicy();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void allows_the_versioned_paragraph_document() throws Exception {
        assertThatCode(() -> policy.requireAllowed(json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"text\":\"安全文本\"}]}"))).doesNotThrowAnyException();
    }

    @Test void rejects_html_or_event_attribute_shapes() throws Exception {
        assertThatThrownBy(() -> policy.requireAllowed(json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"html\",\"text\":\"<script>alert(1)</script>\"}]}")))
                .isInstanceOf(ApiException.class).hasMessageContaining("不接受 HTML");
        assertThatThrownBy(() -> policy.requireAllowed(json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"text\":\"x\",\"onClick\":\"x\"}]}")))
                .isInstanceOf(ApiException.class);
    }
}
