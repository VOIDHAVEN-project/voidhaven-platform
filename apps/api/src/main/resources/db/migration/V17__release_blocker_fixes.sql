CREATE TABLE public_status_query_rate_limit_events (
  id CHAR(26) PRIMARY KEY,
  subject_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_public_status_query_lookup (subject_hash, expires_at),
  INDEX idx_public_status_query_cleanup (expires_at)
);

ALTER TABLE manual_delivery_records
  DROP CHECK chk_manual_delivery_subject,
  ADD CONSTRAINT chk_manual_delivery_subject
    CHECK (subject_type IN ('ACCOUNT','PARTNER_APPLICATION','SUBMISSION'));

ALTER TABLE partner_applications
  ADD COLUMN provisioned_account_id CHAR(26) NULL AFTER activation_delivered_at,
  ADD CONSTRAINT fk_partner_application_account
    FOREIGN KEY (provisioned_account_id) REFERENCES accounts(id),
  ADD CONSTRAINT uq_partner_application_account UNIQUE (provisioned_account_id);
