-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 006: Geocoding columns + multi-pin saved locations
--
-- Hibernate ddl-auto:update creates the new columns / table from the
-- entities on next boot, but this SQL is the canonical reference and is
-- the path used in production / fresh-DB installs.
--
-- Phase 2 (geocoding) needs:
--   • nodes.address                — full reverse-geocoded address line
--   • users.whatsapp_number        — for WhatsApp Cloud API delivery
--                                     (Phase 3 schema landed here for
--                                     forward compatibility)
--
-- Phase 3 (saved locations + radius alerts) needs:
--   • user_saved_locations table   — multi-pin, per-pin alert radius
--
-- Idempotent — safe to re-run.
-- ─────────────────────────────────────────────────────────────────────────────

-- Phase 2: geocoded address on each sensor node.
ALTER TABLE nodes ADD COLUMN IF NOT EXISTS address TEXT;
COMMENT ON COLUMN nodes.address IS
    'Full reverse-geocoded address line from Google Maps Geocoding API. '
    'Populated by GeocodeBackfillRunner / AdminGeocodingController.';

-- Phase 3 forward-compat: WhatsApp number (E.164 format) on users.
-- The corresponding `whatsappAlerts` toggle lives in user_settings and
-- the existing `phone` column already covers SMS.
ALTER TABLE users ADD COLUMN IF NOT EXISTS whatsapp_number VARCHAR(50);

-- Phase 3: user_saved_locations — multi-pin support. A user can drop
-- multiple personal pins ("Home", "Workplace", "Parent's House"); each
-- pin has its own alert radius (1–50 km, default 5 km). When any
-- sensor inside the pin's radius crosses Alert / Warning / Critical,
-- the FloodAlertFanOutListener notifies the owning user via their
-- enabled channels (email / push / SMS / WhatsApp).
--
-- Note: this is intentionally separate from `user_favourite_nodes`,
-- which remains the "I starred this specific sensor" feature read by
-- the mobile app. Different concept, different surface.
CREATE TABLE IF NOT EXISTS user_saved_locations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label            VARCHAR(80) NOT NULL,
    address          TEXT,
    latitude         DOUBLE PRECISION NOT NULL,
    longitude        DOUBLE PRECISION NOT NULL,
    alert_radius_km  NUMERIC(5,2) NOT NULL DEFAULT 5.0
                       CHECK (alert_radius_km >= 1.0 AND alert_radius_km <= 50.0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_saved_loc_user ON user_saved_locations(user_id);
CREATE INDEX IF NOT EXISTS idx_saved_loc_geo  ON user_saved_locations(latitude, longitude);
