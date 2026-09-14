ALTER TABLE content_submissions
  ADD COLUMN access_code_hash CHAR(64) NULL AFTER image_description,
  ADD COLUMN assignee_account_id CHAR(26) NULL AFTER reviewed_at,
  ADD COLUMN due_at TIMESTAMP(6) NULL AFTER assignee_account_id,
  ADD COLUMN public_result VARCHAR(2000) NULL AFTER due_at,
  ADD COLUMN resolved_at TIMESTAMP(6) NULL AFTER public_result,
  ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at,
  ADD CONSTRAINT fk_submission_assignee FOREIGN KEY (assignee_account_id) REFERENCES accounts(id),
  ADD UNIQUE INDEX uq_submission_access_code (access_code_hash),
  ADD INDEX idx_submission_tasks (state, due_at, created_at);

ALTER TABLE partner_applications
  ADD COLUMN access_code_hash CHAR(64) NULL AFTER portfolio_url,
  ADD COLUMN assignee_account_id CHAR(26) NULL AFTER reviewed_at,
  ADD COLUMN due_at TIMESTAMP(6) NULL AFTER assignee_account_id,
  ADD COLUMN public_result VARCHAR(2000) NULL AFTER due_at,
  ADD COLUMN activation_delivered_at TIMESTAMP(6) NULL AFTER public_result,
  ADD COLUMN activated_at TIMESTAMP(6) NULL AFTER activation_delivered_at,
  ADD COLUMN resolved_at TIMESTAMP(6) NULL AFTER activated_at,
  ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at,
  ADD CONSTRAINT fk_partner_application_assignee FOREIGN KEY (assignee_account_id) REFERENCES accounts(id),
  ADD UNIQUE INDEX uq_partner_application_access_code (access_code_hash),
  ADD INDEX idx_partner_application_tasks (state, due_at, created_at);

CREATE TABLE public_case_status_history (
  id CHAR(26) PRIMARY KEY,
  case_type VARCHAR(32) NOT NULL,
  case_id CHAR(26) NOT NULL,
  from_state VARCHAR(32) NULL,
  to_state VARCHAR(32) NOT NULL,
  public_message VARCHAR(2000) NULL,
  actor_account_id CHAR(26) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_public_case_history_actor FOREIGN KEY (actor_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_public_case_history_type CHECK (case_type IN ('SUBMISSION','PARTNER_APPLICATION')),
  INDEX idx_public_case_history_lookup (case_type, case_id, created_at)
);

-- Public-case states are independent from content-review PENDING_REVIEW.
-- Historical rows keep a one-time alias mapping; content_items.workflow_status is unchanged.
UPDATE content_submissions
SET state = CASE
  WHEN state = 'PENDING_REVIEW' THEN 'RECEIVED'
  WHEN state = 'APPROVED' THEN 'RESOLVED'
  ELSE state
END;

UPDATE partner_applications
SET state = 'RECEIVED'
WHERE state = 'PENDING_REVIEW';

ALTER TABLE content_submissions
  ALTER COLUMN state SET DEFAULT 'RECEIVED',
  ADD CONSTRAINT chk_submission_public_state
    CHECK (state IN ('RECEIVED','IN_PROGRESS','WAITING_FOR_REQUESTER','RESOLVED','REJECTED','CLOSED'));

ALTER TABLE partner_applications
  ALTER COLUMN state SET DEFAULT 'RECEIVED',
  ADD CONSTRAINT chk_partner_application_public_state
    CHECK (state IN ('RECEIVED','IN_REVIEW','WAITING_FOR_APPLICANT','APPROVED','ACTIVATION_PENDING','ACTIVATED','REJECTED','CLOSED'));
