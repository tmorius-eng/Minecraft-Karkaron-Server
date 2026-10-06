-- SÜLD schema V3 (MySQL / MariaDB): clans (овог) and clan membership.
-- Invariants enforced by the database, not just the service:
--   * a player belongs to at most one clan  (PRIMARY KEY on suld_clan_members.player_uuid)
--   * clan names are unique case-insensitively (UNIQUE name_key = lower(name))
--   * clan tags are unique                      (UNIQUE tag, stored upper-cased)
--   * deleting a clan removes its memberships   (ON DELETE CASCADE)
CREATE TABLE IF NOT EXISTS suld_clans (
    clan_id     CHAR(36)     NOT NULL,
    name        VARCHAR(24)  NOT NULL,
    name_key    VARCHAR(24)  NOT NULL,
    tag         VARCHAR(5)   NOT NULL,
    created_at  BIGINT       NOT NULL,
    exp         BIGINT       NOT NULL DEFAULT 0,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id),
    UNIQUE KEY uq_suld_clans_name_key (name_key),
    UNIQUE KEY uq_suld_clans_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS suld_clan_members (
    player_uuid   CHAR(36)     NOT NULL,
    clan_id       CHAR(36)     NOT NULL,
    last_name     VARCHAR(16)  NOT NULL,
    clan_rank     VARCHAR(16)  NOT NULL,
    contribution  BIGINT       NOT NULL DEFAULT 0,
    joined_at     BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid),
    KEY idx_suld_clan_members_clan (clan_id),
    CONSTRAINT fk_suld_clan_members_clan FOREIGN KEY (clan_id) REFERENCES suld_clans (clan_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
