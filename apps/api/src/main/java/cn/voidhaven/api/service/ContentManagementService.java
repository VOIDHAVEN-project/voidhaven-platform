package cn.voidhaven.api.service;
import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class ContentManagementService {
    private final ContentItemRepository items; private final ContentRevisionRepository revisions; private final PartnerProfileRepository profiles; private final AuditService audit; private final ObjectMapper mapper;
    public ContentManagementService(ContentItemRepository items, ContentRevisionRepository revisions, PartnerProfileRepository profiles, AuditService audit, ObjectMapper mapper) { this.items = items; this.revisions = revisions; this.profiles = profiles; this.audit = audit; this.mapper = mapper; }
    public record Draft(String kind, String title, String summary, JsonNode body, String languageCode, String sourceUrl, String sourceName, String rightsBasis, JsonNode details) { }
    public record Managed(String id, ContentKind kind, WorkflowStatus workflowStatus, Visibility visibility, RecordState recordState, String title, long revision) { }
    @Transactional public Managed create(Draft draft, String ownerProfileId, String actorId, String role, String requestId) {
        ContentKind kind = parseKind(draft.kind()); validate(draft, kind); ContentItem item = new ContentItem(Ids.ulid(), kind, WorkflowStatus.DRAFT, Visibility.HIDDEN, RecordState.ACTIVE); item.setOwnerPartnerProfileId(ownerProfileId); items.save(item);
        ContentRevision revision = new ContentRevision(Ids.ulid(), item, 1, draft.title().trim(), draft.summary().trim(), json(draft.body()), draft.languageCode().trim(), draft.sourceUrl().trim(), draft.sourceName().trim(), draft.rightsBasis().trim(), json(draft.details()), actorId); revisions.save(revision); item.setCurrentRevision(revision); items.save(item); audit.record(actorId, role, "CONTENT_CREATED", "CONTENT_ITEM", item.getId(), "{}", "{\"workflowStatus\":\"DRAFT\"}", null, requestId); return managed(item);
    }
    @Transactional public Managed submit(String id, String ownerProfileId, String actorId, String role, String requestId) { ContentItem item = owned(id, ownerProfileId); require(item.getWorkflowStatus() == WorkflowStatus.DRAFT || item.getWorkflowStatus() == WorkflowStatus.CHANGES_REQUESTED, "CONTENT_NOT_SUBMITTABLE"); item.submit(); audit.record(actorId, role, "CONTENT_SUBMITTED", "CONTENT_ITEM", id, "{\"workflowStatus\":\"DRAFT\"}", "{\"workflowStatus\":\"PENDING_REVIEW\"}", null, requestId); return managed(item); }
    @Transactional public Managed decide(String id, String decision, String reason, String actorId, String role, String requestId) { ContentItem item = find(id); require(item.getWorkflowStatus() == WorkflowStatus.PENDING_REVIEW, "CONTENT_NOT_IN_REVIEW"); String before = "{\"workflowStatus\":\"PENDING_REVIEW\"}"; if ("APPROVE".equals(decision)) item.approve(); else if ("CHANGES_REQUESTED".equals(decision)) item.requestChanges(); else if ("REJECT".equals(decision)) item.reject(); else throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_DECISION", "审核决定无效。"); audit.record(actorId, role, "REVIEW_" + decision, "CONTENT_ITEM", id, before, "{\"workflowStatus\":\"" + item.getWorkflowStatus() + "\"}", reason, requestId); return managed(item); }
    @Transactional public Managed publish(String id, String actorId, String role, String requestId) { ContentItem item = find(id); require(item.getWorkflowStatus() == WorkflowStatus.APPROVED, "CONTENT_NOT_APPROVED"); if (item.getOwnerPartnerProfileId() != null) { PartnerProfile profile = profiles.findById(item.getOwnerPartnerProfileId()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PARTNER_PROFILE_INACTIVE", "内容归属合作方当前不可用。")); require(profile.isActive(), "PARTNER_PROFILE_INACTIVE"); long active = items.countByOwnerPartnerProfileIdAndWorkflowStatusAndVisibilityAndRecordState(profile.getId(), WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE); if (active >= profile.getContentQuota()) throw new ApiException(HttpStatus.CONFLICT, "PARTNER_QUOTA_EXCEEDED", "该合作方的公开活跃内容已达到配额。"); } item.publish(); audit.record(actorId, role, "CONTENT_PUBLISHED", "CONTENT_ITEM", id, "{\"visibility\":\"HIDDEN\"}", "{\"visibility\":\"PUBLIC\"}", null, requestId); return managed(item); }
    @Transactional public Managed hide(String id, String reason, String actorId, String role, String requestId) { ContentItem item = find(id); item.hide(); audit.record(actorId, role, "CONTENT_HIDDEN", "CONTENT_ITEM", id, "{}", "{\"visibility\":\"HIDDEN\"}", reason, requestId); return managed(item); }
    @Transactional public Managed archive(String id, String reason, String actorId, String role, String requestId) { ContentItem item = find(id); item.archive(); audit.record(actorId, role, "CONTENT_ARCHIVED", "CONTENT_ITEM", id, "{}", "{\"recordState\":\"ARCHIVED\"}", reason, requestId); return managed(item); }
    public List<Managed> pending() { return items.findByWorkflowStatusOrderByIdAsc(WorkflowStatus.PENDING_REVIEW).stream().map(this::managed).toList(); }
    public List<Managed> ownedList(String profileId) { return items.findByOwnerPartnerProfileIdOrderByIdDesc(profileId).stream().map(this::managed).toList(); }
    private ContentItem find(String id) { return items.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在。")); }
    private ContentItem owned(String id, String profileId) { ContentItem item = find(id); if (!Objects.equals(item.getOwnerPartnerProfileId(), profileId)) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。"); return item; }
    private void validate(Draft draft, ContentKind kind) { if (blank(draft.title()) || blank(draft.summary()) || blank(draft.sourceUrl()) || blank(draft.sourceName()) || blank(draft.rightsBasis()) || draft.body() == null || draft.details() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_VALIDATION_FAILED", "请补齐标题、导读、正文、来源、权利说明和结构化字段。"); if (!draft.sourceUrl().startsWith("https://")) throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_URL_REQUIRED", "官方或原始来源必须使用 HTTPS 链接。"); if (kind == ContentKind.COMPETITION && !draft.details().hasNonNull("officialRulesUrl")) throw new ApiException(HttpStatus.BAD_REQUEST, "OFFICIAL_SOURCE_REQUIRED", "竞赛必须提供可核验的官方规则链接。"); }
    private ContentKind parseKind(String value) { try { return ContentKind.valueOf(value); } catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTENT_KIND", "内容类型无效。"); } }
    private String json(JsonNode node) { try { return mapper.writeValueAsString(node); } catch (Exception e) { throw new IllegalStateException(e); } }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void require(boolean valid, String code) { if (!valid) throw new ApiException(HttpStatus.CONFLICT, code, "当前内容状态不允许此操作。"); }
    private Managed managed(ContentItem item) { return new Managed(item.getId(), item.getKind(), item.getWorkflowStatus(), item.getVisibility(), item.getRecordState(), item.getCurrentRevision().getTitle(), item.getVersion() == null ? 0 : item.getVersion()); }
}
