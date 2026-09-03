package cn.voidhaven.api.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_items")
public class ContentItem {
    @Id private String id;
    @Enumerated(EnumType.STRING) private ContentKind kind;
    @Enumerated(EnumType.STRING) @Column(name = "workflow_status") private WorkflowStatus workflowStatus;
    @Enumerated(EnumType.STRING) @Column(name = "publication_status") private PublicationStatus publicationStatus = PublicationStatus.NONE;
    @Enumerated(EnumType.STRING) private Visibility visibility;
    @Enumerated(EnumType.STRING) @Column(name = "record_state") private RecordState recordState;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "current_revision_id") private ContentRevision currentRevision;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "published_revision_id") private ContentRevision publishedRevision;
    @Column(name = "owner_partner_profile_id") private String ownerPartnerProfileId;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "archived_at") private Instant archivedAt;
    @Version private Long version;
    protected ContentItem() { }
    public ContentItem(String id, ContentKind kind, WorkflowStatus workflowStatus, Visibility visibility, RecordState recordState) {
        this.id = id; this.kind = kind; this.workflowStatus = workflowStatus; this.visibility = visibility; this.recordState = recordState;
    }
    public String getId() { return id; }
    public ContentKind getKind() { return kind; }
    public WorkflowStatus getWorkflowStatus() { return workflowStatus; }
    public PublicationStatus getPublicationStatus() { return publicationStatus; }
    public Visibility getVisibility() { return visibility; }
    public RecordState getRecordState() { return recordState; }
    public ContentRevision getCurrentRevision() { return currentRevision; }
    public ContentRevision getPublishedRevision() { return publishedRevision; }
    public String getOwnerPartnerProfileId() { return ownerPartnerProfileId; }
    public Long getVersion() { return version; }
    public Instant getClosedAt() { return closedAt; }
    public void setCurrentRevision(ContentRevision value) { currentRevision = value; }
    public void setPublishedRevision(ContentRevision value) { publishedRevision = value; }
    public void setOwnerPartnerProfileId(String value) { ownerPartnerProfileId = value; }
    public void beginCandidateRevision() { workflowStatus = WorkflowStatus.DRAFT; }
    public void submit() { workflowStatus = WorkflowStatus.PENDING_REVIEW; }
    public void approve() { workflowStatus = WorkflowStatus.APPROVED; }
    public void reject() { workflowStatus = WorkflowStatus.REJECTED; }
    public void requestChanges() { workflowStatus = WorkflowStatus.CHANGES_REQUESTED; }
    public void publish() { workflowStatus = WorkflowStatus.PUBLISHED; publicationStatus = PublicationStatus.ACTIVE; closedAt = null; visibility = Visibility.PUBLIC; recordState = RecordState.ACTIVE; publishedRevision = currentRevision; }
    public void closePublic(Instant when) { publicationStatus = PublicationStatus.CLOSED; closedAt = when; }
    public void hide() { visibility = Visibility.HIDDEN; }
    public void show() { visibility = Visibility.PUBLIC; }
    public void archive() { recordState = RecordState.ARCHIVED; visibility = Visibility.HIDDEN; archivedAt = Instant.now(); }
    public void restore() { recordState = RecordState.ACTIVE; visibility = Visibility.HIDDEN; workflowStatus = WorkflowStatus.APPROVED; publicationStatus = PublicationStatus.NONE; archivedAt = null; }
    public void markDeleted() { recordState = RecordState.DELETED; visibility = Visibility.HIDDEN; }
}
