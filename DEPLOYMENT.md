# SÜLD — Production Deployment (Vultr · Ubuntu 24.04)

This is the runbook for putting SÜLD on a **fresh Ubuntu 24.04 VPS**. Everything lives in
[`deploy/`](deploy/). Nothing here has been deployed yet: no VPS exists. See
[What has been verified](#what-has-been-verified) for which parts were tested in the cloud dev sandbox.

---

## 1. What you get

```
Players ──TCP 25565──▶ Paper 1.21.11 + SULD plugin  (user "suld", tmux session, systemd unit suld.service)
        ──TCP 8080───▶ nginx: static resource pack  (/opt/suld/pack/suld-pack-<sha1>.zip only)
                       PostgreSQL 16                 (localhost only, never exposed)
Admin   ──TCP 22─────▶ SSH (rate-limited by ufw, fail2ban)
```

| Path | Owner | Purpose |
|---|---|---|
| `/etc/suld/suld.env` | root:suld 640 | **The** configuration (incl. generated DB password). Never commit. |
| `/opt/suld/bin/` | root 755 | Installed copy of `deploy/` (scripts root executes are not writable by `suld`). |
| `/opt/suld/repo/` | suld | Git checkout used for builds. |
| `/opt/suld/server/` | suld | Paper server (worlds, `plugins/SULD.jar`, `plugins/SULD/config.yml`). |
| `/opt/suld/pack/` | suld | Content-addressed resource-pack ZIPs (newest 3 kept). |
| `/opt/suld/releases/` | suld | Staged plugin jars + `current.state`/`previous.state` for rollback. |
| `/opt/suld/backups/` | suld 750 | Daily backups (`BACKUP_KEEP`, default 14). |
| `/opt/suld/run/tmux.sock` | suld | Console socket. |

Scripts (all support `-h` via their header comment):

| Script | Does |
|---|---|
| `deploy.sh [--dry-run] [--skip-firewall]` | Full first install; idempotent (safe to re-run). |
| `update.sh [--warn S] [--paper] [--branch B] [--skip-tests] [--force] [--rollback]` | Zero-surprise update with automatic rollback. |
| `start.sh` / `stop.sh [--warn S]` / `restart.sh [--warn S]` | Service control (graceful stop saves worlds + all profiles). |
| `console.sh` | Attach to the live console (detach: **Ctrl-b then d**, never Ctrl-c). |
| `op.sh <name…>` / `op.sh --deop <name>` | Grant/revoke operator (validated names). |
| `backup.sh [--no-db] [--no-world]` | Online-consistent backup (save-all → save-off → archive → save-on). |
| `health-check.sh [--quiet] [--no-pack]` | Process, protocol, plugin, DB, pack, disk and backup checks; exit 1 on failure. |
| `firewall.sh [--ssh-port N]` | ufw: only SSH, Minecraft, pack port. |
| `build-pack.sh` | Validate + build a reproducible pack ZIP (`PACK_FILE=`/`PACK_SHA1=`). |

---

## 2. Before you buy the VPS

**Plan size.** RAM is the limit. `deploy.sh` refuses to run if RAM < `JVM_MAX` + 768 MB.

| Players (concurrent) | Vultr plan (Cloud Compute, Regular or High Frequency) | `JVM_MIN`/`JVM_MAX` |
|---|---|---|
| test / ≤ 10 | 4 GB RAM, 2 vCPU | `2G` / `2560M` |
| ≤ 30 | 8 GB RAM, 4 vCPU (High Frequency preferred) | `3G` / `5G` |
| ≤ 60 | 16 GB RAM, 4–6 vCPU (High Frequency / dedicated) | `6G` / `10G` |

Minecraft's main thread is single-threaded, so **High Frequency** (faster single core) helps more
than extra cores. Avoid the 1–2 GB plans.

**Region.** Pick the region closest to your players. For players in Mongolia, compare Vultr's East-Asia
regions (e.g. Seoul, Tokyo) and test ping from Ulaanbaatar before you commit.

**OS image.** Ubuntu **24.04 LTS** x64. Add your **SSH key** when creating the instance.

**Decide now** (you put these in `/etc/suld/suld.env`):
- `ACCEPT_EULA=true`. You must read and accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) yourself.
- `ADMIN_PLAYERS` defaults to `qeevr_`. These names become operator after the first boot.
- `REPO_BRANCH`. Use the branch you want live. Until the work is merged into `main`, that is
  `claude/gracious-lamport-5aaowc`.
