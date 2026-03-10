# Pärandiplaan MVP — Claude Code Implementation Prompt

## Context

Pärandiplaan is a digital legacy and life organization platform. Users store their most important life information (bank accounts, insurance, passwords, property docs, wills, final wishes) in an encrypted vault, and designate trusted people who receive access when needed. Tech stack: Spring Boot 3.x (Java 21), Angular 18+ with SSR, PostgreSQL 16, Docker on Contabo VPS. Payments via LemonSqueezy, email via Resend. All UI in Estonian (i18n-ready for LV, LT, FI, EN later).

The MVP covers 4 phases over 16 weeks. This prompt covers Phases 1–3 (weeks 5–12): auth, vault, guided setup, trusted contacts, and the handover system.

---

## 1. Database Schema (Flyway migrations)

### V1__core_schema.sql

```sql
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
    totp_secret VARCHAR(255),         -- 2FA secret (encrypted)
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
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',  -- FREE, PLUS, FAMILY
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CANCELLED, PAST_DUE
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

-- Predefined categories for organizing vault entries
CREATE TABLE vault_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(50) NOT NULL UNIQUE,    -- 'banking', 'insurance', 'property', etc.
    name_et VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    icon VARCHAR(50) NOT NULL,           -- emoji or icon identifier
    sort_order INT NOT NULL DEFAULT 0,
    field_template JSONB NOT NULL DEFAULT '[]'  -- defines expected fields per category
);

-- The core vault: each entry is one "item" (a bank account, an insurance policy, etc.)
CREATE TABLE vault_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES vault_categories(id),
    title VARCHAR(255) NOT NULL,          -- user-visible title, stored encrypted
    encrypted_data TEXT NOT NULL,          -- AES-256-GCM encrypted JSON blob
    encryption_iv VARCHAR(255) NOT NULL,   -- initialization vector
    notes_encrypted TEXT,                  -- optional encrypted notes
    notes_iv VARCHAR(255),
    has_attachments BOOLEAN NOT NULL DEFAULT FALSE,
    is_complete BOOLEAN NOT NULL DEFAULT FALSE,  -- all required fields filled?
    last_reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- File attachments (PDFs, images) linked to vault entries
CREATE TABLE vault_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vault_entry_id UUID NOT NULL REFERENCES vault_entries(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name_encrypted TEXT NOT NULL,
    file_name_iv VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,     -- S3 object key (file is encrypted before upload)
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================
-- TRUSTED CONTACTS & HANDOVER
-- =============================================

-- People designated to receive vault access
CREATE TABLE trusted_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    relationship VARCHAR(50),             -- 'spouse', 'child', 'parent', 'sibling', 'friend', 'lawyer'
    access_level VARCHAR(20) NOT NULL DEFAULT 'FULL',  -- FULL, PARTIAL
    activation_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',  -- MANUAL, INACTIVITY, IMMEDIATE
    inactivity_days INT DEFAULT 90,       -- for INACTIVITY mode: days before activation
    -- Partial access: which categories this contact can see (NULL = all for FULL)
    allowed_categories UUID[],
    -- Encrypted half-key for split-key access (user holds other half)
    server_key_share TEXT,
    invite_token UUID,
    invite_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    invite_accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Handover requests (when a trusted contact requests access)
CREATE TABLE handover_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trusted_contact_id UUID NOT NULL REFERENCES trusted_contacts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- vault owner
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, DENIED, AUTO_APPROVED, CANCELLED
    reason TEXT,                           -- why the contact is requesting access
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    response_deadline TIMESTAMPTZ,         -- owner must respond by this time, or auto-approve
    responded_at TIMESTAMPTZ,
    responded_by VARCHAR(20),              -- 'OWNER', 'SYSTEM' (auto-approved after deadline)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================
-- INACTIVITY MONITORING (Dead Man's Switch)
-- =============================================
CREATE TABLE inactivity_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    check_type VARCHAR(20) NOT NULL,       -- 'WARNING_1', 'WARNING_2', 'FINAL'
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,              -- user clicked "I'm still here"
    response_token UUID NOT NULL DEFAULT gen_random_uuid()
);

-- =============================================
-- PROGRESS TRACKING (Gamification)
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
    type VARCHAR(30) NOT NULL,             -- 'INCOMPLETE_SETUP', 'REVIEW_ENTRIES', 'ANNUAL_AUDIT'
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
```

