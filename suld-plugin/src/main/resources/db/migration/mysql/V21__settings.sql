-- SÜLD schema V21: per-player settings of the «Тохиргоо» page (docs/UI_HUB.md). A bitmask of the settings a player
-- turned OFF (mn.suld.api.style.PlayerSetting), so every existing row keeps every setting on.
ALTER TABLE suld_player_style ADD COLUMN settings_off BIGINT NOT NULL DEFAULT 0;
