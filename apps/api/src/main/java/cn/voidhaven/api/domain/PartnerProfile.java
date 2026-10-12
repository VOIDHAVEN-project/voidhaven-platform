package cn.voidhaven.api.domain;
import jakarta.persistence.*;
@Entity @Table(name = "partner_profiles")
public class PartnerProfile {
    @Id private String id;
    @Column(name = "account_id") private String accountId;
    @Column(name = "display_name") private String displayName;
    @Column(name = "content_quota") private int contentQuota;
    private boolean active;
    @Column(name = "member_quota") private int memberQuota;
    @Column(name = "security_epoch") private long securityEpoch;
    @Version private long version;
    protected PartnerProfile() { }
    public PartnerProfile(String id, String accountId, String displayName, int contentQuota) { this.id = id; this.accountId = accountId; this.displayName = displayName; this.contentQuota = contentQuota; this.active = true; }
    public String getId() { return id; } public String getAccountId() { return accountId; } public int getContentQuota() { return contentQuota; } public boolean isActive() { return active; }
    public String getDisplayName() { return displayName; }
    public int getMemberQuota() { return memberQuota; }
    public long getSecurityEpoch() { return securityEpoch; }
    public long getVersion() { return version; }
    public void setMemberQuota(int value) { memberQuota = value; }
    public void changeActive(boolean value) { active = value; securityEpoch++; }
    public void setContentQuota(int value) { contentQuota = value; }
}
