ALTER TABLE activation_tokens
  DROP CHECK chk_activation_token_type,
  ADD CONSTRAINT chk_activation_token_type
    CHECK (token_type IN ('ACTIVATION','PASSWORD_RESET','PASSWORD_RESET_SELF_SERVICE','RECOVERY_EMAIL_VERIFY'));

ALTER TABLE auth_rate_limit_events
  DROP CHECK auth_rate_limit_events_chk_1,
  ADD CONSTRAINT chk_auth_rate_limit_scope
    CHECK (scope IN ('LOGIN_ID','LOGIN_NETWORK','ACTIVATION_NETWORK','PASSWORD_RESET_LOGIN','PASSWORD_RESET_NETWORK'));

CREATE TABLE account_recovery_emails (
  account_id CHAR(26) PRIMARY KEY,
  verified_email_ciphertext TEXT NULL,
  verified_email_key_id VARCHAR(100) NULL,
  verified_at TIMESTAMP(6) NULL,
  pending_email_ciphertext TEXT NULL,
  pending_email_key_id VARCHAR(100) NULL,
  pending_requested_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_recovery_email_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT chk_recovery_email_verified_pair CHECK (
    (verified_email_ciphertext IS NULL AND verified_email_key_id IS NULL AND verified_at IS NULL)
    OR
    (verified_email_ciphertext IS NOT NULL AND verified_email_key_id IS NOT NULL AND verified_at IS NOT NULL)
  ),
  CONSTRAINT chk_recovery_email_pending_pair CHECK (
    (pending_email_ciphertext IS NULL AND pending_email_key_id IS NULL AND pending_requested_at IS NULL)
    OR
    (pending_email_ciphertext IS NOT NULL AND pending_email_key_id IS NOT NULL AND pending_requested_at IS NOT NULL)
  )
);
