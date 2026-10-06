-- SULD schema V1 (MySQL / MariaDB). Timestamps are epoch milliseconds (BIGINT)
-- for dialect- and timezone-independence. Statements are separated by ';' on
-- its own line region; the migrator splits on ';'.

CREATE TABLE IF NOT EXISTS suld_profiles (
    player_uuid     CHAR(36)     NOT NULL,
    name            VARCHAR(16)  NOT NULL,
    class_id        VARCHAR(32)  NULL,
    level           INT          NOT NULL DEFAULT 1,
    exp_into_level  BIGINT       NOT NULL DEFAULT 0,
    created_at      BIGINT       NOT NULL,
    last_seen_at    BIGINT       NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- World-unique items: one row per unique item identity. The PRIMARY KEY on
-- item_key and the UNIQUE item_uuid make duplication impossible at the storage
-- level — the backbone of the anti-dup guarantee.
CREATE TABLE IF NOT EXISTS suld_world_unique_items (
    item_key     VARCHAR(64)  NOT NULL,
    item_uuid    CHAR(36)     NOT NULL,
    owner_uuid   CHAR(36)     NULL,
    state        VARCHAR(16)  NOT NULL DEFAULT 'UNCLAIMED',
    acquired_at  BIGINT       NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (item_key),
    UNIQUE KEY uq_world_unique_item_uuid (item_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS suld_world_unique_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    item_key    VARCHAR(64)  NOT NULL,
    owner_uuid  CHAR(36)     NULL,
    event       VARCHAR(24)  NOT NULL,
    at          BIGINT       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_wuh_item (item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS suld_analytics_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    type        VARCHAR(64)  NOT NULL,
    player_uuid CHAR(36)     NULL,
    at          BIGINT       NOT NULL,
    attributes  TEXT         NULL,
    PRIMARY KEY (id),
    KEY idx_analytics_type_at (type, at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS suld_audit_log (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    at      BIGINT       NOT NULL,
    actor   VARCHAR(64)  NOT NULL,
    action  VARCHAR(64)  NOT NULL,
    target  VARCHAR(64)  NULL,
    detail  TEXT         NULL,
    PRIMARY KEY (id),
    KEY idx_audit_at (at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
