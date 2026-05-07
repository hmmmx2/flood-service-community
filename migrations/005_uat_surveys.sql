-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 005: UAT survey response storage
--
-- Single table for User Acceptance Testing feedback collected from BOTH the
-- community website (end-users) and the CRM (staff/admins). The questions
-- shown to each respondent are role-driven on the frontend; we store the
-- raw answer payload as JSONB so the schema can evolve without DB migrations.
--
-- Two top-level metrics are denormalised out of the JSON for fast filtering
-- and dashboard charts:
--   • satisfaction_score   1–5 stars
--   • recommend_score      0–10 NPS-style
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS uat_survey_responses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NULL,                          -- nullable: anonymous submissions allowed
    display_name        VARCHAR(120),                        -- captured at submit time, denormalised
    role                VARCHAR(40)  NOT NULL,               -- 'user' | 'admin' | 'both'
    source              VARCHAR(20)  NOT NULL,               -- 'community' | 'crm'
    answers             JSONB        NOT NULL,               -- full answer payload
    satisfaction_score  SMALLINT     NULL CHECK (satisfaction_score IS NULL OR satisfaction_score BETWEEN 1 AND 5),
    recommend_score     SMALLINT     NULL CHECK (recommend_score    IS NULL OR recommend_score    BETWEEN 0 AND 10),
    business_fit        VARCHAR(40)  NULL,                   -- 'meets' | 'partial' | 'misses'
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    app_version         VARCHAR(40),
    user_agent          TEXT
);

CREATE INDEX IF NOT EXISTS idx_uat_survey_role         ON uat_survey_responses(role);
CREATE INDEX IF NOT EXISTS idx_uat_survey_source       ON uat_survey_responses(source);
CREATE INDEX IF NOT EXISTS idx_uat_survey_submitted_at ON uat_survey_responses(submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_uat_survey_user_id      ON uat_survey_responses(user_id) WHERE user_id IS NOT NULL;
