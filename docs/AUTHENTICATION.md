# SÜLD Authentication & Player Identity

**SÜLD has no `/register`, no `/login`, and never sees a password.** A player's identity is their
**Minecraft/Microsoft-authenticated account UUID**, established by the normal Minecraft login
with `online-mode=true`. SÜLD trusts nothing else, and refuses every login when the server is not
verifying accounts.

| Rule | Where enforced |
|---|---|
| UUID is the only identity; the name is display-only | `PlayerIdentity` (equality = UUID), `DefaultProfileService` (keyed by UUID) |
| A username change never creates a new character | `ProfileService.acquire` adopts the new name on the same profile; `auth.name_change` audited |
| Taking someone's old name never grants their profile | Lookup is by UUID only (`sameNameOnAnotherUuidIsAnotherCharacter` test) |
| No offline/cracked logins in production | `AuthPolicy` fails closed; `deploy.sh` forces `online-mode=true`; `health-check.sh` fails otherwise |
| Spoofed or misconfigured identities are rejected | A verified server never issues offline-style (v3) UUIDs, so `AuthPolicy` denies them |
| No player enters the world without a loaded profile | Profile acquired in async pre-login; failure → login refused, never a blank profile |
| Quit → instant rejoin can't read stale data | Per-UUID `KeyedSequencer`: a load always waits for the previous save |
| Duplicate/concurrent logins are safe | `SessionRegistry`: the profile is released only when the **last** session ends |
| Security-relevant events are audited, with no secrets | `AuditLog` → `suld_audit_log` + server log; control characters stripped |

---

## 1. Why there is no /login

`/register` and `/login` plugins exist for **offline-mode ("cracked") servers**, where the server has
no idea who is connecting and asks for a password. SÜLD does the opposite:

- With `online-mode=true`, the **client** authenticates with Microsoft, and the **server** verifies
  that session with Mojang's session server before the player gets in. That handshake is
  Minecraft's own, so SÜLD never handles it and never sees credentials.
- When Paper fires `AsyncPlayerPreLoginEvent`, the UUID in it is already proven. SÜLD starts there.
- **SÜLD stores no passwords, tokens or Microsoft data.** It stores the UUID, the last-seen name for
  display, and game state.

Do not install or build any offline-mode authentication, "premium/cracked" hybrid login, or
username-based identity. These are explicitly out of scope and unsafe.

## 2. Authentication modes (decided once at startup)

`AuthenticationService.detectMode` reads the live server and Paper's proxy config:

| Mode | When | SÜLD behaviour |
|---|---|---|
| `ONLINE` | `online-mode=true` | Normal production mode. Logins allowed; UUID must be v4. |
| `VELOCITY_FORWARDED` | `online-mode=false`, but `config/paper-global.yml` has `proxies.velocity.enabled: true`, `online-mode: true` and a non-empty `secret` | Supported for a future Velocity proxy. Forwarding is HMAC-signed by the proxy. UUID must be v4. |
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
        │
        ▼  PlayerLifecycleListener (NORMAL): onboarding
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
| `auth.denied` | policy refused | `DENIED_SERVER_INSECURE` / `DENIED_UNAUTHENTICATED_UUID` / `DENIED_INVALID_NAME` |
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
`server.properties`: `online-mode=true` (deploy enforces it). `enforce-secure-profile=true` is also set,
which requires signed chat keys from authenticated clients.

## 7. What has been verified

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
