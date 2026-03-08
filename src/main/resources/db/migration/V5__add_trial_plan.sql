-- Set default plan to TRIAL for new subscriptions
ALTER TABLE subscriptions ALTER COLUMN plan SET DEFAULT 'TRIAL';

-- Existing NONE users stay NONE (they do not get a trial)
