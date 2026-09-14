package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HomepageAdministrationService {
    public record PlacementInput(String slot, String contentItemId, int sortOrder, Instant startsAt, Instant endsAt) { }
    public record Placement(String id, String slot, String contentItemId, int sortOrder, Instant startsAt, Instant endsAt, long version) { }
    public record PlacementReplacement(List<Placement> placements) { }
    private static final Set<String> SLOTS = Set.of("FOCUS", "RECRUITMENT", "RESOURCE");
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public HomepageAdministrationService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PlacementReplacement current() {
        return new PlacementReplacement(list());
    }

    @Transactional(readOnly = true)
    public List<Placement> list() {
        return jdbc.query("select id, slot, content_item_id, sort_order, starts_at, ends_at, version from homepage_placements where active = true order by slot, sort_order, id", (row, index) -> {
            Timestamp starts = row.getTimestamp("starts_at"); Timestamp ends = row.getTimestamp("ends_at");
            return new Placement(row.getString("id"), row.getString("slot"), row.getString("content_item_id"), row.getInt("sort_order"), starts == null ? null : starts.toInstant(), ends == null ? null : ends.toInstant(), row.getLong("version"));
        });
    }

    @Transactional
    public PlacementReplacement replace(List<PlacementInput> requested, String actorId, String role, String requestId) {
        List<PlacementInput> placements = requested == null ? List.of() : requested;
        if (placements.size() > 30) throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_PLACEMENTS_TOO_MANY", "首页编排最多支持 30 条内容。");
        Set<String> identities = new HashSet<>();
        for (PlacementInput input : placements) {
            validate(input);
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
        audit.record(actorId, role, "HOMEPAGE_PLACEMENTS_REPLACED", "HOMEPAGE", "00000000000000000000000000", "{}", "{\"count\":" + placements.size() + "}", null, requestId);
        return current();
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

    private void validate(PlacementInput input) {
        if (input == null || !SLOTS.contains(input.slot())) throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_SLOT_INVALID", "首页栏目无效。");
        if (input.contentItemId() == null || input.contentItemId().length() != 26) throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_CONTENT_INVALID", "首页内容 ID 无效。");
        if (input.startsAt() != null && input.endsAt() != null && !input.startsAt().isBefore(input.endsAt())) throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_TIME_INVALID", "首页编排结束时间必须晚于开始时间。");
        Integer count = jdbc.queryForObject("select count(*) from content_items where id = ? and published_revision_id is not null and record_state = 'ACTIVE'", Integer.class, input.contentItemId());
        if (count == null || count != 1) throw new ApiException(HttpStatus.BAD_REQUEST, "HOMEPAGE_CONTENT_INVALID", "首页只能编排已发布且未归档的内容。");
    }

    private String identity(String slot, String contentItemId) { return slot + ":" + contentItemId; }
    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private record ExistingPlacement(String id) { }
}
