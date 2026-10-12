package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CatalogOrderService {
    public static final int MAX_ENTRIES = 100;

    public record EntryInput(String contentItemId, int sortOrder) { }
    public record EntryItem(
            String id,
            String contentItemId,
            int sortOrder,
            String title,
            ContentKind kind,
            String operationalStatus,
            java.time.Instant updatedAt,
            String deadline,
            boolean publicEligible,
            String issue) { }
    public record Candidate(
            String id,
            String title,
            ContentKind kind,
            String operationalStatus,
            java.time.Instant updatedAt,
            String deadline,
            boolean publicEligible,
            String issue) { }
    public record Replacement(long version, String kind, String defaultSort, List<EntryItem> entries) { }
    public record CandidatePage(List<Candidate> items, String nextCursor) { }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ContentItemRepository items;
    private final ContentService content;
    private final ObjectMapper mapper;

    public CatalogOrderService(
            JdbcTemplate jdbc,
            AuditService audit,
            ContentItemRepository items,
            ContentService content,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.items = items;
        this.content = content;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Replacement current(ContentKind kind) {
        return new Replacement(settingsVersion(kind), kind.name(), configuredSort(kind).wire(), describe(kind, listRows(kind)));
    }

    @Transactional
    public Replacement replace(ContentKind kind, String defaultSort, List<EntryInput> requested, long expectedVersion, String actorId, String role, String requestId) {
        long currentVersion = lockVersion(kind);
        if (currentVersion != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "CATALOG_VERSION_CONFLICT", "目录排序已被其他运营人员更新，请刷新后重试。");
        }
        CatalogSort sort = CatalogSort.parse(defaultSort == null || defaultSort.isBlank() ? "default" : defaultSort);
        if (sort == null) sort = CatalogSort.DEFAULT;
        if (sort == CatalogSort.DEADLINE && kind == ContentKind.RESOURCE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_SORT_UNSUPPORTED", "学习资源不支持按截止日期排序。");
        }
        List<EntryInput> entries = requested == null ? List.of() : requested;
        if (entries.size() > MAX_ENTRIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_ORDER_TOO_MANY", "每个目录最多支持 " + MAX_ENTRIES + " 条人工顺序。");
        }
        Map<String, ContentItem> byId = loadItems(entries.stream().map(EntryInput::contentItemId).toList());
        Set<String> identities = new HashSet<>();
        for (EntryInput input : entries) {
            validate(kind, input, byId.get(input.contentItemId()));
            if (!identities.add(input.contentItemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_ORDER_DUPLICATE", "同一目录不能重复编排同一内容。");
            }
        }
        Map<String, String> existing = existingByContent(kind);
        Set<String> retained = new HashSet<>();
        for (EntryInput input : entries) {
            String previous = existing.get(input.contentItemId());
            if (previous == null) {
                String id = Ids.ulid();
                jdbc.update("insert into catalog_order_entries (id, kind, content_item_id, sort_order, created_by_account_id) values (?, ?, ?, ?, ?)",
                        id, kind.name(), input.contentItemId(), input.sortOrder(), actorId);
                retained.add(id);
            } else {
                jdbc.update("update catalog_order_entries set sort_order = ?, active = true, version = version + 1 where id = ?",
                        input.sortOrder(), previous);
                retained.add(previous);
            }
        }
        deactivateUnused(kind, retained);
        jdbc.update("update catalog_sort_settings set default_sort = ?, version = version + 1, updated_by_account_id = ? where kind = ?",
                sort.name(), actorId, kind.name());
        audit.record(actorId, role, "CATALOG_ORDER_REPLACED", "CATALOG", auditObjectId(kind), "{}",
                "{\"kind\":\"" + kind.name() + "\",\"sort\":\"" + sort.wire() + "\",\"count\":" + entries.size() + "}", null, requestId);
        return current(kind);
    }

    @Transactional(readOnly = true)
    public CandidatePage candidates(ContentKind kind, String query, String cursor) {
        List<ContentItem> rows = items.findPublicSearch(kind, blank(cursor), blank(query), org.springframework.data.domain.PageRequest.of(0, 21));
        List<Candidate> matches = new ArrayList<>();
        for (ContentItem item : rows) {
            matches.add(toCandidate(item));
            if (matches.size() == 20) break;
        }
        String next = rows.size() > 20 && !matches.isEmpty() ? matches.get(matches.size() - 1).id() : null;
        return new CandidatePage(matches, next);
    }

    public CatalogSort configuredSort(ContentKind kind) {
        String value = jdbc.queryForObject("select default_sort from catalog_sort_settings where kind = ?", String.class, kind.name());
        if (value == null || value.isBlank()) return CatalogSort.DEFAULT;
        try {
            return CatalogSort.valueOf(value);
        } catch (Exception error) {
            return CatalogSort.DEFAULT;
        }
    }

    private List<OrderRow> listRows(ContentKind kind) {
        return jdbc.query("select id, content_item_id, sort_order from catalog_order_entries where kind = ? and active = true order by sort_order, content_item_id",
                (row, index) -> new OrderRow(row.getString("id"), row.getString("content_item_id"), row.getInt("sort_order")),
                kind.name());
    }

    private List<EntryItem> describe(ContentKind kind, List<OrderRow> rows) {
        Map<String, ContentItem> byId = loadItems(rows.stream().map(OrderRow::contentItemId).toList());
        List<EntryItem> described = new ArrayList<>();
        for (OrderRow row : rows) {
            Inspection inspection = inspect(kind, byId.get(row.contentItemId()));
            described.add(new EntryItem(
                    row.id(),
                    row.contentItemId(),
                    row.sortOrder(),
                    inspection.title(),
                    inspection.kind(),
                    inspection.operationalStatus(),
                    inspection.updatedAt(),
                    inspection.deadline(),
                    inspection.publicEligible(),
                    inspection.issue()));
        }
        return described;
    }

    private void validate(ContentKind kind, EntryInput input, ContentItem item) {
        if (input == null || input.contentItemId() == null || input.contentItemId().length() != 26) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_CONTENT_INVALID", "目录内容 ID 无效。");
        }
        Inspection inspection = inspect(kind, item);
        if (!inspection.publicEligible()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_CONTENT_INVALID",
                    inspection.issue() == null ? "目录只能编排仍可公开的内容。" : inspection.issue());
        }
    }

    private Inspection inspect(ContentKind kind, ContentItem item) {
        if (item == null) {
            return new Inspection(null, null, null, null, null, false, "内容不存在或已删除。");
        }
        boolean published = item.getPublishedRevision() != null;
        boolean sourced = published && content.sourcedForPublic(item);
        boolean kindMatches = item.getKind() == kind;
        boolean listable = kindMatches
                && published
                && sourced
                && item.getVisibility() == cn.voidhaven.api.domain.Visibility.PUBLIC
                && item.getRecordState() == cn.voidhaven.api.domain.RecordState.ACTIVE;
        String issue = !kindMatches ? "内容类型与当前目录不一致。"
                : !published ? "内容尚未发布。"
                : !sourced ? "内容缺少可公开的来源。"
                : item.getVisibility() != cn.voidhaven.api.domain.Visibility.PUBLIC ? "内容当前未公开。"
                : item.getRecordState() != cn.voidhaven.api.domain.RecordState.ACTIVE ? "内容已归档或删除。"
                : null;
        String title = published ? item.getPublishedRevision().getTitle()
                : item.getCurrentRevision() == null ? item.getId() : item.getCurrentRevision().getTitle();
        return new Inspection(title, item.getKind(), item.getOperationalStatus(), item.getUpdatedAt(), deadline(item), listable, issue);
    }

    private Candidate toCandidate(ContentItem item) {
        Inspection inspection = inspect(item.getKind(), item);
        return new Candidate(
                item.getId(),
                inspection.title(),
                inspection.kind(),
                inspection.operationalStatus(),
                inspection.updatedAt(),
                inspection.deadline(),
                inspection.publicEligible(),
                inspection.issue());
    }

    private String deadline(ContentItem item) {
        if (item.getPublishedRevision() == null) return null;
        try {
            String raw = item.getPublishedRevision().getMetadataJson() == null
                    ? item.getPublishedRevision().getDetailsJson()
                    : item.getPublishedRevision().getMetadataJson();
            JsonNode details = mapper.readTree(raw == null ? "{}" : raw);
            String value = details.path("deadlineDate").asText("");
            return value.isBlank() ? null : value;
        } catch (Exception error) {
            return null;
        }
    }

    private Map<String, ContentItem> loadItems(List<String> ids) {
        Map<String, ContentItem> byId = new LinkedHashMap<>();
        List<String> unique = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (unique.isEmpty()) return byId;
        for (ContentItem item : items.findGraphByIds(unique)) byId.put(item.getId(), item);
        return byId;
    }

    private long settingsVersion(ContentKind kind) {
        Long version = jdbc.queryForObject("select version from catalog_sort_settings where kind = ?", Long.class, kind.name());
        return version == null ? 0 : version;
    }

    private long lockVersion(ContentKind kind) {
        Long version = jdbc.queryForObject("select version from catalog_sort_settings where kind = ? for update", Long.class, kind.name());
        if (version == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CATALOG_SETTINGS_MISSING", "目录排序配置不存在。");
        }
        return version;
    }

    private void deactivateUnused(ContentKind kind, Set<String> retainedIds) {
        if (retainedIds.isEmpty()) {
            jdbc.update("update catalog_order_entries set active = false, version = version + 1 where kind = ? and active = true", kind.name());
            return;
        }
        String placeholders = String.join(",", retainedIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(kind.name());
        args.addAll(retainedIds);
        jdbc.update("update catalog_order_entries set active = false, version = version + 1 where kind = ? and active = true and id not in (" + placeholders + ")",
                args.toArray());
    }

    private Map<String, String> existingByContent(ContentKind kind) {
        Map<String, String> values = new LinkedHashMap<>();
        jdbc.query("select id, content_item_id from catalog_order_entries where kind = ?", (row) -> {
            values.put(row.getString("content_item_id"), row.getString("id"));
        }, kind.name());
        return values;
    }

    private String auditObjectId(ContentKind kind) {
        return switch (kind) {
            case COMPETITION -> "01JXCATALOGORDERCOMPETE0001";
            case RECRUITMENT -> "01JXCATALOGORDERRECRUIT001";
            case RESOURCE -> "01JXCATALOGORDERRESOURCE01";
        };
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record OrderRow(String id, String contentItemId, int sortOrder) { }
    private record Inspection(
            String title,
            ContentKind kind,
            String operationalStatus,
            java.time.Instant updatedAt,
            String deadline,
            boolean publicEligible,
            String issue) { }
}