- `REPO_URL`. If the repository is **private**, use an SSH deploy key (see §9). Never put a token in the URL.
- `PUBLIC_HOST` is auto-detected (the Vultr public IPv4 is on the NIC). Set a DNS name instead if you have one.

---

## 3. Vultr cloud firewall (recommended, in addition to ufw)

In the Vultr panel, create a Firewall Group and attach it to the instance:

| Protocol | Port | Source |
|---|---|---|
| TCP | 22 | your IP(s) if static, else anywhere |
| TCP | 25565 | anywhere |
| TCP | 8080 | anywhere (resource pack download) |

Do not open 5432 (PostgreSQL).

---

## 4. First deployment (about 10–20 minutes)

```bash
ssh root@<SERVER_IP>

apt-get update && apt-get -y upgrade && reboot      # start from a patched system
# reconnect, then:
apt-get install -y git
git clone --branch claude/gracious-lamport-5aaowc https://github.com/tmorius-eng/Minecraft-Karkaron-Server.git /root/suld-src
cd /root/suld-src

# Optional: see exactly what will happen, changes nothing
./deploy/deploy.sh --dry-run

# Run 1: creates /etc/suld/suld.env (DB password generated, public IP detected), then STOPS.
./deploy/deploy.sh

nano /etc/suld/suld.env       # set ACCEPT_EULA=true, check REPO_BRANCH, JVM_MIN/MAX, ADMIN_PLAYERS, MOTD

# Run 2: installs and starts everything
./deploy/deploy.sh
```

Run 2 performs these steps, in order:
1. Preflight: OS, EULA, RAM against `JVM_MAX`, ≥ 8 GB free disk, network reachability.
2. apt installs: OpenJDK 21, PostgreSQL 16, nginx, tmux, ufw, fail2ban, git and others. It verifies Java 21.
3. Creates the system user `suld` (no login shell) and the directory tree.
4. Clones `REPO_URL@REPO_BRANCH` into `/opt/suld/repo`.
5. Sets up PostgreSQL: creates the role and database, re-syncs the password, and tests login over TCP. This step is idempotent.
6. Runs `./gradlew build` (compile + all unit tests), or uses `PLUGIN_JAR` if you set it.
7. Downloads Paper `MC_VERSION` from the official fill API and **verifies its SHA-256**.
8. Writes `eula.txt` and `server.properties` (first time only), installs `plugins/SULD.jar`, extracts the
   plugin's default `config.yml`, and syncs the `database.*` section from the env file.
9. Validates the resource pack, builds it, serves it with nginx on `PACK_PORT`, and writes `resource-pack.url/sha1`.
10. Installs `/opt/suld/bin`, `suld.service` and `suld-backup.timer` (daily 04:30 UTC).
11. Configures ufw: SSH allowed and rate-limited *before* enabling, plus 25565 and 8080.
12. Starts the server, waits for `Done`, runs the health check, and ops `ADMIN_PLAYERS`.

Then connect with Minecraft Java **1.21.11** to `<SERVER_IP>:25565`.

> Small plan / slow build? Build on your own machine (`./gradlew :suld-plugin:shadowJar`), `scp` the jar
> to the server, and set `PLUGIN_JAR=/root/suld-plugin.jar` in the env file before run 2.

---

## 5. Daily operations

```bash
sudo /opt/suld/bin/health-check.sh          # everything OK?
sudo /opt/suld/bin/console.sh               # live console (Ctrl-b d to leave)
sudo /opt/suld/bin/op.sh SomePlayer         # add an operator
sudo /opt/suld/bin/restart.sh --warn 60     # announce, wait 60s, restart
sudo /opt/suld/bin/stop.sh                  # keep it down (an in-game /stop auto-restarts)
systemctl status suld ; journalctl -u suld -n 100
tail -f /opt/suld/server/logs/latest.log
systemctl list-timers suld-backup.timer
```

The health check covers:
- systemd unit, tmux session, java process
- a real Minecraft server-list ping
- SULD plugin enabled; a warning if storage is MEMORY
- PostgreSQL query (schema version); a warning if PostgreSQL listens on a public interface
- resource pack reachable over HTTP **with a matching SHA-1**
- disk and memory
- backup freshness

