-- Add separate IV column for title encryption
ALTER TABLE vault_entries ADD COLUMN title_iv VARCHAR(255);

-- Backfill existing rows: copy encryption_iv to title_iv (won't decrypt correctly but prevents null errors)
UPDATE vault_entries SET title_iv = encryption_iv WHERE title_iv IS NULL;

-- Make it NOT NULL after backfill
ALTER TABLE vault_entries ALTER COLUMN title_iv SET NOT NULL;
