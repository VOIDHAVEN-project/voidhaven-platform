ALTER TABLE content_submissions
  ADD COLUMN reviewed_by_account_id CHAR(26) NULL AFTER state,
  ADD COLUMN review_reason VARCHAR(1000) NULL AFTER reviewed_by_account_id,
  ADD COLUMN reviewed_at TIMESTAMP(6) NULL AFTER review_reason,
  ADD CONSTRAINT fk_submission_reviewer FOREIGN KEY (reviewed_by_account_id) REFERENCES accounts(id);

ALTER TABLE partner_applications
  ADD COLUMN reviewed_by_account_id CHAR(26) NULL AFTER state,
  ADD COLUMN review_reason VARCHAR(1000) NULL AFTER reviewed_by_account_id,
  ADD COLUMN reviewed_at TIMESTAMP(6) NULL AFTER review_reason,
  ADD CONSTRAINT fk_partner_application_reviewer FOREIGN KEY (reviewed_by_account_id) REFERENCES accounts(id);
