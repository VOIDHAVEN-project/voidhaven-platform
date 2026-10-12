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
    public record PublicPage(List<PublicContentDto> items, String nextCursor, String sort) {
        public PublicPage(List<PublicContentDto> items, String nextCursor) {
            this(items, nextCursor, null);
        }
    }
    public List<PublicContentDto> listPublic(ContentKind kind, int size) { return listPublicPage(kind, size, null, null).items(); }
    public PublicPage listPublicPage(ContentKind kind, int size, String cursor, String query) {
        return listCatalog(kind, null, size, cursor, query, null, null, null, null);
    }
    public PublicPage listCatalog(ContentKind kind, String moduleKey, int size, String cursor, String query,
                                  String sort, String filterKey, String filterValue, String status) {
        int pageSize = Math.max(1, Math.min(size, 50));
        ContentModuleRegistry.Module module = moduleKey == null ? null : modules.requireActive(moduleKey);
        ContentKind resolvedKind = module == null ? kind : module.legacyKind();
        String normalizedFilterKey = blankToNull(filterKey);
        String normalizedFilterValue = blankToNull(filterValue);
        if ((normalizedFilterKey == null) != (normalizedFilterValue == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_FILTER_INCOMPLETE", "筛选字段和值必须同时提供。");
        }
        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null && !Set.of("OPEN", "PAUSED", "FILLED", "EXPIRED", "CANCELLED").contains(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_STATUS_INVALID", "目录状态筛选无效。");
        }
        CatalogSort applied = CatalogSort.resolve(resolvedKind, sort, configuredSort(resolvedKind));
        CatalogCursor decoded = CatalogCursor.decode(blankToNull(cursor), applied);
        ContentModuleRegistry.Field filterField = null;
        Object boundFilter = null;
        if (normalizedFilterKey != null) {
            if (normalizedFilterValue.length() > 512) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_FILTER_INVALID", "筛选值超出长度限制。");
            filterField = module == null ? modules.requireFilterable(resolvedKind, normalizedFilterKey) : modules.requireFilterable(module, normalizedFilterKey);
            try {
                boundFilter = "date".equals(filterField.type()) ? java.sql.Date.valueOf(normalizedFilterValue)
                        : "number".equals(filterField.type()) ? new BigDecimal(normalizedFilterValue)
                        : normalizedFilterValue;
            } catch (IllegalArgumentException error) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_FILTER_INVALID", "筛选值格式无效。");
            }
        }
        CatalogQuery.BoundSql sql = CatalogQuery.selectIds(new CatalogQuery.Spec(
                resolvedKind, module == null ? null : module.key(), blankToNull(query), applied, decoded,
                filterField == null ? null : filterField.key(), filterField == null ? null : filterField.type(),
                boundFilter, normalizedStatus, pageSize + 1));
        List<CatalogQuery.RankedId> ranked = jdbc.query(sql.sql(), (row, index) -> new CatalogQuery.RankedId(
                row.getString("id"), CatalogQuery.encodeKey(applied, row.getObject("sort_key"))), sql.args());
        boolean hasNext = ranked.size() > pageSize;
        List<CatalogQuery.RankedId> page = hasNext ? ranked.subList(0, pageSize) : ranked;
        List<String> ids = page.stream().map(CatalogQuery.RankedId::id).toList();
        List<PublicContentDto> visible = inOrder(ids).stream().filter(this::hasPublicSource).map(item -> toPublic(item, false)).toList();
        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            CatalogQuery.RankedId last = page.get(page.size() - 1);
            nextCursor = CatalogCursor.of(applied, last.key(), last.id()).encode();
        }
        return new PublicPage(visible, nextCursor, applied.wire());
    }
    public PublicContentDto publicDetail(ContentKind kind, String id) {
        return items.findPublicByIdAndKind(id, kind)
                .filter(this::hasPublicSource)
                .map(item -> toPublic(item, true))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。"));
    }
    public PublicPage listPublicModule(String moduleKey, int size, String cursor, String query, String filterKey, String filterValue) {
        return listCatalog(null, moduleKey, size, cursor, query, null, filterKey, filterValue, null);
    }
    public PublicPage listPublicModule(String moduleKey, int size, String cursor, String query, String sort, String filterKey, String filterValue, String status) {
        return listCatalog(null, moduleKey, size, cursor, query, sort, filterKey, filterValue, status);
    }
    public PublicContentDto publicDetail(String id) {
        return items.findPublicById(id).filter(this::hasPublicSource).map(item -> toPublic(item, true))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。"));
    }
    public PublicPage related(String id) {
        PublicContentDto source = publicDetail(id);
        LinkedHashSet<String> ids = new LinkedHashSet<>(jdbc.queryForList("select distinct related_id from (select rel.target_content_item_id related_id from content_items source join revision_content_relations rel on rel.revision_id = source.published_revision_id where source.id = ? union select source.id related_id from content_items source join revision_content_relations rel on rel.revision_id = source.published_revision_id where rel.target_content_item_id = ?) related", String.class, id, id));
        ids.addAll(RelatedContentIds.fromDetails(source.details()));
        ids.remove(id);
        if (ids.isEmpty()) return new PublicPage(List.of(), null);
        List<String> limited = ids.stream().limit(24).toList();
        List<PublicContentDto> values = inOrder(limited).stream().filter(this::hasPublicSource).map(item -> toPublic(item, false)).toList();
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

    public PublicContentDto publicCard(ContentItem item) { return toPublic(item, false); }
    boolean sourcedForPublic(ContentItem item) { return hasPublicSource(item); }
    public List<ContentItem> publicPool(ContentKind kind, int size) {
        return items.findPublicByKind(kind, PageRequest.of(0, Math.max(1, Math.min(size, 50))));
    }
    private JsonNode resourcePresentation(ContentRevision revision) {
        if (revision.getSchemaSnapshotJson() == null) return null;
        JsonNode snapshot = mapper.readTree(revision.getSchemaSnapshotJson()).path("resourceConfig");
        if (!snapshot.isObject()) return null;
        JsonNode details = mapper.readTree(revision.getMetadataJson() == null ? revision.getDetailsJson() : revision.getMetadataJson());
        var result = mapper.createObjectNode();
        String category = details.path("resourceType").asText();
        if (!"GENERAL".equals(category)) for (JsonNode c : snapshot.path("categories")) {
            if (category.equals(c.path("key").asText())) result.put("category", c.path("label").asText());
        }
        var fields = result.putArray("fields");
        for (JsonNode field : snapshot.path("fields")) if (details.path("customFields").has(field.path("key").asText())) fields.add(field);
        return result;
    }

    private PublicContentDto toPublic(ContentItem item, boolean includeMedia) {
        ContentRevision r = item.getPublishedRevision();
        try { return new PublicContentDto(item.getId(), item.getKind(), moduleKey(item), r.getSchemaVersion(), r.getTitle(), r.getSummary(), mapper.readTree(r.getBodyJson()), r.getLanguageCode(), languages.label(r.getLanguageCode()), r.getOriginalTitle(), r.getSourceName(), r.getSourceUrl(), r.getRightsBasis(), r.getAuthorDisplayName(), r.getReprintPermission(), r.getAdaptationPermission(), mapper.readTree(r.getMetadataJson() == null ? r.getDetailsJson() : r.getMetadataJson()), item.getVersion() == null ? 0 : item.getVersion(), includeMedia ? media.forPublishedRevision(r.getId()) : List.of(), item.getOperationalStatus(), includeMedia && actions != null ? actions.publicActions(item.getId()) : List.of(), includeMedia && verifications != null ? verifications.latestPublic(item.getId()) : null, item.getUpdatedAt(), resourcePresentation(r)); }
        catch (Exception e) { throw new IllegalStateException("Invalid persisted content document", e); }
    }
    boolean hasPublicSource(ContentItem item) {
        ContentRevision revision = item.getPublishedRevision();
        if (revision == null) return false;
        if (revision.getSourceUrl() != null && sources.isAllowed(revision.getSourceUrl())) return true;
        try {
            JsonNode metadata = mapper.readTree(revision.getMetadataJson() == null ? revision.getDetailsJson() : revision.getMetadataJson());
            return modules.requireKnown(moduleKey(item)).allowsOriginalSource() && "ORIGINAL".equals(metadata.path("sourceMode").asText());
        } catch (Exception error) { return false; }
    }
    private String moduleKey(ContentItem item) { return item.getModuleKey() == null ? modules.legacyModuleKey(item.getKind()) : item.getModuleKey(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private CatalogSort configuredSort(ContentKind kind) {
        try {
            String value = jdbc.queryForObject("select default_sort from catalog_sort_settings where kind = ?", String.class, kind.name());
            if (value == null || value.isBlank()) return CatalogSort.DEFAULT;
            return CatalogSort.valueOf(value);
        } catch (Exception error) {
            return CatalogSort.DEFAULT;
        }
    }
    private List<ContentItem> inOrder(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        Map<String, ContentItem> byId = new LinkedHashMap<>();
        for (ContentItem item : items.findPublicByIds(ids)) byId.put(item.getId(), item);
        List<ContentItem> ordered = new ArrayList<>();
        for (String id : ids) {
            ContentItem item = byId.get(id);
            if (item != null) ordered.add(item);
        }
        return ordered;
    }
}
