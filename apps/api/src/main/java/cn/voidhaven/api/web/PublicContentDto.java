package cn.voidhaven.api.web;
import cn.voidhaven.api.domain.*;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
public record PublicContentDto(String id, ContentKind kind, String moduleKey, int schemaVersion, String title, String summary, JsonNode body, String languageCode,
                               String languageLabel, String originalTitle, String sourceName, String sourceUrl, String rightsBasis,
                               String authorDisplayName, String reprintPermission, String adaptationPermission, JsonNode details, long revision,
                               List<PublicMediaDto> media, String operationalStatus,
                               List<cn.voidhaven.api.service.ContentActionService.PublicAction> actions,
                               cn.voidhaven.api.service.ContentVerificationService.PublicVerification verification,
                               Instant updatedAt, JsonNode resourcePresentation) { }
