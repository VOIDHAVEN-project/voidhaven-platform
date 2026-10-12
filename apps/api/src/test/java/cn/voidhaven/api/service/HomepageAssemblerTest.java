package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HomepageAssemblerTest {
    @Test void keeps_manual_order_and_caps_each_section_at_four() {
        HomepageAssembler.HomeIds home = HomepageAssembler.assemble(
                items("f1", ContentKind.COMPETITION, "f2", ContentKind.RESOURCE, "f3", ContentKind.RECRUITMENT, "f4", ContentKind.COMPETITION, "f5", ContentKind.RESOURCE),
                items("c1", ContentKind.COMPETITION, "c2", ContentKind.COMPETITION, "c3", ContentKind.COMPETITION, "c4", ContentKind.COMPETITION, "c5", ContentKind.COMPETITION),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        assertThat(home.focus()).containsExactly("f1", "f2", "f3", "f4");
        assertThat(home.competitions()).containsExactly("c1", "c2", "c3", "c4");
        assertThat(home.recruitments()).isEmpty();
        assertThat(home.resources()).isEmpty();
    }

    @Test void fills_typed_slots_from_the_pool_when_manual_picks_are_short() {
        HomepageAssembler.HomeIds home = HomepageAssembler.assemble(
                List.of(),
                items("c1", ContentKind.COMPETITION),
                List.of(),
                List.of(),
                items("c1", ContentKind.COMPETITION, "c2", ContentKind.COMPETITION, "c3", ContentKind.COMPETITION),
                List.of(),
                List.of());
        assertThat(home.competitions()).containsExactly("c1", "c2", "c3");
    }

    @Test void autofill_prefers_type_balance_and_avoids_typed_section_duplicates() {
        HomepageAssembler.HomeIds home = HomepageAssembler.assemble(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                items("c1", ContentKind.COMPETITION, "c2", ContentKind.COMPETITION, "c3", ContentKind.COMPETITION, "c4", ContentKind.COMPETITION, "c5", ContentKind.COMPETITION, "c6", ContentKind.COMPETITION),
                items("r1", ContentKind.RECRUITMENT, "r2", ContentKind.RECRUITMENT, "r3", ContentKind.RECRUITMENT, "r4", ContentKind.RECRUITMENT, "r5", ContentKind.RECRUITMENT),
                items("s1", ContentKind.RESOURCE, "s2", ContentKind.RESOURCE, "s3", ContentKind.RESOURCE, "s4", ContentKind.RESOURCE, "s5", ContentKind.RESOURCE));
        assertThat(home.competitions()).containsExactly("c1", "c2", "c3", "c4");
        assertThat(home.recruitments()).containsExactly("r1", "r2", "r3", "r4");
        assertThat(home.resources()).containsExactly("s1", "s2", "s3", "s4");
        assertThat(home.focus()).containsExactly("c5", "r5", "s5", "c6");
    }

    @Test void overlaps_typed_sections_only_when_unique_candidates_are_scarce() {
        HomepageAssembler.HomeIds home = HomepageAssembler.assemble(
                List.of(),
                items("c1", ContentKind.COMPETITION, "c2", ContentKind.COMPETITION),
                items("r1", ContentKind.RECRUITMENT),
                items("s1", ContentKind.RESOURCE),
                items("c1", ContentKind.COMPETITION, "c2", ContentKind.COMPETITION, "c3", ContentKind.COMPETITION, "c4", ContentKind.COMPETITION),
                items("r1", ContentKind.RECRUITMENT, "r2", ContentKind.RECRUITMENT, "r3", ContentKind.RECRUITMENT),
                items("s1", ContentKind.RESOURCE, "s2", ContentKind.RESOURCE, "s3", ContentKind.RESOURCE));
        assertThat(home.competitions()).containsExactly("c1", "c2", "c3", "c4");
        assertThat(home.focus()).containsExactly("c1", "r1", "s1", "c2");
    }

    @Test void keeps_manual_focus_even_when_it_exceeds_the_usual_type_cap() {
        HomepageAssembler.HomeIds home = HomepageAssembler.assemble(
                items("c1", ContentKind.COMPETITION, "c2", ContentKind.COMPETITION, "c3", ContentKind.COMPETITION),
                List.of(),
                List.of(),
                List.of(),
                items("c4", ContentKind.COMPETITION),
                items("r1", ContentKind.RECRUITMENT),
                List.of());
        assertThat(home.focus()).containsExactly("c1", "c2", "c3", "r1");
    }

    @Test void relaxes_type_cap_and_then_allows_overlap_when_candidates_are_scarce() {
        HomepageAssembler.Item only = new HomepageAssembler.Item("c1", ContentKind.COMPETITION);
        HomepageAssembler.HomeIds home = HomepageAssembler.assemble(
                List.of(),
                List.of(only),
                List.of(),
                List.of(),
                List.of(only),
                List.of(),
                List.of());
        assertThat(home.competitions()).containsExactly("c1");
        assertThat(home.focus()).containsExactly("c1");
    }

    @Test void fillFocus_skips_ids_already_selected() {
        List<HomepageAssembler.Item> result = HomepageAssembler.fillFocus(
                items("a", ContentKind.COMPETITION),
                List.of(items("a", ContentKind.COMPETITION, "b", ContentKind.COMPETITION)),
                Set.of());
        assertThat(result.stream().map(HomepageAssembler.Item::id)).containsExactly("a", "b");
    }

    private static List<HomepageAssembler.Item> items(Object... values) {
        List<HomepageAssembler.Item> result = new java.util.ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            result.add(new HomepageAssembler.Item((String) values[index], (ContentKind) values[index + 1]));
        }
        return result;
    }
}
