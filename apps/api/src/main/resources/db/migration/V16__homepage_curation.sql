CREATE TABLE homepage_placements (
  id CHAR(26) PRIMARY KEY,
  slot VARCHAR(32) NOT NULL,
  content_item_id CHAR(26) NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  starts_at TIMESTAMP(6) NULL,
  ends_at TIMESTAMP(6) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by_account_id CHAR(26) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_homepage_placement_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_homepage_placement_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_homepage_placement_slot CHECK (slot IN ('FOCUS','RECRUITMENT','RESOURCE')),
  UNIQUE KEY uq_homepage_slot_content (slot, content_item_id),
  INDEX idx_homepage_active (slot, active, starts_at, ends_at, sort_order)
);
