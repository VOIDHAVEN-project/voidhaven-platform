ALTER TABLE partner_profiles
    ADD COLUMN member_quota INT NOT NULL DEFAULT 0,
    ADD COLUMN security_epoch BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_partner_member_quota CHECK (member_quota >= 0);

CREATE TABLE partner_members (
    account_id CHAR(26) NOT NULL PRIMARY KEY,
    partner_profile_id CHAR(26) NOT NULL,
    created_by_account_id CHAR(26) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_member_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_member_profile FOREIGN KEY (partner_profile_id) REFERENCES partner_profiles(id),
    CONSTRAINT fk_member_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id),
    INDEX idx_members_profile (partner_profile_id, account_id)
);

ALTER TABLE activation_tokens
    ADD COLUMN organization_id CHAR(26) NULL,
    ADD COLUMN organization_security_epoch BIGINT NULL,
    ADD CONSTRAINT fk_credential_organization FOREIGN KEY (organization_id) REFERENCES partner_profiles(id);
