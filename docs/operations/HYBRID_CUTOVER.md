# Switching a server between AUTH_MODE=online and AUTH_MODE=hybrid

`docs/AUTHENTICATION.md` explains the two account modes. This page is the one-off procedure for a server that already
has players. **Rehearse it on the dev host first** (`tools/loadtest/hybrid-dev.sh`), never on production data.

## 1. What changes for players

In `online` mode a player is the random (v4) UUID of their Microsoft account. In `hybrid` mode every player is the
offline (v3) UUID of their name: `UUID.nameUUIDFromBytes("OfflinePlayer:" + name)`, case kept. So after the switch a
returning premium player **arrives as a new, empty character** unless their data is moved to the new UUID (§3).
Everything is keyed by UUID: the SÜLD tables in PostgreSQL, Paper's `world/playerdata/<uuid>.dat`,
`world/stats/<uuid>.json`, `world/advancements/<uuid>.json`, LuckPerms, `plugins/SULD/owners.yml`, `ops.json`.

Before alpha (test profiles only) the simple path is a fresh start: switch, and let everyone register.

## 2. Switch

1. Announce it. Tell every player: «Сервер шинэ нэвтрэлт рүү шилжиж байна: эхний удаа орохдоо нууц үгээ үүсгэнэ
   (бүртгэл). Microsoft данстай бол нэвтэрсний дараа `/premium confirm`.»
2. `sudo /opt/suld/bin/backup.sh` (world + database, `docs/operations/RESTORE_DRILL.md`).
3. `sudo /opt/suld/bin/stop.sh`.
4. In `/etc/suld/suld.env`: `AUTH_MODE=hybrid` (leave the two secrets blank: `deploy.sh` generates them).
5. If players keep their characters: §3 now, with the server stopped.
6. `sudo ./deploy/deploy.sh` from the repo checkout. It moves Paper to `127.0.0.1:$PAPER_PORT`, seeds the Velocity
   forwarding, installs AuthMe on both sides and the proxy on `SERVER_PORT`, tunes AuthMe after its first boot and
   runs `health-check.sh` (the hybrid checks: loopback bind, the signed forwarding, mode-600 secrets, the proxy,
   `Authentication: HYBRID_AUTHME`, AuthMe's table in PostgreSQL).
7. Register the owner names before anyone else can: console `authme register <name> <password>` for every name in
   `owners:` (the server log warns about each one that has no account).

Back to `online`: the same steps with `AUTH_MODE=online`; `deploy.sh` disables the proxy unit, Paper takes
`SERVER_PORT` again and stops trusting forwarding. Characters move back with §3 in the other direction.

## 3. Moving a character to its new UUID (re-key)

For each player who keeps their character: old UUID (v4, `suld_profiles.player_uuid`) → new UUID (the offline UUID of
the name they will use in hybrid mode; Python: `uuid.UUID(bytes=…)` of the MD5 rule, or SÜLD's
`PlayerIdentity.offlineUuid`). Do it **before** the player first joins in hybrid mode, or a fresh profile already
holds the new UUID.

PostgreSQL, in one transaction: list every UUID column, then update each one.

```sql
-- the columns that hold player UUIDs (review the list: a clan or item UUID is not a player)
SELECT table_name, column_name FROM information_schema.columns
 WHERE table_schema = 'public' AND data_type = 'uuid' ORDER BY 1, 2;

BEGIN;
-- for each (table, column) above that holds a player:
--   UPDATE <table> SET <column> = '<new>' WHERE <column> = '<old>';
-- generated:
SELECT format('UPDATE %I SET %I = %L WHERE %I = %L;', table_name, column_name, '<new>', column_name, '<old>')
  FROM information_schema.columns WHERE table_schema = 'public' AND data_type = 'uuid';
-- run the statements, check suld_profiles has exactly one row for <new> and none for <old>, then
COMMIT;
```

`suld_audit_log.actor` is text: leave the history under the old UUID (it records what happened then).

Files, with the server stopped: rename `world/playerdata/<old>.dat` (and `.dat_old`), `world/stats/<old>.json` and
`world/advancements/<old>.json` to `<new>`; LuckPerms: give the new UUID the old one's groups after the start
(`lp user <old> info` lists them, `lp user <name> parent add <group>` adds each); owners: the pin in `plugins/SULD/owners.yml` is dropped and re-made on the owner's first verified join.

A premium player who renames in hybrid mode needs the same re-key (old name's UUID → new name's UUID), on request.
