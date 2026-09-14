-- V11 data-safety checks. Run against a restored copy, never as a reason to DROP data.
-- Expected: Flyway 11 applied; business row counts match the pre-cutover baseline;
-- activation-token idempotency cache is empty; new lock tables exist.

SELECT version, success, checksum
  FROM flyway_schema_history
 WHERE version = '11';

SELECT COUNT(*) AS account_count FROM accounts;
SELECT COUNT(*) AS content_count FROM content_items;
SELECT COUNT(*) AS session_count FROM SPRING_SESSION;
SELECT COUNT(*) AS media_deletion_job_count FROM media_deletion_jobs;

SELECT COUNT(*) AS activation_token_idempotency_rows
  FROM idempotency_records
 WHERE CAST(response_body AS CHAR) LIKE '%activationToken%';

SELECT COUNT(*) AS accounts_missing_version
  FROM accounts
 WHERE version IS NULL;

SELECT COUNT(*) AS rate_limit_lock_rows FROM rate_limit_locks;
SELECT COUNT(*) AS public_write_rate_limit_event_rows FROM public_write_rate_limit_events;

SHOW COLUMNS FROM media_deletion_jobs LIKE 'locked_by';
SELECT COUNT(*) AS replayable_flag_present
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 'idempotency_records'
   AND column_name = 'response_replayable';
