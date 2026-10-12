package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCursorTest {
    @Test void round_trips_opaque_tokens_and_rejects_sort_mismatch() {
        CatalogCursor cursor = CatalogCursor.of(CatalogSort.DEADLINE, "2026-10-15", "01HZXC0V4K7N9PQRSTVWXY0001");
        String token = cursor.encode();
        assertThat(token).startsWith("c1.");
        CatalogCursor decoded = CatalogCursor.decode(token, CatalogSort.DEADLINE);
        assertThat(decoded.id()).isEqualTo("01HZXC0V4K7N9PQRSTVWXY0001");
        assertThat(decoded.key()).isEqualTo("2026-10-15");
        assertThat(decoded.legacy()).isFalse();
        assertThatThrownBy(() -> CatalogCursor.decode(token, CatalogSort.UPDATED))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CATALOG_CURSOR_MISMATCH");
    }

    @Test void accepts_legacy_ulid_only_for_default_sort() {
        CatalogCursor cursor = CatalogCursor.decode("01HZXC0V4K7N9PQRSTVWXY0001", CatalogSort.DEFAULT);
        assertThat(cursor.legacy()).isTrue();
        assertThat(cursor.id()).isEqualTo("01HZXC0V4K7N9PQRSTVWXY0001");
        assertThatThrownBy(() -> CatalogCursor.decode("01HZXC0V4K7N9PQRSTVWXY0001", CatalogSort.UPDATED))
                .hasFieldOrPropertyWithValue("code", "CATALOG_CURSOR_INVALID");
        assertThatThrownBy(() -> CatalogCursor.decode("not-a-cursor", CatalogSort.DEFAULT))
                .hasFieldOrPropertyWithValue("code", "CATALOG_CURSOR_INVALID");
    }
}

class CatalogSortTest {
    @Test void user_choice_beats_admin_default_and_resources_cannot_use_deadline() {
        assertThat(CatalogSort.resolve(ContentKind.COMPETITION, null, CatalogSort.DEADLINE)).isEqualTo(CatalogSort.DEADLINE);
        assertThat(CatalogSort.resolve(ContentKind.COMPETITION, "updated", CatalogSort.DEADLINE)).isEqualTo(CatalogSort.UPDATED);
        assertThat(CatalogSort.resolve(ContentKind.RESOURCE, null, CatalogSort.DEFAULT)).isEqualTo(CatalogSort.DEFAULT);
        assertThat(CatalogSort.resolve(ContentKind.RESOURCE, null, CatalogSort.DEADLINE)).isEqualTo(CatalogSort.UPDATED);
        assertThatThrownBy(() -> CatalogSort.resolve(ContentKind.RESOURCE, "deadline", CatalogSort.DEFAULT))
                .hasFieldOrPropertyWithValue("code", "CATALOG_SORT_UNSUPPORTED");
        assertThatThrownBy(() -> CatalogSort.parse("popularity")).hasFieldOrPropertyWithValue("code", "CATALOG_SORT_INVALID");
    }
}

class CatalogQueryTest {
    @Test void default_sort_uses_manual_order_then_id_and_encodes_stable_keys() {
        CatalogQuery.BoundSql sql = CatalogQuery.selectIds(new CatalogQuery.Spec(
                ContentKind.COMPETITION, null, "数学", CatalogSort.DEFAULT,
                CatalogCursor.of(CatalogSort.DEFAULT, "2", "01JCONTENT0000000000000001"),
                null, null, null, null, 21));
        assertThat(sql.sql()).contains("left join catalog_order_entries");
        assertThat(sql.sql()).contains("coalesce(o.sort_order, 20000)");
        assertThat(sql.sql()).contains("c.id < ?");
        assertThat(sql.args()).contains("COMPETITION", "数学", 2, "01JCONTENT0000000000000001", 21);
        assertThat(CatalogQuery.encodeKey(CatalogSort.DEFAULT, null)).isEqualTo("20000");
        assertThat(CatalogQuery.encodeKey(CatalogSort.DEADLINE, null)).isEqualTo("~");
    }

    @Test void deadline_cursor_places_nulls_last() {
        CatalogQuery.BoundSql sql = CatalogQuery.selectIds(new CatalogQuery.Spec(
                ContentKind.RECRUITMENT, null, null, CatalogSort.DEADLINE,
                CatalogCursor.of(CatalogSort.DEADLINE, "2026-10-15", "01JCONTENT0000000000000001"),
                "headcount", "number", new java.math.BigDecimal("3"), "OPEN", 21));
        assertThat(sql.sql()).contains("coalesce(cd.deadline_date, rd.deadline_date) is null then 1 else 0");
        assertThat(sql.sql()).contains("f.value_number = ?");
        assertThat(sql.sql()).contains("c.operational_status = ?");
        assertThat(sql.args()).contains("RECRUITMENT", "headcount", "OPEN");
    }
}