### V2__seed_categories.sql

```sql
INSERT INTO vault_categories (slug, name_et, name_en, icon, sort_order, field_template) VALUES
('banking', 'Pangandus', 'Banking', '🏦', 1,
 '[{"key":"bank_name","label_et":"Panga nimi","label_en":"Bank name","type":"text","required":true},
   {"key":"account_number","label_et":"Konto number","label_en":"Account number","type":"text","required":true},
   {"key":"account_type","label_et":"Konto tüüp","label_en":"Account type","type":"select","options":["checking","savings","investment","credit"]},
   {"key":"online_banking_user","label_et":"Internetipanga kasutajanimi","label_en":"Online banking username","type":"text"},
   {"key":"contact_phone","label_et":"Panga kontakttelefon","label_en":"Bank contact phone","type":"text"}]'),

('insurance', 'Kindlustus', 'Insurance', '🛡️', 2,
 '[{"key":"provider","label_et":"Kindlustusandja","label_en":"Provider","type":"text","required":true},
   {"key":"policy_number","label_et":"Poliisi number","label_en":"Policy number","type":"text","required":true},
   {"key":"type","label_et":"Kindlustuse tüüp","label_en":"Insurance type","type":"select","options":["life","health","home","car","travel","other"]},
   {"key":"coverage_amount","label_et":"Kindlustussumma","label_en":"Coverage amount","type":"text"},
   {"key":"beneficiary","label_et":"Soodustatud isik","label_en":"Beneficiary","type":"text"},
   {"key":"renewal_date","label_et":"Uuendamise kuupäev","label_en":"Renewal date","type":"date"},
   {"key":"contact","label_et":"Kontakt","label_en":"Contact","type":"text"}]'),

('property', 'Kinnisvara', 'Property', '🏠', 3,
 '[{"key":"address","label_et":"Aadress","label_en":"Address","type":"text","required":true},
   {"key":"type","label_et":"Tüüp","label_en":"Type","type":"select","options":["apartment","house","land","commercial"]},
   {"key":"ownership","label_et":"Omandi osakaal","label_en":"Ownership share","type":"text"},
   {"key":"cadastral_number","label_et":"Katastri number","label_en":"Cadastral number","type":"text"},
   {"key":"mortgage_provider","label_et":"Laenuandja","label_en":"Mortgage provider","type":"text"},
   {"key":"mortgage_remaining","label_et":"Laenujääk","label_en":"Remaining mortgage","type":"text"}]'),

('digital_accounts', 'Digitaalsed kontod', 'Digital Accounts', '💻', 4,
 '[{"key":"service_name","label_et":"Teenuse nimi","label_en":"Service name","type":"text","required":true},
   {"key":"username","label_et":"Kasutajanimi","label_en":"Username","type":"text"},
   {"key":"email","label_et":"E-posti aadress","label_en":"Email","type":"text"},
   {"key":"url","label_et":"Veebiaadress","label_en":"URL","type":"text"},
   {"key":"action_after_death","label_et":"Tegevus pärast surma","label_en":"Action after death","type":"select","options":["delete","memorialize","transfer","close"]}]'),

('health', 'Tervis', 'Health', '❤️', 5,
 '[{"key":"doctor_name","label_et":"Arsti nimi","label_en":"Doctor name","type":"text"},
   {"key":"clinic","label_et":"Kliinik","label_en":"Clinic","type":"text"},
   {"key":"conditions","label_et":"Diagnoosid/seisundid","label_en":"Conditions","type":"textarea"},
   {"key":"medications","label_et":"Ravimid","label_en":"Medications","type":"textarea"},
   {"key":"allergies","label_et":"Allergiad","label_en":"Allergies","type":"textarea"},
   {"key":"blood_type","label_et":"Veregrupp","label_en":"Blood type","type":"select","options":["A+","A-","B+","B-","AB+","AB-","O+","O-","unknown"]},
   {"key":"organ_donor","label_et":"Doonorkaart","label_en":"Organ donor","type":"select","options":["yes","no","unknown"]}]'),

('legal', 'Õiguslikud dokumendid', 'Legal Documents', '⚖️', 6,
 '[{"key":"document_type","label_et":"Dokumendi tüüp","label_en":"Document type","type":"select","required":true,"options":["will","power_of_attorney","prenup","divorce_decree","court_order","contract","other"]},
   {"key":"created_date","label_et":"Koostamise kuupäev","label_en":"Created date","type":"date"},
   {"key":"notary","label_et":"Notar","label_en":"Notary","type":"text"},
   {"key":"location","label_et":"Asukoht (kus hoitakse)","label_en":"Location (where stored)","type":"text"},
   {"key":"notes","label_et":"Märkused","label_en":"Notes","type":"textarea"}]'),

('contracts', 'Lepingud ja tellimused', 'Contracts & Subscriptions', '📋', 7,
 '[{"key":"provider","label_et":"Teenusepakkuja","label_en":"Provider","type":"text","required":true},
   {"key":"type","label_et":"Tüüp","label_en":"Type","type":"select","options":["lease","loan","subscription","telecom","utility","other"]},
   {"key":"monthly_cost","label_et":"Kuumakse","label_en":"Monthly cost","type":"text"},
   {"key":"end_date","label_et":"Lõppkuupäev","label_en":"End date","type":"date"},
   {"key":"cancellation_method","label_et":"Tühistamise viis","label_en":"Cancellation method","type":"textarea"},
   {"key":"auto_renew","label_et":"Automaatne pikenemine","label_en":"Auto-renew","type":"select","options":["yes","no","unknown"]}]'),

('vehicles', 'Sõidukid', 'Vehicles', '🚗', 8,
 '[{"key":"make_model","label_et":"Mark ja mudel","label_en":"Make and model","type":"text","required":true},
   {"key":"reg_number","label_et":"Registreerimisnumber","label_en":"Registration number","type":"text"},
   {"key":"vin","label_et":"VIN kood","label_en":"VIN","type":"text"},
   {"key":"insurance_provider","label_et":"Kindlustus","label_en":"Insurance provider","type":"text"},
   {"key":"leasing_provider","label_et":"Liisinguandja","label_en":"Leasing provider","type":"text"}]'),

('personal_wishes', 'Isiklikud soovid', 'Personal Wishes', '🕊️', 9,
 '[{"key":"funeral_type","label_et":"Matuse tüüp","label_en":"Funeral type","type":"select","options":["burial","cremation","natural","other","no_preference"]},
   {"key":"funeral_location","label_et":"Matuse koht","label_en":"Funeral location","type":"text"},
   {"key":"music","label_et":"Muusika matustel","label_en":"Music at funeral","type":"textarea"},
   {"key":"message","label_et":"Sõnum lähedastele","label_en":"Message to loved ones","type":"textarea"},
   {"key":"special_instructions","label_et":"Erilised soovid","label_en":"Special instructions","type":"textarea"}]'),

('important_contacts', 'Olulised kontaktid', 'Important Contacts', '📞', 10,
 '[{"key":"name","label_et":"Nimi","label_en":"Name","type":"text","required":true},
   {"key":"role","label_et":"Roll","label_en":"Role","type":"select","options":["lawyer","accountant","financial_advisor","doctor","employer","landlord","other"]},
   {"key":"phone","label_et":"Telefon","label_en":"Phone","type":"text"},
   {"key":"email","label_et":"E-post","label_en":"Email","type":"text"},
   {"key":"notes","label_et":"Märkused","label_en":"Notes","type":"textarea"}]');
```

