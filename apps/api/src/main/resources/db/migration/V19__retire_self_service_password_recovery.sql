UPDATE activation_tokens
SET consumed_at = COALESCE(consumed_at, CURRENT_TIMESTAMP(6))
WHERE token_type IN ('RECOVERY_EMAIL_VERIFY', 'PASSWORD_RESET_SELF_SERVICE');

DELETE FROM account_recovery_emails;

DELETE FROM auth_rate_limit_events
WHERE scope IN ('PASSWORD_RESET_LOGIN', 'PASSWORD_RESET_NETWORK');

