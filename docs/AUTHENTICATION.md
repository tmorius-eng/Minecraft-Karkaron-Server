# SÜLD Authentication & Player Identity

**SÜLD itself has no `/register`, no `/login`, and never sees a password.** A player's identity is their account
UUID, and the server runs in one of two account modes (`AUTH_MODE` in `suld.env`, decided by the owner 2026-10-18):

* **`online`** (default): only Microsoft (premium) accounts. Paper verifies every login with Mojang
  (`online-mode=true`) and the identity is the account's random v4 UUID.
* **`hybrid`**: premium **and** offline accounts, Java Edition only. An offline Velocity proxy sits in front of Paper,
  AuthMe 6 runs on both, and every account, premium or not, has the **offline UUID of its name**
  (`UUID.nameUUIDFromBytes("OfflinePlayer:" + name)`). AuthMe holds the passwords; SÜLD never sees one, and lets a
  player into its game only once AuthMe has logged them in (§2b).

SÜLD trusts nothing else, and refuses every login when the server verifies accounts in neither way.

| Rule | Where enforced |
|---|---|
| UUID is the only identity; the name is display-only | `PlayerIdentity` (equality = UUID), `DefaultProfileService` (keyed by UUID) |
| A username change never creates a new character | `ProfileService.acquire` adopts the new name on the same profile; `auth.name_change` audited |
| Taking someone's old name never grants their profile | Lookup is by UUID only (`sameNameOnAnotherUuidIsAnotherCharacter` test) |
| No unverified logins in production | `AuthPolicy` fails closed; `deploy.sh` sets the account mode's files on every deploy; `health-check.sh` fails on a reachable offline Paper |
| Spoofed or misconfigured identities are rejected | `online`: a verified server never issues offline-style (v3) UUIDs, so `AuthPolicy` denies them. `hybrid`: only the exact offline UUID of the name passes, and only through the proxy's signed forwarding |
| Behind AuthMe nothing runs before the login | `AuthGate` + `SuldJoinEvent`: every SÜLD join action waits for AuthMe's login; a waiting player can only log in |
| A name has one owner whatever its case | AuthMe `preventOtherCase`, and SÜLD's pre-login `ProfileRepository.ownerOfName` (V30) |
| No player enters the world without a loaded profile | Profile acquired in async pre-login; failure → login refused, never a blank profile |
| Quit → instant rejoin can't read stale data | Per-UUID `KeyedSequencer`: a load always waits for the previous save |
| Duplicate/concurrent logins are safe | `SessionRegistry`: the profile is released only when the **last** session ends |
| Security-relevant events are audited, with no secrets | `AuditLog` → `suld_audit_log` + server log; control characters stripped |

---

## 1. Decisions

* **2026-10-06 → 2026-10-12: online mode only.** The external audit (FIND-06, `docs/audits/2026-10-12-external/`)
  pointed out that it excludes players without a Microsoft account; the decision then was to keep online mode and run
  a player survey first (`docs/ROADMAP.md`).
* **2026-10-18: the owner chose the hybrid login** (premium and offline accounts on one server), **Java Edition only**
  (Bedrock waits: SÜLD's glyph UI does not work there), behind **Velocity + AuthMe 6.0.1** (no custom bridge: the
  pasted Gemini bridge targeted AuthMe 5.6 / Paper 1.21.4, MySQL only, hard-coded secrets, invented APIs), with
  **offline v3 UUIDs for everyone**. `AUTH_MODE=online` stays the default; a server changes mode only through
  `docs/operations/HYBRID_CUTOVER.md`. The spike that the code is built on: `docs/qa/2026-10-18-authme-spike.md`.

### Online mode: why SÜLD needs no /login

`/register` and `/login` plugins exist for **offline-mode ("cracked") servers**, where the server has
no idea who is connecting and asks for a password. In online mode SÜLD does the opposite:

- With `online-mode=true`, the **client** authenticates with Microsoft, and the **server** verifies
  that session with Mojang's session server before the player gets in. That handshake is
  Minecraft's own, so SÜLD never handles it and never sees credentials.
