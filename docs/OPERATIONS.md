# SÜLD — Running the server

Day-to-day reference for owners and staff: commands, staff badges, the credit store, Bedrock players and the
knobs in `plugins/SULD/config.yml`. Installation is covered in [DEPLOYMENT.md](../DEPLOYMENT.md) (production) and
the README (local test server).

## Player commands

| Command | What it does |
|---|---|
| `/menu` (or the clock in hotbar slot 9) | Main menu: character, class, quests, rank, shop, tutorial |
| `/help`, `/tutorial` | Guide GUI · step-by-step guide for new players |
| `/class`, `/profile`, `/exp` | Class GUI · character sheet · level/EXP |
| `/skills` (`/tree`), `/skill <node>` | Skill-tree map (points, passive spells, spell modifiers, ultimate), spells list, builds, respec, search |
| `/quest` | Storyline board (18 chapters); `/quest info` prints the active chapter; `/quest track` toggles the tracker (boss bar with arrow and distance to the objective's region) |
| `/rankup`, `/lvlup` | Rank ladder (Ард → Хаан, costs ₮) · level rewards |
| `/shop`, `/cosmetics`, `/buy` | Supplies and selling loot · tags/colours/join messages/emojis · credit store |
| `/party`, `/dungeon`, `/clan`, `/cc` | Groups, the four dungeons (`/dungeon list`), clans and clan chat |
| `/mori` | Personal steppe horse from level 5 (faster, finer coat with rank); disappears when you get off |
| `/trade <player>` | Safe trade window with a player within 24 blocks: items and coins, both confirm (relics and the menu clock cannot be traded; audited in the log) |
| `/discord`, `/website`, `/vote` | Clickable links from `branding.discord`, `branding.domain`, `branding.vote-url` |
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
| `/credits give\|take <player\|uuid> <amount>` | Store credits; also what the web store runs from the console |
| `/suldevent`, `/suldpack`, `/worldbuild`, `/relic …` | World events · resource pack · city builder · relic admin |
| `/suld auth` | Authentication mode and session state |

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
| `default` (everyone) | `/home /sethome /delhome` (several homes), `/tpa /tpaccept /tpdeny /tpacancel`, `/msg /r`, `/mail`, `/ignore`, `/afk`, `/list`; plus all SÜLD game commands |
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
buying (a chat sample, or the effect around the player). Coins buy most items (a tenth of the price in credits works
too); some are credits-only (see `/buy`). Effects are skipped for invisible players, spectators and souls.

## Credit store

- Credits (✦) buy **cosmetics only** — tags, colours, join messages, emojis. Nothing that affects combat
  (Minecraft EULA; no pay-to-win).
- Prices shown in `/buy` come from `store.packages` in config.yml; the link from `branding.store-url`.
- Connect a web store (e.g. Tebex) by making each package run a **console** command, using the player's UUID:
  `credits give {id} 250`. Refunds or chargebacks: `credits take {id} 250` (credits never go below 0).
- Credits are changed with an atomic database update, so a purchase is never lost to a concurrent save.

## Hardcore death

Configured under `death:`. On death a player keeps soulbound gear (class weapon, armour, menu), drops half of the
other stacks (`loot-loss-fraction`), loses 10 % of the EXP into the current level (never a level), and kept gear
loses 25 % durability. They respawn as a **Сүнс** for 30 s: they cannot deal or take damage and mobs ignore them.
`allow-free-revive: false` keeps souls until `/revive`.

## Bedrock players (Geyser)

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