---

## 2. Backend (Spring Boot)

### 2.1 Project Structure

```
src/main/java/ee/parandiplaan/
├── config/
│   ├── SecurityConfig.java          -- Spring Security + JWT + 2FA
│   ├── CorsConfig.java
│   └── EncryptionConfig.java        -- AES-256-GCM bean config
├── auth/
│   ├── AuthController.java          -- /api/v1/auth/**
│   ├── AuthService.java
│   ├── JwtService.java
│   ├── TotpService.java             -- Google Authenticator 2FA
│   └── dto/ (LoginRequest, RegisterRequest, TotpSetupResponse, etc.)
├── user/
│   ├── UserController.java          -- /api/v1/users/me
│   ├── UserService.java
│   └── dto/
├── vault/
│   ├── VaultCategoryController.java -- /api/v1/vault/categories
│   ├── VaultEntryController.java    -- /api/v1/vault/entries
│   ├── VaultAttachmentController.java -- /api/v1/vault/attachments
│   ├── VaultCategoryService.java
│   ├── VaultEntryService.java
│   ├── VaultAttachmentService.java
│   ├── EncryptionService.java       -- AES-256-GCM encrypt/decrypt (server-side layer)
│   └── dto/
├── trust/
│   ├── TrustedContactController.java -- /api/v1/trusted-contacts
│   ├── HandoverController.java       -- /api/v1/handover
│   ├── TrustedContactService.java
│   ├── HandoverService.java
│   └── dto/
├── progress/
│   ├── ProgressController.java      -- /api/v1/progress
│   ├── ProgressService.java
│   └── dto/
├── notification/
│   ├── EmailService.java            -- Resend integration
│   ├── ReminderService.java         -- Scheduled reminders
│   └── InactivityMonitorService.java -- Dead man's switch
├── subscription/
│   ├── SubscriptionController.java  -- /api/v1/subscription
│   ├── SubscriptionService.java
│   ├── LemonSqueezyWebhookController.java
│   └── dto/
└── common/
    ├── entity/ (BaseEntity with id, createdAt, updatedAt)
    ├── exception/ (GlobalExceptionHandler, custom exceptions)
    └── security/ (CurrentUser annotation, TenantFilter)
```

