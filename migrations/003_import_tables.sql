-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 003: Create tables for MongoDB-exported data not covered by JPA
--
-- These tables are NOT mapped to Spring Boot entities — they are raw data
-- tables imported from the MongoDB export. The API can query them via
-- native JPQL or direct SQL if needed.
-- ─────────────────────────────────────────────────────────────────────────────

-- COMMANDS (IoT command messages sent from master to nodes)
CREATE TABLE IF NOT EXISTS commands (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_id      INTEGER,
    node_id         VARCHAR(100),
    from_master     VARCHAR(100),
    to_node         VARCHAR(100),
    action          VARCHAR(255),
    timestamp       TIMESTAMPTZ,
    status          VARCHAR(50),
    ack_received    BOOLEAN DEFAULT FALSE,
    ack_response_time TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- HEARTBEATS (node liveness pings)
CREATE TABLE IF NOT EXISTS heartbeats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id         VARCHAR(100) NOT NULL,
    timestamp       TIMESTAMPTZ,
    status          VARCHAR(50),
    checked_by      VARCHAR(100),
    response_time   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- DATA_UPDATES (raw sensor readings: water level, temperature, humidity)
CREATE TABLE IF NOT EXISTS data_updates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id         VARCHAR(100) NOT NULL,
    timestamp       TIMESTAMPTZ,
    water_level     DOUBLE PRECISION,
    temperature     DOUBLE PRECISION,
    humidity        DOUBLE PRECISION,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    raw_message     TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_data_updates_node_id    ON data_updates(node_id);
CREATE INDEX IF NOT EXISTS idx_data_updates_timestamp  ON data_updates(timestamp DESC);

-- MASTER_LOGS (master node analysis and command audit trail)
CREATE TABLE IF NOT EXISTS master_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    log_id            INTEGER,
    master_id         VARCHAR(100),
    action            VARCHAR(255),
    timestamp         TIMESTAMPTZ,
    node_id           VARCHAR(100),
    analysis_result   TEXT,
    issued_command    VARCHAR(255),
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- USER REGISTERED NODES (mobile app: customer → node subscriptions from MongoDB)
CREATE TABLE IF NOT EXISTS user_registered_nodes (
    user_id   UUID REFERENCES users(id) ON DELETE CASCADE,
    node_id   VARCHAR(100),
    PRIMARY KEY (user_id, node_id)
);
