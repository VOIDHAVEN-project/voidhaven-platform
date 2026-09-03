package cn.voidhaven.api.domain;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity @Table(name = "accounts")
public class Account {
    @Id private String id;
    @Column(name = "login_id") private String loginId;
    @Column(name = "password_hash") private String passwordHash;
    @Column(name = "qualification_status") private String qualificationStatus;
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
    public boolean isActive() { return "ACTIVE".equals(qualificationStatus); }
    public Set<Role> getRoles() { return roles; }
    public void addRole(Role role) { roles.add(role); }
    public void activateWithPassword(String value) { passwordHash = value; qualificationStatus = "ACTIVE"; }
    public void suspend() { qualificationStatus = "SUSPENDED"; }
    public void changeQualificationStatus(String value) { qualificationStatus = value; }
}
