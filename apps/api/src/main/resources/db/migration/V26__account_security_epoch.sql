ALTER TABLE accounts ADD COLUMN security_epoch BIGINT NOT NULL DEFAULT 0;
ALTER TABLE activation_tokens
  ADD COLUMN target_security_epoch BIGINT NULL,
  ADD COLUMN issuer_account_id CHAR(26) NULL,
  ADD COLUMN issuer_security_epoch BIGINT NULL;
CREATE INDEX idx_activation_issuer ON activation_tokens (issuer_account_id, consumed_at);
