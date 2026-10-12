-- Public catalog default sort plus an optional operator-defined order.
-- Historical content without new metadata remains listable; these tables only affect order.

CREATE TABLE catalog_sort_settings (
  kind VARCHAR(32) PRIMARY KEY,
  default_sort VARCHAR(16) NOT NULL DEFAULT 'DEFAULT',
  version BIGINT NOT NULL DEFAULT 0,
  updated_by_account_id CHAR(26) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_catalog_sort_actor FOREIGN KEY (updated_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_catalog_sort_kind CHECK (kind IN ('COMPETITION', 'RESOURCE', 'RECRUITMENT')),
  CONSTRAINT chk_catalog_sort_mode CHECK (default_sort IN ('DEFAULT', 'DEADLINE', 'UPDATED'))
);

INSERT INTO catalog_sort_settings (kind, default_sort) VALUES
  ('COMPETITION', 'DEFAULT'),
  ('RECRUITMENT', 'DEFAULT'),
  ('RESOURCE', 'DEFAULT');

CREATE TABLE catalog_order_entries (
  id CHAR(26) PRIMARY KEY,
  kind VARCHAR(32) NOT NULL,
  content_item_id CHAR(26) NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_catalog_order_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_catalog_order_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_catalog_order_kind CHECK (kind IN ('COMPETITION', 'RESOURCE', 'RECRUITMENT')),
  UNIQUE KEY uq_catalog_order_kind_content (kind, content_item_id),
  INDEX idx_catalog_order_active (kind, active, sort_order, content_item_id)
);

CREATE INDEX idx_content_items_kind_updated
  ON content_items (kind, visibility, record_state, updated_at, id);

CREATE INDEX idx_competition_deadline_date
  ON competition_revision_details (deadline_date);

CREATE INDEX idx_recruitment_deadline_date
  ON recruitment_revision_details (deadline_date);