Hook it into cron or uptime monitoring with `--quiet`, which only prints problems.

---

## 6. Updating

```bash
sudo /opt/suld/bin/update.sh --warn 60          # newest commit of REPO_BRANCH
sudo /opt/suld/bin/update.sh --paper --warn 60  # also newest Paper build of MC_VERSION
sudo /opt/suld/bin/update.sh --rollback         # go back to the previous release
```

1. The script backs up, fetches the code, and builds it with tests and pack validation **while the server keeps running**.
   If any of that fails, it restores the old checkout and leaves the server untouched.
2. It stages the jar into `/opt/suld/releases`, stops the server (after `--warn`), swaps the jar and pack config, and starts it again.
3. If the new version doesn't boot within 5 minutes or fails the health check, it **reinstalls the
   previous release automatically** and exits non-zero.

**Database:** migrations are forward-only and additive, and run on plugin start. A jar rollback does not undo
them. The pre-update backup is the real database rollback (§7).

To change `MC_VERSION`: edit the env file, then run `update.sh --paper --force`. Major Minecraft versions also
need a matching Paper API version in the build. Treat that as a code change, not just an ops change.

---

## 7. Backups and restore

`suld-backup.timer` runs `backup.sh` daily. Each backup is a directory
`/opt/suld/backups/suld-<UTC timestamp>/` containing:
- `database.sql.gz`: `pg_dump`, verified with `gzip -t`
- `server.tar.gz`: worlds, `ops.json`, `server.properties`, plugin config and data. Logs, caches,
  libraries and the Paper jar are excluded because they can be downloaded again.
- `SHA256SUMS`

Manual backup: `sudo -u suld /opt/suld/bin/backup.sh`.

**Copy backups off the server.** A backup on the same disk is not disaster recovery. Options:
Vultr snapshots or automatic backups (panel), or `rclone` to Vultr Object Storage / S3 on a timer.

**Restore** (choose the backup directory as `B`):

```bash
sudo systemctl stop suld
B=/opt/suld/backups/suld-20261006-043000
cd "$B" && sha256sum -c SHA256SUMS
set -a; source /etc/suld/suld.env; set +a

# Database: recreate empty, then load the dump
sudo -u postgres dropdb "$DB_NAME"
sudo -u postgres createdb -O "$DB_USER" "$DB_NAME"
zcat "$B/database.sql.gz" | PGPASSWORD="$DB_PASS" psql -X -q -v ON_ERROR_STOP=1 \
     -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" >/dev/null

# Server files: keep the broken copy aside, unpack, bring back the Paper jar
sudo mv "$SERVER_DIR" "$SERVER_DIR.broken-$(date +%s)"
sudo install -d -o suld -g suld "$SERVER_DIR"
sudo -u suld tar -C "$SERVER_DIR" -xzf "$B/server.tar.gz"
sudo cp "$SERVER_DIR".broken-*/paper.jar "$SERVER_DIR"/ && sudo chown suld:suld "$SERVER_DIR/paper.jar"
# (or: sudo /opt/suld/bin/update.sh --paper --force   to download Paper again)

sudo /opt/suld/bin/start.sh && sudo /opt/suld/bin/health-check.sh
```

The database restore path (a dump from an online backup into a fresh database) was exercised in the dev sandbox.

---

## 8. Resource pack

- `build-pack.sh` runs `tools/validation/run_all.py`: registry, pack structure, and the size budget
  (no raw GLB/FBX/OBJ, texture limits). It then builds a **reproducible** ZIP with fixed timestamps and
  sorted entries, so the same content always gets the same SHA-1.
- The file name contains the hash (`suld-pack-<sha1>.zip`). nginx serves it with `immutable` caching, and
  clients can never get a stale pack after an update.
- The plugin sends the pack on join. `resource-pack.required` stays `false` by default. Set it to `true` in
  `/opt/suld/server/plugins/SULD/config.yml` once the pack is final, then run `restart.sh`.
- With a domain and TLS later (e.g. Caddy or certbot in front of nginx), set `PUBLIC_HOST` to the domain
  and re-run `update.sh --force`.

---

## 9. Security notes

