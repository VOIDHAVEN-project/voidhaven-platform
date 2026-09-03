package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A deliberately small Markdown subset which produces a safe, renderer-owned document tree. */
@Component
public class MarkdownDocumentService {
    private static final int MAX_SOURCE_LENGTH = 256 * 1024;
    private static final Pattern HTML = Pattern.compile("(?is)<\\s*/?\\s*[a-z!][^>]*>");
    private static final Pattern HEADING = Pattern.compile("^(#{1,4})\\s+(.+)$");
    private static final Pattern IMAGE = Pattern.compile("^!\\[([^\\]]{1,240})]\\(media:([A-Za-z0-9]{26})\\)$");
    private static final Pattern ORDERED = Pattern.compile("^\\d+[.)]\\s+(.+)$");
    private static final Pattern UNORDERED = Pattern.compile("^[-*+]\\s+(.+)$");
    private static final Pattern INLINE = Pattern.compile("\\[([^\\]]{1,500})]\\(([^)\\s]{1,2048})\\)|\\*\\*([^*]{1,1000})\\*\\*|`([^`]{1,1000})`|\\*([^*]{1,1000})\\*");
    private final ObjectMapper mapper;

    public MarkdownDocumentService(ObjectMapper mapper) { this.mapper = mapper; }

    public ObjectNode parse(String source) {
        if (source == null || source.isBlank() || source.length() > MAX_SOURCE_LENGTH || HTML.matcher(source).find()) reject();
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\n", -1);
        ObjectNode document = mapper.createObjectNode();
        document.put("version", 2);
        ArrayNode blocks = document.putArray("blocks");
        for (int index = 0; index < lines.length;) {
            String line = lines[index];
            if (line.isBlank()) { index++; continue; }
            if (blocks.size() >= 400) reject();
            if (line.startsWith("```")) {
                String language = line.substring(3).trim();
                StringBuilder code = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < lines.length) {
                    if (lines[index].startsWith("```")) { closed = true; index++; break; }
                    if (code.length() + lines[index].length() + 1 > 20_000) reject();
                    if (!code.isEmpty()) code.append('\n');
                    code.append(lines[index++]);
                }
                if (!closed) reject();
                ObjectNode block = blocks.addObject(); block.put("type", "code"); block.put("language", language); block.put("text", code.toString());
                continue;
            }
            Matcher image = IMAGE.matcher(line.trim());
            if (image.matches()) {
                ObjectNode block = blocks.addObject(); block.put("type", "image"); block.put("mediaId", image.group(2)); block.put("alt", image.group(1)); index++; continue;
            }
            if (line.contains("![")) reject();
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                ObjectNode block = blocks.addObject(); block.put("type", "heading"); block.put("level", heading.group(1).length()); block.set("inlines", inlines(heading.group(2))); index++; continue;
            }
            if (line.trim().equals("---") || line.trim().equals("***")) { blocks.addObject().put("type", "rule"); index++; continue; }
            if (line.startsWith("> ")) {
                StringBuilder quote = new StringBuilder();
                while (index < lines.length && lines[index].startsWith("> ")) { if (!quote.isEmpty()) quote.append('\n'); quote.append(lines[index].substring(2)); index++; }
                ObjectNode block = blocks.addObject(); block.put("type", "quote"); block.set("inlines", inlines(quote.toString())); continue;
            }
            Matcher listStart = UNORDERED.matcher(line);
            Matcher orderedStart = ORDERED.matcher(line);
            if (listStart.matches() || orderedStart.matches()) {
                boolean ordered = orderedStart.matches();
                ObjectNode block = blocks.addObject(); block.put("type", "list"); block.put("ordered", ordered); ArrayNode items = block.putArray("items");
                while (index < lines.length) {
                    Matcher matcher = ordered ? ORDERED.matcher(lines[index]) : UNORDERED.matcher(lines[index]);
                    if (!matcher.matches()) break;
                    items.add(inlines(matcher.group(1))); index++;
                }
                continue;
            }
            StringBuilder paragraph = new StringBuilder(line);
            index++;
            while (index < lines.length && !lines[index].isBlank() && !isBlockStart(lines[index])) {
                if (lines[index].contains("![")) reject();
                paragraph.append('\n').append(lines[index++]);
            }
            ObjectNode block = blocks.addObject(); block.put("type", "paragraph"); block.set("inlines", inlines(paragraph.toString()));
        }
        if (blocks.isEmpty()) reject();
        return document;
    }

    public String legacyToMarkdown(JsonNode legacy) {
        if (legacy == null || !legacy.isObject()) return "";
        List<String> paragraphs = new ArrayList<>();
        for (JsonNode block : legacy.path("blocks")) {
            String text = block.path("text").asText("").trim();
            if (!text.isBlank()) paragraphs.add(text);
        }
        return String.join("\n\n", paragraphs);
    }

    public List<String> mediaIds(JsonNode document) {
        List<String> ids = new ArrayList<>();
        for (JsonNode block : document.path("blocks")) if ("image".equals(block.path("type").asText())) ids.add(block.path("mediaId").asText());
        return ids;
    }

    private boolean isBlockStart(String value) {
        return value.startsWith("```") || value.startsWith("> ") || HEADING.matcher(value).matches() || IMAGE.matcher(value.trim()).matches()
                || UNORDERED.matcher(value).matches() || ORDERED.matcher(value).matches() || value.trim().equals("---") || value.trim().equals("***");
    }

    private ArrayNode inlines(String input) {
        ArrayNode values = mapper.createArrayNode();
        Matcher matcher = INLINE.matcher(input);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) values.addObject().put("type", "text").put("text", input.substring(cursor, matcher.start()));
            ObjectNode node = values.addObject();
            if (matcher.group(1) != null) {
                if (!safeHttps(matcher.group(2))) reject();
                node.put("type", "link").put("text", matcher.group(1)).put("href", matcher.group(2));
            } else if (matcher.group(3) != null) node.put("type", "strong").put("text", matcher.group(3));
            else if (matcher.group(4) != null) node.put("type", "code").put("text", matcher.group(4));
            else node.put("type", "emphasis").put("text", matcher.group(5));
            cursor = matcher.end();
        }
        if (cursor < input.length()) values.addObject().put("type", "text").put("text", input.substring(cursor));
        if (values.isEmpty()) values.addObject().put("type", "text").put("text", " ");
        return values;
    }

    private boolean safeHttps(String value) {
        try { URI uri = URI.create(value); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank() && uri.getUserInfo() == null; }
        catch (IllegalArgumentException error) { return false; }
    }

    private void reject() { throw new ApiException(HttpStatus.BAD_REQUEST, "MARKDOWN_INVALID", "正文仅支持受控 Markdown；不接受 HTML、脚本、远程图片或危险链接。"); }
}
