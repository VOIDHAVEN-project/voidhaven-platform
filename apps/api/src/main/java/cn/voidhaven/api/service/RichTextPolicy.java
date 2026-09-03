package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RichTextPolicy {
    private static final Set<String> ROOT_FIELDS = Set.of("version", "blocks");
    private static final Set<String> BLOCK_FIELDS = Set.of("type", "text");
    private static final Pattern MEDIA_ID = Pattern.compile("[A-Za-z0-9]{26}");

    public void requireAllowed(JsonNode document) {
        if (document == null || !document.isObject() || document.size() > ROOT_FIELDS.size() || !document.has("version") || !document.has("blocks") || !document.get("blocks").isArray() || document.get("blocks").size() == 0 || document.get("blocks").size() > 400) reject();
        for (String rootName : document.propertyNames()) if (!ROOT_FIELDS.contains(rootName)) reject();
        int version = document.get("version").asInt(-1);
        if (version == 2) { requireV2(document.get("blocks")); return; }
        if (version != 1) reject();
        for (JsonNode block : document.get("blocks")) {
            if (!block.isObject() || block.size() != BLOCK_FIELDS.size() || !"paragraph".equals(block.path("type").asText()) || !block.has("text") || !block.get("text").isTextual() || block.get("text").asText().length() > 5000) reject();
            for (String name : block.propertyNames()) if (!BLOCK_FIELDS.contains(name)) reject();
        }
    }

    private void requireV2(JsonNode blocks) {
        for (JsonNode block : blocks) {
            if (!block.isObject()) reject();
            switch (block.path("type").asText()) {
                case "paragraph", "heading", "quote" -> {
                    Set<String> allowed = "heading".equals(block.path("type").asText()) ? Set.of("type", "level", "inlines") : Set.of("type", "inlines");
                    if (!hasOnly(block, allowed) || ("heading".equals(block.path("type").asText()) && (block.path("level").asInt() < 1 || block.path("level").asInt() > 4))) reject();
                    requireInlines(block.get("inlines"));
                }
                case "list" -> {
                    if (!hasOnly(block, Set.of("type", "ordered", "items")) || !block.path("ordered").isBoolean() || !block.path("items").isArray() || block.path("items").size() == 0 || block.path("items").size() > 100) reject();
                    for (JsonNode item : block.path("items")) requireInlines(item);
                }
                case "code" -> {
                    if (!hasOnly(block, Set.of("type", "language", "text")) || !block.path("language").isTextual() || !block.path("text").isTextual() || block.path("language").asText().length() > 32 || block.path("text").asText().length() > 20_000) reject();
                }
                case "image" -> {
                    if (!hasOnly(block, Set.of("type", "mediaId", "alt")) || !block.path("mediaId").isTextual() || !MEDIA_ID.matcher(block.path("mediaId").asText()).matches() || !block.path("alt").isTextual() || block.path("alt").asText().isBlank() || block.path("alt").asText().length() > 240) reject();
                }
                case "rule" -> { if (!hasOnly(block, Set.of("type"))) reject(); }
                default -> reject();
            }
        }
    }

    private void requireInlines(JsonNode inlines) {
        if (inlines == null || !inlines.isArray() || inlines.size() == 0 || inlines.size() > 1000) reject();
        for (JsonNode inline : inlines) {
            if (!inline.isObject() || !inline.path("type").isTextual() || !inline.path("text").isTextual() || inline.path("text").asText().length() > 5000) reject();
            String type = inline.path("type").asText();
            if ("link".equals(type)) {
                if (!hasOnly(inline, Set.of("type", "text", "href")) || !inline.path("href").isTextual() || !safeHttps(inline.path("href").asText())) reject();
            } else if (!Set.of("text", "strong", "emphasis", "code").contains(type) || !hasOnly(inline, Set.of("type", "text"))) reject();
        }
    }

    private boolean safeHttps(String value) {
        try { URI uri = URI.create(value); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank() && uri.getUserInfo() == null; }
        catch (IllegalArgumentException error) { return false; }
    }

    private boolean hasOnly(JsonNode node, Set<String> allowed) {
        if (node.size() != allowed.size()) return false;
        for (String name : node.propertyNames()) if (!allowed.contains(name)) return false;
        return true;
    }

    private void reject() { throw new ApiException(HttpStatus.BAD_REQUEST, "RICH_TEXT_INVALID", "正文只支持安全的版本化段落文本，不接受 HTML、脚本或未声明的属性。"); }
}
