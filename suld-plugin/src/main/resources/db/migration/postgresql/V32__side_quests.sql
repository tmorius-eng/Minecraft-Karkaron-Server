-- SÜLD schema V32 (PostgreSQL): side quests (docs/SIDE_QUESTS.md).
--   * one row per player and side quest, written when it is taken (the primary key refuses a second take);
--   * state ACTIVE until it is handed in, then DONE by a compare-and-set update (WHERE state = 'ACTIVE'), so a
--     reward is paid once whatever the clicks; abandoning deletes the ACTIVE row; a DONE row is never deleted by play.
CREATE TABLE IF NOT EXISTS suld_side_quests (
    player_uuid   UUID         NOT NULL,
    quest_id      VARCHAR(64)  NOT NULL,
    state         VARCHAR(8)   NOT NULL,
    progress      INTEGER      NOT NULL DEFAULT 0,
    started_at    BIGINT       NOT NULL,
    completed_at  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, quest_id),
    CONSTRAINT ck_side_quest_state CHECK (state IN ('ACTIVE', 'DONE'))
);
CREATE INDEX IF NOT EXISTS ix_side_quests_quest ON suld_side_quests (quest_id, state);
