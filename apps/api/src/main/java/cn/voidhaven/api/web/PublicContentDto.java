package cn.voidhaven.api.web;
import cn.voidhaven.api.domain.*;
import tools.jackson.databind.JsonNode;
public record PublicContentDto(String id, ContentKind kind, String title, String summary, JsonNode body, String languageCode,
                               String sourceName, String sourceUrl, String rightsBasis, JsonNode details, long revision) { }
