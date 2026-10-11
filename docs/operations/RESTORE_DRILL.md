# Backup and restore drill: current schema (V16)

## Setup

| | |
|---|---|
| Date | 2026-10-11 |
| Build | `claude/p1-alpha-stabilization` (schema V16 = V1–V15 + `suld_credit_grants`) |
| Where | The dev container used for the audit: Paper 1.21.11-132 under tmux, PostgreSQL 16.15 on 127.0.0.1:5499 (throwaway cluster), the real `deploy/backup.sh` run with a drill-only env file (`SULD_ENV=…/drill.env`; `SULD_USER=root`, local paths) |
| Not exercised here | A real VPS, systemd units, the backup timer, the service user, and a real remote host. The "off-host" target was a **separate local directory** (`BACKUP_REMOTE=<dir>`, rsync). On a VPS it must be another machine or a bucket (`user@host:/path` or `rclone:<remote>:<path>`) |

## 1. Backup (the real script)

```
SULD_ENV=drill.env bash deploy/backup.sh
==> Backup -> …/backups/suld-20261011-130032
 OK  SÜLD flushed (ok 1791723632177 profiles=1 ms=8)
 OK  database dumped (40K)
 OK  server archive written (139M)
 OK  checksums written
 OK  copied off-host to …/offhost and verified
 OK  backup complete (keeping the newest 3)
```

- **Order:** `/suld flush` (waits for `plugins/SULD/flush.done`) → `save-all flush` → `save-off` → `pg_dump` → `tar` → `save-on` → `SHA256SUMS` → off-host copy and verify → rotation.
- **Load during the backup:** one player was online.
- **Wall time:** 19.0 s.
- **Sizes:** database dump 39.6 KB gzip; `server.tar.gz` 145 MB, because the drill world includes ~22k chunks of pre-generation work.

## 2. Restore (from the off-host copy)

```bash
B=<offhost>/suld-20261011-130032
cd "$B" && sha256sum -c SHA256SUMS                # database.sql.gz: OK, server.tar.gz: OK
psql -c "CREATE DATABASE suld_restored OWNER suld"
zcat "$B/database.sql.gz" | psql -X -q -v ON_ERROR_STOP=1 -d suld_restored
mkdir srv_restored && tar -C srv_restored -xzf "$B/server.tar.gz"
cp <old>/paper.jar srv_restored/ ; cp -r <old>/{libraries,versions,cache} srv_restored/   # excluded from the archive by design
```

| Step | Time |
|---|---|
| Database restore | 0.5 s |
| Files restore | 2.6 s |

## 3. Comparison: restored vs live

**Database (`suld_restored` vs `suld_live`): every table identical** except `suld_audit_log` (697 vs 698). The extra live row is the `auth.session_end` written when the online player logged out *after* the backup.

| Table | Rows | | Table | Rows |
|---|---|---|---|---|
| suld_profiles | 98 | | suld_player_style | 43 |
| suld_player_cosmetics | 42 | | suld_credit_grants | 2 |
| suld_death_state | 2 | | suld_death_recovery | 8 |
| suld_world_unique_items | 2 | | suld_world_unique_history | 4 |
| suld_audit_log | 697 | | | |

- **Schema version:** 16.
- **Content hashes:** `md5` over every profile (uuid, name, level, EXP, coins, class) and over every credit balance were **equal**.

**Player files.** 41 `world/playerdata/*.dat`. All are byte-identical except the online player's, which the post-backup logout rewrote.

**Boot of the restored copy** (port 25600, `database: suld_restored`):
- `SULD storage ready (POSTGRESQL), migrations applied: 0`. The schema is already at V16; nothing to migrate.
- `Done (26.474s)`.
- The world-build validator repaired 4 city blocks on boot (`worldbuild repair: 4 blocks restored`). This is its normal self-check and does not depend on the restore.

**A player on the restored server.** `Crash02` joined and read «Анги: Баатар · Түвшин 2», EXP 9 %, «Зоос: 20 ₮», «Сүлд Кредит: 850 ✦». That is the same state as on the live server at backup time.

## 4. Outcome

**PASS** for this environment: backup → off-host copy → checksum → restore into a fresh database and directory → identical data → server boots on the restored copy at the current schema, and a player's progress and credits are intact.

## 5. Still required before the closed alpha (not shown here)

1. Run the same drill **on the deployment VPS**:
   - as the service user via `sudo /opt/suld/bin/backup.sh`;
   - with `BACKUP_REMOTE` pointing at a **different machine or bucket**;
   - then `DEPLOYMENT.md` "Restore" on a scratch database.
2. Record the times.
3. Check the systemd backup timer fires and its log shows "copied off-host … and verified".
4. Decide the retention for the off-host copies. `BACKUP_KEEP` rotates only the local ones.
