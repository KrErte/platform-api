-- Vault key escrow: store encrypted copy of user's vault key on server
ALTER TABLE users ADD COLUMN encrypted_vault_key TEXT;
ALTER TABLE users ADD COLUMN vault_key_escrowed_at TIMESTAMPTZ;

-- Shared vault access tokens for trusted contacts
CREATE TABLE shared_vault_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    handover_request_id UUID NOT NULL REFERENCES handover_requests(id) ON DELETE CASCADE,
    trusted_contact_id UUID NOT NULL REFERENCES trusted_contacts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ,
    access_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_svt_hash ON shared_vault_tokens(token_hash) WHERE revoked_at IS NULL;
CREATE INDEX idx_svt_user ON shared_vault_tokens(user_id);
