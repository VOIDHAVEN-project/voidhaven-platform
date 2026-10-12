package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownDocumentServiceTest {
    private final MarkdownDocumentService markdown = new MarkdownDocumentService(JsonMapper.builder().build());

    @Test void builds_a_safe_document_with_inline_media() {
        var document = markdown.parse("# 标题\n\n正文含有 **重点** 与 [来源](https://voidhaven.example/a)。\n\n![流程图](media:01JCONTENT0000000000000000)");
        assertThat(document.path("version").asInt()).isEqualTo(2);
        assertThat(markdown.mediaIds(document)).containsExactly("01JCONTENT0000000000000000");
    }

    @Test void rejects_html_and_remote_images() {
        assertThatThrownBy(() -> markdown.parse("<script>alert(1)</script>")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> markdown.parse("![追踪图](https://tracker.example/image.png)")).isInstanceOf(ApiException.class);
    }
}
