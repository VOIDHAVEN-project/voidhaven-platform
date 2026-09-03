ALTER TABLE content_items
  ADD COLUMN publication_status VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER workflow_status;

UPDATE content_items
SET publication_status = 'ACTIVE'
WHERE published_revision_id IS NOT NULL AND record_state = 'ACTIVE';

ALTER TABLE content_items
  ADD CONSTRAINT chk_content_publication_status
  CHECK (publication_status IN ('NONE', 'ACTIVE', 'CLOSED'));
