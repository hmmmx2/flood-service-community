-- ═══════════════════════════════════════════════════════════════════════════
-- Migration 004: Schema Hardening
--
-- Applied: 2026-04-09
-- Author:  DBA / Claude Sonnet 4.6
--
-- Findings from audit:
--   • Missing CHECK constraints on all enum-like columns (role, severity,
--     status, risk_level, section, event_type)
--   • No composite indexes — hot query paths use two separate single-col
--     indexes that PostgreSQL cannot combine efficiently
--   • broadcasts.body capped at VARCHAR(160) — too short for push payloads
--   • Missing updated_at on reports, blogs, user_settings
--   • No explicit NOT NULL on sent_at / submitted_at
--   • Missing partial indexes for the most common filtered queries
--   • 2,875 orphaned events (node_id not in nodes) — FK deferred to FLOOD-09
--
-- NOTE: CREATE INDEX CONCURRENTLY cannot run inside a transaction block.
--       Run the DDL section first (BEGIN…COMMIT), then the index section.
-- ═══════════════════════════════════════════════════════════════════════════


-- ┌─────────────────────────────────────────────────────────────────────────┐
-- │  SECTION 1 — CHECK CONSTRAINTS (domain / enum validation)              │
-- │  Wrap in transaction so all-or-nothing. Existing rows must satisfy.     │
-- └─────────────────────────────────────────────────────────────────────────┘

BEGIN;

-- 1.1  users.role ─────────────────────────────────────────────────────────
ALTER TABLE users
    ADD CONSTRAINT chk_users_role
    CHECK (role IN ('admin', 'customer'));

-- 1.2  events.event_type ──────────────────────────────────────────────────
ALTER TABLE events
    ADD CONSTRAINT chk_events_event_type
    CHECK (event_type IN (
        'heartbeat',
        'node_created',
        'node_dead',
        'water_level_update'
    ));
-- NOTE: events.new_level and nodes.current_level already have CHECK constraints
--       from earlier migrations (events_new_level_check, nodes_current_level_check).

-- 1.3  broadcasts.severity ────────────────────────────────────────────────
ALTER TABLE broadcasts
    ADD CONSTRAINT chk_broadcasts_severity
    CHECK (severity IN ('info', 'warning', 'critical'));

-- 1.4  reports.severity ───────────────────────────────────────────────────
ALTER TABLE reports
    ADD CONSTRAINT chk_reports_severity
    CHECK (severity IN ('info', 'warning', 'critical'));

-- 1.5  reports.status ─────────────────────────────────────────────────────
ALTER TABLE reports
    ADD CONSTRAINT chk_reports_status
    CHECK (status IN ('pending', 'reviewed', 'resolved'));

-- 1.6  zones.risk_level ───────────────────────────────────────────────────
ALTER TABLE zones
    ADD CONSTRAINT chk_zones_risk_level
    CHECK (risk_level IN ('low', 'medium', 'high', 'extreme'));

-- 1.7  safety_content.section ─────────────────────────────────────────────
ALTER TABLE safety_content
    ADD CONSTRAINT chk_safety_content_section
    CHECK (section IN ('before', 'during', 'after', 'contacts', 'zones'));

-- 1.8  safety_content.lang — BCP-47 two-letter or five-char locale code ───
ALTER TABLE safety_content
    ADD CONSTRAINT chk_safety_content_lang
    CHECK (lang ~ '^[a-z]{2}(-[A-Z]{2})?$');

-- 1.9  data_updates — physical sensor bounds ──────────────────────────────
ALTER TABLE data_updates
    ADD CONSTRAINT chk_data_updates_water_level
    CHECK (water_level IS NULL OR (water_level >= 0 AND water_level <= 30)),
    ADD CONSTRAINT chk_data_updates_temperature
    CHECK (temperature IS NULL OR (temperature >= -20 AND temperature <= 80)),
    ADD CONSTRAINT chk_data_updates_humidity
    CHECK (humidity IS NULL OR (humidity >= 0 AND humidity <= 100));

-- 1.10  heartbeats.status ──────────────────────────────────────────────────
ALTER TABLE heartbeats
    ADD CONSTRAINT chk_heartbeats_status
    CHECK (status IN ('Alive', 'Dead', 'Unknown', ''));


-- ┌─────────────────────────────────────────────────────────────────────────┐
-- │  SECTION 2 — DATA TYPE CORRECTIONS                                     │
-- └─────────────────────────────────────────────────────────────────────────┘

-- 2.1  broadcasts.body: SMS 160-char cap is too short for in-app payloads.
--      Widen to TEXT. JPA entity updated to columnDefinition = "TEXT".
ALTER TABLE broadcasts
    ALTER COLUMN body TYPE TEXT;

-- 2.2  reports.photo_url is already TEXT. Confirm description is also TEXT.
--      (Already TEXT per entity — no change needed.)


-- ┌─────────────────────────────────────────────────────────────────────────┐
-- │  SECTION 3 — NOT NULL HARDENING                                        │
-- └─────────────────────────────────────────────────────────────────────────┘

-- 3.1  broadcasts.sent_at should never be null — defaults to NOW().
ALTER TABLE broadcasts
    ALTER COLUMN sent_at SET NOT NULL;

