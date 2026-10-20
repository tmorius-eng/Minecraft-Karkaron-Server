-- SÜLD schema V32 (MySQL / MariaDB, H2 in MySQL mode): side quests (docs/SIDE_QUESTS.md).
--   * one row per player and side quest, written when it is taken (the primary key refuses a second take);
--   * state ACTIVE until it is handed in, then DONE by a compare-and-set update (WHERE state = 'ACTIVE'), so a
--     quest is handed in once whatever the clicks; abandoning deletes the ACTIVE row; a DONE row is never deleted by play;
--   * paid_at: 0 while a DONE row still owes its reward, then the time the payment claimed it (WHERE paid_at = 0), so a
--     reward is paid once, and a player who left before it was paid is paid at the next load.
--   * the state's two values are kept by the code (no CHECK here, as V17 and V31).
CREATE TABLE IF NOT EXISTS suld_side_quests (
    player_uuid   CHAR(36)     NOT NULL,
    quest_id      VARCHAR(64)  NOT NULL,
    state         VARCHAR(8)   NOT NULL,
    progress      INT          NOT NULL DEFAULT 0,
    started_at    BIGINT       NOT NULL,
    completed_at  BIGINT       NOT NULL DEFAULT 0,
    paid_at       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, quest_id),
    KEY ix_side_quests_quest (quest_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
