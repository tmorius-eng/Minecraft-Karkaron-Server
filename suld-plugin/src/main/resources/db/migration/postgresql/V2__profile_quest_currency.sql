-- SÜLD schema V2 (PostgreSQL): add currency + active-quest tracking to profiles.
ALTER TABLE suld_profiles
    ADD COLUMN currency        BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN active_quest_id VARCHAR(64),
    ADD COLUMN quest_progress  INT         NOT NULL DEFAULT 0,
    ADD COLUMN quest_completed  BOOLEAN     NOT NULL DEFAULT FALSE;
