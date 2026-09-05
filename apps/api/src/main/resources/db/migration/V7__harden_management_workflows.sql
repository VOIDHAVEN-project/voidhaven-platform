ALTER TABLE content_items
  ADD COLUMN submitted_at TIMESTAMP(6) NULL AFTER archived_at;

ALTER TABLE media_assets
  ADD COLUMN lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER owner_account_id,
  ADD CONSTRAINT chk_media_lifecycle_state CHECK (lifecycle_state IN ('ACTIVE','DELETION_PENDING','DELETED'));

CREATE TABLE media_deletion_jobs (
  id CHAR(26) PRIMARY KEY,
  media_asset_id CHAR(26) NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_error VARCHAR(500) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_media_deletion_asset FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
  CONSTRAINT uq_media_deletion_asset UNIQUE (media_asset_id),
  CHECK (state IN ('PENDING','FAILED','SUCCEEDED')),
  INDEX idx_media_deletion_due (state, next_attempt_at)
);

CREATE TABLE auth_rate_limit_events (
  id CHAR(26) PRIMARY KEY,
  scope VARCHAR(32) NOT NULL,
  subject_hash CHAR(64) NOT NULL,
  outcome VARCHAR(16) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (scope IN ('LOGIN_ID','LOGIN_NETWORK','ACTIVATION_NETWORK')),
  CHECK (outcome IN ('ATTEMPT','FAILURE','SUCCESS')),
  INDEX idx_auth_rate_limit_lookup (scope, subject_hash, expires_at),
  INDEX idx_auth_rate_limit_cleanup (expires_at)
);

UPDATE content_submissions
SET state = 'PENDING_REVIEW', reviewed_by_account_id = NULL, review_reason = NULL, reviewed_at = NULL
WHERE state = 'CHANGES_REQUESTED';

UPDATE partner_applications
SET state = 'PENDING_REVIEW', reviewed_by_account_id = NULL, review_reason = NULL, reviewed_at = NULL
WHERE state = 'CHANGES_REQUESTED';

CREATE INDEX idx_content_management_page ON content_items (workflow_status, record_state, id);
CREATE INDEX idx_submission_management_page ON content_submissions (state, created_at, id);
CREATE INDEX idx_partner_application_management_page ON partner_applications (state, created_at, id);
