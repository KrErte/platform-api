-- Performance indexes for frequently queried columns
-- Uses IF NOT EXISTS for safety (some may already exist from earlier migrations)

-- vault_entries: category filtering without user_id prefix
CREATE INDEX IF NOT EXISTS idx_vault_entries_category_id ON vault_entries(category_id);

-- trusted_contacts: invite token lookup for accept-invite flow
CREATE INDEX IF NOT EXISTS idx_trusted_contacts_invite_token ON trusted_contacts(invite_token) WHERE invite_token IS NOT NULL;

-- handover_requests: lookup by trusted_contact_id
CREATE INDEX IF NOT EXISTS idx_handover_requests_contact ON handover_requests(trusted_contact_id);
