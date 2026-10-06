-- SÜLD schema V5 (MySQL / MariaDB): player style — rank ladder, cosmetics, level rewards.
--   * one style row per player                     (PRIMARY KEY player_uuid)
--   * a cosmetic is owned at most once per player   (PRIMARY KEY player_uuid, cosmetic_id)
--   * credits: store currency, spent only on cosmetics (never negative: CHECK)
--   * claimed_levels: bit N set = the level-N reward of /lvlup was paid out (exactly once)
CREATE TABLE IF NOT EXISTS suld_player_style (
    player_uuid     CHAR(36)     NOT NULL,
    rank_id         VARCHAR(16)  NOT NULL DEFAULT 'ard',
    tag_id          VARCHAR(48)  NULL,
    name_color_id   VARCHAR(48)  NULL,
    chat_color_id   VARCHAR(48)  NULL,
    join_message_id VARCHAR(48)  NULL,
    claimed_levels  BIGINT       NOT NULL DEFAULT 0,
    credits         BIGINT       NOT NULL DEFAULT 0,
    updated_at      BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid),
    CONSTRAINT ck_style_credits CHECK (credits >= 0)
);

CREATE TABLE IF NOT EXISTS suld_player_cosmetics (
    player_uuid  CHAR(36)    NOT NULL,
    cosmetic_id  VARCHAR(48) NOT NULL,
    obtained_at  BIGINT      NOT NULL,
    PRIMARY KEY (player_uuid, cosmetic_id)
);
