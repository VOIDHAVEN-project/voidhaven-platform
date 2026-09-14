INSERT INTO accounts (id, login_id, password_hash, qualification_status)
VALUES ('01JAAAAAAAAAAAAACCOUNT0001', 'super.admin', '!ACTIVATION_REQUIRED!', 'ACTIVE');
INSERT INTO accounts (id, login_id, password_hash, qualification_status)
VALUES ('01JAAAAAAAAAAAAACCOUNT0002', 'keep.pending', '!ACTIVATION_REQUIRED!', 'PENDING');
INSERT INTO idempotency_records (id, idempotency_key, request_hash, response_status, response_body, expires_at)
VALUES (
  '01JAAAAAAAAAAAAIDEMPOT0001',
  'legacy-admin-create',
  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  201,
  CAST('{"id":"01JAAAAAAAAAAAAACCOUNT0002","activationToken":"secret-should-be-purged"}' AS JSON),
  DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 DAY)
);
INSERT INTO idempotency_records (id, idempotency_key, request_hash, response_status, response_body, expires_at)
VALUES (
  '01JAAAAAAAAAAAAIDEMPOT0002',
  'harmless-write',
  'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
  200,
  CAST('{"ok":true}' AS JSON),
  DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 DAY)
);
