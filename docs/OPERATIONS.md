# SÜLD — Running the server

Day-to-day reference for owners and staff: commands, staff badges, the credit store, Bedrock players and the
knobs in `plugins/SULD/config.yml`. Installation is covered in [DEPLOYMENT.md](../DEPLOYMENT.md) (production) and
the README (local test server).

## Player commands

| Command | What it does |
|---|---|
| `/menu` (or the clock in hotbar slot 9) | «Командын Өргөө» HD hub: 8 tabs (Тоглогч, Даалгавар, Ур чадвар, Дэлгүүр, Rankup, Аялал, Тохиргоо, Тусламж) and 8 command cards (`docs/UI_HUB.md`) |
| `/help [page]`, `/tutorial`, `/commands` | Тусламж page (8 topics; a topic opens a dialog page) · step-by-step guide · every command, one dialog page per group |
| `/class`, `/profile`, `/exp` | Class choice (once) · the Тоглогч page of the hub (level, coins, rank, clan; console prints the text) |
| `/skills`, `/skill <node>` | Skill-tree map (points, passive spells, spell modifiers, ultimate), spells list, builds, respec, search |
| `/quest` | Storyline board (42 chapters); `/quest info` opens the book on the active chapter; `/quest track` toggles the tracker (boss bar with arrow and distance to the objective's region) |
| `/rankup`, `/lvlup` | Rankup page (Ард → Хаан, costs coins; owned ranks glow) · level rewards |
| `/shop`, `/cosmetics`, `/buy` | Дэлгүүр pages (supplies; page 2: selling, cosmetics, credit store, recipe book, order board) · tags/colours/join messages/emojis · credit store |
| `/travel`, `/settings` | Аялал page (relays: travel only while standing at one, historic sites, nearest ovoo, `/spawn`) · Тохиргоо page (8 per-player on/off settings, V21) |
| `/party`, `/dungeon`, `/clan`, `/cc` | Groups, the dungeon window (`/dungeon`: the ten-dungeon ladder, a way-finder to each gate), clans and clan chat |
| `/mori` | Personal steppe horse from level 5 (faster, finer coat with rank); disappears when you get off |
| `/trade <player>` | Safe trade window with a player within 24 blocks: items and coins, both confirm (relics and the menu clock cannot be traded; audited in the log) |
| `/discord`, `/website`, `/vote` | A link dialog («Нээх» opens the browser) from `branding.discord`, `branding.domain`, `branding.vote-url` |
| `/top [level\|coins]` | Leaderboards (podium GUI; also works from the console) |
| `/tasks` | Three daily hunting tasks from the regions your level can handle (coins and EXP, paid on completion) |
| `/daily` | Daily login reward: 7-day streak of coins and EXP (time zone: `daily.timezone`) |
| `/spawn`, `/balance`, `/pay`, `/rules`, `/relic` | Back to Kharkhorum · coins · send coins · rules · world relics |

## Admin commands (permission `suld.admin`, default: op)

| Command | What it does |
|---|---|
| `/suld guide` | Re-place the floating guide boards in front of the arrival point |
| `/suld exp <player> <amount>` | Grant EXP (levels up and upgrades the class weapon) |
| `/suld coins <player> <±amount>` | Add or remove SÜLD coins (audited in the log) |
| `/suld quest <player> <1..18\|reset>` | Move a player in the storyline (support tickets, testing) |
| `/revive <player>` | End a player's soul state early and heal them (audited) |
| `/credits give <player\|uuid> <amount> <txid>` · `/credits take <player\|uuid> <amount>` | Store credits; `give` is what the web store runs from the console, with its transaction id |
| `/suldevent`, `/suldpack`, `/worldbuild`, `/relic …` | World events · resource pack · city builder · relic admin |
| `/suld auth` | Authentication mode and session state; `/suld auth free <name>` prints the steps that free a name (hybrid) |

## Permissions: who can do what

`/commands` lists, for whoever types it, every command they may use (players: game commands plus the extras their
group grants; staff: the tools their permissions unlock; the console sees all). SÜLD's own commands are open to
everybody; what is restricted is listed below.

On the first start with LuckPerms installed, SÜLD creates the groups once from
`plugins/SULD/…/permissions/luckperms.txt` (marker file `plugins/SULD/.permissions-applied`; delete it to apply
again, or set `permissions.auto-setup: false` to manage LuckPerms yourself). Groups inherit upwards
(`admin > mod > helper > default`); every player is in `default`.

| Group | What it adds |
|---|---|
| `default` (everyone) | `/home /sethome /delhome` (one home; no `/tpa`, travel is part of the game, GAME-001), `/msg /r`, `/mail`, `/ignore`, `/afk`, `/list`; plus all SÜLD game commands |
| `helper` | Badge ТУСЛАГЧ, `/kick /mute /tp /seen /whois`, CoreProtect `inspect`/`lookup`, `suld.chat.bypass`, exempt from kick/mute/ignore |
| `mod` | Badge МОД, `/revive`, `/tphere /vanish /socialspy /invsee /back /ban /tempban`, CoreProtect `rollback`/`restore` |
| `admin` | Badge АДМИН, `suld.admin` (all SÜLD admin commands), `essentials.*`, `coreprotect.*`, `worldedit.*`, `worldguard.*`, `chunky.*` |
| `developer` | Inherits `mod`; badge DEV |
| `streamer`, `sponsor` | Cosmetic badges only |

Give someone a role with `lp user <name> parent add <group>` (they stay in `default`). The owners named in
`owners:` are OPed automatically on a verified server and show the ЭЗЭН badge.

Admin-only SÜLD commands (all need `suld.admin` unless noted): `/suld exp|coins|quest|guide|auth|spawnmob`,
`/credits give|take` (`suld.admin.credits`), `/revive` (`suld.admin.revive`), `/suldevent start|stop`
(`suld.admin.event`), `/suldpack reload|force` (`suld.admin.pack`), `/worldbuild` (`suld.admin.world`),
`/relic setshrine|return|give|recover|tp` (`suld.admin.relic`). `suld.admin` also lets a player look at others'
`/profile /exp /class /quest /balance <name>`, skips the `/spawn` warm-up and may abort any dungeon.

Building in Kharkhorum by hand: the city is protected (no breaking, placing, buckets, signs) for everyone. An
operator (or `suld.admin.world`) types `/worldbuild edit` to build there; typing it again protects the city again.
The mode lasts until the server restarts. Hand-built changes stay: the start-up self-repair
that put the city back to its plan is off (`world.kharkhorum-auto-repair: false`). Do not run `/worldbuild repair`
or `/worldbuild rollback` once the spawn is built by hand: both rewrite the city from the plan.

SÜLD owns these labels even where EssentialsX has the same command: `/help /rules /spawn /balance /pay /top /baltop
/exp /rank /vote /discord`. Staff reach Essentials' own versions with the prefix, e.g. `/essentials:top`,
`/essentials:exp`. While in a dungeon run, players cannot use `/home /tpa /back /warp /tp…` (staff excepted).

## Staff badges (LuckPerms)

Badges show in chat, above heads and in TAB. Each is a permission, `default: false`:
`suld.badge.owner|admin|developer|mod|helper|streamer|sponsor`; the groups above already carry them. To do it by
hand instead:

```
lp creategroup admin
lp group admin permission set suld.badge.admin
lp group admin permission set suld.admin
lp creategroup mod
lp group mod permission set suld.badge.mod
lp group mod permission set suld.admin.revive
lp creategroup helper
lp group helper permission set suld.badge.helper
lp creategroup developer
lp group developer permission set suld.badge.developer
lp creategroup streamer
lp group streamer permission set suld.badge.streamer
lp creategroup sponsor
lp group sponsor permission set suld.badge.sponsor
lp user <name> parent add mod
```

## Cosmetics

`/cosmetics` is a card menu with eight categories: tags, name colours, chat colours, join messages, emojis, and
three particle categories — **auras** (light circling the player), **trails** (left behind while walking or riding)
and **kill effects** (where a monster falls). Everything is visual only. Shift + left click previews an item before
buying (a chat sample, or the effect around the player). Common (Энгийн) shop items can be bought with coins
(left click) or credits at a tenth of the price (right click); rarer ones — Ховор, Гайхамшигт, Домогт — and every
store item are credits-only (`cosmetics.coin-max-rarity`, default `COMMON`). Cosmetics already owned are kept. Effects are skipped for invisible players, spectators and souls.

## Credit store

- Credits (✦) buy **cosmetics only** — tags, colours, join messages, emojis. Nothing that affects combat
  (Minecraft EULA; no pay-to-win).
- Prices shown in `/buy` come from `store.packages` in config.yml; the link from `branding.store-url`.
- Connect a web store (e.g. Tebex) by making each package run a **console** command, using the player's UUID:
  `credits give {id} 250 {transaction}`. The transaction id is required: the grant and a ledger row
  (`suld_credit_grants`, V16) commit together, so a retried or replayed command reports `duplicate` and grants nothing.
  Every give and take writes an audit record. Refunds or chargebacks: `credits take {id} 250` (credits never go below 0).
- Credits are changed with an atomic database update, so a purchase is never lost to a concurrent save.

## Hardcore death

Configured under `death:`. On death a player keeps soulbound gear (class weapon, armour, menu), drops half of the
other stacks (`loot-loss-fraction`), loses 10 % of the EXP into the current level (never a level), and kept gear
loses 25 % durability. They respawn as a **Сүнс** for 30 s: they cannot deal or take damage and mobs ignore them.
`allow-free-revive: false` keeps souls until `/revive`.

## Accounts (AUTH_MODE)

`docs/AUTHENTICATION.md`. With `AUTH_MODE=hybrid`:

* **Players**: new players register in the dialog that opens while joining (1.21.6+ clients) or with
  `/register <нууц үг> <нууц үг>` in the world (older clients), then `/login <нууц үг>`. Nothing of SÜLD starts before
  the login. A Microsoft account owner may turn on premium (no password from then on): `/premium` shows a warning,
  `/premium confirm` within 2 minutes does it, and the next join is checked with Mojang.
* **A locked-out player** (turned premium on a name they do not own): the proxy keeps asking for a Mojang login until
  it restarts: `sudo systemctl restart suld-proxy`, then `authme freemium <name>` if the row was marked.
* **A squatted name** (someone registered a name that belongs to another person's Microsoft account): the owner proves
  the account to staff; `authme unregister <name>`; if the squatter played on it, that character goes with the name.
  `/suld auth free <name>` (op or console) prints these steps for one name with what AuthMe and the profile store hold
  for it (registered or not, whose profile, online now); it changes nothing itself.
* **Owners** must have their AuthMe account before the server opens (`authme register <name> <password>`; the log
  warns at start). `op.sh <name>` ops the offline UUID of the name.
* **A premium player who renamed** is a new character; staff move the old one with the re-key in
  `docs/operations/HYBRID_CUTOVER.md` §3.
* Health: `health-check.sh` checks the proxy, the loopback bind, the signed forwarding, the secret files and AuthMe's
  table; `/suld auth` shows the mode, the AuthMe bridge and who is still waiting for the login.

## Bedrock players (Geyser)

**Off by default.** SÜLD is Java Edition 1.21.11 only: its HUD, windows and lock-on need the Java resource pack and a keyboard, which Bedrock clients do not have (external audit FIND-03). Geyser and Floodgate are the `optional` plugin profile; to try them, install that profile and set `BEDROCK_PORT=19132` in `suld.env` (the firewall opens the UDP port only then). The notes below apply only when it is on.

- Geyser listens on UDP **19132**. Open it in the firewall next to TCP 25565.
- Without Floodgate, Bedrock players sign in with a Java (Microsoft) account (`auth-type: online`). Accounts are
  always verified.
- Windows only, local testing: a Bedrock client on the same PC needs a loopback exemption once:
  `CheckNetIsolation LoopbackExempt -a -n="Microsoft.MinecraftUWP_8wekyb3d8bbwe"`, then connect to `127.0.0.1:19132`.

## Server list

`branding.motd` takes two MiniMessage lines (empty = the built-in SÜLD lines). The bundled 64×64 icon is used
unless the server folder has its own `server-icon.png` or `branding.server-icon: false`.

## Guide for new players

Five floating boards stand in front of the arrival point (start, earning coins, classes and combat, regions and
danger, commands); they are re-placed whenever a player arrives and every 30 s, and glitter so newcomers look at
them. A joining low-level player also gets a title pointing at them, a clickable five-step card in chat, and for
the first 20 minutes an action-bar hint that fits their progress (no class, first hunt, loot to sell, coins to
spend). Switch the boards off with `guide.boards: false`.

## Chat guard

`chat-guard:` limits each player to 4 messages per 5 s, blocks the same message twice within 30 s and other
servers' addresses or IPs (the domains in `allowed-domains`, default `branding.domain`, pass), and lowers SHOUTED
messages. Staff with `suld.chat.bypass` (default: op) are not limited.

## Chat tips

`tips.interval-minutes` (default 6, `0` = off) broadcasts the next line of `tips.messages` (MiniMessage; empty = the
built-in tips about /daily, /quest, skills, dungeons, death and the shop).

## World pre-generation

SÜLD's own throttled pre-generator (`world.pregenerate`, docs/world/PREGENERATION.md) generates spawn, Kharkhorum,
the routes and the heart of every area once the city is built, and resumes after restarts. Progress:
`/suldworld pregen status`; the world, border and disk report: `/suldworld report`. The world border (10 000 blocks,
centred on the spawn) is docs/world/WORLD_BORDER.md.


## Skill tree

* Players spend **skill points** on `/skills` (a map: gold line = learned path, turquoise = next step, red link = pick one).
  Points: one per level to 30 then one per two levels (44 at 60), +1 per three finished story chapters, +1 per five
  discovered regions (max 4), plus admin grants. Left click learns / ranks up, right click refunds a rank, Shift+click
  shows details. `F` (swap hands) while holding the class weapon casts the learned ultimate (60 resource, 45 s).
* `/skills build save|load|delete|list <name>` (slots: `skills.builds.slots`), `/skills reset [defense|spell|offense]`
  (confirmation, `skills.respec.coins-per-point`, 300 s cooldown, free up to level 10). **Мартагдлын Бөмбөрцөг**
  (1 diamond + 4 ender pearls) refunds everything for free with no cooldown.
* Admin (`suld.admin.skills`, included in `suld.admin`): `/skillsadmin inspect|grantpoints|unlock|reset|orb|fire <player> …`,
  `reload` (re-read the data files), `validate` (report problems with exact file and field without applying).
* **Content is data**: `plugins/SULD/skills/*.json` (copied from the jar on first start; edit and `/skillsadmin reload`).
  A file with a problem is reported (file + field) and that class keeps its previous tree. Saved builds refer to node ids,
  so renaming/reordering nodes is safe; removing a node refunds its points on the player's next join.
* Storage: `suld_profiles.skill_data` (migration V10), one versioned JSON document per player. A newer or unreadable
  document makes that login fail loudly instead of being overwritten.
* Design, architecture, tests and QA record: `docs/SKILL_TREE_*.md`, `audit/skill-tree-status.json`.

## Alerts (operators' webhook)

`ALERT_WEBHOOK_URL` in `/etc/suld/suld.env` (a Discord webhook, kept secret) receives: the health check's failures
(`suld-health.timer`, every 10 minutes, one post per change of the failing set and one when healthy again), a unit
that failed or that systemd stopped restarting (`suld`, `suld-proxy`, `suld-backup`, `suld-funnel`), and a failed
backup. Test it once after deploying: `sudo /opt/suld/bin/notify.sh "test" "SÜLD alerts work"`. The weekly funnel
report (`suld-funnel.timer`, Monday 09:10 Улаанбаатар, `docs/ANALYTICS.md` «Weekly report») posts its summary to
`REPORT_WEBHOOK_URL`, or to the alert webhook when that is blank; the Markdown stays in `/opt/suld/run/reports/`. `MAX_PLAYERS` defaults to 25, what
the last load test holds (`docs/perf/M3_2026-10-12.md`); raise it only after a run on the real host (`DEPLOYMENT.md`).

## Persistence and shutdown (audit Phase 2)

* **Items and coins never duplicate on a crash.** Items live in the player file, coins in SQL. Every sale and trade
  writes the player file synchronously first and saves the coins after; every shop purchase charges and saves the
  coins first and gives the item only once the row is committed (`PlayerDataSaves`). A crash in between can cost
  coins (the `[audit]` and `[coins]` log lines say which player and what), never create an item. A coin save that
  fails tells the player and leaves the profile dirty, so the autosave and the quit save retry it.
* **`/pay` and `/trade` move coins in one database transaction** (`ProfileRepository.saveTogether`): both rows or
  neither. If the transaction fails the in-memory balances are put back and the payer is told
  (`[audit] pay … FAILED` in the log).
* **A stale save is refused on every database** (MySQL and H2 included): nothing is marked persisted unless the row
  carries it.
* **Shutdown has one deadline.** After the 15 s profile flush, `shutdown.budget-seconds` (default 20, `config.yml`)
  is shared by the style/clan/analytics flushes and the drain of every background pool. A pool still busy afterwards
  (a dead or slow database) is interrupted and the log says what was abandoned: `[shutdown] pool <name> not
  drained…` and `[shutdown] N profile save(s) not confirmed: <uuids>`. Those players' last changes may be missing;
  the database row is what it was before.
* **Trade mailbox.** Items a trade could not hand back to an offline player (a disconnect during the cancel, a stop
  mid-trade) wait in `plugins/SULD/mailbox/<uuid>.yml` and are delivered on the next join (`[mailbox]` log lines).
* **Farming fatigue survives a restart:** `plugins/SULD/farm-fatigue.yml`, written every 5 minutes and on stop.
* **One time zone.** `daily.timezone` (default `Asia/Ulaanbaatar`) now drives every clock: daily resets, daily
  tasks, ovoo days, death-lock times, the death log and the chat screen.
* **Souls** (players under a death lock) no longer count toward world-event thresholds and draw no spawns.
* **Anti-xray** is on in the deploy tuning (`anticheat.anti-xray.engine-mode: 2`, Paper's default block lists).
* **Resource-pack budget.** `tools/validation/validate_pack_budget.py` passes again: `assets/registry/pack_budget.json`
  names every exception and why (HUD glyph widths are structural, the HD badges and chest backgrounds, the blank
  vanilla-hide sprites, the three prop models). HUD glyph families that share a bitmap now share one file.
