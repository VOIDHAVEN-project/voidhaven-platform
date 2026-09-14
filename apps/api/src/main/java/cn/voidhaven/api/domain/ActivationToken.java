package cn.voidhaven.api.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "activation_tokens")
public class ActivationToken {
    @Id private String id;
    @Column(name = "account_id") private String accountId;
    @Column(name = "token_type") private String tokenType;
    @Column(name = "token_hash") private String tokenHash;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    protected ActivationToken() { }
    public ActivationToken(String id, String accountId, String tokenHash, Instant expiresAt) { this(id, accountId, "ACTIVATION", tokenHash, expiresAt); }
    public ActivationToken(String id, String accountId, String tokenType, String tokenHash, Instant expiresAt) { this.id = id; this.accountId = accountId; this.tokenType = tokenType; this.tokenHash = tokenHash; this.expiresAt = expiresAt; }
    public String getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getTokenType() { return tokenType; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isUsableAt(Instant now) { return consumedAt == null && expiresAt.isAfter(now); }
    public void consume(Instant now) { consumedAt = now; }
}
