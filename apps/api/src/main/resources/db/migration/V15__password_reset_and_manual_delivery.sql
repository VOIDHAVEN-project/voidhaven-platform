ALTER TABLE activation_tokens
  ADD COLUMN token_type VARCHAR(32) NOT NULL DEFAULT 'ACTIVATION' AFTER account_id,
  ADD CONSTRAINT chk_activation_token_type CHECK (token_type IN ('ACTIVATION','PASSWORD_RESET'));

ALTER TABLE accounts
  ADD COLUMN password_changed_at TIMESTAMP(6) NULL AFTER password_hash;

CREATE TABLE manual_delivery_records (
  id CHAR(26) PRIMARY KEY,
  subject_type VARCHAR(32) NOT NULL,
  subject_id CHAR(26) NOT NULL,
  delivery_type VARCHAR(32) NOT NULL,
  delivered_by_account_id CHAR(26) NOT NULL,
  delivered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  note VARCHAR(1000) NULL,
  CONSTRAINT fk_manual_delivery_actor FOREIGN KEY (delivered_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_manual_delivery_subject CHECK (subject_type IN ('ACCOUNT','PARTNER_APPLICATION')),
  CONSTRAINT chk_manual_delivery_type CHECK (delivery_type IN ('ACTIVATION','PASSWORD_RESET','STATUS_RESULT')),
  INDEX idx_manual_delivery_subject (subject_type, subject_id, delivered_at)
);
