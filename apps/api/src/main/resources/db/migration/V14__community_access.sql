CREATE TABLE community_entries (
  id CHAR(26) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NULL,
  entry_type VARCHAR(32) NOT NULL,
  public_value VARCHAR(2048) NULL,
  private_value_ciphertext TEXT NULL,
  private_value_key_id VARCHAR(100) NULL,
  media_asset_id CHAR(26) NULL,
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  primary_slot TINYINT GENERATED ALWAYS AS (CASE WHEN active = TRUE AND is_primary = TRUE THEN 1 ELSE NULL END) STORED,
  created_by_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_community_entry_media FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
  CONSTRAINT fk_community_entry_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_community_entry_type CHECK (entry_type IN ('URL','WECHAT','QQ_GROUP','QR_CODE','TEXT')),
  CONSTRAINT uq_community_primary UNIQUE (primary_slot),
  INDEX idx_community_entry_public (active, is_primary, sort_order)
);
CREATE TABLE community_invite_batches (
  id CHAR(26) PRIMARY KEY,
  label VARCHAR(120) NOT NULL,
  code_hmac CHAR(64) NOT NULL UNIQUE,
  valid_from TIMESTAMP(6) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  max_uses INT NOT NULL,
  use_count INT NOT NULL DEFAULT 0,
  state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_by_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  revoked_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_community_invite_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_community_invite_state CHECK (state IN ('ACTIVE','REVOKED','EXPIRED')),
  CONSTRAINT chk_community_invite_uses CHECK (max_uses BETWEEN 1 AND 10000 AND use_count >= 0 AND use_count <= max_uses),
  INDEX idx_community_invite_active (state, valid_from, expires_at)
);

CREATE TABLE community_access_grants (
  id CHAR(26) PRIMARY KEY,
  invite_batch_id CHAR(26) NOT NULL,
  grant_hash CHAR(64) NOT NULL UNIQUE,
  anonymous_subject_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_community_grant_batch FOREIGN KEY (invite_batch_id) REFERENCES community_invite_batches(id),
  INDEX idx_community_grant_lookup (grant_hash, expires_at),
  INDEX idx_community_grant_cleanup (expires_at)
);
