ALTER TABLE content_items
  ADD COLUMN operational_status VARCHAR(16) NULL AFTER publication_status,
  ADD CONSTRAINT chk_content_operational_status
    CHECK (operational_status IS NULL OR operational_status IN ('OPEN','PAUSED','FILLED','EXPIRED','CANCELLED'));

UPDATE content_items
SET operational_status = CASE
  WHEN publication_status = 'CLOSED' THEN 'EXPIRED'
  ELSE 'OPEN'
END
WHERE kind = 'RECRUITMENT';

CREATE INDEX idx_content_operational_status
  ON content_items (kind, operational_status, visibility, record_state);

CREATE TABLE content_action_entries (
  id CHAR(26) PRIMARY KEY,
  revision_id CHAR(26) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  access_level VARCHAR(32) NOT NULL,
  label VARCHAR(120) NOT NULL,
  public_value VARCHAR(2048) NULL,
  private_value_ciphertext TEXT NULL,
  private_value_key_id VARCHAR(100) NULL,
  media_asset_id CHAR(26) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_content_action_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_content_action_media FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
  CONSTRAINT chk_content_action_type CHECK (action_type IN ('APPLICATION_URL','PUBLIC_EMAIL','QQ','WECHAT','QQ_GROUP','WECHAT_GROUP','QR_CODE')),
  CONSTRAINT chk_content_action_access CHECK (access_level IN ('PUBLIC','COMMUNITY_GATED')),
  INDEX idx_content_action_revision (revision_id, enabled, sort_order)
);

INSERT INTO content_action_entries
  (id, revision_id, action_type, access_level, label, public_value, enabled, sort_order)
SELECT UPPER(SUBSTRING(SHA2(CONCAT('action:', revision_id), 256), 1, 26)),
       revision_id, 'APPLICATION_URL', 'PUBLIC', '立即了解或报名', follow_up_url, TRUE, 0
FROM recruitment_revision_details
WHERE follow_up_url IS NOT NULL AND TRIM(follow_up_url) <> '';

CREATE TABLE content_verification_records (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  detail_summary VARCHAR(1000) NULL,
  checked_by_account_id CHAR(26) NULL,
  checked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  next_check_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_content_verification_item FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_content_verification_actor FOREIGN KEY (checked_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_content_verification_outcome CHECK (outcome IN ('VALID','INVALID','NEEDS_REVIEW')),
  INDEX idx_content_verification_due (next_check_at, outcome),
  INDEX idx_content_verification_item (content_item_id, checked_at)
);
