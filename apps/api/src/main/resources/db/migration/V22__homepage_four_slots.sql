-- Four homepage slots and a singleton curation version for concurrent saves.
-- Existing FOCUS / RECRUITMENT / RESOURCE rows are kept as-is so live picks are not dropped.

ALTER TABLE homepage_placements
  DROP CHECK chk_homepage_placement_slot,
  ADD CONSTRAINT chk_homepage_placement_slot
    CHECK (slot IN ('FOCUS', 'COMPETITION', 'RECRUITMENT', 'RESOURCE'));

CREATE TABLE homepage_curation (
  id CHAR(26) PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  updated_by_account_id CHAR(26) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_homepage_curation_actor FOREIGN KEY (updated_by_account_id) REFERENCES accounts(id)
);

INSERT INTO homepage_curation (id, version) VALUES ('01JX000H0MEPAGESTATE000001', 0);
