ALTER TABLE users ADD COLUMN totp_backup_codes TEXT;
ALTER TABLE users ADD COLUMN notify_expiration_reminders BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN notify_inactivity_warnings BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN notify_security_alerts BOOLEAN NOT NULL DEFAULT true;
