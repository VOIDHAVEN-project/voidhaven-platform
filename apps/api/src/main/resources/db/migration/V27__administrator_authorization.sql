CREATE TABLE admin_authorizations (
  account_id CHAR(26) NOT NULL PRIMARY KEY,
  grants JSON NOT NULL,
  long_term BOOLEAN NOT NULL,
  expires_at DATETIME(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_admin_authorization_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);
-- No grants are inferred from legacy ADMIN roles. SUPER_ADMIN explicitly reassigns duties.
