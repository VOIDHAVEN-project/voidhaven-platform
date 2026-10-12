package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentKind;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CatalogQuery {
    public static final int UNORDERED_RANK = 20000;

    public record BoundSql(String sql, Object[] args) { }
    public record RankedId(String id, String key) { }
    public record Spec(
            ContentKind kind,
            String moduleKey,
            String query,
            CatalogSort sort,
            CatalogCursor cursor,
            String filterKey,
            String filterType,
            Object filterValue,
            String status,
            int limit) { }

    private CatalogQuery() { }

    public static BoundSql selectIds(Spec spec) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("select c.id as id, ").append(sortExpression(spec.sort())).append(" as sort_key ");
        sql.append("from content_items c ");
        sql.append("join content_revisions r on r.id = c.published_revision_id ");
        if (spec.sort() == CatalogSort.DEFAULT) {
            sql.append("left join catalog_order_entries o on o.content_item_id = c.id and o.kind = c.kind and o.active = true ");
        }
        if (spec.sort() == CatalogSort.DEADLINE) {
            sql.append("left join competition_revision_details cd on cd.revision_id = r.id ");
            sql.append("left join recruitment_revision_details rd on rd.revision_id = r.id ");
        }
        if (spec.filterKey() != null) {
            sql.append("join revision_filter_values f on f.revision_id = r.id ");
        }
        sql.append("where c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE' ");
        if (spec.moduleKey() != null) {
            sql.append("and c.module_key = ? ");
            args.add(spec.moduleKey());
        } else {
            sql.append("and c.kind = ? ");
            args.add(spec.kind().name());
        }
        if (spec.query() != null) {
            sql.append("and (lower(r.title) like lower(concat('%', ?, '%')) or lower(r.summary) like lower(concat('%', ?, '%')) or lower(r.source_name) like lower(concat('%', ?, '%'))) ");
            args.add(spec.query());
            args.add(spec.query());
            args.add(spec.query());
        }
        if (spec.filterKey() != null) {
            sql.append("and f.field_key = ? ");
            args.add(spec.filterKey());
            if ("date".equals(spec.filterType())) sql.append("and f.value_date = ? ");
            else if ("number".equals(spec.filterType())) sql.append("and f.value_number = ? ");
            else sql.append("and f.value_text = ? ");
            args.add(spec.filterValue());
        }
        if (spec.status() != null) {
            sql.append("and c.operational_status = ? ");
            args.add(spec.status());
        }
        appendCursor(sql, args, spec.sort(), spec.cursor());
        sql.append(orderBy(spec.sort()));
        sql.append(" limit ?");
        args.add(spec.limit());
        return new BoundSql(sql.toString(), args.toArray());
    }

    public static String encodeKey(CatalogSort sort, Object sortKey) {
        if (sort == CatalogSort.DEADLINE) {
            if (sortKey == null) return "~";
            if (sortKey instanceof Date date) return date.toLocalDate().toString();
            return sortKey.toString().substring(0, Math.min(10, sortKey.toString().length()));
        }
        if (sort == CatalogSort.UPDATED) {
            if (sortKey instanceof Timestamp timestamp) return timestamp.toInstant().toString();
            if (sortKey instanceof java.util.Date date) return date.toInstant().toString();
            return Instant.parse(String.valueOf(sortKey)).toString();
        }
        return Integer.toString(sortKey == null ? UNORDERED_RANK : ((Number) sortKey).intValue());
    }

    static String sortExpression(CatalogSort sort) {
        return switch (sort) {
            case DEFAULT -> "coalesce(o.sort_order, " + UNORDERED_RANK + ")";
            case DEADLINE -> "coalesce(cd.deadline_date, rd.deadline_date)";
            case UPDATED -> "c.updated_at";
        };
    }

    static String orderBy(CatalogSort sort) {
        return switch (sort) {
            case DEFAULT -> " order by coalesce(o.sort_order, " + UNORDERED_RANK + ") asc, c.id desc";
            case DEADLINE -> " order by case when coalesce(cd.deadline_date, rd.deadline_date) is null then 1 else 0 end asc, coalesce(cd.deadline_date, rd.deadline_date) asc, c.id desc";
            case UPDATED -> " order by c.updated_at desc, c.id desc";
        };
    }

    static void appendCursor(StringBuilder sql, List<Object> args, CatalogSort sort, CatalogCursor cursor) {
        if (cursor == null) return;
        if (cursor.legacy()) {
            sql.append("and c.id < ? ");
            args.add(cursor.id());
            return;
        }
        if (sort == CatalogSort.DEFAULT) {
            int rank = parseRank(cursor.key());
            sql.append("and (coalesce(o.sort_order, ").append(UNORDERED_RANK).append(") > ? or (coalesce(o.sort_order, ")
                    .append(UNORDERED_RANK).append(") = ? and c.id < ?)) ");
            args.add(rank);
            args.add(rank);
            args.add(cursor.id());
            return;
        }
        if (sort == CatalogSort.DEADLINE) {
            String expr = "coalesce(cd.deadline_date, rd.deadline_date)";
            if (cursor.key() == null || cursor.key().isBlank() || "~".equals(cursor.key())) {
                sql.append("and ").append(expr).append(" is null and c.id < ? ");
                args.add(cursor.id());
                return;
            }
            Date date = Date.valueOf(cursor.key());
            sql.append("and (").append(expr).append(" > ? or (").append(expr).append(" = ? and c.id < ?) or ").append(expr).append(" is null) ");
            args.add(date);
            args.add(date);
            args.add(cursor.id());
            return;
        }
        Timestamp timestamp = Timestamp.from(Instant.parse(cursor.key()));
        sql.append("and (c.updated_at < ? or (c.updated_at = ? and c.id < ?)) ");
        args.add(timestamp);
        args.add(timestamp);
        args.add(cursor.id());
    }

    private static int parseRank(String key) {
        try {
            return Integer.parseInt(key);
        } catch (Exception error) {
            return UNORDERED_RANK;
        }
    }
}
