package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Set;

@Component
public class RichTextPolicy {
    private static final Set<String> ROOT_FIELDS = Set.of("version", "blocks");
    private static final Set<String> BLOCK_FIELDS = Set.of("type", "text");

    public void requireAllowed(JsonNode document) {
        if (document == null || !document.isObject() || document.size() > ROOT_FIELDS.size() || !document.has("version") || document.get("version").asInt(-1) != 1 || !document.has("blocks") || !document.get("blocks").isArray() || document.get("blocks").size() == 0 || document.get("blocks").size() > 100) reject();
        for (String rootName : document.propertyNames()) if (!ROOT_FIELDS.contains(rootName)) reject();
        for (JsonNode block : document.get("blocks")) {
            if (!block.isObject() || block.size() != BLOCK_FIELDS.size() || !"paragraph".equals(block.path("type").asText()) || !block.has("text") || !block.get("text").isTextual() || block.get("text").asText().length() > 5000) reject();
            for (String name : block.propertyNames()) if (!BLOCK_FIELDS.contains(name)) reject();
        }
    }

    private void reject() { throw new ApiException(HttpStatus.BAD_REQUEST, "RICH_TEXT_INVALID", "正文只支持安全的版本化段落文本，不接受 HTML、脚本或未声明的属性。"); }
}
