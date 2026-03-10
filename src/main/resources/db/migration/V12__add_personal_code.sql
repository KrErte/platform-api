ALTER TABLE users ADD COLUMN personal_code VARCHAR(20);
CREATE UNIQUE INDEX idx_users_personal_code ON users (personal_code) WHERE personal_code IS NOT NULL;
