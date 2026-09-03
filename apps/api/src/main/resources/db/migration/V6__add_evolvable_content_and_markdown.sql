ALTER TABLE content_items
  ADD COLUMN module_key VARCHAR(64) NULL AFTER kind;

UPDATE content_items
SET module_key = CASE kind
  WHEN 'COMPETITION' THEN 'competition'
  WHEN 'RECRUITMENT' THEN 'recruitment'
  ELSE 'resource'
END;

ALTER TABLE content_items
  MODIFY COLUMN module_key VARCHAR(64) NOT NULL;

CREATE INDEX idx_content_items_module_public ON content_items (module_key, visibility, record_state, id);

ALTER TABLE content_revisions
  ADD COLUMN markdown_source MEDIUMTEXT NULL AFTER body_json,
  ADD COLUMN document_version INT NOT NULL DEFAULT 1 AFTER markdown_source,
  ADD COLUMN schema_version INT NOT NULL DEFAULT 1 AFTER document_version,
  ADD COLUMN schema_snapshot_json JSON NULL AFTER schema_version,
  ADD COLUMN metadata_json JSON NULL AFTER details_json;

UPDATE content_revisions
SET metadata_json = details_json,
    schema_snapshot_json = JSON_OBJECT('legacy', TRUE, 'version', 1);

ALTER TABLE content_revisions
  MODIFY COLUMN source_url VARCHAR(2048) NULL;

CREATE TABLE revision_filter_values (
  revision_id CHAR(26) NOT NULL,
  field_key VARCHAR(64) NOT NULL,
  value_type VARCHAR(16) NOT NULL,
  value_text VARCHAR(512) NULL,
  value_number DECIMAL(20, 6) NULL,
  value_date DATE NULL,
  PRIMARY KEY (revision_id, field_key),
  CONSTRAINT fk_filter_value_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  INDEX idx_filter_value_text (field_key, value_text),
  INDEX idx_filter_value_number (field_key, value_number),
  INDEX idx_filter_value_date (field_key, value_date)
);

CREATE TABLE revision_content_relations (
  revision_id CHAR(26) NOT NULL,
  target_content_item_id CHAR(26) NOT NULL,
  relation_key VARCHAR(64) NOT NULL,
  PRIMARY KEY (revision_id, target_content_item_id, relation_key),
  CONSTRAINT fk_relation_revision FOREIGN KEY (revision_id) REFERENCES content_revisions(id),
  CONSTRAINT fk_relation_target FOREIGN KEY (target_content_item_id) REFERENCES content_items(id),
  INDEX idx_relation_target (target_content_item_id, relation_key)
);
