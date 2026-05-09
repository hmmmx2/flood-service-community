-- Email sender registry — one row per email purpose so ops can swap
-- the from-address at runtime without redeploying the service.
--
-- The Java EmailSenderResolver caches each lookup; after editing rows,
-- restart the service or call the admin invalidate endpoint to refresh.

CREATE TABLE IF NOT EXISTS email_senders (
    id            UUID PRIMARY KEY,
    purpose       VARCHAR(32)  NOT NULL UNIQUE,
    from_address  VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Default seeds for the four purposes the codebase currently uses.
-- Edit `from_address` here OR via an UPDATE statement in production
-- once the floodwatch.app domain is verified on Resend.
INSERT INTO email_senders (id, purpose, from_address, display_name)
VALUES
    (gen_random_uuid(), 'REGISTRATION',   'noreply@floodwatch.app', 'FloodWatch'),
    (gen_random_uuid(), 'PASSWORD_RESET', 'noreply@floodwatch.app', 'FloodWatch'),
    (gen_random_uuid(), 'FLOOD_ALERT',    'alerts@floodwatch.app',  'FloodWatch Alerts'),
    (gen_random_uuid(), 'BROADCAST',      'alerts@floodwatch.app',  'FloodWatch Alerts')
ON CONFLICT (purpose) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_email_senders_active
    ON email_senders (active) WHERE active = TRUE;
