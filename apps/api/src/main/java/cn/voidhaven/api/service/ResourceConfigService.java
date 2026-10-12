package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;

/** Presentation configuration only; cannot alter source, permission or publication policies. */
@Service
public class ResourceConfigService {
    public record Category(String key, String label, boolean enabled) { }
    public record Field(String key, String label, String type, String help, List<String> options, boolean enabled) { }
    public record Template(String key, String label, String markdown, List<String> fieldKeys, boolean enabled) { }
    public record Config(long version, List<Category> categories, List<Field> fields, List<Template> templates) { }
    private static final Set<String> TYPES = Set.of("text", "textarea", "date", "url", "number", "select", "tags");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    public ResourceConfigService(JdbcTemplate jdbc, ObjectMapper mapper, AuditService audit) {
        this.jdbc = jdbc; this.mapper = mapper; this.audit = audit;
    }

    public Config current() {
        return jdbc.queryForObject("select v.config_json from resource_config_versions v join resource_config_state s on s.current_version = v.version where s.id = 1",
                (row, index) -> mapper.readValue(row.getString(1), Config.class));
    }

    public List<Config> history(Long before) {
        if (before != null && before < 1) invalid("历史版本游标无效。");
        return jdbc.query("select config_json from resource_config_versions where version < ? order by version desc limit 20",
                (row, index) -> mapper.readValue(row.getString(1), Config.class), before == null ? Long.MAX_VALUE : before);
    }

    @Transactional
    public Config replace(Config input, long expected, String actor, String role, String requestId) {
        Long version = jdbc.queryForObject("select current_version from resource_config_state where id = 1 for update", Long.class);
        if (version == null || version != expected) conflict();
        Config previous = current();
        validateConfig(input, previous);
        Config next = new Config(version + 1, input.categories(), input.fields(), input.templates());
        jdbc.update("insert into resource_config_versions(version, config_json, actor_id, created_at) values (?, ?, ?, current_timestamp(6))",
                next.version(), mapper.writeValueAsString(next), actor);
        jdbc.update("update resource_config_state set current_version = ? where id = 1", next.version());
        audit.record(actor, role, "RESOURCE_CONFIG_UPDATED", "RESOURCE_CONFIG", "1", "{\"version\":" + version + "}",
                "{\"version\":" + next.version() + "}", null, requestId);
        return next;
    }

    public void validateConfig(Config next, Config previous) {
        if (next == null || next.categories() == null || next.fields() == null || next.templates() == null
                || next.version() < 1 || next.categories().size() > 100 || next.fields().size() > 100 || next.templates().size() > 50) invalid("配置数量或格式无效。");
        Set<String> categories = new HashSet<>(), fields = new HashSet<>(), templates = new HashSet<>();
        for (Category c : next.categories()) { if (c == null) invalid("分类无效。"); identity(c.key(), c.label(), categories); }
        if (next.categories().stream().noneMatch(c -> "GENERAL".equals(c.key()) && c.enabled())) invalid("通用分类必须保留并启用。");
        for (Field f : next.fields()) {
            if (f == null) invalid("字段无效。");
            identity(f.key(), f.label(), fields);
            if (!TYPES.contains(Objects.toString(f.type(), "")) || f.help() == null || f.help().length() > 500 || f.options() == null
                    || f.options().size() > 100 || f.options().stream().anyMatch(v -> v == null || v.isBlank() || v.length() > 40)
                    || new HashSet<>(f.options()).size() != f.options().size()) invalid("字段类型、说明或选项无效。");
            if ("select".equals(f.type()) && f.options().isEmpty()) invalid("单选字段至少需要一个选项。");
        }
        for (Template t : next.templates()) {
            if (t == null) invalid("模板无效。");
            identity(t.key(), t.label(), templates);
            if (t.markdown() == null || t.markdown().length() > 262144 || t.fieldKeys() == null || t.fieldKeys().size() > 30
                    || new HashSet<>(t.fieldKeys()).size() != t.fieldKeys().size() || !fields.containsAll(t.fieldKeys())) invalid("模板正文或推荐字段无效。");
        }
        if (previous == null) return;
        if (!categories.containsAll(previous.categories().stream().map(Category::key).toList())
                || !fields.containsAll(previous.fields().stream().map(Field::key).toList())
                || !templates.containsAll(previous.templates().stream().map(Template::key).toList())) invalid("已保存的分类、字段和模板只能停用，不能删除或改标识。");
        for (Field old : previous.fields()) {
            Field f = next.fields().stream().filter(v -> v.key().equals(old.key())).findFirst().orElseThrow();
            if (!f.type().equals(old.type()) || !f.options().containsAll(old.options())) invalid("已有字段不能改变类型或移除历史选项；请新建字段并停用旧字段。");
        }
    }

