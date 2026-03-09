ALTER TABLE users ADD COLUMN password_reset_token UUID;
ALTER TABLE users ADD COLUMN password_reset_token_expires_at TIMESTAMPTZ;
