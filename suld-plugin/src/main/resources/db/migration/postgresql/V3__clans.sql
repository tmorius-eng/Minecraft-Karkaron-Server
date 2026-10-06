-- SÜLD schema V3 (PostgreSQL): clans (овог) and clan membership.
-- Invariants enforced by the database, not just the service:
--   * a player belongs to at most one clan  (PRIMARY KEY on suld_clan_members.player_uuid)
--   * clan names are unique case-insensitively (UNIQUE name_key = lower(name))
--   * clan tags are unique                      (UNIQUE tag, stored upper-cased)
--   * deleting a clan removes its memberships   (ON DELETE CASCADE)
CREATE TABLE IF NOT EXISTS suld_clans (
    clan_id     UUID         NOT NULL,
    name        VARCHAR(24)  NOT NULL,
    name_key    VARCHAR(24)  NOT NULL,
    tag         VARCHAR(5)   NOT NULL,
    created_at  BIGINT       NOT NULL,
    exp         BIGINT       NOT NULL DEFAULT 0,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id),
    CONSTRAINT uq_suld_clans_name_key UNIQUE (name_key),
    CONSTRAINT uq_suld_clans_tag UNIQUE (tag)
);

CREATE TABLE IF NOT EXISTS suld_clan_members (
    player_uuid   UUID         NOT NULL,
    clan_id       UUID         NOT NULL REFERENCES suld_clans (clan_id) ON DELETE CASCADE,
    last_name     VARCHAR(16)  NOT NULL,
    clan_rank     VARCHAR(16)  NOT NULL,
    contribution  BIGINT       NOT NULL DEFAULT 0,
    joined_at     BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_suld_clan_members_clan ON suld_clan_members (clan_id);