### 2.2 Encryption Architecture

This is the most critical part. User data must be encrypted at rest.

**MVP approach (server-side encryption with user-derived key):**

```
User password → PBKDF2 (100,000 iterations) → 256-bit encryption key
                                                    ↓
Vault entry data (JSON) → AES-256-GCM encrypt → stored in DB (encrypted_data column)
                                                    ↓
File attachments → AES-256-GCM encrypt → stored in S3 (encrypted blob)
```

**EncryptionService.java:**
- `encrypt(String plaintext, String userEncryptionKey)` → returns `{ciphertext, iv}`
- `decrypt(String ciphertext, String iv, String userEncryptionKey)` → returns plaintext
- The user's encryption key is derived from their password at login and stored ONLY in the JWT session (never persisted)
- When user changes password, all vault entries must be re-encrypted (background job)

**For MVP:** server-side encryption is acceptable. For v2, move to client-side encryption (zero-knowledge) where the server never sees the plaintext or the key.

**Trusted contact access:**
- When a handover is approved, the system decrypts using the vault owner's key and re-encrypts with a temporary access key shared with the trusted contact
- For MVP: the owner's encryption key is escrowed (encrypted with a platform master key) specifically for handover scenarios. This is NOT zero-knowledge but is acceptable for MVP with clear disclosure to users.

### 2.3 Key Services

**VaultEntryService:**
- CRUD operations for vault entries
- All data encrypted before storage, decrypted on retrieval
- Enforces plan limits (FREE: 10 entries max, PLUS: unlimited)
- Attachment size limits per plan (FREE: 100MB, PLUS: 5GB, FAMILY: 25GB)
- After each create/update, recalculate UserProgress

**TrustedContactService:**
- Add/remove trusted contacts (limit by plan: FREE=1, PLUS=3, FAMILY=10)
- Send invite email via Resend with unique token
- Contact accepts invite → sets up their own account (or links existing)
- Set access level (FULL/PARTIAL) and activation mode (MANUAL/INACTIVITY/IMMEDIATE)
- For PARTIAL: select which vault_categories the contact can see

**HandoverService:**
- Trusted contact initiates handover request
- MANUAL mode: owner gets email + dashboard notification, has 14 days to approve/deny. If no response → auto-approve
- INACTIVITY mode: handled by InactivityMonitorService (see below)
- IMMEDIATE mode: access granted instantly (for spouse/partner scenarios)
- On approval: generate temporary access token, grant read-only access to allowed vault entries

**InactivityMonitorService:**
- `@Scheduled(cron = "0 0 9 * * *")` — runs daily at 9:00 EET
- Checks users who have INACTIVITY-mode trusted contacts
- If user hasn't logged in for (inactivity_days - 14): send WARNING_1 email ("Kas kõik on korras? Logi sisse, et kinnitada.")
- If user hasn't logged in for (inactivity_days - 7): send WARNING_2 email (more urgent)
- If user hasn't logged in for inactivity_days: send FINAL email, wait 48h, then auto-trigger handover
- User can click "I'm still here" link in email → resets the timer
- Each check creates an inactivity_checks record for audit trail

