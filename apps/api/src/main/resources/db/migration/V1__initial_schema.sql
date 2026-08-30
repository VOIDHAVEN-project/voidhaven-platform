CREATE TABLE accounts (
  id CHAR(26) PRIMARY KEY,
  login_id VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  qualification_status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CHECK (qualification_status IN ('PENDING','ACTIVE','SUSPENDED','REVOKED'))
);

CREATE TABLE roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE
);
CREATE TABLE account_roles (
  account_id CHAR(26) NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (account_id, role_id),
  CONSTRAINT fk_account_roles_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT fk_account_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
);
CREATE TABLE partner_profiles (
  id CHAR(26) PRIMARY KEY,
  account_id CHAR(26) NOT NULL UNIQUE,
  display_name VARCHAR(120) NOT NULL,
  content_quota TINYINT NOT NULL DEFAULT 3,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_partner_profile_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CHECK (content_quota BETWEEN 0 AND 5)
);
CREATE TABLE activation_tokens (
  id CHAR(26) PRIMARY KEY,
  account_id CHAR(26) NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMP(6) NOT NULL,
  consumed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_activation_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE TABLE content_items (
  id CHAR(26) PRIMARY KEY,
  kind VARCHAR(32) NOT NULL,
  owner_partner_profile_id CHAR(26) NULL,
  workflow_status VARCHAR(32) NOT NULL,
  visibility VARCHAR(16) NOT NULL,
  record_state VARCHAR(16) NOT NULL,
  current_revision_id CHAR(26) NULL,
  published_revision_id CHAR(26) NULL,
  closed_at TIMESTAMP(6) NULL,
  archived_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_content_owner FOREIGN KEY (owner_partner_profile_id) REFERENCES partner_profiles(id),
  CHECK (kind IN ('COMPETITION','RESOURCE','RECRUITMENT')),
  CHECK (workflow_status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED','APPROVED','PUBLISHED','CLOSED','REJECTED')),
  CHECK (visibility IN ('PUBLIC','HIDDEN')),
  CHECK (record_state IN ('ACTIVE','ARCHIVED','DELETED'))
);
CREATE TABLE content_revisions (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  revision_number INT NOT NULL,
  title VARCHAR(240) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  body_json JSON NOT NULL,
  language_code VARCHAR(16) NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  source_name VARCHAR(240) NOT NULL,
  rights_basis VARCHAR(120) NOT NULL,
  details_json JSON NOT NULL,
  created_by_account_id CHAR(26) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (content_item_id, revision_number),
  CONSTRAINT fk_revision_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_revision_author FOREIGN KEY (created_by_account_id) REFERENCES accounts(id)
);
ALTER TABLE content_items ADD CONSTRAINT fk_content_current_revision FOREIGN KEY (current_revision_id) REFERENCES content_revisions(id);
ALTER TABLE content_items ADD CONSTRAINT fk_content_published_revision FOREIGN KEY (published_revision_id) REFERENCES content_revisions(id);

CREATE TABLE competition_revision_details (
  revision_id CHAR(26) PRIMARY KEY,
  deadline_at TIMESTAMP(6) NULL,
  deadline_date DATE NULL,
  official_rules_url VARCHAR(2048) NOT NULL,
  CONSTRAINT fk_competition_detail_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id)
);
CREATE TABLE recruitment_revision_details (
  revision_id CHAR(26) PRIMARY KEY,
  deadline_at TIMESTAMP(6) NULL,
  deadline_date DATE NULL,
  related_competition_id CHAR(26) NULL,
  follow_up_url VARCHAR(2048) NULL,
  CONSTRAINT fk_recruitment_detail_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_recruitment_detail_competition FOREIGN KEY (related_competition_id) REFERENCES content_items(id)
);
CREATE TABLE resource_revision_details (
  revision_id CHAR(26) PRIMARY KEY,
  resource_type VARCHAR(64) NOT NULL,
  CONSTRAINT fk_resource_detail_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id)
);

