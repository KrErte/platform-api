-- =============================================
-- USERS & AUTH
-- =============================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    date_of_birth DATE,
    country VARCHAR(3) NOT NULL DEFAULT 'EST',
    language VARCHAR(5) NOT NULL DEFAULT 'et',
    totp_secret VARCHAR(255),
    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verification_token UUID,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================
-- SUBSCRIPTION
-- =============================================
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    lemonsqueezy_subscription_id VARCHAR(255),
    lemonsqueezy_customer_id VARCHAR(255),
    current_period_start TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id)
);

-- =============================================
-- VAULT: Categories & Entries
-- =============================================
CREATE TABLE vault_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(50) NOT NULL UNIQUE,
    name_et VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    icon VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    field_template JSONB NOT NULL DEFAULT '[]'
);

CREATE TABLE vault_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES vault_categories(id),
    title VARCHAR(255) NOT NULL,
    encrypted_data TEXT NOT NULL,
    encryption_iv VARCHAR(255) NOT NULL,
    notes_encrypted TEXT,
    notes_iv VARCHAR(255),
    has_attachments BOOLEAN NOT NULL DEFAULT FALSE,
    is_complete BOOLEAN NOT NULL DEFAULT FALSE,
    last_reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vault_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vault_entry_id UUID NOT NULL REFERENCES vault_entries(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name_encrypted TEXT NOT NULL,
    file_name_iv VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================
-- TRUSTED CONTACTS & HANDOVER
-- =============================================
CREATE TABLE trusted_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    relationship VARCHAR(50),
    access_level VARCHAR(20) NOT NULL DEFAULT 'FULL',
    activation_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    inactivity_days INT DEFAULT 90,
    allowed_categories UUID[],
    server_key_share TEXT,
    invite_token UUID,
    invite_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    invite_accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE handover_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trusted_contact_id UUID NOT NULL REFERENCES trusted_contacts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    response_deadline TIMESTAMPTZ,
    responded_at TIMESTAMPTZ,
    responded_by VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================
-- INACTIVITY MONITORING
-- =============================================
CREATE TABLE inactivity_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    check_type VARCHAR(20) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    response_token UUID NOT NULL DEFAULT gen_random_uuid()
);

-- =============================================
-- PROGRESS TRACKING
-- =============================================
CREATE TABLE user_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    total_categories INT NOT NULL DEFAULT 0,
    completed_categories INT NOT NULL DEFAULT 0,
    total_entries INT NOT NULL DEFAULT 0,
    completed_entries INT NOT NULL DEFAULT 0,
    progress_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    last_calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id)
);

-- =============================================
-- REMINDERS
-- =============================================
CREATE TABLE reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ
);

-- =============================================
-- INDEXES
-- =============================================
CREATE INDEX idx_vault_entries_user ON vault_entries(user_id);
CREATE INDEX idx_vault_entries_category ON vault_entries(user_id, category_id);
CREATE INDEX idx_vault_attachments_entry ON vault_attachments(vault_entry_id);
CREATE INDEX idx_trusted_contacts_user ON trusted_contacts(user_id);
CREATE INDEX idx_trusted_contacts_email ON trusted_contacts(email);
CREATE INDEX idx_handover_requests_status ON handover_requests(user_id, status);
CREATE INDEX idx_inactivity_checks_user ON inactivity_checks(user_id, check_type);
CREATE INDEX idx_reminders_scheduled ON reminders(user_id, scheduled_at) WHERE sent_at IS NULL;
