-- SÜLD schema V4 (PostgreSQL): world-unique relics (Vertical Slice 4).
-- Extends the V1 world-unique tables. Database-enforced invariants:
--   * one row per relic key, one minted item_uuid             (V1 PK + UNIQUE)
--   * a relic is either UNCLAIMED (no owner) or OWNED (owner)  (CHECK)
--   * a player bears at most one relic at a time                (UNIQUE owner_uuid; NULLs allowed)
--   * ownership changes are version compare-and-set updates     (service + version column)
ALTER TABLE suld_world_unique_items
    ADD COLUMN owner_name   VARCHAR(16),
    ADD COLUMN shrine_world VARCHAR(64),
    ADD COLUMN shrine_x     INT,
    ADD COLUMN shrine_y     INT,
    ADD COLUMN shrine_z     INT;

ALTER TABLE suld_world_unique_items
    ADD CONSTRAINT ck_wui_state CHECK (state IN ('UNCLAIMED', 'OWNED')),
    ADD CONSTRAINT ck_wui_owner_matches_state CHECK ((state = 'OWNED') = (owner_uuid IS NOT NULL));

CREATE UNIQUE INDEX IF NOT EXISTS uq_wui_one_relic_per_owner ON suld_world_unique_items (owner_uuid);

ALTER TABLE suld_world_unique_history
    ADD COLUMN actor  VARCHAR(64)  NOT NULL DEFAULT '',
    ADD COLUMN detail VARCHAR(255) NOT NULL DEFAULT '';