CREATE TABLE media_assets (
  id CHAR(26) PRIMARY KEY,
  storage_key VARCHAR(512) NOT NULL UNIQUE,
  media_type VARCHAR(64) NOT NULL,
  size_bytes BIGINT NOT NULL,
  width_px INT NULL,
  height_px INT NULL,
  owner_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_media_owner FOREIGN KEY (owner_account_id) REFERENCES accounts(id)
);
CREATE TABLE revision_media (
  revision_id CHAR(26) NOT NULL,
  media_asset_id CHAR(26) NOT NULL,
  sort_order SMALLINT NOT NULL,
  PRIMARY KEY (revision_id, media_asset_id),
  CONSTRAINT fk_revision_media_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_revision_media_asset FOREIGN KEY (media_asset_id) REFERENCES media_assets(id)
);
CREATE TABLE external_links (
  id CHAR(26) PRIMARY KEY,
  revision_id CHAR(26) NOT NULL,
  label VARCHAR(240) NOT NULL,
  url VARCHAR(2048) NOT NULL,
  CONSTRAINT fk_external_link_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id)
);
CREATE TABLE source_attributions (
  id CHAR(26) PRIMARY KEY,
  revision_id CHAR(26) NOT NULL,
  source_name VARCHAR(240) NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  CONSTRAINT fk_attribution_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id)
);
CREATE TABLE rights_declarations (
  id CHAR(26) PRIMARY KEY,
  revision_id CHAR(26) NOT NULL,
  declaration VARCHAR(500) NOT NULL,
  CONSTRAINT fk_rights_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id)
);

CREATE TABLE review_records (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  revision_id CHAR(26) NOT NULL,
  reviewer_account_id CHAR(26) NULL,
  decision VARCHAR(32) NOT NULL,
  reason VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_review_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_review_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_account_id) REFERENCES accounts(id)
);
CREATE TABLE content_ownership_transfers (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  from_partner_profile_id CHAR(26) NULL,
  to_partner_profile_id CHAR(26) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  operator_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_transfer_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_transfer_to_profile FOREIGN KEY (to_partner_profile_id) REFERENCES partner_profiles(id),
  CONSTRAINT fk_transfer_operator FOREIGN KEY (operator_account_id) REFERENCES accounts(id)
);
CREATE TABLE partner_quota_overrides (
  id CHAR(26) PRIMARY KEY,
  partner_profile_id CHAR(26) NOT NULL,
  quota TINYINT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  operator_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_quota_profile FOREIGN KEY (partner_profile_id) REFERENCES partner_profiles(id),
  CONSTRAINT fk_quota_operator FOREIGN KEY (operator_account_id) REFERENCES accounts(id),
  CHECK (quota BETWEEN 0 AND 5)
);
CREATE TABLE migration_records (
  id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  source_reference VARCHAR(500) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  operator_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_migration_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_migration_operator FOREIGN KEY (operator_account_id) REFERENCES accounts(id)
);

CREATE TABLE content_submissions (
  id CHAR(26) PRIMARY KEY,
  submission_type VARCHAR(32) NOT NULL,
  title VARCHAR(240) NOT NULL,
  message TEXT NOT NULL,
  link_url VARCHAR(2048) NULL,
  image_description VARCHAR(1000) NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  private_contact_ciphertext TEXT NULL,
  private_contact_key_id VARCHAR(100) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (submission_type IN ('CONTENT','FEEDBACK','COPYRIGHT'))
);
CREATE TABLE partner_applications (
  id CHAR(26) PRIMARY KEY,
  display_name VARCHAR(120) NOT NULL,
  introduction TEXT NOT NULL,
  portfolio_url VARCHAR(2048) NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  private_contact_ciphertext TEXT NULL,
  private_contact_key_id VARCHAR(100) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE private_contact_records (
  id CHAR(26) PRIMARY KEY,
  subject_type VARCHAR(32) NOT NULL,
  subject_id CHAR(26) NOT NULL,
  ciphertext TEXT NOT NULL,
  key_id VARCHAR(100) NOT NULL,
  delete_after TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_private_contact_cleanup (delete_after)
);
CREATE TABLE idempotency_records (
  id CHAR(26) PRIMARY KEY,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  response_status SMALLINT NOT NULL,
  response_body JSON NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (idempotency_key),
  INDEX idx_idempotency_cleanup (expires_at)
);
CREATE TABLE community_gate_events (
  id CHAR(26) PRIMARY KEY,
  anonymous_subject_hash CHAR(64) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_community_cleanup (expires_at)
);
CREATE TABLE audit_logs (
  id CHAR(26) PRIMARY KEY,
  actor_account_id CHAR(26) NULL,
  actor_role VARCHAR(32) NOT NULL,
  action VARCHAR(100) NOT NULL,
  object_type VARCHAR(64) NOT NULL,
  object_id CHAR(26) NOT NULL,
  before_summary JSON NULL,
  after_summary JSON NULL,
  reason VARCHAR(1000) NULL,
  request_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_audit_object (object_type, object_id),
  INDEX idx_audit_created_at (created_at)
);
CREATE TABLE retention_runs (
  id CHAR(26) PRIMARY KEY,
  task_name VARCHAR(100) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  affected_count INT NOT NULL,
  detail_summary VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
