# Anticheat

SÜLD defends against cheat clients in four layers:

1. **GrimAC** checks every movement, hit, block action and packet. Grim is an open-source anticheat that runs as its
   own plugin. Production installs it from Modrinth (`deploy/plugins/plugins.json`, profile `hardening`). SÜLD has no
   compile dependency on Grim and bundles none of its code.
2. **SÜLD punishments for Grim** (`deploy/grim/punishments.yml`, installed by `deploy.sh` / `update.sh`).
3. **Server-side hiding** for cheats that only draw what the client already has. No packet anticheat can see those.
4. **SÜLD's own rules**, so the cheats Grim does not cover gain nothing.

On top of these, the **SÜLD → Grim bridge** keeps SÜLD's own movement from being punished: dashes, leaps, dodges and
the sky skill-tree seat.

## What the three big cheat clients do, and what stops it

The clients surveyed are Meteor Client, Wurst7 and LiquidBounce. Their module lists were read from their public
repositories in October 2026; only module names were read, no code. Grim check names are from Grim 2.3.74.

| Cheat family (module names) | Stopped by |
|---|---|
| Fly, Flight, CreativeFlight, Glide, Jetpack, AirJump, AntiVoid | Grim Simulation, BadPacketsI. The move is set back; at Simulation VL 250, kick. |
| Speed, BunnyHop, Strafe, TargetStrafe, TerrainSpeed, Sprint (omni) | Grim Simulation, Sprint. Set back; kick at VL 250. |
| NoFall, Criticals (packet mode), MaceDmg, ArrowDmg, Damage | Grim NoFall, GroundSpoof, Simulation. |
| Jesus, LiquidWalk; Step, Spider, HighJump, LongJump, FastClimb, ReverseStep | Grim Simulation, Phase. |
| NoSlow, NoSlowdown, NoWeb, SwordBlock | Grim NoSlow (Misc group: kick at VL 80). |
| Velocity, AntiKnockback, KeepSprint, NoPush, AntiWaterPush | Grim AntiKB, AntiExplosion. Kick at Knockback VL 40. |
| Timer, Blink, FakeLag, TickBase, TimeShift, PingSpoof | Grim Timer, TimerLimit, TransactionOrder. `max-transaction-time` kicks a client that withholds answers for 60 s. |
| Phase, NoClip, Clip, ClickTP, Teleport, Burrow | Grim Phase, Simulation, BadPacketsN. |
| ElytraFly, ElytraBoost, BoatFly, EntityControl, VehicleBoost | Grim Elytra, Vehicle. |
| KillAura, MultiAura, TpAura, ClickAura, FightBot, Aimbot | Grim Reach, Hitboxes, Aim, PacketOrder, Interact. An impossible hit never lands (`block-impossible-hits`). Kick at Reach VL 20, Hitboxes VL 25, Combat VL 80. |
| Reach, Hitboxes | Grim Reach, Hitboxes. |
| Scaffold, Tower, AirPlace, FastPlace, AutoBuild | Grim place checks (RotationPlace, AirLiquidPlace, FarPlace, PositionPlace…). Building is also off in Kharkhorum (WorldGuard). |
| Nuker, FastBreak, SpeedMine, PacketMine, InstantRebreak, VeinMiner | Grim break checks (FastBreak, WrongBreak, PositionBreak…). The city is protected. |
| InventoryMove, GUIMove, InvWalk | Grim MultiActions. |
| ChestStealer, AutoSteal, InventoryCleaner, AutoShop, ItemScroller, Replenish | **SÜLD**: clicks in SÜLD menus are rate-limited (burst 10, then 10 a second). Faster clicks are dropped and staff with `suld.anticheat.alerts` are told. Every purchase is checked and charged on the server, so nothing duplicates. |
| AutoClicker, NoMissCooldown | Grim has no CPS check. **SÜLD**: melee damage follows the server's own attack-strength timer (0.2 + 0.8·charge²), so spam-clicking deals less, not more. Spells need a 400 ms gap and have cooldowns and a mana cost. |
| AutoFish, AutoFarm, TreeBot, Baritone, AutoWalk, AntiAFK (farming bots) | Grim Baritone catches sloppy pathing. **SÜLD**: farm fatigue (repeated kills in one place give less EXP and loot, and survive restarts) and rewards only for active play (ActivePlaytime). |
| X-Ray, BlockESP, StorageESP, ChestESP, CaveFinder, BaseFinder, Search, Freecam | **Paper anti-xray, engine mode 2** (`deploy/paper-tuning.conf`). Ores, chests, barrels, ender chests and spawners are sent as fake blocks until the player is next to them. |
| ESP, Tracers, Chams, Nametags, Radar, HealthTags | Packet anticheat cannot see these: the client only draws entities the server already sent. Entity tracking ranges limit how far they reach. In a PvE game they give little. |
| Fullbright, NoRender, NoFog, Zoom, FreeLook | Client-side only. They do not matter. |
| BookBot, ServerCrasher, CrashChest, Kaboom, packet exploits | Grim Crash, Exploit, BadPackets (kick at BadPackets VL 50). Paper's packet limiter kicks at more than 500 packets in 7 s. Creative mode is never given. |
| Spammer, MessageAura, InfiniChat | Grim Chat. SÜLD chat protection (rate limit, repeats, adverts). |
| Disabler, AntiCheatDetect, ResetVL, PacketCanceller | Grim TransactionOrder, BadPackets, Post and `max-transaction-time`. Keep Grim up to date (`update.sh` fetches the newest build for the server's version). |

Grim checks that are still *experimental* are off by default (`experimental-checks: false` in Grim's config.yml).
Turn them on only on a test server first, after watching their false positives.

## The SÜLD → Grim bridge

SÜLD moves players itself:
- the Баатар leap, the Харваач disengage, the Хүлэгчин charge and Хийморийн Давхилт;
- the ultimate dash and the sneak-jump dodge;
- the sky skill-tree seat, a camera on an entity high above the world;
- the lock-on camera turn.

A cheat-detecting anticheat can mistake these for a cheat. `anticheat/MovementExemptions` opens a short window for
every such movement:

| SÜLD movement | Window |
|---|---|
| leap, disengage, charge, windHorse, ultimate dash, dodge (`MovementExemptions.launch`) | 40 ticks (2 s) |
| sky skill-tree seat | while seated, then 20 ticks after the return teleport |
| lock-on camera turn | 10 ticks |

`anticheat/GrimBridge` listens to Grim's `FlagEvent`. It registers the event by name, so SÜLD runs unchanged without
Grim, and loads with `softdepend: [GrimAC]`. A flag is cancelled only when both conditions hold:
- the player is inside a window;
- the check is in `anticheat.grim.exempt-checks`. The default is `[Simulation, GroundSpoof, NoFall, Phase]`:
  movement only, never combat, packet or world checks.

A cancelled flag counts no violation and causes no setback. Every flag outside a window, and every check not in the
list, is left to Grim.

`/suld grim` shows, per check, how many flags fell:
- outside a window;
- inside a window and were forgiven;
- inside a window but were not forgiven because the check is not listed.

`/suld grim reset` clears the counts. If a listed check never shows a forgiven flag on the live server, remove it.

## Measurement (dev server, mineflayer bot, 2026-10-11)

The measurement used `skillsadmin cast <player> <SPELL>`, an admin command that casts one spell now. Counts are
flags per step, shown as (outside a window, forgiven, in a window but kept). They were counted by the bridge, one
bot run per column.

| Step | Bridge off (`exempt-checks: []`) | Bridge on (default list) |
|---|---|---|
| idle (baseline) | TickTimer 54/0/0 | TickTimer 27/0/0 |
| walk (baseline) | Simulation 80/0/0, GroundSpoof 79/0/0 | Simulation 81/0/0, GroundSpoof 78/0/0 |
| sprint-jump (baseline) | Simulation 81/0/0, GroundSpoof 79/0/0 | Simulation 80/0/0, GroundSpoof 80/0/0 |
| leap ×3 | Simulation 0/0/**49**, GroundSpoof 0/0/3 | Simulation 0/**48**/0, GroundSpoof 0/**3**/0 |
| disengage ×3 | Simulation 13/0/**19** | Simulation 16/**18**/0 |
| charge ×3 | Simulation 29/0/**11**, GroundSpoof 1/0/2 | Simulation 23/**14**/0, GroundSpoof 4/**3**/0 |
| windHorse ×3, dodge, sky tree | no flags inside a window (the bot did not trigger these the way a client does) | same |

TickTimer flags fall inside the windows too (for example, leap 59). They stay: TickTimer is a packet-timing check,
not a movement check, and the bot fails it everywhere.

Read this with care:
- Mineflayer is not a vanilla client. Its physics fails Grim's prediction even when idle or walking. Grim's
  TickTimer flags it constantly because it sends no tick-end packet.
- The steps that matter are the ones whose flags fall *inside* the windows. With the bridge on, those are forgiven.
  The baseline outside the windows (idle, walk, sprint-jump) stays, as it should.
- **How a real client feels after a SÜLD dash (no rubber-band) is MANUAL_QA_REQUIRED.**

## Dev server notes

- The local dev server runs Grim with its default punishments, which only alert. With `deploy/grim/punishments.yml`,
  mineflayer bots would be kicked within seconds because of their own physics.
- Never give players `grim.exempt`: it switches Grim off for them entirely.
