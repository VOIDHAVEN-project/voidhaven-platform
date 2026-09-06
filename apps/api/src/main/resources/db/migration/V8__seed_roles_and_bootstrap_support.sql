-- Ensures the role catalog exists on a freshly migrated database, even before any
-- application-level bootstrap has run. Idempotent: safe to apply on a database that
-- already has these roles (e.g. local/candidate environments seeded another way).
INSERT INTO roles (code) VALUES ('ADMIN')       ON DUPLICATE KEY UPDATE code = code;
INSERT INTO roles (code) VALUES ('SUPER_ADMIN') ON DUPLICATE KEY UPDATE code = code;
INSERT INTO roles (code) VALUES ('PARTNER')     ON DUPLICATE KEY UPDATE code = code;
