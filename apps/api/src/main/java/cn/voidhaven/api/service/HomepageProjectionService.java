package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.web.PublicContentDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HomepageProjectionService {
    public record HomeSections(
            List<PublicContentDto> focus,
            List<PublicContentDto> competitions,
            List<PublicContentDto> recruitments,
            List<PublicContentDto> resources) { }

    public record PlacementRow(String slot, String contentItemId, int sortOrder, Instant startsAt, Instant endsAt) { }

    private static final int POOL_SIZE = 24;
    private final JdbcTemplate jdbc;
    private final ContentItemRepository items;
    private final ContentService content;
    private final JsonMapper json = JsonMapper.builder().build();

    public HomepageProjectionService(JdbcTemplate jdbc, ContentItemRepository items, ContentService content) {
        this.jdbc = jdbc;
        this.items = items;
        this.content = content;
    }

    @Transactional(readOnly = true)
    public HomeSections projectLive() {
        return project(loadLiveRows(), Instant.now());
    }

    @Transactional(readOnly = true)
    public HomeSections preview(List<HomepageAdministrationService.PlacementInput> inputs) {
        List<PlacementRow> rows = new ArrayList<>();
        if (inputs != null) {
            for (HomepageAdministrationService.PlacementInput input : inputs) {
                if (input == null) continue;
                rows.add(new PlacementRow(input.slot(), input.contentItemId(), input.sortOrder(), input.startsAt(), input.endsAt()));
            }
        }
        return project(rows, Instant.now());
    }

    HomeSections project(List<PlacementRow> rows, Instant now) {
        Map<String, ContentItem> byId = load(allIds(rows));
        List<HomepageAssembler.Item> focusManual = manuals(rows, "FOCUS", byId, now);
        List<HomepageAssembler.Item> competitionManual = manuals(rows, "COMPETITION", byId, now);
        List<HomepageAssembler.Item> recruitmentManual = manuals(rows, "RECRUITMENT", byId, now);
        List<HomepageAssembler.Item> resourceManual = manuals(rows, "RESOURCE", byId, now);
        List<ContentItem> competitionPoolItems = listablePool(ContentKind.COMPETITION, now);
        List<ContentItem> recruitmentPoolItems = listablePool(ContentKind.RECRUITMENT, now);
        List<ContentItem> resourcePoolItems = listablePool(ContentKind.RESOURCE, now);
        remember(byId, competitionPoolItems);
        remember(byId, recruitmentPoolItems);
        remember(byId, resourcePoolItems);
        HomepageAssembler.HomeIds ids = HomepageAssembler.assemble(
                focusManual,
                competitionManual,
                recruitmentManual,
                resourceManual,
                toAssembler(competitionPoolItems),
                toAssembler(recruitmentPoolItems),
                toAssembler(resourcePoolItems));
        return new HomeSections(
                cards(ids.focus(), byId),
                cards(ids.competitions(), byId),
                cards(ids.recruitments(), byId),
                cards(ids.resources(), byId));
    }

    private List<PlacementRow> loadLiveRows() {
        return jdbc.query("""
                        select slot, content_item_id, sort_order, starts_at, ends_at
                        from homepage_placements
                        where active = true
                        order by slot, sort_order, id
                        """,
                (row, index) -> {
                    Timestamp starts = row.getTimestamp("starts_at");
                    Timestamp ends = row.getTimestamp("ends_at");
                    return new PlacementRow(
                            row.getString("slot"),
                            row.getString("content_item_id"),
                            row.getInt("sort_order"),
                            starts == null ? null : starts.toInstant(),
                            ends == null ? null : ends.toInstant());
                });
    }

    private List<HomepageAssembler.Item> manuals(
            List<PlacementRow> rows, String slot, Map<String, ContentItem> byId, Instant now) {
        List<HomepageAssembler.Item> selected = new ArrayList<>();
        for (PlacementRow row : rows) {
            if (!slot.equals(row.slot())) continue;
            if (!HomepageEligibility.inDisplayWindow(row.startsAt(), row.endsAt(), now)) continue;
            ContentItem item = byId.get(row.contentItemId());
            if (item == null || !listable(item, now) || !HomepageEligibility.kindMatchesSlot(slot, item.getKind())) continue;
            selected.add(new HomepageAssembler.Item(item.getId(), item.getKind()));
        }
        selected.sort((left, right) -> {
            int leftOrder = orderOf(rows, slot, left.id());
            int rightOrder = orderOf(rows, slot, right.id());
            return Integer.compare(leftOrder, rightOrder);
        });
        return selected;
    }

    private int orderOf(List<PlacementRow> rows, String slot, String id) {
        for (PlacementRow row : rows) {
            if (slot.equals(row.slot()) && id.equals(row.contentItemId())) return row.sortOrder();
        }
        return Integer.MAX_VALUE;
    }

    private List<ContentItem> listablePool(ContentKind kind, Instant now) {
        List<ContentItem> pool = new ArrayList<>();
        for (ContentItem item : content.publicPool(kind, POOL_SIZE)) {
            if (listable(item, now)) pool.add(item);
        }
        return pool;
    }

    private boolean listable(ContentItem item, Instant now) {
        JsonNode details = detailsOf(item);
        return HomepageEligibility.currentlyListable(
                item.getVisibility(),
                item.getRecordState(),
                item.getPublicationStatus(),
                item.getPublishedRevision() != null,
                content.sourcedForPublic(item),
                item.getKind(),
                item.getOperationalStatus(),
                details,
                now);
    }

    JsonNode detailsOf(ContentItem item) {
        if (item.getPublishedRevision() == null && item.getCurrentRevision() == null) return json.nullNode();
        var revision = item.getPublishedRevision() != null ? item.getPublishedRevision() : item.getCurrentRevision();
        try {
            return json.readTree(revision.getMetadataJson() == null ? revision.getDetailsJson() : revision.getMetadataJson());
        } catch (Exception error) {
            return json.nullNode();
        }
    }

    private List<HomepageAssembler.Item> toAssembler(List<ContentItem> values) {
        return values.stream().map(item -> new HomepageAssembler.Item(item.getId(), item.getKind())).toList();
    }

    private List<PublicContentDto> cards(List<String> ids, Map<String, ContentItem> byId) {
        List<PublicContentDto> cards = new ArrayList<>();
        for (String id : ids) {
            ContentItem item = byId.get(id);
            if (item == null || item.getPublishedRevision() == null) continue;
            cards.add(content.publicCard(item));
        }
        return cards;
    }

    private Map<String, ContentItem> load(Collection<String> ids) {
        Map<String, ContentItem> byId = new LinkedHashMap<>();
        if (ids.isEmpty()) return byId;
        remember(byId, items.findGraphByIds(ids));
        return byId;
    }

    private void remember(Map<String, ContentItem> byId, List<ContentItem> values) {
        for (ContentItem item : values) byId.put(item.getId(), item);
    }

    private List<String> allIds(List<PlacementRow> rows) {
        return rows.stream().map(PlacementRow::contentItemId).distinct().toList();
    }
}
