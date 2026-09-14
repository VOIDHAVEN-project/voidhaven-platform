package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.support.PageResult;
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
    private AccountLockService accountLocks;
    private ContentActionService contentActions;

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
                          Visibility visibility, RecordState recordState, String title, long revision, String ownerPartnerProfileId,
                          Instant submittedAt, ReviewRecord latestReview, String operationalStatus) { }
    public record ReviewRecord(String decision, String reason, Instant createdAt, String revisionId) { }
    public record Editable(String id, Draft draft, long revision, WorkflowStatus workflowStatus, Visibility visibility,
                           RecordState recordState, PublicationStatus publicationStatus, String ownerPartnerProfileId,
                           Instant submittedAt, List<PublicMediaDto> media, List<ReviewRecord> reviews, String operationalStatus) { }
    public record PublicationBlocker(String code, String message) { }
    public record PublicationReadiness(boolean ready, List<PublicationBlocker> blockers, String partnerProfileId,
                                       Integer quota, Long publicActiveCount) { }
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

    @Autowired(required = false)
    void setAccountLocks(AccountLockService accountLocks) { this.accountLocks = accountLocks; }

    @Autowired(required = false)
    void setContentActions(ContentActionService contentActions) { this.contentActions = contentActions; }

    private void requireActiveActor(String actorId) {
        if (accountLocks != null) accountLocks.requireActiveActor(actorId);
    }

    @Transactional
    public Managed create(Draft draft, String ownerProfileId, String actorId, String role, String requestId) {
        requireActiveActor(actorId);
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

    @Transactional public Managed submit(String id, String ownerProfileId, String actorId, String role, String requestId) { requireActiveActor(actorId); ContentItem item = owned(id, ownerProfileId); submit(item, actorId, role, requestId); return managed(item); }
    @Transactional public Managed submitAsAdmin(String id, String actorId, String role, String requestId) { requireActiveActor(actorId); ContentItem item = find(id); submit(item, actorId, role, requestId); return managed(item); }
    private void submit(ContentItem item, String actorId, String role, String requestId) {
        require(item.getWorkflowStatus() == WorkflowStatus.DRAFT || item.getWorkflowStatus() == WorkflowStatus.CHANGES_REQUESTED, "CONTENT_NOT_SUBMITTABLE");
        requireCurrentMediaMatchesDocument(item.getCurrentRevision());
        item.submit();
        audit.record(actorId, role, "CONTENT_SUBMITTED", "CONTENT_ITEM", item.getId(), "{\"workflowStatus\":\"DRAFT\"}", "{\"workflowStatus\":\"PENDING_REVIEW\"}", null, requestId);
    }

    @Transactional
    public Managed revise(String id, Draft draft, String ownerProfileId, Long expectedVersion, String actorId, String role, String requestId) {
        requireActiveActor(actorId);
        // Locked (not find()/owned()): two concurrent edits of a PUBLISHED item both
        // increment revision_number from the same value, so without serializing here the
        // second saveAndFlush() would race the first and hit the
        // UNIQUE (content_item_id, revision_number) constraint instead of the intended
        // CONTENT_VERSION_CONFLICT response.
        ContentItem item = lockedOwned(id, ownerProfileId);
        if (expectedVersion == null || !Objects.equals(item.getVersion(), expectedVersion)) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "内容已被其他操作更新，请刷新后再编辑。");
        require(item.getRecordState() == RecordState.ACTIVE, "CONTENT_NOT_EDITABLE");
        require(item.getWorkflowStatus() == WorkflowStatus.DRAFT || item.getWorkflowStatus() == WorkflowStatus.CHANGES_REQUESTED || item.getWorkflowStatus() == WorkflowStatus.PUBLISHED, "CONTENT_NOT_EDITABLE");
        Prepared prepared = prepare(draft, item);
        ContentRevision previous = item.getCurrentRevision();
        int nextRevision = previous.getRevisionNumber();
        boolean createCandidate = item.getWorkflowStatus() == WorkflowStatus.PUBLISHED;
        ContentRevision revision;
        if (createCandidate) {
            nextRevision++;
            revision = revisions.saveAndFlush(revision(Ids.ulid(), item, nextRevision, draft, prepared, actorId));
            copyReferencedMedia(previous.getId(), revision.getId(), markdown.mediaIds(prepared.document()));
            writeRevisionProjections(revision.getId(), prepared.module(), prepared.kind(), draft.details());
            if (contentActions != null) contentActions.copyActions(previous.getId(), revision.getId());
            item.setCurrentRevision(revision);
        } else {
            revision = previous;
            syncReferencedMedia(revision.getId(), markdown.mediaIds(prepared.document()));
            replaceRevisionProjections(revision.getId(), prepared.module(), prepared.kind(), draft.details());
            revision.updateDraft(draft.title().trim(), draft.summary().trim(), json(prepared.document()), prepared.language(),
                    nullable(draft.originalTitle()), prepared.sourceUrl(), prepared.sourceName(), draft.rightsBasis().trim(),
                    draft.authorDisplayName().trim(), draft.reprintPermission().trim(), draft.adaptationPermission().trim(),
                    json(draft.details()), prepared.markdownSource(), 2, modules.version(),
                    json(modules.snapshot(prepared.module())), json(draft.details()));
            revisions.saveAndFlush(revision);
        }
        item.beginCandidateRevision();
        item = items.saveAndFlush(item);
        audit.record(actorId, role, createCandidate ? "CONTENT_REVISION_CREATED" : "CONTENT_DRAFT_SAVED", "CONTENT_ITEM", id,
                "{\"revision\":" + (createCandidate ? nextRevision - 1 : nextRevision) + "}",
                "{\"revision\":" + nextRevision + ",\"workflowStatus\":\"DRAFT\"}", null, requestId);
        return managed(item);
    }

    @Transactional
    public Managed decide(String id, String decision, String reason, Long expectedVersion, String actorId, String role, String requestId) {
        requireActiveActor(actorId);
        ContentItem item = find(id);
        if (expectedVersion == null || !Objects.equals(item.getVersion(), expectedVersion)) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "送审内容已发生变化，请刷新后重新审核。");
        require(item.getWorkflowStatus() == WorkflowStatus.PENDING_REVIEW, "CONTENT_NOT_IN_REVIEW");
        String normalizedReason = reason == null ? null : reason.trim();
        if (("CHANGES_REQUESTED".equals(decision) || "REJECT".equals(decision)) && blank(normalizedReason)) throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_REASON_REQUIRED", "退回修改或拒绝时必须填写审核原因。");
        String before = "{\"workflowStatus\":\"PENDING_REVIEW\"}";
        if ("APPROVE".equals(decision)) item.approve();
        else if ("CHANGES_REQUESTED".equals(decision)) item.requestChanges();
        else if ("REJECT".equals(decision)) item.reject();
        else throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_DECISION", "审核决定无效。");
        item.touch();
        audit.recordReview(item.getId(), item.getCurrentRevision().getId(), actorId, decision, normalizedReason);
        audit.record(actorId, role, "REVIEW_" + decision, "CONTENT_ITEM", id, before, "{\"workflowStatus\":\"" + item.getWorkflowStatus() + "\"}", normalizedReason, requestId);
        return managed(item);
    }

    @Transactional
    public Managed reopen(String id, String reason, String actorId, String role, String requestId) {
        requireActiveActor(actorId);
        ContentItem item = find(id);
        require(item.getWorkflowStatus() == WorkflowStatus.REJECTED && item.getRecordState() == RecordState.ACTIVE, "CONTENT_NOT_REOPENABLE");
        String normalizedReason = requireReason(reason);
        item.reopenForChanges();
        audit.recordReview(item.getId(), item.getCurrentRevision().getId(), actorId, "REOPENED", normalizedReason);
        audit.record(actorId, role, "CONTENT_REOPENED", "CONTENT_ITEM", id, "{\"workflowStatus\":\"REJECTED\"}", "{\"workflowStatus\":\"CHANGES_REQUESTED\"}", normalizedReason, requestId);
        return managed(item);
    }

    @Transactional
    public Managed publish(String id, String actorId, String role, String requestId) {
        requireActiveActor(actorId);
        ContentItem item = items.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。"));
        require(item.getWorkflowStatus() == WorkflowStatus.APPROVED, "CONTENT_NOT_APPROVED");
        requireCurrentMediaMatchesDocument(item.getCurrentRevision());
        if (item.getKind() == ContentKind.RECRUITMENT && (contentActions == null || !contentActions.hasEnabledAction(item.getCurrentRevision().getId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "RECRUITMENT_ACTION_REQUIRED", "招募内容至少需要一个有效行动入口后才能发布。");
        }
        requirePartnerPublicationCapacity(item);
        item.publish();
        audit.record(actorId, role, "CONTENT_PUBLISHED", "CONTENT_ITEM", id, "{\"visibility\":\"HIDDEN\"}", "{\"visibility\":\"PUBLIC\"}", null, requestId);
        return managed(item);
    }

    @Transactional public Managed hide(String id, String reason, String actorId, String role, String requestId) { requireActiveActor(actorId); ContentItem item = find(id); require(item.getPublishedRevision() != null && item.getVisibility() == Visibility.PUBLIC && item.getRecordState() == RecordState.ACTIVE, "CONTENT_NOT_HIDEABLE"); item.hide(); audit.record(actorId, role, "CONTENT_HIDDEN", "CONTENT_ITEM", id, "{\"visibility\":\"PUBLIC\"}", "{\"visibility\":\"HIDDEN\"}", requireReason(reason), requestId); return managed(item); }
    @Transactional public Managed show(String id, String actorId, String role, String requestId) { requireActiveActor(actorId); ContentItem item = items.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。")); require(item.getPublishedRevision() != null && item.getPublicationStatus() != PublicationStatus.NONE && item.getRecordState() == RecordState.ACTIVE && item.getVisibility() == Visibility.HIDDEN, "CONTENT_NOT_SHOWABLE"); requirePartnerPublicationCapacity(item); item.show(); audit.record(actorId, role, "CONTENT_SHOWN", "CONTENT_ITEM", id, "{\"visibility\":\"HIDDEN\"}", "{\"visibility\":\"PUBLIC\"}", null, requestId); return managed(item); }
    @Transactional public Managed archive(String id, String reason, String actorId, String role, String requestId) { requireActiveActor(actorId); ContentItem item = find(id); require(item.getPublishedRevision() != null && item.getRecordState() == RecordState.ACTIVE, "CONTENT_NOT_ARCHIVABLE"); item.archive(); audit.record(actorId, role, "CONTENT_ARCHIVED", "CONTENT_ITEM", id, "{\"recordState\":\"ACTIVE\"}", "{\"recordState\":\"ARCHIVED\"}", requireReason(reason), requestId); return managed(item); }
    @Transactional public Managed restore(String id, String reason, String actorId, String role, String requestId) { requireActiveActor(actorId); ContentItem item = find(id); require(item.getRecordState() == RecordState.ARCHIVED, "CONTENT_NOT_RESTORABLE"); item.restore(); audit.record(actorId, role, "CONTENT_RESTORED", "CONTENT_ITEM", id, "{\"recordState\":\"ARCHIVED\"}", "{\"recordState\":\"ACTIVE\",\"visibility\":\"HIDDEN\",\"workflowStatus\":\"APPROVED\"}", reason, requestId); return managed(item); }
    @Transactional public Managed delete(String id, String reason, String actorId, String role, String requestId) { requireActiveActor(actorId); ContentItem item = find(id); item.markDeleted(); audit.record(actorId, role, "CONTENT_DELETED", "CONTENT_ITEM", id, "{}", "{\"recordState\":\"DELETED\",\"visibility\":\"HIDDEN\"}", reason, requestId); return managed(item); }

    @Transactional
    public Managed changeOperationalStatus(String id, String status, String reason, Long expectedVersion, String actorId, String role, String requestId) {
        requireActiveActor(actorId);
        ContentItem item = items.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。"));
        if (expectedVersion == null || !Objects.equals(item.getVersion(), expectedVersion)) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "内容已被其他操作更新，请刷新后重试。");
        if (item.getKind() != ContentKind.RECRUITMENT || !Set.of("OPEN", "PAUSED", "FILLED", "EXPIRED", "CANCELLED").contains(status)) throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATIONAL_STATUS_INVALID", "招募业务状态无效。");
        String before = item.getOperationalStatus();
        item.setOperationalStatus(status);
        audit.record(actorId, role, "CONTENT_OPERATIONAL_STATUS_CHANGED", "CONTENT_ITEM", id,
                "{\"operationalStatus\":" + quote(before) + "}", "{\"operationalStatus\":\"" + status + "\"}", requireReason(reason), requestId);
        return managed(item);
    }

    @Transactional(readOnly = true) public List<Managed> pending() { return items.findByWorkflowStatusOrderByIdAsc(WorkflowStatus.PENDING_REVIEW).stream().map(this::managed).toList(); }
    @Transactional(readOnly = true) public List<Managed> allManaged() { return items.findAllByOrderByIdDesc().stream().map(this::managed).toList(); }
    @Transactional(readOnly = true) public List<Managed> ownedList(String profileId) { return items.findByOwnerPartnerProfileIdOrderByIdDesc(profileId).stream().map(this::managed).toList(); }
    @Transactional(readOnly = true) public long publicActiveCount(String profileId) { return items.countPublicActiveByOwnerPartnerProfileId(profileId); }
    @Transactional(readOnly = true)
    public PageResult<Managed> managedPage(String ownerProfileId, String requiredStatus, String moduleKey, String query, String cursor, int requestedSize) {
        int pageSize = Math.max(1, Math.min(requestedSize, 50));
        StringBuilder sql = new StringBuilder("select c.id from content_items c join content_revisions r on r.id = c.current_revision_id where 1=1");
        List<Object> arguments = new ArrayList<>();
        if (!blank(ownerProfileId)) { sql.append(" and c.owner_partner_profile_id = ?"); arguments.add(ownerProfileId); }
        if (!blank(requiredStatus)) { try { WorkflowStatus.valueOf(requiredStatus); } catch (Exception error) { throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_STATUS_INVALID", "内容状态筛选无效。"); } sql.append(" and c.workflow_status = ?"); arguments.add(requiredStatus); }
        if (!blank(moduleKey)) { modules.requireKnown(moduleKey); sql.append(" and c.module_key = ?"); arguments.add(moduleKey); }
        if (!blank(query)) { sql.append(" and (lower(r.title) like ? or lower(r.summary) like ?)"); String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%"; arguments.add(pattern); arguments.add(pattern); }
        if (!blank(cursor)) { sql.append(" and c.id < ?"); arguments.add(cursor); }
        sql.append(" order by c.id desc limit ?"); arguments.add(pageSize + 1);
        List<Managed> values = jdbc.query(sql.toString(), (row, index) -> row.getString(1), arguments.toArray()).stream().map(id -> managed(find(id))).toList();
        return PageResult.from(values, pageSize, Managed::id);
    }
    @Transactional(readOnly = true) public Editable editable(String id) { return editable(find(id)); }
    @Transactional(readOnly = true) public Editable ownedEditable(String id, String profileId) { return editable(owned(id, profileId)); }

    @Transactional(readOnly = true)
    public PublicationReadiness publicationReadiness(String id) {
        ContentItem item = find(id);
        List<PublicationBlocker> blockers = new ArrayList<>();
        if (item.getWorkflowStatus() != WorkflowStatus.APPROVED) blockers.add(new PublicationBlocker("CONTENT_NOT_APPROVED", "内容尚未审核通过。"));
        try { requireCurrentMediaMatchesDocument(item.getCurrentRevision()); }
        catch (ApiException error) { blockers.add(new PublicationBlocker(error.code(), error.getMessage())); }
        if (item.getKind() == ContentKind.RECRUITMENT && (contentActions == null || !contentActions.hasEnabledAction(item.getCurrentRevision().getId()))) blockers.add(new PublicationBlocker("RECRUITMENT_ACTION_REQUIRED", "招募内容至少需要一个有效行动入口。"));
        Integer quota = null; Long active = null;
        if (item.getOwnerPartnerProfileId() != null) {
            PartnerProfile profile = profiles.findById(item.getOwnerPartnerProfileId()).orElse(null);
            if (profile == null || !profile.isActive()) blockers.add(new PublicationBlocker("PARTNER_PROFILE_INACTIVE", "内容归属合作方当前不可用。"));
            else {
                quota = profile.getContentQuota(); active = items.countPublicActiveByOwnerPartnerProfileId(profile.getId());
                boolean alreadyPublic = item.getPublishedRevision() != null && item.getVisibility() == Visibility.PUBLIC && item.getRecordState() == RecordState.ACTIVE;
                if (!alreadyPublic && active >= quota) blockers.add(new PublicationBlocker("PARTNER_QUOTA_EXCEEDED", "该合作方公开活跃内容已达到配额 " + active + " / " + quota + "。请先调整配额或隐藏其他内容。"));
            }
        }
        return new PublicationReadiness(blockers.isEmpty(), List.copyOf(blockers), item.getOwnerPartnerProfileId(), quota, active);
    }

    private Editable editable(ContentItem item) {
        ContentRevision revision = item.getCurrentRevision();
        try {
            String moduleKey = item.getModuleKey() == null ? modules.legacyModuleKey(item.getKind()) : item.getModuleKey();
            String source = revision.getMarkdownSource() == null || revision.getMarkdownSource().isBlank() ? markdown.legacyToMarkdown(mapper.readTree(revision.getBodyJson())) : revision.getMarkdownSource();
            JsonNode metadata = revision.getMetadataJson() == null ? mapper.readTree(revision.getDetailsJson()) : mapper.readTree(revision.getMetadataJson());
            Draft draft = new Draft(item.getKind().name(), revision.getTitle(), revision.getSummary(), mapper.readTree(revision.getBodyJson()), revision.getLanguageCode(), revision.getOriginalTitle(), revision.getSourceUrl(), revision.getSourceName(), revision.getRightsBasis(), revision.getAuthorDisplayName(), revision.getReprintPermission(), revision.getAdaptationPermission(), metadata, moduleKey, revision.getSchemaVersion(), source);
            return new Editable(item.getId(), draft, item.getVersion() == null ? 0 : item.getVersion(), item.getWorkflowStatus(),
                    item.getVisibility(), item.getRecordState(), item.getPublicationStatus(), item.getOwnerPartnerProfileId(), item.getSubmittedAt(),
                    mediaForRevision(revision.getId()), reviews(item.getId()), item.getOperationalStatus());
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
    private void syncReferencedMedia(String revisionId, List<String> referencedIds) {
        Map<String, String> available = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("select media_asset_id, alt_text from revision_media where revision_id = ?", revisionId)) available.put(Objects.toString(row.get("media_asset_id")), Objects.toString(row.get("alt_text"), ""));
        for (String assetId : referencedIds) if (!available.containsKey(assetId)) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_MEDIA_REFERENCE_INVALID", "正文引用了不属于当前内容的图片。 ");
        jdbc.update("delete from revision_media where revision_id = ?", revisionId);
        for (int index = 0; index < referencedIds.size(); index++) jdbc.update("insert into revision_media (revision_id, media_asset_id, sort_order, alt_text) values (?, ?, ?, ?)", revisionId, referencedIds.get(index), index, available.get(referencedIds.get(index)));
    }
    private void replaceRevisionProjections(String revisionId, ContentModuleRegistry.Module module, ContentKind kind, JsonNode details) {
        jdbc.update("delete from revision_filter_values where revision_id = ?", revisionId);
        jdbc.update("delete from revision_content_relations where revision_id = ?", revisionId);
        if (kind == ContentKind.COMPETITION) jdbc.update("delete from competition_revision_details where revision_id = ?", revisionId);
        else if (kind == ContentKind.RESOURCE) jdbc.update("delete from resource_revision_details where revision_id = ?", revisionId);
        else jdbc.update("delete from recruitment_revision_details where revision_id = ?", revisionId);
        writeRevisionProjections(revisionId, module, kind, details);
    }
    private List<PublicMediaDto> mediaForRevision(String revisionId) {
        return jdbc.query("select media_asset_id, media_type, width_px, height_px, alt_text from revision_media join media_assets on media_assets.id = revision_media.media_asset_id where revision_id = ? and media_assets.lifecycle_state = 'ACTIVE' order by sort_order asc",
                (row, index) -> new PublicMediaDto(row.getString("media_asset_id"), "/api/v1/media/" + row.getString("media_asset_id") + "/preview",
                        row.getString("media_type"), row.getInt("width_px"), row.getInt("height_px"), row.getString("alt_text")), revisionId);
    }
    private List<ReviewRecord> reviews(String contentId) {
        return jdbc.query("select decision, reason, created_at, revision_id from review_records where content_item_id = ? order by created_at desc",
                (row, index) -> new ReviewRecord(row.getString("decision"), row.getString("reason"), row.getTimestamp("created_at").toInstant(), row.getString("revision_id")), contentId);
    }
    private void requireCurrentMediaMatchesDocument(ContentRevision revision) {
        try {
            Set<String> documentIds = new LinkedHashSet<>(markdown.mediaIds(mapper.readTree(revision.getBodyJson())));
            List<String> attached = jdbc.query("select media_asset_id from revision_media where revision_id = ? order by sort_order", (row, index) -> row.getString(1), revision.getId());
            if (documentIds.size() != attached.size() || !documentIds.equals(new LinkedHashSet<>(attached))) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_MEDIA_REFERENCE_INVALID", "正文图片引用与已上传图片不一致；请保存正文后再送审。 ");
        } catch (ApiException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Invalid persisted content document", error); }
    }
    private Managed managed(ContentItem item) { String moduleKey = item.getModuleKey() == null ? modules.legacyModuleKey(item.getKind()) : item.getModuleKey(); List<ReviewRecord> history = reviews(item.getId()); return new Managed(item.getId(), item.getKind(), moduleKey, item.getWorkflowStatus(), item.getPublicationStatus(), item.getVisibility(), item.getRecordState(), item.getCurrentRevision().getTitle(), item.getVersion() == null ? 0 : item.getVersion(), item.getOwnerPartnerProfileId(), item.getSubmittedAt(), history.isEmpty() ? null : history.get(0), item.getOperationalStatus()); }
    private ContentItem find(String id) { return items.findWithCurrentRevisionById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。")); }
    private ContentItem owned(String id, String profileId) { ContentItem item = find(id); if (!Objects.equals(item.getOwnerPartnerProfileId(), profileId)) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。"); return item; }
    private ContentItem lockedOwned(String id, String profileId) {
        ContentItem item = items.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。"));
        if (profileId != null && !Objects.equals(item.getOwnerPartnerProfileId(), profileId)) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。");
        return item;
    }
    private String moduleFromKind(String kind) { try { return modules.legacyModuleKey(ContentKind.valueOf(kind)); } catch (Exception error) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTENT_KIND", "内容类型无效。"); } }
    private Timestamp parseDeadlineAt(JsonNode details) { String value = text(details, "deadlineAt"); if (blank(value)) return null; try { return Timestamp.from(Instant.parse(value)); } catch (Exception error) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE", "截止时间格式无效。 "); } }
    private Date parseDeadlineDate(JsonNode details) { String value = text(details, "deadlineDate"); if (blank(value)) return null; try { return Date.valueOf(LocalDate.parse(value)); } catch (Exception error) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE", "截止日期格式无效。 "); } }
    private String json(JsonNode value) { try { return mapper.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException("Cannot serialize content document", error); } }
    private String quote(String value) { return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\""; }
    private String text(JsonNode value, String field) { return value == null ? "" : value.path(field).asText("").trim(); }
    private String nullable(String value) { return blank(value) ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String requireReason(String value) { String normalized = value == null ? "" : value.trim(); if (normalized.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "ACTION_REASON_REQUIRED", "请填写操作原因。"); return normalized; }
    private void requirePartnerPublicationCapacity(ContentItem item) {
        if (item.getOwnerPartnerProfileId() == null) return;
        PartnerProfile profile = profiles.findByIdForUpdate(item.getOwnerPartnerProfileId()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PARTNER_PROFILE_INACTIVE", "内容归属合作方当前不可用。"));
        require(profile.isActive(), "PARTNER_PROFILE_INACTIVE");
        long active = items.countPublicActiveByOwnerPartnerProfileId(profile.getId());
        boolean alreadyPublic = item.getPublishedRevision() != null && item.getVisibility() == Visibility.PUBLIC && item.getRecordState() == RecordState.ACTIVE;
        if (!alreadyPublic && active >= profile.getContentQuota()) throw new ApiException(HttpStatus.CONFLICT, "PARTNER_QUOTA_EXCEEDED", "该合作方公开活跃内容已达到配额 " + active + " / " + profile.getContentQuota() + "。请先调整配额或隐藏其他内容。");
    }
    private void require(boolean condition, String code) { if (!condition) throw new ApiException(HttpStatus.CONFLICT, code, "当前内容状态不支持该操作。 "); }
}