- When Paper fires `AsyncPlayerPreLoginEvent`, the UUID in it is already proven. SÜLD starts there.
- **SÜLD stores no passwords, tokens or Microsoft data.** It stores the UUID, the last-seen name for
  display, and game state.

Do not build SÜLD's own password login, a second hybrid bridge, or any username-based identity: the hybrid mode is
AuthMe's, behind the signed proxy, and SÜLD only asks it whether a player is logged in.

## 2. Authentication modes (decided once at startup)

`AuthenticationService.detectMode` reads the live server and Paper's proxy config:

| Mode | When | SÜLD behaviour |
|---|---|---|
| `ONLINE` | `online-mode=true` | Normal production mode. Logins allowed; UUID must be v4. |
| `VELOCITY_FORWARDED` | `online-mode=false`, but `config/paper-global.yml` has `proxies.velocity.enabled: true`, `online-mode: true` and a non-empty `secret` | An online-mode Velocity proxy. Forwarding is HMAC-signed by the proxy. UUID must be v4. |
| `HYBRID_AUTHME` | `online-mode=false`, `proxies.velocity.enabled: true`, `online-mode: false`, a non-empty `secret`, and the AuthMe plugin installed | `AUTH_MODE=hybrid` (§2b). The UUID must be exactly the offline UUID of the name. SÜLD's join waits for AuthMe's login. AuthMe not reachable → every login refused (`DENIED_AUTH_BRIDGE_UNAVAILABLE`). |
| `INSECURE` | Anything else (plain offline mode, legacy BungeeCord forwarding, which is spoofable) | **Fail closed: every login is refused.** |

`INSECURE` + `auth.allow-insecure-offline-dev-mode: true` is a **local-development escape hatch only**.
It exists so automated protocol bots can exercise the join pipeline (§7).
- Every such login is audited as `auth.insecure_dev_login`.
- A large warning is printed at startup.
- `deploy.sh` forces the flag back to `false` on every deploy, and `health-check.sh` fails if it is on.

**The v4 rule.** Microsoft/Mojang accounts always have random (version 4) UUIDs. Offline mode
derives version 3 UUIDs from the name. If a server that believes it is verifying accounts ever sees
a v3 UUID, something is spoofing it or misconfigured (e.g. a proxy without forwarding), so the login
is denied (`DENIED_UNAUTHENTICATED_UUID`).

## 2b. The hybrid mode (`AUTH_MODE=hybrid`)

```
client ──▶ Velocity :25565 (offline mode, AuthMe Velocity)
              │  premium account that opted in (/premium confirm): Mojang check here, auto-login
              │  everyone else: no check here
              ▼  modern forwarding, HMAC with forwarding.secret (Paper trusts nothing else)
           Paper 127.0.0.1:25566 (online-mode=false, never reachable from outside)
              │  AuthMe: pre-join dialog (1.21.6+ clients) or /login, /register in the world (older clients)
              │  SÜLD pre-login: offline UUID of the name? name free in every case? AuthMe reachable?
              ▼  PlayerJoinEvent → AuthGate: wait for AuthMe's LoginEvent (or a 1 s check of isAuthenticated)
           SuldJoinEvent → every SÜLD join action (class menu, rewards, items, OP, HUD, the join line)
```

* **Identity.** The UUID is the offline UUID of the exact name (case kept). AuthMe refuses another case of a
  registered name (`You should join using username Dorj, not dorj.`), and SÜLD refuses a name a stored profile holds
  in any case (`DENIED_NAME_TAKEN`, V30 index). A **premium player who renames is a new character** (new name → new
  UUID); staff move a character to a new name only with the re-key in `docs/operations/HYBRID_CUTOVER.md` §3.
* **The gate.** A joined player may still be logged out: the spike saw a wrong password and a cancelled login go
  through `PlayerJoinEvent` before the kick, and clients older than 1.21.6 log in by chat. So no SÜLD listener uses
  `PlayerJoinEvent` (the cosmetic join line aside, and it is held back); they listen to `SuldJoinEvent`, fired once
  per connection when `AuthGate` lets the player in. Until then SÜLD cancels everything but AuthMe's login commands
  (commands, chat, interaction, drops, pickups, inventory clicks, hits); AuthMe and the proxy block them too. A
  player whom AuthMe logs out again (`/logout`) is kicked: their game is never set up twice.
