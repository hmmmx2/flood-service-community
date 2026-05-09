-- Widen users.avatar_url so the in-app file uploader can store a small
-- base64-encoded data URL inline (typical 256x256 JPEG → ~50-70KB after
-- client-side resize). The legacy 500-char URL form still fits.
--
-- Hibernate's ddl-auto: update may run this automatically once the entity
-- swaps to columnDefinition = "TEXT", but apply manually if running on a
-- DB where ddl-auto is locked down.

ALTER TABLE users
    ALTER COLUMN avatar_url TYPE TEXT;
