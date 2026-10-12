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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HomepageAdministrationService {
    public static final String CURATION_ID = "01JX000H0MEPAGESTATE000001";
    public static final int MAX_PLACEMENTS = 48;

    public record PlacementInput(String slot, String contentItemId, int sortOrder, Instant startsAt, Instant endsAt) { }
    public record PlacementItem(
            String id,
            String slot,
            String contentItemId,
            int sortOrder,
            Instant startsAt,
            Instant endsAt,
            long version,
            String title,
            ContentKind kind,
            String operationalStatus,
            Instant updatedAt,
            String deadline,
            boolean publicEligible,
            String issue) { }
    public record Candidate(
            String id,
            String title,
            ContentKind kind,
            String operationalStatus,
            Instant updatedAt,
            String deadline,
            boolean publicEligible,
            String issue) { }
    public record PlacementReplacement(long version, List<PlacementItem> placements) { }
    public record CandidatePage(List<Candidate> items, String nextCursor) { }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ContentItemRepository items;
    private final ContentService content;
    private final HomepageProjectionService projection;

    public HomepageAdministrationService(
            JdbcTemplate jdbc,
            AuditService audit,
            ContentItemRepository items,
            ContentService content,
            HomepageProjectionService projection) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.items = items;
        this.content = content;
        this.projection = projection;
    }

    @Transactional(readOnly = true)
    public PlacementReplacement current() {
        return new PlacementReplacement(curationVersion(), describe(listRows()));
    }

    @Transactional
    public PlacementReplacement replace(List<PlacementInput> requested, long expectedVersion, String actorId, String role, String requestId) {
        long currentVersion = lockCurationVersion();
        if (currentVersion != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "HOMEPAGE_VERSION_CONFLICT", "首页编排已被其他运营人员更新，请刷新后重试。");
        }
        List<PlacementInput> placements = requested == null ? List.of() : requested;
        if (placements.size() > MAX_PLACEMENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_PLACEMENTS_TOO_MANY", "首页编排最多支持 " + MAX_PLACEMENTS + " 条内容。");
        }
        Instant now = Instant.now();
        Map<String, ContentItem> byId = loadItems(placements.stream().map(PlacementInput::contentItemId).toList());
        Set<String> identities = new HashSet<>();
        for (PlacementInput input : placements) {
            validate(input, byId.get(input.contentItemId()), now);
            if (!identities.add(identity(input.slot(), input.contentItemId()))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_PLACEMENT_DUPLICATE", "同一栏目不能重复编排同一内容。");
            }
        }
        Map<String, ExistingPlacement> existing = existingByIdentity();
        Set<String> retained = new HashSet<>();
        for (PlacementInput input : placements) {
            String key = identity(input.slot(), input.contentItemId());
            ExistingPlacement previous = existing.get(key);
            if (previous == null) {
                String id = Ids.ulid();
                jdbc.update("insert into homepage_placements (id, slot, content_item_id, sort_order, starts_at, ends_at, created_by_account_id) values (?, ?, ?, ?, ?, ?, ?)",
                        id, input.slot(), input.contentItemId(), input.sortOrder(), timestamp(input.startsAt()), timestamp(input.endsAt()), actorId);
                retained.add(id);
            } else {
                jdbc.update("update homepage_placements set sort_order = ?, starts_at = ?, ends_at = ?, active = true, version = version + 1 where id = ?",
                        input.sortOrder(), timestamp(input.startsAt()), timestamp(input.endsAt()), previous.id());
                retained.add(previous.id());
            }
        }
        deactivateUnused(retained);
        jdbc.update("update homepage_curation set version = version + 1, updated_by_account_id = ? where id = ?", actorId, CURATION_ID);
        audit.record(actorId, role, "HOMEPAGE_PLACEMENTS_REPLACED", "HOMEPAGE", "00000000000000000000000000", "{}", "{\"count\":" + placements.size() + "}", null, requestId);
        return current();
    }

    @Transactional(readOnly = true)
    public HomepageProjectionService.HomeSections preview(List<PlacementInput> requested) {
        Instant now = Instant.now();
        Map<String, ContentItem> byId = loadItems(requested == null ? List.of() : requested.stream().map(PlacementInput::contentItemId).toList());
        if (requested != null) {
            for (PlacementInput input : requested) {
                validateShape(input);
                ContentItem item = byId.get(input.contentItemId());
                if (item == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_CONTENT_INVALID", "首页只能编排已存在的内容。");
                }
                inspect(input, item, now);
            }
        }
        return projection.preview(requested);
    }

    @Transactional(readOnly = true)
    public CandidatePage candidates(String slot, String query, String cursor) {
        if (slot != null && !slot.isBlank() && !HomepageEligibility.SLOTS.contains(slot)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_SLOT_INVALID", "首页栏目无效。");
        }
        ContentKind kind = slot == null || slot.isBlank() || "FOCUS".equals(slot) ? null : HomepageEligibility.typedKind(slot);
        Instant now = Instant.now();
        List<ContentItem> rows = items.findPublicSearch(kind, blank(cursor), blank(query), org.springframework.data.domain.PageRequest.of(0, 21));
        List<Candidate> matches = new ArrayList<>();
        for (ContentItem item : rows) {
            if (slot != null && !slot.isBlank() && !HomepageEligibility.kindMatchesSlot(slot, item.getKind())) continue;
            matches.add(toCandidate(item, slot == null || slot.isBlank() ? "FOCUS" : slot, now));
            if (matches.size() == 20) break;
        }
        String next = rows.size() > 20 && !matches.isEmpty() ? matches.get(matches.size() - 1).id() : null;
        return new CandidatePage(matches, next);
    }

    private List<PlacementRow> listRows() {
        return jdbc.query("select id, slot, content_item_id, sort_order, starts_at, ends_at, version from homepage_placements where active = true order by slot, sort_order, id",
                (row, index) -> {
                    Timestamp starts = row.getTimestamp("starts_at");
                    Timestamp ends = row.getTimestamp("ends_at");
                    return new PlacementRow(
                            row.getString("id"),
                            row.getString("slot"),
                            row.getString("content_item_id"),
                            row.getInt("sort_order"),
                            starts == null ? null : starts.toInstant(),
                            ends == null ? null : ends.toInstant(),
                            row.getLong("version"));
                });
    }

    private List<PlacementItem> describe(List<PlacementRow> rows) {
        Instant now = Instant.now();
        Map<String, ContentItem> byId = loadItems(rows.stream().map(PlacementRow::contentItemId).toList());
        List<PlacementItem> described = new ArrayList<>();
        for (PlacementRow row : rows) {
            ContentItem item = byId.get(row.contentItemId());
            Inspection inspection = inspect(row.slot(), row.startsAt(), row.endsAt(), item, now);
            described.add(new PlacementItem(
                    row.id(),
                    row.slot(),
                    row.contentItemId(),
                    row.sortOrder(),
                    row.startsAt(),
                    row.endsAt(),
                    row.version(),
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

    private void validate(PlacementInput input, ContentItem item, Instant now) {
        validateShape(input);
        Inspection inspection = inspect(input, item, now);
        if (!inspection.publicEligible()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_CONTENT_INVALID",
                    inspection.issue() == null ? "首页只能编排仍可公开的内容。" : inspection.issue());
        }
    }

    private void validateShape(PlacementInput input) {
        if (input == null || !HomepageEligibility.SLOTS.contains(input.slot())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_SLOT_INVALID", "首页栏目无效。");
        }
        if (input.contentItemId() == null || input.contentItemId().length() != 26) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_CONTENT_INVALID", "首页内容 ID 无效。");
        }
        if (input.startsAt() != null && input.endsAt() != null && !input.startsAt().isBefore(input.endsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_TIME_INVALID", "首页编排结束时间必须晚于开始时间。");
        }
    }

    private Inspection inspect(PlacementInput input, ContentItem item, Instant now) {
        return inspect(input.slot(), input.startsAt(), input.endsAt(), item, now);
    }

    private Inspection inspect(String slot, Instant startsAt, Instant endsAt, ContentItem item, Instant now) {
        if (item == null) {
            return new Inspection(null, null, null, null, null, false, "内容不存在或已删除。");
        }
        boolean published = item.getPublishedRevision() != null;
        boolean sourced = published && content.sourcedForPublic(item);
        boolean kindMatches = HomepageEligibility.kindMatchesSlot(slot, item.getKind());
        JsonNode details = projection.detailsOf(item);
        boolean curatable = HomepageEligibility.publiclyCuratable(item.getVisibility(), item.getRecordState(), published, sourced)
                && kindMatches
                && item.getPublicationStatus() != cn.voidhaven.api.domain.PublicationStatus.CLOSED
                && !HomepageEligibility.inactiveOperationalStatus(item.getOperationalStatus())
                && !HomepageEligibility.deadlinePassed(item.getKind(), details, now);
        String issue = HomepageEligibility.issue(
                item.getVisibility(),
                item.getRecordState(),
                item.getPublicationStatus(),
                published,
                sourced,
                kindMatches,
                startsAt,
                endsAt,
                item.getKind(),
                item.getOperationalStatus(),
                details,
                now);
        String title = published ? item.getPublishedRevision().getTitle()
                : item.getCurrentRevision() == null ? item.getId() : item.getCurrentRevision().getTitle();
        return new Inspection(
                title,
                item.getKind(),
                item.getOperationalStatus(),
                item.getUpdatedAt(),
                HomepageEligibility.deadlineLabel(details),
                curatable,
                issue);
    }

    private Candidate toCandidate(ContentItem item, String slot, Instant now) {
        Inspection inspection = inspect(slot, null, null, item, now);
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

    private Map<String, ContentItem> loadItems(List<String> ids) {
        Map<String, ContentItem> byId = new LinkedHashMap<>();
        List<String> unique = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (unique.isEmpty()) return byId;
        for (ContentItem item : items.findGraphByIds(unique)) byId.put(item.getId(), item);
        return byId;
    }

    private long curationVersion() {
        Long version = jdbc.queryForObject("select version from homepage_curation where id = ?", Long.class, CURATION_ID);
        return version == null ? 0 : version;
    }

    private long lockCurationVersion() {
        Long version = jdbc.queryForObject("select version from homepage_curation where id = ? for update", Long.class, CURATION_ID);
        if (version == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "HOMEPAGE_CURATION_MISSING", "首页编排版本记录不存在。");
        }
        return version;
    }

    private void deactivateUnused(Set<String> retainedIds) {
        if (retainedIds.isEmpty()) {
            jdbc.update("update homepage_placements set active = false, version = version + 1 where active = true");
            return;
        }
        String placeholders = String.join(",", retainedIds.stream().map(id -> "?").toList());
        jdbc.update("update homepage_placements set active = false, version = version + 1 where active = true and id not in (" + placeholders + ")",
                retainedIds.toArray());
    }

    private Map<String, ExistingPlacement> existingByIdentity() {
        Map<String, ExistingPlacement> values = new LinkedHashMap<>();
        jdbc.query("select id, slot, content_item_id from homepage_placements", (row) -> {
            values.put(identity(row.getString("slot"), row.getString("content_item_id")),
                    new ExistingPlacement(row.getString("id")));
        });
        return values;
    }

    private String identity(String slot, String contentItemId) { return slot + ":" + contentItemId; }
    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record ExistingPlacement(String id) { }
    private record PlacementRow(String id, String slot, String contentItemId, int sortOrder, Instant startsAt, Instant endsAt, long version) { }
    private record Inspection(String title, ContentKind kind, String operationalStatus, Instant updatedAt, String deadline, boolean publicEligible, String issue) { }
}