* **`/premium`.** Turning premium on ties the name to the Microsoft account of that name. Whoever does not own it is
  locked out (the proxy demands a Mojang login on every reconnect until it restarts; the spike, S12). SÜLD runs
  AuthMe's `/premium` only as `/premium confirm` within 2 minutes of a Mongolian warning.
* **Names are first come.** AuthMe reserves nothing: an offline player can register a name that belongs to someone's
  Microsoft account. Policy: the rightful owner proves the account to staff (logs in to a staff-run online-mode test
  server, or shows the account page); staff then `authme unregister <name>` (and re-key the profile if it was played
  by someone else). **Owner names must be registered before the server opens** (console:
  `authme register <name> <password>`): `OwnerService` warns at start for every owner name without an AuthMe
  account, because whoever registers it would be opped.
* **What is lost.** Chat signing (`enforce-secure-profile=false`: an offline proxy never asks for the client's key)
  and skins of offline accounts (none is fetched).

## 3. The join pipeline

```
Minecraft client ──(Microsoft auth)──▶ Mojang session check (Paper, online-mode)
        │
        ▼  AsyncPlayerPreLoginEvent  (async thread, BEFORE the player exists in the world)
  AuthenticationService.onPreLogin   priority HIGHEST (bans/whitelist decide first)
   1. AuthPolicy.evaluate(uuid, name)        → deny = kick + audit auth.denied
   2. SessionRegistry.begin(uuid)            → duplicate? audit auth.duplicate_session
   3. ProfileService.acquire(identity)       (bounded by auth.profile-load-timeout-seconds)
        • already online elsewhere → share the live instance
        • found in DB → load (name updated if renamed → audit auth.name_change)
        • DB says "unknown UUID" → create + persist first → audit auth.first_join
        • DB error/timeout → REFUSE login (auth.profile_load_failed). Never a blank profile.
        │
        ▼  PlayerJoinEvent
  onJoin  priority LOWEST: bind this connection to its session.
          No pending session or no profile → kick (auth.join_without_session).
  onJoined priority MONITOR: AuthGate → SuldJoinEvent now (online, Velocity), or once AuthMe logged
          the player in (hybrid; auth.authme_pending, then auth.authme_login)
        │
        ▼  SuldJoinEvent → PlayerLifecycleListener (NORMAL): onboarding
          first join → class-selection GUI (mandatory) → starter weapon + first quest
          returning  → HUD + state already restored, play immediately
        ...
        ▼  PlayerQuitEvent
  onQuit  priority MONITOR (after every gameplay handler):
          SessionRegistry.end → last session? saveAndUnload : save only
          audit auth.session_end
```

What gets restored, and from where (always keyed by UUID):

| State | Stored in |
|---|---|
| class, level, EXP, currency, quest progress | `suld_profiles` (PostgreSQL) |
| clan membership, rank, contribution | `suld_clans` / `suld_clan_members` |
| inventory, ender chest, position, vanilla stats | Paper's `world/playerdata/<uuid>.dat` and `world/stats/<uuid>.json` (UUID-keyed by Minecraft itself, so renames keep them) |
| items' SÜLD identity (rarity, stats, owner) | inside each item (PDC), so it travels with the inventory |

## 4. Race conditions and how they are closed

**Quit → instant rejoin (stale read / rollback).** Before this system, quitting started an async save
and evicted the profile at once. A reconnect could then read the database *before* the save landed,
loading old data that would later overwrite the newer data. Now every storage operation for a UUID
(`acquire`, `save`, `saveAndUnload`) runs through `KeyedSequencer<UUID>`, strictly in order. A load
cannot overtake the save, so this rollback no longer happens. A mutation test was run: with the sequencing removed,
`quitThenInstantRejoinNeverReadsStaleData` fails. Different players still load in parallel.

**Duplicate session (orphaned profile).** When an account logs in while already online, Paper kicks the
old connection with "logged in from another location". Previously the old connection's quit handler
evicted the profile that the *new* connection was using, so the new player had no profile and nothing
saved. Now `SessionRegistry` tracks every live connection per UUID. The new login opens its session
at pre-login (before the old one is kicked), so the old quit sees it is not the last session and only
saves. The profile is released only when the last session ends.

**Concurrent first logins.** 16 simultaneous first logins for one UUID create exactly one profile
(`concurrentFirstLoginsCreateExactlyOnce`). 200 concurrent pre-logins register exactly one "first"
session (`concurrentPreLoginsAreSafe`).

**Abandoned logins.** A client can pass pre-login and then vanish, e.g. during the configuration phase
or after being denied by another plugin. No quit event fires for it. Such PENDING sessions expire after
`auth.pending-session-ttl-seconds`, and the profile is released if no other session holds it.

**Failed save on quit.** The profile is evicted only *after* a successful save, so a database outage
keeps the live copy in memory for the next save attempt.

## 5. Audit log

Written to `suld_audit_log` (PostgreSQL/MySQL; schema V1) and mirrored to the server log
(`[audit] …`). In MEMORY mode it goes to the server log only.

| `action` | When | `detail` |
|---|---|---|
| `auth.mode` | startup | `acceptsLogins=true/false` (target = ONLINE/VELOCITY_FORWARDED/INSECURE) |
| `auth.first_join` | profile created | `profile created` |
| `auth.login` | returning player | `level=N` |
| `auth.name_change` | stored name ≠ authenticated name | `Old -> New (same UUID, same profile)` |
| `auth.duplicate_session` | login while already online | old session will be replaced |
| `auth.denied` | policy refused | `DENIED_SERVER_INSECURE` / `DENIED_UNAUTHENTICATED_UUID` / `DENIED_INVALID_NAME` / `DENIED_NAME_TAKEN` / `DENIED_AUTH_BRIDGE_UNAVAILABLE` |
| `auth.authme_pending` | hybrid: joined, AuthMe has not logged the player in | `joined, waiting for the AuthMe login` |
| `auth.authme_login` | hybrid: AuthMe logged the player in, SÜLD's game starts | `enters the game` |
| `auth.authme_logout` | hybrid: AuthMe logged an entered player out | `kicked to log in again` |
| `auth.insecure_dev_login` | dev mode only | `unverified identity` |
| `auth.profile_load_failed` | DB error/timeout at login | exception class or `timeout` (never the message, which could contain connection details) |
| `auth.join_without_session` | join with no pre-login/profile (should never happen) | session id |
| `auth.pending_expired` | pre-login never completed | `last=true/false` |
| `auth.session_end` | quit | `profile released` / `another session still owns the profile` |

`actor` = UUID, `target` = name at that moment. **Never logged:** passwords, tokens, session keys,
IP addresses. Values are stripped of control characters so a crafted name can't forge log lines.

Useful queries:
```sql
-- full identity history of one player (renames, logins, duplicates)
SELECT to_timestamp(at/1000), action, target, detail FROM suld_audit_log
 WHERE actor = '<uuid>' ORDER BY at;
-- every name change
SELECT to_timestamp(at/1000), actor, detail FROM suld_audit_log WHERE action = 'auth.name_change';
```

Live diagnostics: `/suld auth` (op/console) prints the auth mode and, for each online player, their
UUID, UUID version, live session count and whether a profile is loaded.

## 6. Configuration

```yaml
auth:
  allow-insecure-offline-dev-mode: false   # LOCAL DEV ONLY; deploy.sh forces false
  profile-load-timeout-seconds: 10         # 2..30; login refused if the profile isn't ready
  pending-session-ttl-seconds: 60          # 15..600
```
`server.properties` (deploy sets these four on every deploy, from `AUTH_MODE`): `online`: `online-mode=true`,
`enforce-secure-profile=true` (signed chat keys from authenticated clients), Paper on `SERVER_PORT`. `hybrid`:
`online-mode=false`, `enforce-secure-profile=false`, `server-ip=127.0.0.1`, `server-port=PAPER_PORT`, plus
`config/paper-global.yml` `proxies.velocity` (enabled, offline, the 64-hex secret), the proxy
(`deploy/templates/velocity.toml.tpl`, `authme-velocity.config.yml.tpl`, `suld-proxy.service`) and AuthMe's config
(PostgreSQL in the SÜLD database, `Hooks.bungeecord`, the shared secret, premium on). Secrets
(`VELOCITY_FORWARDING_SECRET`, `AUTHME_PROXY_SECRET`) are generated into `suld.env` and only ever written to mode-600
files.

## 7. What has been verified

**Hybrid mode (2026-10-18).** `PlayerIdentityTest` (offline UUID vector), `AuthPolicyTest` (hybrid admits only the
exact offline UUID, ignores the dev flag; the name owner check), `AuthGateTest` (5: wait, enter once, the check,
old-connection events, logout), `JdbcProfileNameIT` (V30 on H2 and PostgreSQL). Live on a Velocity 3.5.1 → Paper
1.21.11 dev pair with AuthMe 6.0.1 (`docs/qa/2026-10-18-authme-spike.md`, `tools/loadtest/hybrid-dev.sh`,
`tools/loadtest/bots/authbot.js`): register in the pre-join dialog → SÜLD's welcome only after AuthMe's login line;
a wrong password → kicked, SÜLD's join never ran, no join line; a cancelled register dialog → waiting in the world
with SÜLD shut, `/register` by chat → in. Not verified: a premium account's auto-login (needs a real Microsoft
account on a real client; owner check).

