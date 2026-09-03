package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.web.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The module file is deliberately versioned with the application.  It is a
 * presentation and validation contract, not an administrator-editable schema.
 */
@Component
public class ContentModuleRegistry {
    private static final Set<String> SUPPORTED_FIELD_TYPES = Set.of(
            "text", "textarea", "date", "datetime", "select", "url", "boolean", "relation", "number", "tags");
    private static final Pattern MODULE_KEY = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern FIELD_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    public record Field(String key, String label, String type, boolean required, boolean filterable, List<String> options, String help) { }
    public record Module(String key, String label, ContentKind legacyKind, boolean active, boolean allowsOriginalSource,
                         boolean requiresOfficialRules, String deadlinePolicy, String template, List<Field> fields, JsonNode snapshot) { }

    private final ObjectMapper mapper;
    private final int version;
    private final Map<String, Module> modules;

    public ContentModuleRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
        try (InputStream input = new ClassPathResource("content-modules.v1.json").getInputStream()) {
            JsonNode document = mapper.readTree(input);
            this.version = document.path("version").asInt(-1);
            if (version < 1 || !document.path("modules").isArray()) throw new IllegalStateException("Invalid content module registry");
            Map<String, Module> loaded = new LinkedHashMap<>();
            for (JsonNode value : document.path("modules")) {
                String key = value.path("key").asText();
                if (!MODULE_KEY.matcher(key).matches() || loaded.containsKey(key)) throw new IllegalStateException("Duplicate or invalid content module key");
                List<Field> fields = value.path("fields").valueStream().map(field -> new Field(
                        field.path("key").asText(), field.path("label").asText(), field.path("type").asText(),
                        field.path("required").asBoolean(false), field.path("filterable").asBoolean(false),
                        field.path("options").valueStream().map(JsonNode::asText).toList(), field.path("help").asText("")
                )).toList();
                if (fields.stream().anyMatch(field -> !FIELD_KEY.matcher(field.key()).matches() || !SUPPORTED_FIELD_TYPES.contains(field.type()))
                        || fields.stream().map(Field::key).distinct().count() != fields.size()) throw new IllegalStateException("Invalid or duplicate content module field");
                loaded.put(key, new Module(key, value.path("label").asText(), ContentKind.valueOf(value.path("legacyKind").asText()),
                        value.path("active").asBoolean(false), value.path("allowsOriginalSource").asBoolean(false),
                        value.path("requiresOfficialRules").asBoolean(false), value.path("deadlinePolicy").asText("NONE"),
                        value.path("template").asText(""), fields, value.deepCopy()));
            }
            this.modules = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load content module registry", error);
        }
    }

    public int version() { return version; }

    public Module requireActive(String key) {
        Module module = modules.get(key);
        if (module == null || !module.active()) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_MODULE_UNAVAILABLE", "该内容模块不存在或当前已停用。");
        return module;
    }

    public Module requireKnown(String key) {
        Module module = modules.get(key);
        if (module == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_MODULE_UNAVAILABLE", "该内容模块不存在或当前已停用。");
        return module;
    }

    public String legacyModuleKey(ContentKind kind) {
        return switch (kind) {
            case COMPETITION -> "competition";
            case RESOURCE -> "resource";
            case RECRUITMENT -> "recruitment";
        };
    }

    public JsonNode publicDefinitions() {
        ObjectNode result = mapper.createObjectNode();
        result.put("version", version);
        ArrayNode values = result.putArray("modules");
        modules.values().stream().filter(Module::active).forEach(module -> values.add(module.snapshot().deepCopy()));
        return result;
    }

    public JsonNode snapshot(Module module) { return module.snapshot().deepCopy(); }

    public Field requireFilterable(Module module, String key) {
        return module.fields().stream().filter(field -> field.key().equals(key) && field.filterable()).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_FILTER_UNAVAILABLE", "该筛选条件不存在或不可公开筛选。"));
    }

    public void requireValidMetadata(Module module, JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) reject("CONTENT_METADATA_INVALID", "内容模块字段格式无效。");
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Field field : module.fields()) fields.put(field.key(), field);
        for (String key : metadata.propertyNames()) if (!fields.containsKey(key)) reject("CONTENT_METADATA_INVALID", "内容包含当前模块未声明的字段：" + key + "。");
        for (Field field : module.fields()) {
            JsonNode value = metadata.get(field.key());
            if (field.required() && (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank()) || (value.isArray() && value.isEmpty()))) {
                reject("CONTENT_METADATA_REQUIRED", "请填写“" + field.label() + "”。");
            }
            if (value == null || value.isNull()) continue;
            if (Set.of("text", "textarea", "url", "date", "datetime", "relation", "select").contains(field.type()) && !value.isTextual()) {
                reject("CONTENT_METADATA_INVALID", "“" + field.label() + "”格式无效。");
            }
            if (value.isTextual() && value.asText().length() > 2048) reject("CONTENT_METADATA_INVALID", "“" + field.label() + "”超出长度限制。");
            if (field.filterable() && value.toString().length() > 512) reject("CONTENT_METADATA_INVALID", "“" + field.label() + "”超出可筛选字段长度限制。");
            if (field.type().equals("select") && !field.options().isEmpty() && value.isTextual() && !field.options().contains(value.asText())) {
                reject("CONTENT_METADATA_INVALID", "“" + field.label() + "”不是允许的选项。");
            }
            try {
                if (field.type().equals("date") && !value.asText().isBlank()) LocalDate.parse(value.asText());
                if (field.type().equals("datetime") && !value.asText().isBlank()) Instant.parse(value.asText());
                if (field.type().equals("url") && !value.asText().isBlank() && !"https".equalsIgnoreCase(URI.create(value.asText()).getScheme())) throw new IllegalArgumentException();
            } catch (Exception error) { reject("CONTENT_METADATA_INVALID", "“" + field.label() + "”格式无效。"); }
            if (field.type().equals("boolean") && !value.isBoolean()) reject("CONTENT_METADATA_INVALID", "“" + field.label() + "”必须为布尔值。");
            if (field.type().equals("number") && !value.isNumber()) reject("CONTENT_METADATA_INVALID", "“" + field.label() + "”必须为数字。");
            if (field.type().equals("tags")) {
                if (!value.isArray() || value.size() > 20 || value.valueStream().anyMatch(tag -> !tag.isTextual() || tag.asText().isBlank() || tag.asText().length() > 40)) {
                    reject("CONTENT_METADATA_INVALID", "“" + field.label() + "”必须为最多 20 个有效标签。");
                }
            }
        }
    }

    private void reject(String code, String message) { throw new ApiException(HttpStatus.BAD_REQUEST, code, message); }
}
