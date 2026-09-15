CREATE TABLE content_action_media_reservations (
  media_asset_id CHAR(26) PRIMARY KEY,
  content_item_id CHAR(26) NOT NULL,
  owner_account_id CHAR(26) NOT NULL,
  alt_text VARCHAR(240) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  consumed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_action_media_asset FOREIGN KEY (media_asset_id) REFERENCES media_assets(id),
  CONSTRAINT fk_action_media_content FOREIGN KEY (content_item_id) REFERENCES content_items(id),
  CONSTRAINT fk_action_media_owner FOREIGN KEY (owner_account_id) REFERENCES accounts(id),
  INDEX idx_action_media_expiry (expires_at, consumed_at),
  INDEX idx_action_media_content (content_item_id, owner_account_id)
);
