-- SÜLD schema V19 (PostgreSQL): Баатрын Хроник (docs/KHURALDAI.md §Хроник).
--   * suld_chronicle is append-only; an entry is hidden, never deleted
--   * suld_chronicle_records holds the one first / record per (kind, subject): a first is an INSERT that succeeds or
--     hits the primary key, a record is replaced only when better, both in one transaction with the log row
CREATE TABLE IF NOT EXISTS suld_chronicle (
    id            BIGSERIAL     NOT NULL,
    kind          VARCHAR(32)   NOT NULL,
    subject_key   VARCHAR(64)   NOT NULL,
    player_uuid   UUID          NULL,
    player_name   VARCHAR(16)   NOT NULL DEFAULT '',
    party         TEXT          NOT NULL DEFAULT '',
    clan_tag      VARCHAR(8)    NOT NULL DEFAULT '',
    entry_value   BIGINT        NULL,
    detail        VARCHAR(255)  NOT NULL DEFAULT '',
    cycle_id      VARCHAR(16)   NOT NULL DEFAULT '',
    at            BIGINT        NOT NULL,
    hidden        SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_chronicle_kind ON suld_chronicle (kind, at);
CREATE INDEX IF NOT EXISTS idx_chronicle_player ON suld_chronicle (player_uuid, at);
CREATE INDEX IF NOT EXISTS idx_chronicle_at ON suld_chronicle (at);

CREATE TABLE IF NOT EXISTS suld_chronicle_records (
    kind          VARCHAR(32)   NOT NULL,
    subject_key   VARCHAR(64)   NOT NULL,
    entry_id      BIGINT        NOT NULL,
    entry_value   BIGINT        NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (kind, subject_key)
);