**ProgressService:**
- Calculates completion percentage based on:
  - How many categories have at least 1 entry (out of 10)
  - How many entries have all required fields filled (is_complete = true)
  - Whether trusted contacts are set up
  - Whether personal wishes are filled
- Formula: `progress = (completed_weight / total_weight) * 100`
  - Category has entries: 7 points each (70 total)
  - Trusted contact set up: 15 points
  - Personal wishes filled: 15 points
- Recalculated after each vault change

**ReminderService:**
- `@Scheduled(cron = "0 0 10 * * MON")` — weekly on Monday
- Users with incomplete setup (< 50%): "Sul on X kategooriat täitmata"
- Users who haven't logged in 30+ days: "Kas su info on ajakohane?"
- Annual review reminder (365 days since last_reviewed_at on any entry)
- Sends via Resend, tracks in reminders table

### 2.4 REST API Endpoints

**AuthController** `/api/v1/auth`
```
POST /register          -- email, password, full_name → creates account + sends verification
POST /login             -- email, password → returns JWT (if 2FA enabled, returns partial token)
POST /login/2fa         -- partial_token, totp_code → returns full JWT
POST /verify-email      -- token → marks email verified
POST /forgot-password   -- email → sends reset link
POST /reset-password    -- token, new_password → resets + re-encrypts vault
POST /2fa/setup         -- returns QR code for Google Authenticator
POST /2fa/enable        -- totp_code → enables 2FA
POST /2fa/disable       -- totp_code → disables 2FA
```

**VaultCategoryController** `/api/v1/vault/categories`
```
GET /                   -- list all categories with entry counts and completion status
```

**VaultEntryController** `/api/v1/vault/entries`
```
GET /                   -- list all entries (decrypt on-the-fly), filter by ?category=
GET /{id}               -- get single entry (decrypted)
POST /                  -- create entry (encrypt before storage)
PUT /{id}               -- update entry
DELETE /{id}            -- soft-delete entry
PUT /{id}/review        -- mark entry as reviewed (updates last_reviewed_at)
```

**VaultAttachmentController** `/api/v1/vault/attachments`
```
POST /                  -- upload file (encrypt + store in S3), link to vault_entry_id
GET /{id}/download      -- decrypt + stream file
DELETE /{id}            -- delete from S3 + DB
```

**TrustedContactController** `/api/v1/trusted-contacts`
```
GET /                   -- list my trusted contacts
POST /                  -- add trusted contact (sends invite email)
PUT /{id}               -- update access level, activation mode
DELETE /{id}            -- remove trusted contact
POST /{id}/resend-invite -- resend invite email
```

**HandoverController** `/api/v1/handover`
```
-- For trusted contacts:
POST /request           -- initiate handover request (with reason)
GET /my-access          -- list vaults I have been granted access to

-- For vault owners:
GET /requests           -- list pending handover requests for my vault
PUT /requests/{id}/approve  -- approve request
PUT /requests/{id}/deny     -- deny request

-- For inactivity responses:
GET /still-here/{token} -- user confirms they're alive (from email link)
```

**ProgressController** `/api/v1/progress`
```
GET /                   -- get my progress (percentage, breakdown by category)
GET /suggestions        -- get next recommended actions ("Alusta kindlustusest")
```

**SubscriptionController** `/api/v1/subscription`
```
GET /                   -- get current plan + limits + usage
POST /checkout          -- generate LemonSqueezy checkout URL for upgrade
POST /portal            -- generate customer portal URL
POST /webhook           -- LemonSqueezy webhook (subscription_created, updated, cancelled, payment_failed)
```

---

## 3. Frontend (Angular)

### 3.1 Module Structure