-- 3.2  reports.submitted_at should never be null.
ALTER TABLE reports
    ALTER COLUMN submitted_at SET NOT NULL;


-- ┌─────────────────────────────────────────────────────────────────────────┐
-- │  SECTION 4 — EXPLICIT DEFAULTS (prevent NULL surprises on inserts)     │
-- └─────────────────────────────────────────────────────────────────────────┘

ALTER TABLE reports      ALTER COLUMN status       SET DEFAULT 'pending';
ALTER TABLE reports      ALTER COLUMN severity      SET DEFAULT 'warning';
ALTER TABLE broadcasts   ALTER COLUMN severity      SET DEFAULT 'warning';
ALTER TABLE broadcasts   ALTER COLUMN target_zone   SET DEFAULT 'all';
ALTER TABLE broadcasts   ALTER COLUMN recipient_count SET DEFAULT 0;
ALTER TABLE zones        ALTER COLUMN risk_level    SET DEFAULT 'low';


-- ┌─────────────────────────────────────────────────────────────────────────┐
-- │  SECTION 5 — MISSING AUDIT TIMESTAMPS                                  │
-- └─────────────────────────────────────────────────────────────────────────┘

-- 5.1  reports — track when a report's status was last changed.
ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE reports SET updated_at = submitted_at WHERE updated_at IS NULL;

-- 5.2  blogs — track when a blog was last edited.
ALTER TABLE blogs
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- 5.3  user_settings — track when a setting was last toggled.
ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();


-- ┌─────────────────────────────────────────────────────────────────────────┐
-- │  SECTION 6 — REFERENTIAL INTEGRITY NOTES                               │
-- │  (Not applied — data cleanup required first)                           │
-- └─────────────────────────────────────────────────────────────────────────┘

-- events.node_id has 2,875 orphaned rows (node_ids from MongoDB that were
-- not included in the 111-node export). A direct FK would fail until these
-- are cleaned up. Deferred to FLOOD-09.
--
--   Cleanup preview:
--     SELECT DISTINCT node_id FROM events
--     WHERE node_id NOT IN (SELECT node_id FROM nodes);
--
--   After cleanup:
--     ALTER TABLE events
--       ADD CONSTRAINT fk_events_node_id
--       FOREIGN KEY (node_id) REFERENCES nodes(node_id) ON DELETE CASCADE;
--
-- commands / heartbeats / master_logs reference "Node-1", "Node-2" (test
-- data from MongoDB) which are not real node_ids. Same cleanup required.

COMMIT;


-- ┌─────────────────────────────────────────────────────────────────────────┐
-- │  SECTION 7 — COMPOSITE & PARTIAL INDEXES                               │
-- │  MUST run outside a transaction block (CONCURRENTLY requirement).       │
-- │  Safe on a live database — does not lock tables.                       │
-- └─────────────────────────────────────────────────────────────────────────┘

-- 7.1  events: single most important index in the whole schema.
--      "Give me all events for node X ordered by time" is the hottest path.
--      Separate idx_events_node_id + idx_events_created_at are suboptimal;
--      the planner can't bitmap-merge DESC ordering efficiently.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_events_node_created
    ON events (node_id, created_at DESC);

-- 7.2  events: alert dashboard query — warning/critical events in last N days.
--      Partial index skips ~80% of rows (only levels 2 and 3 qualify).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_events_alert_created
    ON events (new_level, created_at DESC)
    WHERE new_level >= 2;

-- 7.3  heartbeats: "what is the latest status of node X?"
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_heartbeats_node_ts
    ON heartbeats (node_id, timestamp DESC);

-- 7.4  commands: command history per node.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_commands_node_ts
    ON commands (node_id, timestamp DESC);

-- 7.5  data_updates: composite index (separate ones already exist but
--      a composite is faster for range + filter queries).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_data_updates_node_ts
    ON data_updates (node_id, timestamp DESC);

-- 7.6  master_logs: IoT master node audit trail per device.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_master_logs_node_ts
    ON master_logs (node_id, timestamp DESC);

-- 7.7  broadcasts: CRM "filter by severity, most recent first".
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_broadcasts_severity_sent
    ON broadcasts (severity, sent_at DESC);

-- 7.8  reports: admin review queue — pending first, newest first.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reports_status_submitted
    ON reports (status, submitted_at DESC);

-- 7.9  password_reset_codes: only unused codes are ever looked up.
--      Partial index eliminates expired/used codes from the index entirely.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_prc_active
    ON password_reset_codes (user_id, expires_at)
    WHERE used = false;

-- 7.10  refresh_tokens: cleanup job and expiry checks.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refresh_tokens_expires
    ON refresh_tokens (expires_at);

-- 7.11  nodes: live-sensor queries always filter is_dead = false.
--       Partial index covers ~99% of real queries (dead nodes are rare).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_nodes_alive
    ON nodes (current_level, last_updated DESC)
    WHERE is_dead = false;

-- 7.12  blogs: mobile home screen only loads featured blogs.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_blogs_featured
    ON blogs (created_at DESC)
    WHERE is_featured = true;

-- 7.13  user_registered_nodes: looked up by node_id for broadcast targeting.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_registered_nodes_node
    ON user_registered_nodes (node_id);

-- 7.14  user_favourite_nodes: "list all favourites for user X" (join).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_favourite_nodes_user
    ON user_favourite_nodes (user_id);
