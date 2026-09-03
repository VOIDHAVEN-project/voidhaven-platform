package cn.voidhaven.api.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_revisions")
public class ContentRevision {
    @Id private String id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "content_item_id", nullable = false) private ContentItem contentItem;
    @Column(name = "revision_number") private int revisionNumber;
    private String title;
    @Column(length = 1000) private String summary;
    @Column(name = "body_json", columnDefinition = "json") private String bodyJson;
    @Column(name = "language_code") private String languageCode;
    @Column(name = "original_title") private String originalTitle;
    @Column(name = "source_url", length = 2048) private String sourceUrl;
    @Column(name = "source_name") private String sourceName;
    @Column(name = "rights_basis") private String rightsBasis;
    @Column(name = "author_display_name") private String authorDisplayName;
    @Column(name = "reprint_permission") private String reprintPermission;
    @Column(name = "adaptation_permission") private String adaptationPermission;
    @Column(name = "details_json", columnDefinition = "json") private String detailsJson;
    @Column(name = "created_by_account_id") private String createdByAccountId;
    @Column(name = "created_at") private Instant createdAt;
    protected ContentRevision() { }
    public ContentRevision(String id, ContentItem contentItem, int revisionNumber, String title, String summary, String bodyJson,
                           String languageCode, String originalTitle, String sourceUrl, String sourceName, String rightsBasis, String authorDisplayName,
                           String reprintPermission, String adaptationPermission, String detailsJson, String createdByAccountId) {
        this.id = id; this.contentItem = contentItem; this.revisionNumber = revisionNumber; this.title = title; this.summary = summary;
        this.bodyJson = bodyJson; this.languageCode = languageCode; this.originalTitle = originalTitle; this.sourceUrl = sourceUrl; this.sourceName = sourceName;
        this.rightsBasis = rightsBasis; this.authorDisplayName = authorDisplayName; this.reprintPermission = reprintPermission; this.adaptationPermission = adaptationPermission;
        this.detailsJson = detailsJson; this.createdByAccountId = createdByAccountId; this.createdAt = Instant.now();
    }
    public String getId() { return id; }
    public int getRevisionNumber() { return revisionNumber; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getBodyJson() { return bodyJson; }
    public String getLanguageCode() { return languageCode; }
    public String getOriginalTitle() { return originalTitle; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceName() { return sourceName; }
    public String getRightsBasis() { return rightsBasis; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public String getReprintPermission() { return reprintPermission; }
    public String getAdaptationPermission() { return adaptationPermission; }
    public String getDetailsJson() { return detailsJson; }
}