```
src/app/
├── core/
│   ├── auth/           -- AuthService, AuthGuard, JwtInterceptor
│   ├── encryption/     -- Client-side encryption helpers (for v2)
│   └── services/       -- ApiService, NotificationService
├── shared/
│   ├── components/     -- ProgressBar, CategoryIcon, Badge, Modal, EmptyState
│   └── pipes/          -- DatePipe (Estonian format), TruncatePipe
├── features/
│   ├── auth/           -- LoginComponent, RegisterComponent, TotpSetupComponent, ForgotPasswordComponent
│   ├── onboarding/     -- OnboardingWizardComponent (guided first-time setup)
│   ├── dashboard/      -- DashboardComponent (overview with progress + recent activity)
│   ├── vault/
│   │   ├── VaultOverviewComponent    -- grid of categories with completion indicators
│   │   ├── CategoryDetailComponent   -- list of entries in a category
│   │   ├── EntryFormComponent        -- dynamic form based on field_template
│   │   └── EntryDetailComponent      -- view single entry with attachments
│   ├── trust/
│   │   ├── TrustedContactsComponent  -- manage trusted contacts
│   │   ├── AddContactComponent       -- add new contact form
│   │   └── HandoverComponent         -- manage handover requests
│   ├── progress/
│   │   └── ProgressDashboardComponent -- visual progress with suggestions
│   ├── settings/
│   │   ├── ProfileComponent          -- name, email, phone, country
│   │   ├── SecurityComponent         -- password change, 2FA setup
│   │   └── SubscriptionComponent     -- current plan, upgrade
│   └── public/
│       ├── LandingComponent          -- marketing landing page (SSR for SEO)
│       ├── InviteAcceptComponent     -- trusted contact accepts invite
│       └── HandoverAccessComponent   -- trusted contact views shared vault
└── layout/
    ├── SidebarComponent              -- main navigation
    ├── HeaderComponent               -- user menu, notifications, progress badge
    └── FooterComponent
```

### 3.2 Key Screens

**Dashboard (after login):**
- Progress circle in center: "Su elu on 34% korrastatud"
- Category grid (2 columns): each category shows icon, name, entry count, completion status
- "Järgmine samm" suggestion card: "Lisa oma esimene pangakonto" with CTA
- Recent activity: last 5 vault changes
- Trusted contacts summary: "1 usaldusisik määratud" or "⚠️ Lisa usaldusisik"

**Vault Category View:**
- List of entries with status indicators (complete/incomplete)
- "Lisa uus" button
- Category description/help text: "Sisesta kõik pangakontod, et su lähedased teaksid, kus su raha on"

**Entry Form (dynamic):**
- Reads field_template from vault_categories
- Renders appropriate input types (text, select, date, textarea)
- File upload zone for attachments (drag & drop)
- Auto-save draft every 30 seconds
- "Salvesta" button, "Märgi üle vaadatuks" button for existing entries

**Guided Setup Wizard (first-time users):**
- Step-by-step flow through the most important categories
- Step 1: "Alustame. Mitu pangakontot sul on?" → quick-add banking entries
- Step 2: "Kas sul on elukindlustus?" → yes/no → if yes, add insurance entry
- Step 3: "Kas sa omad kinnisvara?" → quick property entry
- Step 4: "Kes on su kõige usaldusväärsem inimene?" → add first trusted contact
- Step 5: "Suurepärane! Su elu on juba 28% korrastatud. Jätka avastamist."
- Skip option available at every step

**Trusted Contacts Management:**
- List of contacts with status (invited/accepted), access level, activation mode
- "Lisa usaldusisik" → form with name, email, relationship, access level, activation mode
- Visual explanation of each activation mode (with illustrations)
- Warning when adding IMMEDIATE access: "See isik saab kohe ligipääsu su andmetele"

**Handover Access View (for trusted contacts):**
- Clean, read-only view of shared vault entries
- Grouped by category
- Download attachments
- Clear messaging: "{Nimi} andis sulle ligipääsu oma elukorralduse infole"

### 3.3 UI/UX Principles

