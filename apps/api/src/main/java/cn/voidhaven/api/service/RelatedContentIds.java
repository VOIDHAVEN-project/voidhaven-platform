package cn.voidhaven.api.service;

import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;

final class RelatedContentIds {
    private RelatedContentIds() {}

    static LinkedHashSet<String> fromDetails(JsonNode details) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (details == null || details.isNull()) return ids;
        add(ids, details.path("relatedCompetitionId").asText(""));
        JsonNode many = details.get("relatedCompetitionIds");
        if (many == null || many.isNull()) return ids;
        if (many.isArray()) {
            for (JsonNode node : many) add(ids, node == null ? "" : node.asText(""));
            return ids;
        }
        add(ids, many.asText(""));
        return ids;
    }

    private static void add(LinkedHashSet<String> ids, String raw) {
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) ids.add(value);
        }
    }
}
