package cn.voidhaven.api.domain;
import jakarta.persistence.*;
@Entity @Table(name = "partner_profiles")
public class PartnerProfile {
    @Id private String id;
    @Column(name = "account_id") private String accountId;
    @Column(name = "display_name") private String displayName;
    @Column(name = "content_quota") private int contentQuota;
    private boolean active;
    protected PartnerProfile() { }
    public String getId() { return id; } public String getAccountId() { return accountId; } public int getContentQuota() { return contentQuota; } public boolean isActive() { return active; }
    public void setContentQuota(int value) { contentQuota = value; }
}