**Unit tests (run on every build):**
- `PlayerIdentityTest` (4): UUID equality, rename = same identity, v3/v4 detection, name validation.
- `AuthPolicyTest` (5): online accepts v4, verified modes reject v3 even with the dev flag,
  insecure fails closed, dev logins are labelled, invalid names are rejected in every mode.
- `SessionRegistryTest` (8): lifecycle, duplicate-login handoff, join without pre-login, pending expiry,
  200-thread concurrency.
- `KeyedSequencerTest` (5): ordering under a slow first operation, parallel keys, failure isolation,
  1000 synchronous completions without leaks, 300-op strict ordering.
- `DefaultProfileServiceTest` (7): first join and restore, **quit → instant rejoin with no stale read**,
  rename keeps the character, same name on another UUID is another character, **storage failure never
  creates a profile**, duplicate sessions share one instance, 16 concurrent first logins create once.

**Live, on a real Paper 1.21.11 server + PostgreSQL 16, driven by mineflayer protocol bots**
(2026-10-06):

| # | Scenario | Observed |
|---|---|---|
| A | Offline server, dev flag **off** | Startup: `auth.mode INSECURE acceptsLogins=false` + "REFUSING ALL LOGINS". Bot refused before entering the world: "Server is misconfigured…", `auth.denied DENIED_SERVER_INSECURE`. |
| 1 | First join | Bot spawned. Chat: welcome + "choose your class". **No /login or /register prompt.** `auth.first_join`. One `suld_profiles` row created. |
| 2 | Reconnect | Same UUID, `auth.login`, still exactly one row. |
| 3 | Username change (DB row's stored name changed to `OldTester`, level 7, 1234 coins, same UUID) | `auth.name_change OldTester -> Tester1 (same UUID, same profile)`. Level 7 and 1234 coins kept. Name updated. Total rows unchanged. |
| 4 | Duplicate session (same account twice) | Old connection kicked with `multiplayer.disconnect.duplicate_login`. New one spawned. `/suld auth` during handoff: `sessions=1 profile=lvl1` (not MISSING). Audit: `duplicate_session`, then `session_end … another session still owns the profile`, then `session_end … profile released`. One row. |
| 5 | 15 rapid reconnects | 15 sessions, 1 creation + 14 logins, 15 releases, exactly one row, no errors. |

**Not verified here, and why:**
- A real Microsoft-authenticated join needs a real Minecraft account and client, and this sandbox
  blocks Mojang's auth hosts (`api.minecraftservices.com`). The live tests therefore used the labelled
  dev mode, which exercises everything *after* Mojang verification: the pipeline, sessions,
  persistence and races.
- A literal Mojang rename can't be performed by a bot. Scenario 3 reproduces exactly what the server
  sees after one: the same UUID arriving with a different name.
- On the first real deployment, verify with a genuine account:
  1. Join with no prompt.
  2. `/suld auth` shows your UUID as `v4`.
  3. Reconnect: you keep the same level and items.
  4. Log in from a second client: the first one is kicked and your progress is intact.
