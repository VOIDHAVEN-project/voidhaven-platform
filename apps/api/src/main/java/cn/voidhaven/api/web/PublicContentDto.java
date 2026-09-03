package cn.voidhaven.api.web;
import cn.voidhaven.api.domain.*;
import tools.jackson.databind.JsonNode;
import java.util.List;
public record PublicContentDto(String id, ContentKind kind, String title, String summary, JsonNode body, String languageCode,
                               String sourceName, String sourceUrl, String rightsBasis, JsonNode details, long revision, List<PublicMediaDto> media) { }
