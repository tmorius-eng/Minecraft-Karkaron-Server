# SÜLD — Running the server

Day-to-day reference for owners and staff: commands, staff badges, the credit store, Bedrock players and the
knobs in `plugins/SULD/config.yml`. Installation is covered in [DEPLOYMENT.md](../DEPLOYMENT.md) (production) and
the README (local test server).

## Player commands

| Command | What it does |
|---|---|
| `/menu` (or the clock in hotbar slot 9) | Main menu: character, class, quests, rank, shop, tutorial |
| `/help`, `/tutorial` | Guide GUI · step-by-step guide for new players |
| `/class`, `/profile`, `/exp`, `/skills` | Class GUI · character sheet · level/EXP · spells and click combos |
| `/quest` | Storyline board (15 chapters); `/quest info` prints the active chapter |
| `/rankup`, `/lvlup` | Rank ladder (Ард → Хаан, costs ₮) · level rewards |
| `/shop`, `/cosmetics`, `/buy` | Supplies and selling loot · tags/colours/join messages/emojis · credit store |
| `/party`, `/dungeon`, `/clan`, `/cc` | Groups, Khasar's Den, clans and clan chat |
| `/spawn`, `/balance`, `/pay`, `/rules`, `/relic` | Back to Kharkhorum · coins · send coins · rules · world relics |

## Admin commands (permission `suld.admin`, default: op)

| Command | What it does |
|---|---|
| `/suld exp <player> <amount>` | Grant EXP (levels up and upgrades the class weapon) |
| `/suld quest <player> <1..15\|reset>` | Move a player in the storyline (support tickets, testing) |
| `/revive <player>` | End a player's soul state early and heal them (audited) |
| `/credits give\|take <player\|uuid> <amount>` | Store credits; also what the web store runs from the console |
| `/suldevent`, `/suldpack`, `/worldbuild`, `/relic …` | World events · resource pack · city builder · relic admin |
| `/suld auth` | Authentication mode and session state |

## Staff badges (LuckPerms)

Badges show in chat, above heads and in TAB. Each is a permission, `default: false`:
`suld.badge.owner|admin|developer|mod|helper|streamer|sponsor`. The owners listed under `owners:` in config.yml
get OP and the ЭЗЭН badge automatically. Typical LuckPerms setup:

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

## World pre-generation

With Chunky installed, `world.pregenerate` pre-generates the playable area on first start (resumes after restarts)
so exploring the steppe does not stall the server. Progress: `/chunky progress`.
