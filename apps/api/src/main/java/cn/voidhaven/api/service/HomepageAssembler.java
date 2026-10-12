package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class HomepageAssembler {
    public static final int SECTION_LIMIT = 4;
    public static final int FOCUS_TYPE_CAP = 2;
    private static final List<ContentKind> TYPE_ORDER =
            List.of(ContentKind.COMPETITION, ContentKind.RECRUITMENT, ContentKind.RESOURCE);

    public record Item(String id, ContentKind kind) { }

    public record HomeIds(
            List<String> focus, List<String> competitions, List<String> recruitments, List<String> resources) { }

    private HomepageAssembler() { }

    public static HomeIds assemble(
            List<Item> focusManual,
            List<Item> competitionManual,
            List<Item> recruitmentManual,
            List<Item> resourceManual,
            List<Item> competitionPool,
            List<Item> recruitmentPool,
            List<Item> resourcePool) {
        List<Item> competitions = fillTyped(competitionManual, competitionPool);
        List<Item> recruitments = fillTyped(recruitmentManual, recruitmentPool);
        List<Item> resources = fillTyped(resourceManual, resourcePool);
        Set<String> typedIds = idsOf(competitions, recruitments, resources);
        List<Item> focus = fillFocus(focusManual, List.of(competitionPool, recruitmentPool, resourcePool), typedIds);
        return new HomeIds(ids(focus), ids(competitions), ids(recruitments), ids(resources));
    }

    static List<Item> fillTyped(List<Item> manual, List<Item> pool) {
        List<Item> result = uniquePrefix(manual, SECTION_LIMIT);
        for (Item item : pool) {
            if (result.size() >= SECTION_LIMIT) break;
            if (contains(result, item.id())) continue;
            result.add(item);
        }
        return result;
    }

    static List<Item> fillFocus(List<Item> manual, List<List<Item>> pools, Set<String> typedSectionIds) {
        List<Item> result = uniquePrefix(manual, SECTION_LIMIT);
        appendBalanced(result, pools, typedSectionIds, true, true);
        appendBalanced(result, pools, typedSectionIds, true, false);
        appendBalanced(result, pools, Set.of(), false, false);
        return result;
    }

    private static void appendBalanced(
            List<Item> result,
            List<List<Item>> pools,
            Set<String> avoid,
            boolean enforceTypeCap,
            boolean avoidTypedSections) {
        boolean progressed = true;
        while (result.size() < SECTION_LIMIT && progressed) {
            progressed = false;
            Item next = nextBalanced(result, pools, avoid, enforceTypeCap, avoidTypedSections);
            if (next != null) {
                result.add(next);
                progressed = true;
            }
        }
    }

    private static Item nextBalanced(
            List<Item> result,
            List<List<Item>> pools,
            Set<String> avoid,
            boolean enforceTypeCap,
            boolean avoidTypedSections) {
        Map<ContentKind, Integer> counts = counts(result);
        Item best = null;
        int bestCount = Integer.MAX_VALUE;
        int bestTypeRank = Integer.MAX_VALUE;
        for (List<Item> pool : pools) {
            for (Item item : pool) {
                if (contains(result, item.id())) continue;
                if (avoidTypedSections && avoid.contains(item.id())) continue;
                int count = counts.getOrDefault(item.kind(), 0);
                if (enforceTypeCap && count >= FOCUS_TYPE_CAP) continue;
                int typeRank = TYPE_ORDER.indexOf(item.kind());
                if (count < bestCount || (count == bestCount && typeRank >= 0 && typeRank < bestTypeRank)) {
                    best = item;
                    bestCount = count;
                    bestTypeRank = typeRank < 0 ? Integer.MAX_VALUE : typeRank;
                }
                break;
            }
        }
        return best;
    }

    private static List<Item> uniquePrefix(List<Item> items, int limit) {
        List<Item> result = new ArrayList<>();
        if (items == null) return result;
        for (Item item : items) {
            if (item == null || item.id() == null || contains(result, item.id())) continue;
            result.add(item);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private static boolean contains(List<Item> items, String id) {
        return items.stream().anyMatch(item -> Objects.equals(item.id(), id));
    }

    private static Map<ContentKind, Integer> counts(List<Item> items) {
        Map<ContentKind, Integer> counts = new LinkedHashMap<>();
        for (Item item : items) counts.merge(item.kind(), 1, Integer::sum);
        return counts;
    }

    @SafeVarargs
    private static Set<String> idsOf(List<Item>... groups) {
        return java.util.Arrays.stream(groups).flatMap(List::stream).map(Item::id).collect(Collectors.toSet());
    }

    private static List<String> ids(List<Item> items) {
        return items.stream().map(Item::id).toList();
    }
}
