ALTER TABLE content_revisions
  ADD COLUMN original_title VARCHAR(240) NULL AFTER title,
  ADD COLUMN author_display_name VARCHAR(120) NOT NULL DEFAULT '未公开署名' AFTER rights_basis,
  ADD COLUMN reprint_permission VARCHAR(32) NOT NULL DEFAULT 'NOT_ALLOWED' AFTER author_display_name,
  ADD COLUMN adaptation_permission VARCHAR(32) NOT NULL DEFAULT 'NOT_ALLOWED' AFTER reprint_permission;
