-- Threaded comments, soft-delete, votes (PostgreSQL).
-- Safe to re-run: uses IF NOT EXISTS style via separate checks in application or manual DBA review.

ALTER TABLE community_comments
    ADD COLUMN IF NOT EXISTS parent_comment_id UUID REFERENCES community_comments (id) ON DELETE SET NULL;

ALTER TABLE community_comments
    ADD COLUMN IF NOT EXISTS content_backup TEXT;

ALTER TABLE community_comments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

ALTER TABLE community_comments
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE community_comments
    ADD COLUMN IF NOT EXISTS deleted_by UUID REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE community_comments
    ADD COLUMN IF NOT EXISTS score INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS community_comment_votes (
    id UUID PRIMARY KEY,
    comment_id UUID NOT NULL REFERENCES community_comments (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    value SMALLINT NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_comment_vote UNIQUE (comment_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_comment_votes_comment ON community_comment_votes (comment_id);
CREATE INDEX IF NOT EXISTS idx_comments_post_parent ON community_comments (post_id, parent_comment_id);
