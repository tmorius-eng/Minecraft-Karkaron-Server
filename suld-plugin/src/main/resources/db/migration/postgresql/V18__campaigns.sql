-- SÜLD schema V18 (PostgreSQL): Их Хуралдай — the weekly vote and the week's campaign (docs/KHURALDAI.md).
--   * one vote cycle per ISO week; closing it is a version compare-and-set
--   * one ballot per player per cycle, enforced by the primary key
--   * one campaign per cycle; state changes are compare-and-set, the total is added to (commutative, never CAS)
--   * each contributor's units; a ledger of the flushed deltas; the world-state flags campaigns leave behind
CREATE TABLE IF NOT EXISTS suld_khuraldai_cycles (
    cycle_id      VARCHAR(16)  NOT NULL,
    options       TEXT         NOT NULL,
    opened_at     BIGINT       NOT NULL,
    closes_at     BIGINT       NOT NULL,
    closed_at     BIGINT       NULL,
    winner        VARCHAR(64)  NULL,
    tally         TEXT         NOT NULL DEFAULT '',
    voters        INT          NOT NULL DEFAULT 0,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (cycle_id)
);

CREATE TABLE IF NOT EXISTS suld_khuraldai_votes (
    cycle_id      VARCHAR(16)  NOT NULL,
    player_uuid   UUID         NOT NULL,
    option_id     VARCHAR(64)  NOT NULL,
    player_level  INT          NOT NULL,
    at            BIGINT       NOT NULL,
    PRIMARY KEY (cycle_id, player_uuid)
);
CREATE INDEX IF NOT EXISTS idx_khuraldai_votes_option ON suld_khuraldai_votes (cycle_id, option_id);

CREATE TABLE IF NOT EXISTS suld_campaigns (
    cycle_id           VARCHAR(16)  NOT NULL,
    campaign_id        VARCHAR(64)  NOT NULL,
    state              VARCHAR(16)  NOT NULL,
    starts_at          BIGINT       NOT NULL,
    ends_at            BIGINT       NOT NULL,
    resolved_at        BIGINT       NULL,
    target             BIGINT       NOT NULL,
    total              BIGINT       NOT NULL DEFAULT 0,
    effect_applied_at  BIGINT       NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (cycle_id),
    CONSTRAINT ck_campaign_state CHECK (state IN ('SCHEDULED', 'ACTIVE', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS suld_campaign_totals (
    cycle_id           VARCHAR(16)  NOT NULL,
    player_uuid        UUID         NOT NULL,
    player_name        VARCHAR(16)  NOT NULL,
    clan_uuid          UUID         NULL,
    units              BIGINT       NOT NULL DEFAULT 0,
    reward_tier        INT          NULL,
    reward_claimed_at  BIGINT       NULL,
    PRIMARY KEY (cycle_id, player_uuid)
);
CREATE INDEX IF NOT EXISTS idx_campaign_totals_units ON suld_campaign_totals (cycle_id, units);

CREATE TABLE IF NOT EXISTS suld_campaign_ledger (
    id            BIGSERIAL    NOT NULL,
    cycle_id      VARCHAR(16)  NOT NULL,
    player_uuid   UUID         NOT NULL,
    units         BIGINT       NOT NULL,
    at            BIGINT       NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_campaign_ledger_cycle ON suld_campaign_ledger (cycle_id, at);

CREATE TABLE IF NOT EXISTS suld_world_state (
    flag_key       VARCHAR(64)  NOT NULL,
    flag_value     TEXT         NOT NULL,
    source_cycle   VARCHAR(16)  NOT NULL,
    expires_cycle  VARCHAR(16)  NULL,
    set_at         BIGINT       NOT NULL,
    PRIMARY KEY (flag_key)
);
