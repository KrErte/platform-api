-- Remove FREE tier: convert existing FREE users to NONE (locked)
UPDATE subscriptions SET plan = 'NONE' WHERE plan = 'FREE';

-- Update default value for new subscriptions
ALTER TABLE subscriptions ALTER COLUMN plan SET DEFAULT 'NONE';