- **Secrets:** the DB password exists only in `/etc/suld/suld.env` (root:suld 640) and in the plugin config
  (`config.yml`, mode 600). Scripts never print it. It reaches `psql`/`pg_dump` through `PGPASSWORD`
  and the config through `env:DB_PASS`, never through argv.
- **Least privilege:** Paper runs as `suld` with no login shell, under a hardened systemd unit
  (`NoNewPrivileges`, `ProtectSystem=full`, `ProtectHome`). Scripts that root executes live in
  root-owned `/opt/suld/bin`, so a compromised game process cannot plant code that root later runs.
- **Trust root:** updates build whatever `REPO_BRANCH` contains. Protect that branch on GitHub.
- **Network:** PostgreSQL is localhost only (the health check warns otherwise). RCON and query are off.
  ufw allows only 22/25565/8080, and fail2ban protects SSH.
- **SSH hardening** (do it yourself after confirming key login works): in `/etc/ssh/sshd_config` set
  `PasswordAuthentication no` and `PermitRootLogin prohibit-password`, then `systemctl reload ssh`.
- **Private repo:** create a read-only *deploy key*:
  `sudo -u suld ssh-keygen -t ed25519 -f /opt/suld/.ssh/id_ed25519 -N ''`.
  Add the `.pub` key to GitHub → Settings → Deploy keys, then set
  `REPO_URL=git@github.com:tmorius-eng/Minecraft-Karkaron-Server.git`.
- **`online-mode=true`** is the production default (real Mojang accounts). Never run a public server offline.

---

## 10. Troubleshooting

| Symptom | Look at / fix |
|---|---|
| `deploy.sh` stops after creating the env file | Expected on the first run. Edit the file and run it again. |
| "RAM … too small" | Lower `JVM_MAX` or choose a bigger plan (§2). |
| Build fails on the server | `sudo -u suld bash -c 'cd /opt/suld/repo && ./gradlew build'` for full output. Or use `PLUGIN_JAR`. |
| Server never prints `Done` | `tail -n 200 /opt/suld/server/logs/latest.log`, `journalctl -u suld -n 100`. |
| "SULD plugin failed to initialise" | Usually DB credentials: compare `/etc/suld/suld.env` with `plugins/SULD/config.yml`, then re-run `deploy.sh` (it re-syncs). |
| Players get "resource pack failed" | `health-check.sh` (pack line), `curl -I http://<IP>:8080/<file>`, Vultr firewall port 8080. |
| `op.sh` says "NOT opped" | The name must be an existing Java account in online mode. Check the spelling. |
| Server keeps restarting | Restarts are capped at 5 per 10 minutes. `systemctl status suld`, `journalctl -u suld`. |
| Locked out by the firewall | Use the Vultr web console → `ufw allow 22/tcp`. firewall.sh adds SSH *before* enabling ufw to prevent this. |

---

## What has been verified

| Item | How | Status |
|---|---|---|
| All scripts parse | `bash -n` | ✅ |
| Full `deploy.sh --dry-run` plan | executed end to end | ✅ |
| systemd units | `systemd-analyze verify` (only "executable missing", expected off-VPS) | ✅ |
| PostgreSQL role/db SQL, idempotent ×2, TCP login | real PostgreSQL 16 | ✅ |
| Reproducible pack build + validation | built twice, mtimes changed, identical SHA-1 | ✅ |
| Config editor (DB + pack keys, missing key = error, secrets via env) | real `config.yml` | ✅ |
| `start.sh --direct` / `stop.sh --direct` (tmux, graceful save) | real Paper 1.21.11 + SULD as an unprivileged user | ✅ |
| `health-check.sh` | same live server: ping, plugin on PostgreSQL (schema v2), pack SHA-1 over HTTP | ✅ (systemd check fails off-VPS, as expected) |
| `backup.sh` online + rotation + checksums | 3 runs, keep=2, save-off/save-on seen in log | ✅ |
| Restore of the DB dump into a fresh database | 6 tables, schema v2 | ✅ |
| `op.sh` op/deop + name validation | live server (`qeevr_`) | ✅ |
| apt installs, nginx, ufw, fail2ban, systemd lifecycle, `update.sh` end to end | needs a real Ubuntu 24.04 VM | ⏳ first real deploy |
