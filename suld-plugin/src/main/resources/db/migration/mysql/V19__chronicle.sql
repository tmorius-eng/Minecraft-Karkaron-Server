-- SÜLD schema V19: Баатрын Хроник (docs/KHURALDAI.md §Хроник).
--   * suld_chronicle is append-only; an entry is hidden, never deleted
--   * suld_chronicle_records holds the one first / record per (kind, subject): a first is an INSERT that succeeds or
--     hits the primary key, a record is replaced only when better, both in one transaction with the log row
CREATE TABLE IF NOT EXISTS suld_chronicle (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    kind          VARCHAR(32)   NOT NULL,
    subject_key   VARCHAR(64)   NOT NULL,
    player_uuid   CHAR(36)      NULL,
    player_name   VARCHAR(16)   NOT NULL,
    party         TEXT          NOT NULL,
    clan_tag      VARCHAR(8)    NOT NULL,
    entry_value   BIGINT        NULL,
    detail        VARCHAR(255)  NOT NULL,
    cycle_id      VARCHAR(16)   NOT NULL,
    at            BIGINT        NOT NULL,
    hidden        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_chronicle_kind (kind, at),
    KEY idx_chronicle_player (player_uuid, at),
    KEY idx_chronicle_at (at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS suld_chronicle_records (
    kind          VARCHAR(32)   NOT NULL,
    subject_key   VARCHAR(64)   NOT NULL,
    entry_id      BIGINT        NOT NULL,
    entry_value   BIGINT        NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (kind, subject_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
