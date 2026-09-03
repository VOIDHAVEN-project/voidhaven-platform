package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import cn.voidhaven.api.web.PublicMediaDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class ContentManagementService {
    private final ContentItemRepository items;
    private final ContentRevisionRepository revisions;
    private final PartnerProfileRepository profiles;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final ContentSourcePolicy sources;
    private final RichTextPolicy richText;
    private final ContentLanguagePolicy languages;
    private final ContentModuleRegistry modules;
    private final MarkdownDocumentService markdown;
    private final JdbcTemplate jdbc;

    public record Draft(
            @Size(max = 32) String kind,
            @NotBlank @Size(max = 240) String title,
            @NotBlank @Size(max = 1000) String summary,
            @NotNull JsonNode body,
            @NotBlank @Size(max = 16) String languageCode,
            @Size(max = 240) String originalTitle,
            @Size(max = 2048) String sourceUrl,
            @Size(max = 240) String sourceName,
            @NotBlank @Size(max = 120) String rightsBasis,
            @NotBlank @Size(max = 120) String authorDisplayName,
            @NotBlank @Size(max = 32) String reprintPermission,
            @NotBlank @Size(max = 32) String adaptationPermission,
            @NotNull JsonNode details,
            @Size(max = 64) String moduleKey,
            Integer schemaVersion,
            @Size(max = 262144) String markdownSource) {
        public Draft(String kind, String title, String summary, JsonNode body, String languageCode, String originalTitle,
                     String sourceUrl, String sourceName, String rightsBasis, String authorDisplayName,
                     String reprintPermission, String adaptationPermission, JsonNode details) {
            this(kind, title, summary, body, languageCode, originalTitle, sourceUrl, sourceName, rightsBasis, authorDisplayName,
                    reprintPermission, adaptationPermission, details, null, null, null);
        }
    }

    public record Managed(String id, ContentKind kind, String moduleKey, WorkflowStatus workflowStatus, PublicationStatus publicationStatus,
                          Visibility visibility, RecordState recordState, String title, long revision) { }
    public record Editable(String id, Draft draft, long revision, WorkflowStatus workflowStatus, Visibility visibility,
                           RecordState recordState, List<PublicMediaDto> media) { }
    private record Prepared(ContentModuleRegistry.Module module, ContentKind kind, String markdownSource, JsonNode document,
                            String language, String sourceUrl, String sourceName) { }

    @Autowired
    public ContentManagementService(ContentItemRepository items, ContentRevisionRepository revisions, PartnerProfileRepository profiles,
                                    AuditService audit, ObjectMapper mapper, ContentSourcePolicy sources, RichTextPolicy richText,
                                    ContentLanguagePolicy languages, ContentModuleRegistry modules, MarkdownDocumentService markdown, JdbcTemplate jdbc) {
        this.items = items; this.revisions = revisions; this.profiles = profiles; this.audit = audit; this.mapper = mapper;
        this.sources = sources; this.richText = richText; this.languages = languages; this.modules = modules; this.markdown = markdown; this.jdbc = jdbc;
    }

    /** Compatibility constructor for focused unit tests and older integrations. */
    public ContentManagementService(ContentItemRepository items, ContentRevisionRepository revisions, PartnerProfileRepository profiles,
                                    AuditService audit, ObjectMapper mapper, ContentSourcePolicy sources, RichTextPolicy richText,
                                    ContentLanguagePolicy languages, JdbcTemplate jdbc) {
        this(items, revisions, profiles, audit, mapper, sources, richText, languages, new ContentModuleRegistry(mapper), new MarkdownDocumentService(mapper), jdbc);
    }

    @Transactional
    public Managed create(Draft draft, String ownerProfileId, String actorId, String role, String requestId) {
        Prepared prepared = prepare(draft, null);
        ContentItem item = new ContentItem(Ids.ulid(), prepared.kind(), WorkflowStatus.DRAFT, Visibility.HIDDEN, RecordState.ACTIVE);
        item.setModuleKey(prepared.module().key());
        item.setOwnerPartnerProfileId(ownerProfileId);
        item = items.saveAndFlush(item);
        ContentRevision revision = revision(Ids.ulid(), item, 1, draft, prepared, actorId);
        revision = revisions.saveAndFlush(revision);
        writeRevisionProjections(revision.getId(), prepared.module(), prepared.kind(), draft.details());
        item.setCurrentRevision(revision);
        item = items.saveAndFlush(item);
        audit.record(actorId, role, "CONTENT_CREATED", "CONTENT_ITEM", item.getId(), "{}", "{\"workflowStatus\":\"DRAFT\",\"moduleKey\":\"" + prepared.module().key() + "\"}", null, requestId);
        return managed(item);
    }

    @Transactional public Managed submit(String id, String ownerProfileId, String actorId, String role, String requestId) { ContentItem item = owned(id, ownerProfileId); submit(item, actorId, role, requestId); return managed(item); }
    @Transactional public Managed submitAsAdmin(String id, String actorId, String role, String requestId) { ContentItem item = find(id); submit(item, actorId, role, requestId); return managed(item); }
    private void submit(ContentItem item, String actorId, String role, String requestId) {
        require(item.getWorkflowStatus() == WorkflowStatus.DRAFT || item.getWorkflowStatus() == WorkflowStatus.CHANGES_REQUESTED, "CONTENT_NOT_SUBMITTABLE");
        requireCurrentMediaMatchesDocument(item.getCurrentRevision());
        item.submit();
        audit.record(actorId, role, "CONTENT_SUBMITTED", "CONTENT_ITEM", item.getId(), "{\"workflowStatus\":\"DRAFT\"}", "{\"workflowStatus\":\"PENDING_REVIEW\"}", null, requestId);
    }

    @Transactional
    public Managed revise(String id, Draft draft, String ownerProfileId, Long expectedVersion, String actorId, String role, String requestId) {
        ContentItem item = ownerProfileId == null ? find(id) : owned(id, ownerProfileId);
        if (expectedVersion == null || !Objects.equals(item.getVersion(), expectedVersion)) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "内容已被其他操作更新，请刷新后再编辑。");
        require(item.getRecordState() == RecordState.ACTIVE, "CONTENT_NOT_EDITABLE");
        require(item.getWorkflowStatus() == WorkflowStatus.DRAFT || item.getWorkflowStatus() == WorkflowStatus.CHANGES_REQUESTED || item.getWorkflowStatus() == WorkflowStatus.PUBLISHED, "CONTENT_NOT_EDITABLE");
        Prepared prepared = prepare(draft, item);
        int nextRevision = item.getCurrentRevision().getRevisionNumber() + 1;
        ContentRevision previous = item.getCurrentRevision();
        ContentRevision revision = revision(Ids.ulid(), item, nextRevision, draft, prepared, actorId);
        revision = revisions.saveAndFlush(revision);
        copyReferencedMedia(previous.getId(), revision.getId(), markdown.mediaIds(prepared.document()));
        writeRevisionProjections(revision.getId(), prepared.module(), prepared.kind(), draft.details());
        item.setCurrentRevision(revision);
        item.beginCandidateRevision();
        item = items.saveAndFlush(item);
        audit.record(actorId, role, "CONTENT_REVISION_CREATED", "CONTENT_ITEM", id, "{\"revision\":" + (nextRevision - 1) + "}", "{\"revision\":" + nextRevision + ",\"workflowStatus\":\"DRAFT\"}", null, requestId);
        return managed(item);
    }

    @Transactional
    public Managed decide(String id, String decision, String reason, String actorId, String role, String requestId) {
        ContentItem item = find(id);
        require(item.getWorkflowStatus() == WorkflowStatus.PENDING_REVIEW, "CONTENT_NOT_IN_REVIEW");
        String before = "{\"workflowStatus\":\"PENDING_REVIEW\"}";
        if ("APPROVE".equals(decision)) item.approve();
        else if ("CHANGES_REQUESTED".equals(decision)) item.requestChanges();
        else if ("REJECT".equals(decision)) item.reject();
        else throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_DECISION", "审核决定无效。");
        audit.recordReview(item.getId(), item.getCurrentRevision().getId(), actorId, decision, reason);
        audit.record(actorId, role, "REVIEW_" + decision, "CONTENT_ITEM", id, before, "{\"workflowStatus\":\"" + item.getWorkflowStatus() + "\"}", reason, requestId);
        return managed(item);
    }

    @Transactional
    public Managed publish(String id, String actorId, String role, String requestId) {
        ContentItem item = items.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。"));
        require(item.getWorkflowStatus() == WorkflowStatus.APPROVED, "CONTENT_NOT_APPROVED");
        requireCurrentMediaMatchesDocument(item.getCurrentRevision());
        if (item.getOwnerPartnerProfileId() != null) {
            PartnerProfile profile = profiles.findByIdForUpdate(item.getOwnerPartnerProfileId()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PARTNER_PROFILE_INACTIVE", "内容归属合作方当前不可用。"));
            require(profile.isActive(), "PARTNER_PROFILE_INACTIVE");
            long active = items.countPublicActiveByOwnerPartnerProfileId(profile.getId());
            boolean alreadyPublic = item.getPublishedRevision() != null && item.getVisibility() == Visibility.PUBLIC && item.getRecordState() == RecordState.ACTIVE;
            if (!alreadyPublic && active >= profile.getContentQuota()) throw new ApiException(HttpStatus.CONFLICT, "PARTNER_QUOTA_EXCEEDED", "该合作方的公开活跃内容已达到配额。");
        }
        item.publish();
        audit.record(actorId, role, "CONTENT_PUBLISHED", "CONTENT_ITEM", id, "{\"visibility\":\"HIDDEN\"}", "{\"visibility\":\"PUBLIC\"}", null, requestId);
        return managed(item);
    }

    @Transactional public Managed hide(String id, String reason, String actorId, String role, String requestId) { ContentItem item = find(id); item.hide(); audit.record(actorId, role, "CONTENT_HIDDEN", "CONTENT_ITEM", id, "{}", "{\"visibility\":\"HIDDEN\"}", reason, requestId); return managed(item); }
    @Transactional public Managed show(String id, String actorId, String role, String requestId) { ContentItem item = find(id); require(item.getPublishedRevision() != null && item.getPublicationStatus() != PublicationStatus.NONE && item.getRecordState() == RecordState.ACTIVE && item.getVisibility() == Visibility.HIDDEN, "CONTENT_NOT_SHOWABLE"); item.show(); audit.record(actorId, role, "CONTENT_SHOWN", "CONTENT_ITEM", id, "{\"visibility\":\"HIDDEN\"}", "{\"visibility\":\"PUBLIC\"}", null, requestId); return managed(item); }
    @Transactional public Managed archive(String id, String reason, String actorId, String role, String requestId) { ContentItem item = find(id); item.archive(); audit.record(actorId, role, "CONTENT_ARCHIVED", "CONTENT_ITEM", id, "{}", "{\"recordState\":\"ARCHIVED\"}", reason, requestId); return managed(item); }
    @Transactional public Managed restore(String id, String reason, String actorId, String role, String requestId) { ContentItem item = find(id); require(item.getRecordState() == RecordState.ARCHIVED, "CONTENT_NOT_RESTORABLE"); item.restore(); audit.record(actorId, role, "CONTENT_RESTORED", "CONTENT_ITEM", id, "{\"recordState\":\"ARCHIVED\"}", "{\"recordState\":\"ACTIVE\",\"visibility\":\"HIDDEN\",\"workflowStatus\":\"APPROVED\"}", reason, requestId); return managed(item); }
    @Transactional public Managed delete(String id, String reason, String actorId, String role, String requestId) { ContentItem item = find(id); item.markDeleted(); audit.record(actorId, role, "CONTENT_DELETED", "CONTENT_ITEM", id, "{}", "{\"recordState\":\"DELETED\",\"visibility\":\"HIDDEN\"}", reason, requestId); return managed(item); }

    @Transactional(readOnly = true) public List<Managed> pending() { return items.findByWorkflowStatusOrderByIdAsc(WorkflowStatus.PENDING_REVIEW).stream().map(this::managed).toList(); }
    @Transactional(readOnly = true) public List<Managed> allManaged() { return items.findAllByOrderByIdDesc().stream().map(this::managed).toList(); }
    @Transactional(readOnly = true) public List<Managed> ownedList(String profileId) { return items.findByOwnerPartnerProfileIdOrderByIdDesc(profileId).stream().map(this::managed).toList(); }
    @Transactional(readOnly = true) public Editable editable(String id) { return editable(find(id)); }
    @Transactional(readOnly = true) public Editable ownedEditable(String id, String profileId) { return editable(owned(id, profileId)); }

    private Editable editable(ContentItem item) {
        ContentRevision revision = item.getCurrentRevision();
        try {
            String moduleKey = item.getModuleKey() == null ? modules.legacyModuleKey(item.getKind()) : item.getModuleKey();
            String source = revision.getMarkdownSource() == null || revision.getMarkdownSource().isBlank() ? markdown.legacyToMarkdown(mapper.readTree(revision.getBodyJson())) : revision.getMarkdownSource();
            JsonNode metadata = revision.getMetadataJson() == null ? mapper.readTree(revision.getDetailsJson()) : mapper.readTree(revision.getMetadataJson());
            Draft draft = new Draft(item.getKind().name(), revision.getTitle(), revision.getSummary(), mapper.readTree(revision.getBodyJson()), revision.getLanguageCode(), revision.getOriginalTitle(), revision.getSourceUrl(), revision.getSourceName(), revision.getRightsBasis(), revision.getAuthorDisplayName(), revision.getReprintPermission(), revision.getAdaptationPermission(), metadata, moduleKey, revision.getSchemaVersion(), source);
            return new Editable(item.getId(), draft, item.getVersion() == null ? 0 : item.getVersion(), item.getWorkflowStatus(),
                    item.getVisibility(), item.getRecordState(), mediaForRevision(revision.getId()));
        } catch (Exception error) { throw new IllegalStateException("Invalid persisted content document", error); }
    }

    private ContentRevision revision(String id, ContentItem item, int number, Draft draft, Prepared prepared, String actorId) {
        ContentRevision revision = new ContentRevision(id, item, number, draft.title().trim(), draft.summary().trim(), json(prepared.document()), prepared.language(), nullable(draft.originalTitle()), prepared.sourceUrl(), prepared.sourceName(), draft.rightsBasis().trim(), draft.authorDisplayName().trim(), draft.reprintPermission().trim(), draft.adaptationPermission().trim(), json(draft.details()), actorId);
        revision.setEvolvableFields(prepared.markdownSource(), 2, modules.version(), json(modules.snapshot(prepared.module())), json(draft.details()));
        return revision;
    }

    private Prepared prepare(Draft draft, ContentItem existing) {
        String requested = blank(draft.moduleKey()) ? (existing == null ? moduleFromKind(draft.kind()) : existing.getModuleKey()) : draft.moduleKey().trim();
        ContentModuleRegistry.Module module = modules.requireActive(requested);
        if (existing != null && !module.key().equals(existing.getModuleKey())) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_MODULE_IMMUTABLE", "已创建内容不能变更内容模块。");
        if (draft.schemaVersion() != null && draft.schemaVersion() != modules.version()) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_SCHEMA_VERSION_CONFLICT", "内容模块定义已更新，请刷新编辑器后重试。");
        ContentKind kind = module.legacyKind();
        String source = blank(draft.markdownSource()) ? markdown.legacyToMarkdown(draft.body()) : draft.markdownSource().trim();
        JsonNode document = markdown.parse(source);
        richText.requireAllowed(document);
        validate(draft, module, kind, source);
        String language = languages.normalize(draft.languageCode());
        boolean original = module.allowsOriginalSource() && "ORIGINAL".equals(text(draft.details(), "sourceMode"));
        String sourceUrl = original ? null : draft.sourceUrl().trim();
        String sourceName = original ? "原创文章" : draft.sourceName().trim();
        return new Prepared(module, kind, source, document, language, sourceUrl, sourceName);
    }

    private void validate(Draft draft, ContentModuleRegistry.Module module, ContentKind kind, String source) {
        String language = languages.normalize(draft.languageCode());
        if (blank(draft.title()) || blank(draft.summary()) || blank(draft.rightsBasis()) || blank(draft.authorDisplayName()) || blank(source) || draft.details() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_VALIDATION_FAILED", "请补齐标题、导读、正文、权利说明、作者展示名和发布设置。");
        if (languages.requiresOriginalTitle(language) && blank(draft.originalTitle())) throw new ApiException(HttpStatus.BAD_REQUEST, "ORIGINAL_TITLE_REQUIRED", "非中文内容必须保留原始标题。 ");
        if (!Set.of("NOT_ALLOWED", "ATTRIBUTION_ALLOWED").contains(draft.reprintPermission()) || !Set.of("NOT_ALLOWED", "ATTRIBUTION_ALLOWED").contains(draft.adaptationPermission())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REUSE_PERMISSION", "转载和改编许可仅支持允许署名或不允许。 ");
        modules.requireValidMetadata(module, draft.details());
        boolean original = module.allowsOriginalSource() && "ORIGINAL".equals(text(draft.details(), "sourceMode"));
        if (!original) {
            if (blank(draft.sourceUrl()) || blank(draft.sourceName())) throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_URL_REQUIRED", "请提供官方或原始来源名称与 HTTPS 链接。");
            sources.requireAllowed(draft.sourceUrl());
        } else if (!blank(draft.sourceUrl())) sources.requireAllowed(draft.sourceUrl());
        parseDeadlineAt(draft.details()); parseDeadlineDate(draft.details());
        if (kind == ContentKind.COMPETITION) {
            String rules = text(draft.details(), "officialRulesUrl");
            if (blank(rules)) throw new ApiException(HttpStatus.BAD_REQUEST, "OFFICIAL_SOURCE_REQUIRED", "竞赛必须提供可核验的官方规则链接。");
            sources.requireAllowed(rules);
        }
        if (kind == ContentKind.RESOURCE && blank(text(draft.details(), "resourceType"))) throw new ApiException(HttpStatus.BAD_REQUEST, "RESOURCE_TYPE_REQUIRED", "学习资源必须标明资源类型。");
        if (kind == ContentKind.RECRUITMENT) {
            if (!blank(text(draft.details(), "followUpUrl"))) sources.requireAllowed(text(draft.details(), "followUpUrl"));
            String related = text(draft.details(), "relatedCompetitionId");
            if (!blank(related)) {
                ContentItem target = items.findById(related).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "RELATED_COMPETITION_NOT_FOUND", "关联竞赛不存在。"));
                if (target.getKind() != ContentKind.COMPETITION) throw new ApiException(HttpStatus.BAD_REQUEST, "RELATED_CONTENT_NOT_COMPETITION", "关联内容必须是竞赛。 ");
            }
        }
    }

    private void writeRevisionProjections(String revisionId, ContentModuleRegistry.Module module, ContentKind kind, JsonNode details) {
        writeTypedDetails(kind, revisionId, details);
        for (ContentModuleRegistry.Field field : module.fields()) {
            JsonNode value = details.get(field.key());
            if (!field.filterable() || value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) continue;
            if ("date".equals(field.type())) {
                try { jdbc.update("insert into revision_filter_values (revision_id, field_key, value_type, value_date) values (?, ?, 'DATE', ?)", revisionId, field.key(), Date.valueOf(value.asText())); }
                catch (IllegalArgumentException error) { throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_METADATA_INVALID", "“" + field.label() + "”必须是有效日期。"); }
            } else if ("number".equals(field.type())) {
                jdbc.update("insert into revision_filter_values (revision_id, field_key, value_type, value_number) values (?, ?, 'NUMBER', ?)", revisionId, field.key(), value.decimalValue());
            } else {
                String text = value.isTextual() ? value.asText().trim() : value.toString();
                jdbc.update("insert into revision_filter_values (revision_id, field_key, value_type, value_text) values (?, ?, 'TEXT', ?)", revisionId, field.key(), text);
            }
        }
        String related = text(details, "relatedCompetitionId");
        if (!blank(related)) jdbc.update("insert into revision_content_relations (revision_id, target_content_item_id, relation_key) values (?, ?, 'RELATED_TO')", revisionId, related);
    }

    private void writeTypedDetails(ContentKind kind, String revisionId, JsonNode details) {
        Timestamp deadlineAt = parseDeadlineAt(details); Date deadlineDate = parseDeadlineDate(details);
        if (kind == ContentKind.COMPETITION) jdbc.update("insert into competition_revision_details (revision_id, deadline_at, deadline_date, official_rules_url) values (?, ?, ?, ?)", revisionId, deadlineAt, deadlineDate, text(details, "officialRulesUrl"));
        else if (kind == ContentKind.RESOURCE) jdbc.update("insert into resource_revision_details (revision_id, resource_type) values (?, ?)", revisionId, text(details, "resourceType"));
        else jdbc.update("insert into recruitment_revision_details (revision_id, deadline_at, deadline_date, related_competition_id, follow_up_url) values (?, ?, ?, ?, ?)", revisionId, deadlineAt, deadlineDate, nullable(text(details, "relatedCompetitionId")), nullable(text(details, "followUpUrl")));
    }

    private void copyReferencedMedia(String oldRevisionId, String newRevisionId, List<String> referencedIds) {
        Map<String, String> available = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("select media_asset_id, alt_text from revision_media where revision_id = ?", oldRevisionId)) {
            available.put(Objects.toString(row.get("media_asset_id")), Objects.toString(row.get("alt_text"), ""));
        }
        for (int index = 0; index < referencedIds.size(); index++) {
            String assetId = referencedIds.get(index);
            String altText = available.get(assetId);
            if (altText == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_MEDIA_REFERENCE_INVALID", "正文引用了不属于当前内容的图片。 ");
            jdbc.update("insert into revision_media (revision_id, media_asset_id, sort_order, alt_text) values (?, ?, ?, ?)",
                    newRevisionId, assetId, index, altText);
        }
    }
    private List<PublicMediaDto> mediaForRevision(String revisionId) {
        return jdbc.query("select media_asset_id, media_type, width_px, height_px, alt_text from revision_media join media_assets on media_assets.id = revision_media.media_asset_id where revision_id = ? order by sort_order asc",
                (row, index) -> new PublicMediaDto(row.getString("media_asset_id"), "/api/v1/public/media/" + row.getString("media_asset_id"),
                        row.getString("media_type"), row.getInt("width_px"), row.getInt("height_px"), row.getString("alt_text")), revisionId);
    }
    private void requireCurrentMediaMatchesDocument(ContentRevision revision) {
        try {
            Set<String> documentIds = new LinkedHashSet<>(markdown.mediaIds(mapper.readTree(revision.getBodyJson())));
            List<String> attached = jdbc.query("select media_asset_id from revision_media where revision_id = ? order by sort_order", (row, index) -> row.getString(1), revision.getId());
            if (documentIds.size() != attached.size() || !documentIds.equals(new LinkedHashSet<>(attached))) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_MEDIA_REFERENCE_INVALID", "正文图片引用与已上传图片不一致；请保存正文后再送审。 ");
        } catch (ApiException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Invalid persisted content document", error); }
    }
    private Managed managed(ContentItem item) { String moduleKey = item.getModuleKey() == null ? modules.legacyModuleKey(item.getKind()) : item.getModuleKey(); return new Managed(item.getId(), item.getKind(), moduleKey, item.getWorkflowStatus(), item.getPublicationStatus(), item.getVisibility(), item.getRecordState(), item.getCurrentRevision().getTitle(), item.getVersion() == null ? 0 : item.getVersion()); }
    private ContentItem find(String id) { return items.findWithCurrentRevisionById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。")); }
    private ContentItem owned(String id, String profileId) { ContentItem item = find(id); if (!Objects.equals(item.getOwnerPartnerProfileId(), profileId)) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。"); return item; }
    private String moduleFromKind(String kind) { try { return modules.legacyModuleKey(ContentKind.valueOf(kind)); } catch (Exception error) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTENT_KIND", "内容类型无效。"); } }
    private Timestamp parseDeadlineAt(JsonNode details) { String value = text(details, "deadlineAt"); if (blank(value)) return null; try { return Timestamp.from(Instant.parse(value)); } catch (Exception error) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE", "截止时间格式无效。 "); } }
    private Date parseDeadlineDate(JsonNode details) { String value = text(details, "deadlineDate"); if (blank(value)) return null; try { return Date.valueOf(LocalDate.parse(value)); } catch (Exception error) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE", "截止日期格式无效。 "); } }
    private String json(JsonNode value) { try { return mapper.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException("Cannot serialize content document", error); } }
    private String text(JsonNode value, String field) { return value == null ? "" : value.path(field).asText("").trim(); }
    private String nullable(String value) { return blank(value) ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void require(boolean condition, String code) { if (!condition) throw new ApiException(HttpStatus.CONFLICT, code, "当前内容状态不支持该操作。 "); }
}
