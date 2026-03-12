CREATE TABLE time_capsules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_contact_id UUID NOT NULL REFERENCES trusted_contacts(id) ON DELETE CASCADE,
    encrypted_title TEXT NOT NULL,
    encrypted_message TEXT NOT NULL,
    trigger_type VARCHAR(20) NOT NULL CHECK (trigger_type IN ('HANDOVER', 'DATE')),
    trigger_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'CANCELLED')),
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_capsules_user ON time_capsules(user_id);
CREATE INDEX idx_capsules_status_trigger ON time_capsules(status, trigger_type, trigger_date);
