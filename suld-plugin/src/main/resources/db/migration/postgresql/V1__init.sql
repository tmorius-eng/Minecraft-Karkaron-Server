-- SULD schema V1 (PostgreSQL). Timestamps are epoch milliseconds (BIGINT).

CREATE TABLE IF NOT EXISTS suld_profiles (
    player_uuid     UUID         NOT NULL,
    name            VARCHAR(16)  NOT NULL,
    class_id        VARCHAR(32),
    level           INT          NOT NULL DEFAULT 1,
    exp_into_level  BIGINT       NOT NULL DEFAULT 0,
    created_at      BIGINT       NOT NULL,
    last_seen_at    BIGINT       NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid)
);

CREATE TABLE IF NOT EXISTS suld_world_unique_items (
    item_key     VARCHAR(64)  NOT NULL,
    item_uuid    UUID         NOT NULL,
    owner_uuid   UUID,
    state        VARCHAR(16)  NOT NULL DEFAULT 'UNCLAIMED',
    acquired_at  BIGINT,
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (item_key),
    CONSTRAINT uq_world_unique_item_uuid UNIQUE (item_uuid)
);

CREATE TABLE IF NOT EXISTS suld_world_unique_history (
    id          BIGSERIAL    PRIMARY KEY,
    item_key    VARCHAR(64)  NOT NULL,
    owner_uuid  UUID,
    event       VARCHAR(24)  NOT NULL,
    at          BIGINT       NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_wuh_item ON suld_world_unique_history (item_key);

CREATE TABLE IF NOT EXISTS suld_analytics_events (
    id          BIGSERIAL    PRIMARY KEY,
    type        VARCHAR(64)  NOT NULL,
    player_uuid UUID,
    at          BIGINT       NOT NULL,
    attributes  TEXT
);
CREATE INDEX IF NOT EXISTS idx_analytics_type_at ON suld_analytics_events (type, at);

CREATE TABLE IF NOT EXISTS suld_audit_log (
    id      BIGSERIAL    PRIMARY KEY,
    at      BIGINT       NOT NULL,
    actor   VARCHAR(64)  NOT NULL,
    action  VARCHAR(64)  NOT NULL,
    target  VARCHAR(64),
    detail  TEXT
);
CREATE INDEX IF NOT EXISTS idx_audit_at ON suld_audit_log (at);
