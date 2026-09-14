package cn.voidhaven.api.service;
import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.web.*;
import tools.jackson.databind.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ContentService {
    private final ContentItemRepository items; private final ObjectMapper mapper; private final ContentSourcePolicy sources; private final MediaReadService media; private final ContentLanguagePolicy languages; private final ContentModuleRegistry modules; private final JdbcTemplate jdbc;
    private ContentActionService actions;
    private ContentVerificationService verifications;
    public ContentService(ContentItemRepository items, ObjectMapper mapper, ContentSourcePolicy sources, MediaReadService media, ContentLanguagePolicy languages, ContentModuleRegistry modules, JdbcTemplate jdbc) { this.items = items; this.mapper = mapper; this.sources = sources; this.media = media; this.languages = languages; this.modules = modules; this.jdbc = jdbc; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setActions(ContentActionService actions) { this.actions = actions; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setVerifications(ContentVerificationService verifications) { this.verifications = verifications; }
    public record PublicPage(List<PublicContentDto> items, String nextCursor) { }
    public List<PublicContentDto> listPublic(ContentKind kind, int size) { return listPublicPage(kind, size, null, null).items(); }
    public PublicPage listPublicPage(ContentKind kind, int size, String cursor, String query) {
        int pageSize = Math.max(1, Math.min(size, 50));
        String normalizedCursor = blankToNull(cursor);
        String normalizedQuery = blankToNull(query);
        List<ContentItem> rows = items.findPublicPageByKind(kind, normalizedCursor, normalizedQuery, PageRequest.of(0, pageSize + 1));
        boolean hasNext = rows.size() > pageSize;
        List<ContentItem> rawPage = hasNext ? rows.subList(0, pageSize) : rows;
        List<ContentItem> visible = rawPage.stream()
                .filter(this::hasPublicSource)
                .toList();
        String nextCursor = hasNext ? rawPage.get(rawPage.size() - 1).getId() : null;
        return new PublicPage(visible.stream().map(item -> toPublic(item, false)).toList(), nextCursor);
    }
    public PublicContentDto publicDetail(ContentKind kind, String id) {
        return items.findPublicByIdAndKind(id, kind)
                .filter(this::hasPublicSource)
                .map(item -> toPublic(item, true))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。"));
    }
    public PublicPage listPublicModule(String moduleKey, int size, String cursor, String query, String filterKey, String filterValue) {
        ContentModuleRegistry.Module module = modules.requireActive(moduleKey);
        int pageSize = Math.max(1, Math.min(size, 50));
        String normalizedFilterKey = blankToNull(filterKey); String normalizedFilterValue = blankToNull(filterValue);
        if ((normalizedFilterKey == null) != (normalizedFilterValue == null)) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_FILTER_INCOMPLETE", "筛选字段和值必须同时提供。");
        List<ContentItem> rows = normalizedFilterKey == null
                ? items.findPublicPageByModuleKey(moduleKey, blankToNull(cursor), blankToNull(query), PageRequest.of(0, pageSize + 1))
                : filteredRows(module, moduleKey, blankToNull(cursor), blankToNull(query), normalizedFilterKey, normalizedFilterValue, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<ContentItem> rawPage = hasNext ? rows.subList(0, pageSize) : rows;
        List<PublicContentDto> visible = rawPage.stream().filter(this::hasPublicSource).map(item -> toPublic(item, false)).toList();
        return new PublicPage(visible, hasNext ? rawPage.get(rawPage.size() - 1).getId() : null);
    }
    private List<ContentItem> filteredRows(ContentModuleRegistry.Module module, String moduleKey, String cursor, String query,
                                           String filterKey, String filterValue, int limit) {
        if (filterValue.length() > 512) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_FILTER_INVALID", "筛选值超出长度限制。");
        ContentModuleRegistry.Field field = modules.requireFilterable(module, filterKey);
        String base = "select c.id from content_items c join content_revisions r on r.id = c.published_revision_id join revision_filter_values f on f.revision_id = r.id where c.module_key = ? and c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE' and (? is null or c.id < ?) and (? is null or lower(r.title) like lower(concat('%', ?, '%')) or lower(r.summary) like lower(concat('%', ?, '%')) or lower(r.source_name) like lower(concat('%', ?, '%'))) and f.field_key = ? and ";
        List<String> ids;
        try {
            if ("date".equals(field.type())) ids = jdbc.queryForList(base + "f.value_date = ? order by c.id desc limit ?", String.class,
                    moduleKey, cursor, cursor, query, query, query, query, filterKey, java.sql.Date.valueOf(filterValue), limit);
            else if ("number".equals(field.type())) ids = jdbc.queryForList(base + "f.value_number = ? order by c.id desc limit ?", String.class,
                    moduleKey, cursor, cursor, query, query, query, query, filterKey, new BigDecimal(filterValue), limit);
            else ids = jdbc.queryForList(base + "f.value_text = ? order by c.id desc limit ?", String.class,
                        moduleKey, cursor, cursor, query, query, query, query, filterKey, filterValue, limit);
        } catch (IllegalArgumentException error) { throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_FILTER_INVALID", "筛选值格式无效。"); }
        return ids.isEmpty() ? List.of() : items.findPublicByIds(ids);
    }
    public PublicContentDto publicDetail(String id) {
        return items.findPublicById(id).filter(this::hasPublicSource).map(item -> toPublic(item, true))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。"));
    }
    public PublicPage related(String id) {
        publicDetail(id);
        List<String> ids = jdbc.queryForList("select distinct related_id from (select rel.target_content_item_id related_id from content_items source join revision_content_relations rel on rel.revision_id = source.published_revision_id where source.id = ? union select source.id related_id from content_items source join revision_content_relations rel on rel.revision_id = source.published_revision_id where rel.target_content_item_id = ?) related", String.class, id, id);
        if (ids.isEmpty()) return new PublicPage(List.of(), null);
        List<PublicContentDto> values = items.findPublicByIds(ids).stream().filter(this::hasPublicSource).map(item -> toPublic(item, false)).toList();
        return new PublicPage(values, null);
    }
    public record SitemapEntry(String id, ContentKind kind) { }

    /**
     * All items a search engine should be allowed to reach: same visibility rule as the
     * public detail endpoint (hasPublicSource), so nothing listed here 404s when crawled.
     */
    public List<SitemapEntry> publicSitemapEntries() {
        List<SitemapEntry> result = new ArrayList<>();
        for (ContentKind kind : ContentKind.values()) {
            for (ContentItem item : items.findPublicByKind(kind, PageRequest.of(0, 2000))) {
                if (hasPublicSource(item)) result.add(new SitemapEntry(item.getId(), kind));
            }
        }
        return result;
    }

    public Map<String, Object> home() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("competitions", homepageSlot("FOCUS", ContentKind.COMPETITION));
        result.put("resources", homepageSlot("RESOURCE", ContentKind.RESOURCE));
        result.put("recruitments", homepageSlot("RECRUITMENT", ContentKind.RECRUITMENT));
        return result;
    }
    private List<PublicContentDto> homepageSlot(String slot, ContentKind fallbackKind) {
        List<String> ids = jdbc.queryForList("select hp.content_item_id from homepage_placements hp join content_items c on c.id = hp.content_item_id where hp.slot = ? and hp.active = true and (hp.starts_at is null or hp.starts_at <= current_timestamp(6)) and (hp.ends_at is null or hp.ends_at > current_timestamp(6)) and c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE' and c.published_revision_id is not null order by hp.sort_order asc, hp.id asc limit 4", String.class, slot);
        if (ids.isEmpty()) return listPublic(fallbackKind, 4);
        Map<String, ContentItem> byId = new LinkedHashMap<>();
        for (ContentItem item : items.findPublicByIds(ids)) byId.put(item.getId(), item);
        return ids.stream().map(byId::get).filter(Objects::nonNull).filter(this::hasPublicSource).map(item -> toPublic(item, false)).toList();
    }
    private PublicContentDto toPublic(ContentItem item, boolean includeMedia) {
        ContentRevision r = item.getPublishedRevision();
        try { return new PublicContentDto(item.getId(), item.getKind(), moduleKey(item), r.getSchemaVersion(), r.getTitle(), r.getSummary(), mapper.readTree(r.getBodyJson()), r.getLanguageCode(), languages.label(r.getLanguageCode()), r.getOriginalTitle(), r.getSourceName(), r.getSourceUrl(), r.getRightsBasis(), r.getAuthorDisplayName(), r.getReprintPermission(), r.getAdaptationPermission(), mapper.readTree(r.getMetadataJson() == null ? r.getDetailsJson() : r.getMetadataJson()), item.getVersion() == null ? 0 : item.getVersion(), includeMedia ? media.forPublishedRevision(r.getId()) : List.of(), item.getOperationalStatus(), includeMedia && actions != null ? actions.publicActions(item.getId()) : List.of(), includeMedia && verifications != null ? verifications.latestPublic(item.getId()) : null); }
        catch (Exception e) { throw new IllegalStateException("Invalid persisted content document", e); }
    }
    private boolean hasPublicSource(ContentItem item) {
        ContentRevision revision = item.getPublishedRevision();
        if (revision.getSourceUrl() != null && sources.isAllowed(revision.getSourceUrl())) return true;
        try {
            JsonNode metadata = mapper.readTree(revision.getMetadataJson() == null ? revision.getDetailsJson() : revision.getMetadataJson());
            return modules.requireKnown(moduleKey(item)).allowsOriginalSource() && "ORIGINAL".equals(metadata.path("sourceMode").asText());
        } catch (Exception error) { return false; }
    }
    private String moduleKey(ContentItem item) { return item.getModuleKey() == null ? modules.legacyModuleKey(item.getKind()) : item.getModuleKey(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
