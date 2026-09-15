ALTER TABLE content_action_entries
  ADD COLUMN presentation_type VARCHAR(16) NULL AFTER revision_id,
  ADD COLUMN contact_channel VARCHAR(16) NULL AFTER presentation_type;

UPDATE content_action_entries
SET presentation_type = CASE action_type
      WHEN 'APPLICATION_URL' THEN 'LINK'
      WHEN 'QR_CODE' THEN 'QR_CODE'
      ELSE 'TEXT'
    END,
    contact_channel = CASE action_type
      WHEN 'APPLICATION_URL' THEN 'URL'
      WHEN 'PUBLIC_EMAIL' THEN 'EMAIL'
      WHEN 'QQ' THEN 'QQ'
      WHEN 'QQ_GROUP' THEN 'QQ'
      WHEN 'WECHAT' THEN 'WECHAT'
      WHEN 'WECHAT_GROUP' THEN 'WECHAT'
      ELSE 'OTHER'
    END;

ALTER TABLE content_action_entries
  DROP CHECK chk_content_action_type,
  MODIFY COLUMN presentation_type VARCHAR(16) NOT NULL,
  MODIFY COLUMN contact_channel VARCHAR(16) NOT NULL,
  ADD CONSTRAINT chk_content_action_presentation CHECK (presentation_type IN ('LINK','QR_CODE','TEXT')),
  ADD CONSTRAINT chk_content_action_channel CHECK (contact_channel IN ('URL','EMAIL','WECHAT','QQ','PHONE','OTHER')),
  DROP COLUMN action_type;

ALTER TABLE community_entries
  ADD COLUMN presentation_type VARCHAR(16) NULL AFTER description,
  ADD COLUMN contact_channel VARCHAR(16) NULL AFTER presentation_type,
  ADD COLUMN access_level VARCHAR(32) NOT NULL DEFAULT 'COMMUNITY_GATED' AFTER contact_channel;

UPDATE community_entries
SET presentation_type = CASE entry_type
      WHEN 'URL' THEN 'LINK'
      WHEN 'QR_CODE' THEN 'QR_CODE'
      ELSE 'TEXT'
    END,
    contact_channel = CASE entry_type
      WHEN 'URL' THEN 'URL'
      WHEN 'WECHAT' THEN 'WECHAT'
      WHEN 'QQ_GROUP' THEN 'QQ'
      ELSE 'OTHER'
    END;

ALTER TABLE community_entries
  DROP CHECK chk_community_entry_type,
  MODIFY COLUMN presentation_type VARCHAR(16) NOT NULL,
  MODIFY COLUMN contact_channel VARCHAR(16) NOT NULL,
  ADD CONSTRAINT chk_community_entry_presentation CHECK (presentation_type IN ('LINK','QR_CODE','TEXT')),
  ADD CONSTRAINT chk_community_entry_channel CHECK (contact_channel IN ('URL','EMAIL','WECHAT','QQ','PHONE','OTHER')),
  ADD CONSTRAINT chk_community_entry_access CHECK (access_level IN ('PUBLIC','COMMUNITY_GATED')),
  DROP COLUMN entry_type;