- **Tone:** warm, calm, reassuring. Not clinical or scary. This is about LIFE organization, not death.
- **Colors:** deep green (#1B4332) primary, sage accents, warm neutrals. NO black, no red (unless error).
- **Progress:** always visible. The progress percentage is the #1 retention hook.
- **Empty states:** every empty category shows a helpful prompt, not a blank page
- **Mobile-first:** many users will fill in info on their phone (e.g., photographing documents)
- **Estonian first:** all UI text in Estonian with i18n keys for future localization

### 3.4 i18n Keys (Estonian)

Add all UI strings as i18n keys. Core terms:
- Vault → Tresor
- Vault entry → Kirje
- Trusted contact → Usaldusisik
- Handover → Üleandmine
- Progress → Edenemine
- Dead man's switch → Inaktiivsuskontroll
- Category → Kategooria
- Attachment → Manus
- Encrypted → Krüpteeritud
- "Your life is X% organized" → "Su elu on X% korrastatud"
- "Add entry" → "Lisa kirje"
- "Mark as reviewed" → "Märgi üle vaadatuks"

---

## 4. Email Templates (Resend)

Create HTML email templates matching Pärandiplaan branding (green theme, warm tone):

**1. Welcome email** (after registration)
- Subject: "Tere tulemast Pärandiplaani! 🌿"
- Body: warm welcome, explain what to do first, link to guided setup

**2. Trusted contact invite**
- Subject: "{Name} lisas sind oma usaldusisikuks Pärandiplaanis"
- Body: explain what this means, what to expect, accept invite CTA

**3. Handover request notification (to vault owner)**
- Subject: "⚠️ {Contact name} taotleb ligipääsu su Pärandiplaanile"
- Body: explain the request, approve/deny buttons, deadline reminder

**4. Inactivity warning (WARNING_1)**
- Subject: "Kas kõik on korras? Logi Pärandiplaani sisse"
- Body: gentle check-in, "I'm still here" button, explain what happens if no response

**5. Inactivity final warning (FINAL)**
- Subject: "🔴 Tähelepanu: su usaldusisik saab peagi ligipääsu"
- Body: urgent but respectful, "I'm still here" button, clear timeline

**6. Weekly reminder (incomplete setup)**
- Subject: "Su Pärandiplaan on {X}% valmis — jätkame?"
- Body: progress summary, next suggested action, direct link

**7. Annual review reminder**
- Subject: "Aeg üle vaadata: kas su Pärandiplaani info on ajakohane?"
- Body: summary of what was last updated when, review CTA

---

## 5. Implementation Order

1. **Project scaffold:** Spring Boot + Angular projects, Docker Compose (PG + app), Flyway migrations
2. **Auth system:** register, login, JWT, email verification, password reset
3. **2FA:** TOTP setup/enable/disable with Google Authenticator
4. **Vault categories:** seed data, list categories endpoint + Angular category grid
5. **Vault entries CRUD:** encrypted create/read/update/delete + dynamic form component
6. **File attachments:** S3 upload/download with encryption
7. **Progress tracking:** calculation service + dashboard progress circle
8. **Guided setup wizard:** onboarding flow for new users
9. **Trusted contacts:** add/remove/invite + accept invite flow
10. **Handover system:** request/approve/deny + read-only vault access view
11. **Inactivity monitor:** scheduled job + warning emails + "I'm still here" flow
12. **Reminders:** weekly/annual email reminders
13. **Subscription:** LemonSqueezy integration + plan limits enforcement
14. **Landing page:** SSR Angular page for SEO with marketing content
15. **Final polish:** error handling, loading states, empty states, mobile responsive

---

## 6. Security Checklist (MUST DO)

- [ ] All vault data encrypted with AES-256-GCM before DB storage
- [ ] File attachments encrypted before S3 upload
- [ ] 2FA available for all users (strongly encouraged in UI)
- [ ] JWT tokens short-lived (15 min access, 7 day refresh)
- [ ] Rate limiting on auth endpoints (5 attempts per minute)
- [ ] CSRF protection on all state-changing endpoints
- [ ] Input validation on all endpoints (javax.validation)
- [ ] SQL injection protection (parameterized queries via JPA)
- [ ] XSS protection (Angular sanitizes by default, CSP headers)
- [ ] HTTPS only (Caddy handles TLS)
- [ ] Secure headers: HSTS, X-Content-Type-Options, X-Frame-Options
- [ ] Audit log: log all vault access, handover events, login attempts
- [ ] No sensitive data in logs (mask emails, never log encryption keys)
- [ ] GDPR: data export endpoint, account deletion with cascade

---

## 7. Important Notes

- **Estonian-first:** all UI and emails in Estonian. Use i18n keys everywhere for future localization.
- **Encryption is not optional:** even for MVP, vault data MUST be encrypted at rest. This is the #1 trust factor.
- **Mobile responsive:** many users will use this on phone. Test all forms on mobile.
- **Progressive disclosure:** don't overwhelm. Show one category at a time in the wizard.
- **Follow existing patterns:** if code structure patterns exist from DoraAudit/BuildQuote, reuse them.
- **Domain:** register parandiplaan.ee (check availability first, fall back to parandiplaan.eu)
- **SEO matters:** the landing page and blog content are the primary acquisition channel. SSR is critical.
- **Test encryption thoroughly:** write unit tests for encrypt/decrypt roundtrip, including edge cases (empty strings, unicode, large files).
