-- ─────────────────────────────────────────────────────────────
-- Migration 002 - Push Notifications + New Feature Tables
-- Run this against your Neon database before deploying the
-- updated backend (SCRUM-102, SCRUM-103, SCRUM-104, SCRUM-105,
-- SCRUM-106, SCRUM-107, SCRUM-112)
-- ─────────────────────────────────────────────────────────────

-- SCRUM-102: Store Expo push token per user
ALTER TABLE users ADD COLUMN IF NOT EXISTS push_token VARCHAR(500);

-- SCRUM-112: Favourite sensor nodes
CREATE TABLE IF NOT EXISTS user_favourite_nodes (
    user_id    UUID REFERENCES users(id) ON DELETE CASCADE,
    node_id    UUID REFERENCES nodes(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, node_id)
);

-- SCRUM-103: Safety awareness content
CREATE TABLE IF NOT EXISTS safety_content (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section    VARCHAR(10)  NOT NULL,  -- 'before' | 'during' | 'after' | 'contacts' | 'zones'
    lang       VARCHAR(5)   NOT NULL DEFAULT 'en',
    content    TEXT         NOT NULL,
    updated_at TIMESTAMPTZ  DEFAULT NOW(),
    updated_by UUID REFERENCES users(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_safety_section_lang ON safety_content(section, lang);

-- SCRUM-104: Emergency broadcasts
CREATE TABLE IF NOT EXISTS broadcasts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(255) NOT NULL,
    body             VARCHAR(160) NOT NULL,
    target_zone      VARCHAR(100) NOT NULL DEFAULT 'all',
    severity         VARCHAR(20)  NOT NULL DEFAULT 'warning',
    sent_by          UUID REFERENCES users(id),
    sent_at          TIMESTAMPTZ  DEFAULT NOW(),
    recipient_count  INTEGER      DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_broadcasts_sent_at ON broadcasts(sent_at DESC);

-- SCRUM-105: Flood incident reports
CREATE TABLE IF NOT EXISTS reports (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID REFERENCES users(id) ON DELETE CASCADE,
    latitude     DECIMAL(9, 6) NOT NULL,
    longitude    DECIMAL(9, 6) NOT NULL,
    severity     VARCHAR(20)   NOT NULL DEFAULT 'warning',
    description  TEXT,
    photo_url    TEXT,
    status       VARCHAR(20)   NOT NULL DEFAULT 'pending',
    submitted_at TIMESTAMPTZ   DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_reports_status      ON reports(status);
CREATE INDEX IF NOT EXISTS idx_reports_submitted_at ON reports(submitted_at DESC);

-- SCRUM-106: Flood risk zones
CREATE TABLE IF NOT EXISTS zones (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    risk_level VARCHAR(10)  NOT NULL DEFAULT 'low',  -- low | medium | high | extreme
    boundary   JSONB        NOT NULL,                -- GeoJSON Polygon
    updated_at TIMESTAMPTZ  DEFAULT NOW()
);

-- Seed default safety content (English)
INSERT INTO safety_content (section, lang, content) VALUES
('before', 'en', 'Prepare an emergency kit with food, water, medications and important documents. Know your nearest evacuation route. Sign up for flood alert notifications. Keep sandbags ready if you live in a low-lying area.'),
('during', 'en', 'Move to higher ground immediately if water rises. Do not walk or drive through floodwater. Turn off electricity at the main switch. Avoid contact with floodwater as it may be contaminated.'),
('after',  'en', 'Do not return home until authorities say it is safe. Document all damage with photos for insurance. Boil water before drinking until the water supply is declared safe. Watch for signs of structural damage before entering buildings.'),
('contacts', 'en', 'Civil Defence (APM): 999 | Fire & Rescue: 994 | Sarawak Disaster Management: +60 82-319 999 | NGO Flood Helpline: +60 82-000 000'),
('zones',  'en', 'Designated evacuation centres in Kuching: SMK Batu Lintang, Dewan Suarah Padungan, Stadium Sarawak. Follow instructions from Civil Defence officers on site.')
ON CONFLICT (section, lang) DO NOTHING;
