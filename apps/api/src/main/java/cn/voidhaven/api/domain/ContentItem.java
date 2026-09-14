package cn.voidhaven.api.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_items")
public class ContentItem {
    @Id private String id;
    @Enumerated(EnumType.STRING) private ContentKind kind;
    @Column(name = "module_key") private String moduleKey;
    @Enumerated(EnumType.STRING) @Column(name = "workflow_status") private WorkflowStatus workflowStatus;
    @Enumerated(EnumType.STRING) @Column(name = "publication_status") private PublicationStatus publicationStatus = PublicationStatus.NONE;
    @Column(name = "operational_status") private String operationalStatus;
    @Enumerated(EnumType.STRING) private Visibility visibility;
    @Enumerated(EnumType.STRING) @Column(name = "record_state") private RecordState recordState;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "current_revision_id") private ContentRevision currentRevision;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "published_revision_id") private ContentRevision publishedRevision;
    @Column(name = "owner_partner_profile_id") private String ownerPartnerProfileId;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Version private Long version;
    protected ContentItem() { }
    public ContentItem(String id, ContentKind kind, WorkflowStatus workflowStatus, Visibility visibility, RecordState recordState) {
        this.id = id; this.kind = kind; this.workflowStatus = workflowStatus; this.visibility = visibility; this.recordState = recordState; this.updatedAt = Instant.now();
    }
    public String getId() { return id; }
    public ContentKind getKind() { return kind; }
    public String getModuleKey() { return moduleKey; }
    public WorkflowStatus getWorkflowStatus() { return workflowStatus; }
    public PublicationStatus getPublicationStatus() { return publicationStatus; }
    public String getOperationalStatus() { return operationalStatus; }
    public Visibility getVisibility() { return visibility; }
    public RecordState getRecordState() { return recordState; }
    public ContentRevision getCurrentRevision() { return currentRevision; }
    public ContentRevision getPublishedRevision() { return publishedRevision; }
    public String getOwnerPartnerProfileId() { return ownerPartnerProfileId; }
    public Long getVersion() { return version; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setCurrentRevision(ContentRevision value) { currentRevision = value; }
    public void setPublishedRevision(ContentRevision value) { publishedRevision = value; }
    public void setOwnerPartnerProfileId(String value) { ownerPartnerProfileId = value; }
    public void setModuleKey(String value) { moduleKey = value; }
    public void setOperationalStatus(String value) { operationalStatus = value; touch(); }
    public void beginCandidateRevision() { workflowStatus = WorkflowStatus.DRAFT; submittedAt = null; touch(); }
    public void submit() { workflowStatus = WorkflowStatus.PENDING_REVIEW; submittedAt = Instant.now(); touch(); }
    public void approve() { workflowStatus = WorkflowStatus.APPROVED; touch(); }
    public void reject() { workflowStatus = WorkflowStatus.REJECTED; touch(); }
    public void requestChanges() { workflowStatus = WorkflowStatus.CHANGES_REQUESTED; touch(); }
    public void reopenForChanges() { workflowStatus = WorkflowStatus.CHANGES_REQUESTED; touch(); }
    public void publish() { workflowStatus = WorkflowStatus.PUBLISHED; publicationStatus = PublicationStatus.ACTIVE; closedAt = null; visibility = Visibility.PUBLIC; recordState = RecordState.ACTIVE; publishedRevision = currentRevision; if (kind == ContentKind.RECRUITMENT && operationalStatus == null) operationalStatus = "OPEN"; touch(); }
    public void closePublic(Instant when) { publicationStatus = PublicationStatus.CLOSED; closedAt = when; touch(); }
    public void hide() { visibility = Visibility.HIDDEN; touch(); }
    public void show() { visibility = Visibility.PUBLIC; touch(); }
    public void archive() { recordState = RecordState.ARCHIVED; visibility = Visibility.HIDDEN; archivedAt = Instant.now(); touch(); }
    public void restore() { recordState = RecordState.ACTIVE; visibility = Visibility.HIDDEN; workflowStatus = WorkflowStatus.APPROVED; publicationStatus = PublicationStatus.NONE; archivedAt = null; touch(); }
    public void markDeleted() { recordState = RecordState.DELETED; visibility = Visibility.HIDDEN; touch(); }
    public void touch() { updatedAt = Instant.now(); }
}
