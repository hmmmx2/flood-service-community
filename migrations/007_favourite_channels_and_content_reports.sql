-- 1. Per-favourite notification channel toggles
--
-- These columns were added to the `UserFavouriteNode` entity but
-- Hibernate's ddl-auto:update can't reliably ALTER a populated table
-- with NOT NULL columns unless we also supply a SQL-level default.
-- This migration is idempotent (IF NOT EXISTS) so it's safe to re-run
-- even if Hibernate did manage to add some of them.

ALTER TABLE user_favourite_nodes
    ADD COLUMN IF NOT EXISTS email_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS sms_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS whatsapp_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS push_enabled     BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Content moderation reports (community)

CREATE TABLE IF NOT EXISTS content_reports (
    id           UUID PRIMARY KEY,
    target_type  VARCHAR(16) NOT NULL,            -- POST | COMMENT
    target_id    UUID        NOT NULL,
    reporter_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason       VARCHAR(32) NOT NULL,            -- spam | harassment | misinformation | off-topic | other
    details      TEXT,
    status       VARCHAR(16) NOT NULL DEFAULT 'pending',
    resolved_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_content_reports_status_created_at
    ON content_reports (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_content_reports_target
    ON content_reports (target_type, target_id);

-- Same user can't spam-report the same content twice.
CREATE UNIQUE INDEX IF NOT EXISTS uq_content_reports_reporter_target
    ON content_reports (reporter_id, target_type, target_id);

-- 3. Email-verification codes (registration flow gate)
--
-- Mirrors password_reset_codes. Created here so the migration set is
-- self-contained — Hibernate auto-creates this when the entity is new
-- (no existing rows to worry about), but staging environments without
-- ddl-auto:update can run this manually.

CREATE TABLE IF NOT EXISTS email_verification_codes (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code        VARCHAR(10) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_verification_codes_user
    ON email_verification_codes (user_id, created_at DESC);

-- 4. users.email_verified — registration confirmation gate.
-- Default TRUE so existing accounts stay logged-in.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;