    private void identity(String key, String label, Set<String> seen) {
        if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || !seen.add(key)
                || label == null || label.isBlank() || label.length() > 80) invalid("标识应为唯一的英文字母、数字、下划线；名称不能为空或超过 80 字。");
    }

    /** Retain disabled definitions only for values already on this content's current revision. */
    public Config forContent(Config current, JsonNode oldSnapshot, JsonNode oldDetails) {
        JsonNode saved = oldSnapshot == null ? null : oldSnapshot.get("resourceConfig");
        if (saved == null || !saved.isObject()) return current;
        Config old = mapper.treeToValue(saved, Config.class);
        Map<String, Field> fields = new LinkedHashMap<>();
        current.fields().forEach(f -> fields.put(f.key(), f));
        old.fields().stream().filter(f -> oldDetails.path("customFields").has(f.key()))
                .filter(f -> !fields.containsKey(f.key()) || !fields.get(f.key()).enabled())
                .forEach(f -> fields.put(f.key(), new Field(f.key(), f.label(), f.type(), f.help(), f.options(), false)));
        return new Config(current.version(), current.categories(), List.copyOf(fields.values()), current.templates());
    }

    public void validateContent(Config config, Long version, JsonNode details, JsonNode oldDetails, ContentModuleRegistry registry) {
        if (version != null && version != config.version()) conflict();
        JsonNode custom = details.get("customFields");
        if (custom != null && (!custom.isObject() || custom.size() > 30)) invalid("每篇最多填写 30 个公共字段。");
        if (custom != null && !custom.isEmpty() && version == null) conflict();
        String category = details.path("resourceType").asText("GENERAL");
        if (config.categories().stream().noneMatch(c -> c.key().equals(category)
                && (c.enabled() || category.equals(oldDetails.path("resourceType").asText())))) invalid("分类不存在或已停用。");
        if (custom == null) return;
        Map<String, Field> known = new HashMap<>(); config.fields().forEach(f -> known.put(f.key(), f));
        for (String key : custom.propertyNames()) {
            Field f = known.get(key);
            if (f == null || (!f.enabled() && !oldDetails.path("customFields").has(key))) invalid("公共字段不存在或已停用：" + key);
            ContentModuleRegistry.Field field = new ContentModuleRegistry.Field(f.key(), f.label(), f.type(), false, false, f.options(), f.help());
            ObjectNode value = mapper.createObjectNode(); value.set(key, custom.get(key));
            registry.requireValidMetadata(new ContentModuleRegistry.Module("custom", "公共字段", cn.voidhaven.api.domain.ContentKind.RESOURCE,
                    true, false, false, "NONE", "", List.of(field), mapper.createObjectNode()), value);
            if ("url".equals(f.type()) && !custom.path(key).asText().isBlank()) {
                try { var uri = java.net.URI.create(custom.path(key).asText()); if (uri.getHost() == null || uri.getUserInfo() != null) invalid("请输入完整的 HTTPS 链接。"); }
                catch (IllegalArgumentException e) { invalid("请输入完整的 HTTPS 链接。"); }
            }
        }
    }

    public static void conflict() { throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_CONFIG_VERSION_CONFLICT", "资源配置已更新，请保留输入，刷新配置并核对后重试。"); }
    private static void invalid(String message) { throw new ApiException(HttpStatus.BAD_REQUEST, "RESOURCE_CONFIG_INVALID", message); }
}
