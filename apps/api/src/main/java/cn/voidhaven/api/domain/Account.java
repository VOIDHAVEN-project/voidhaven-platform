package cn.voidhaven.api.domain;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.time.Instant;

@Entity @Table(name = "accounts")
public class Account {
    @Id private String id;
    @Column(name = "login_id") private String loginId;
    @Column(name = "password_hash") private String passwordHash;
    @Column(name = "password_changed_at") private Instant passwordChangedAt;
    @Column(name = "qualification_status") private String qualificationStatus;
    @Version private Long version;
    @Column(name = "security_epoch") private long securityEpoch;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
    protected Account() { }
    public Account(String id, String loginId, String passwordHash) { this(id, loginId, passwordHash, "ACTIVE"); }
    public Account(String id, String loginId, String passwordHash, String qualificationStatus) { this.id = id; this.loginId = loginId; this.passwordHash = passwordHash; this.qualificationStatus = qualificationStatus; }
    public String getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
    public String getQualificationStatus() { return qualificationStatus; }
    public Long getVersion() { return version; }
    public long getSecurityEpoch() { return securityEpoch; }
    public void advanceSecurityEpoch() { securityEpoch++; }
    public boolean isActive() { return "ACTIVE".equals(qualificationStatus); }
    public boolean isPending() { return "PENDING".equals(qualificationStatus); }
    public boolean hasRole(String code) { return roles.stream().anyMatch(role -> code.equals(role.getCode())); }
    public Set<Role> getRoles() { return roles; }
    public void addRole(Role role) { roles.add(role); }
    public void activateWithPassword(String value) {
        if (!"PENDING".equals(qualificationStatus)) throw new IllegalStateException("Only pending accounts can be activated");
        passwordHash = value;
        qualificationStatus = "ACTIVE";
        passwordChangedAt = Instant.now();
    }
    public void changePassword(String value) { passwordHash = value; passwordChangedAt = Instant.now(); }
    public void suspend() { qualificationStatus = "SUSPENDED"; }
    public void changeQualificationStatus(String value) { qualificationStatus = value; }
    public void reactivate() {
        if (!"SUSPENDED".equals(qualificationStatus)) throw new IllegalStateException("Only suspended accounts can be reactivated");
        qualificationStatus = "!ACTIVATION_REQUIRED!".equals(passwordHash) ? "PENDING" : "ACTIVE";
    }
}
