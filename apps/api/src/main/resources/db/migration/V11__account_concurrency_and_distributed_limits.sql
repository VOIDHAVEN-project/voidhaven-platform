ALTER TABLE accounts
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER qualification_status;

ALTER TABLE idempotency_records
  ADD COLUMN response_replayable BOOLEAN NOT NULL DEFAULT TRUE AFTER response_body;

DELETE FROM idempotency_records
 WHERE CAST(response_body AS CHAR) LIKE '%activationToken%';

CREATE TABLE rate_limit_locks (
  scope VARCHAR(32) NOT NULL,
  subject_hash CHAR(64) NOT NULL,
  PRIMARY KEY (scope, subject_hash)
);

CREATE TABLE public_write_rate_limit_events (
  id CHAR(26) PRIMARY KEY,
  scope VARCHAR(32) NOT NULL,
  subject_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (scope IN ('PUBLIC_WRITE_NETWORK')),
  INDEX idx_public_write_rate_limit_lookup (scope, subject_hash, expires_at),
  INDEX idx_public_write_rate_limit_cleanup (expires_at)
);

ALTER TABLE media_deletion_jobs
  ADD COLUMN locked_by VARCHAR(64) NULL AFTER last_error,
  ADD COLUMN locked_until TIMESTAMP(6) NULL AFTER locked_by;

CREATE INDEX idx_media_deletion_claim ON media_deletion_jobs (state, next_attempt_at, locked_until);
