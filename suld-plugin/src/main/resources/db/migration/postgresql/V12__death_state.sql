-- SÜLD schema V12 (PostgreSQL): persistent hardcore death (docs/DEATH_AND_RECOVERY.md).
--   * one row per death; real-world epoch millis; the lock ends at locked_until whatever the server did
--   * per-player sequence                                   (UNIQUE player_uuid, death_seq)
--   * at most one LOCKED death per player                    (partial UNIQUE index)
--   * state changes are version compare-and-set updates      (service + version column)
--   * the open wound of each player                          (suld_death_recovery)
CREATE TABLE IF NOT EXISTS suld_death_state (
    death_id      UUID         NOT NULL,
    player_uuid   UUID         NOT NULL,
    death_seq     INT          NOT NULL,
    died_at       BIGINT       NOT NULL,
    locked_until  BIGINT       NOT NULL,
    world         VARCHAR(64)  NOT NULL DEFAULT '',
    x             INT          NOT NULL DEFAULT 0,
    y             INT          NOT NULL DEFAULT 0,
    z             INT          NOT NULL DEFAULT 0,
    cause         VARCHAR(64)  NOT NULL DEFAULT '',
    level         INT          NOT NULL,
    ascension     INT          NOT NULL DEFAULT 0,
    wound_after   INT          NOT NULL DEFAULT 0,
    revive_state  VARCHAR(16)  NOT NULL,
    recovered_at  BIGINT       NULL,
    version       INT          NOT NULL,
    PRIMARY KEY (death_id),
    CONSTRAINT uq_death_seq UNIQUE (player_uuid, death_seq),
    CONSTRAINT ck_death_state CHECK (revive_state IN ('LOCKED', 'RECOVERED', 'ADMIN_REVIVED', 'RESET', 'INSTANT_REVIVED')),
    CONSTRAINT ck_death_lock CHECK (locked_until >= died_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_death_one_lock ON suld_death_state (player_uuid) WHERE revive_state = 'LOCKED';

CREATE TABLE IF NOT EXISTS suld_death_recovery (
    player_uuid   UUID              NOT NULL,
    wound_stacks  INT               NOT NULL DEFAULT 0,
    heal_minutes  DOUBLE PRECISION  NOT NULL DEFAULT 0,
    updated_at    BIGINT            NOT NULL,
    PRIMARY KEY (player_uuid),
    CONSTRAINT ck_wound CHECK (wound_stacks >= 0 AND heal_minutes >= 0)
);
