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
| X-Ray, BlockESP, StorageESP, ChestESP, CaveFinder, BaseFinder, Search, Freecam | **Paper anti-xray, engine mode 2** (`deploy/paper-tuning.conf`). Ores, chests, barrels, ender chests and spawners are sent as fake blocks until the player is next to them. Ores are in `hidden-blocks`; the containers and the spawner are tile entities, which mode 2 ignores there, so they are in `replacement-blocks`. |
| ESP, Tracers, Chams, Nametags, Radar, HealthTags | Packet anticheat cannot see these: the client only draws entities the server already sent. Entity tracking ranges limit how far they reach. In a PvE game they give little. |
| Fullbright, NoRender, NoFog, Zoom, FreeLook | Client-side only. They do not matter. |
| BookBot, ServerCrasher, CrashChest, Kaboom, packet exploits | Grim Crash, Exploit, BadPackets (kick at BadPackets VL 50). Paper's packet limiter kicks at more than 500 packets in 7 s. Creative mode is never given. |
| Spammer, MessageAura, InfiniChat | Grim Chat. SÜLD chat protection (rate limit, repeats, adverts). |
| Disabler, AntiCheatDetect, ResetVL, PacketCanceller | Grim TransactionOrder, BadPackets, Post and `max-transaction-time`. Keep Grim up to date (`update.sh` fetches the newest build for the server's version). |

Grim checks that are still *experimental* are off by default (`experimental-checks: false` in Grim's config.yml).
Turn them on only on a test server first, after watching their false positives.

## Grim works against every cheat client, not just these three

Grim does not look for a particular client. It re-simulates every move, hit, block action and packet from vanilla
physics and rules. Whatever produced an impossible move fails the same check: Meteor, Wurst, LiquidBounce, any
other client, a modified one or a private one. The table above is only a map from those clients' module names to
the checks that catch them.

The SÜLD side never weakens that:
- **Windows are kind-specific and short.** Only movement checks are forgiven, and only for as long as the SÜLD
  movement really lasts.
- **Every player has a forgiveness budget.** At most 60 flags at once, then 6 a second
  (`anticheat.grim.budget-burst` / `budget-per-second`). A client that keeps triggering SÜLD movement to hide a
  Fly or Speed cheat inside the windows runs dry within seconds. After that Grim judges every flag, and
  `/suld grim` counts them as "over budget".
- **Combat, packet and world checks are never forgiven:** Reach, Hitboxes, BadPackets, PacketOrder, Timer, Crash,
  FastBreak, the place checks and the Aim checks outside lock-on.

## Every SÜLD feature against Grim

The behaviour on the Grim side was read from Grim's source at f5bbe9c, the installed 2.3.74 build. The SÜLD side
comes from a full audit of `suld-plugin`.

| SÜLD feature | What Grim does with it | SÜLD handling |
|---|---|---|
| leap, disengage, charge, windHorse, ultimate dash, dodge (server velocity on the caster) | predicted, but the client's answer can lag | MOVE window per movement: leap 40, disengage and windHorse 30, charge, ultimate dash and dodge 20 ticks, plus two pings (at most +40) |
| boss knockback, push, pull and slam (ArchetypeBrain, KhasarBrain) | any size, several per tick, each predicted with transactions | nothing needed; AntiKB still catches a Velocity cheat |
| spells on mobs (pulls, knock-ups, shoves) | not players: spells never move other players | — |
| teleports (dungeon in/out, relay, /spawn, world border, sky tree) | handled natively (teleport transactions) | sky-tree return: 20-tick MOVE window |
| sky skill-tree seat (riding an ItemDisplay that the server moves) | non-horse vehicles are never simulated; no Reach/Hitboxes while riding | MOVE hold while seated (backup) |
| lock-on camera turn | `Player#lookAt` sends a look-at packet Grim cannot see, so a wound-up yaw read as an AimModulo360 snap | **fixed**: lock-on now uses `Player#setRotation`, the rotation packet Grim treats as server-set; a 10-tick LOCKON window forgives AimModulo360 and AimDuplicateLook as a backup |
| /mori steppe horse | vehicle-simulated (VehicleA/B/C) | **fixed**: the rider is seated a tick after the horse spawns and gets off before the horse is removed; a 20-tick MOUNT window on mount and dismount |
| Талын Салхи keystone (+60 % horse speed) | horse movement_speed is modeled | **fixed** (gameplay bug): the bonus left the horse fast for every later rider; it now goes when the rider dismounts |
| movement-speed stats (items, tree, keystones), potions (speed, slowness, jump, blindness) | attributes and effects are modeled | the speed attribute is only re-sent when it changes, no longer on every hotbar scroll |
| props, dungeon gate, sky-tree nodes (Interaction boxes), NPC Mannequins | Reach and Hitboxes only check living targets; Mannequin has its own box | prop click box shrunk to the prop's footprint, so it no longer swallows hits on mobs beside a statue |
| rigged mobs and bosses (3D models over an invisible host) | target box = vanilla size × the host's SCALE attribute | **fixed**: the host takes the rig's scale (at most `models.host-scale-max`, 1.6), so the box players aim at is where the model is drawn; hosts are always adult and never jockeys |
| prop barriers (collision) | real blocks, seen like any block | never placed into a standing player; retried later |
| souls (right-click denied), sky-tree scroll, the Q key for lock-on | server cancels; low risk | watched with `/suld grim` |
| GSit `/sit` and `/lay` | ride seats that Grim does not simulate | — |
| GSit `/crawl` (client-side fake blocks) | could flag | crawl is op-only by default (`GSit.Crawl.*: op`) and no player is granted it |

`anticheat/GrimBridge` listens to Grim's `FlagEvent`. It registers the event by name, so SÜLD runs unchanged without
Grim, and loads with `softdepend: [GrimAC]`. A flag is cancelled only when all three conditions hold:
- the player is inside a window;
- the check is listed for that window's kind (`exempt-checks`, `lock-on-exempt-checks`, `mount-exempt-checks`);
- the player still has budget.

A cancelled flag counts no violation and causes no setback (Grim's `Check.recordFlag`).

`/suld grim` shows per check:
- outside a window;
- forgiven;
- inside a window but not forgiven;
- over budget.

`/suld grim reset` clears the counts.

## Measurement (dev server, mineflayer bot, 2026-10-11)

### Run 2: everything else

| Step | Result |
|---|---|
| sky tree | the seat hold opened; no movement flags |
| charge ×3 | 12 Simulation and 3 GroundSpoof forgiven, none outside the window |
| charge spammed every 0.7 s | mana stopped it (NO_RESOURCE); 24 Simulation and 6 GroundSpoof forgiven, under budget |
| /mori, ride, /mori | MOUNT window opened, no Vehicle flags |
| big boss (khangai_savdag) | host SCALE = 1.6 (capped from 2.3) |
| dodge, lock-on | not triggered by the bot (mineflayer's sneak and yaw handling). The bot's own wrapped yaw was flagged AimModulo360 outside any window, which is right. The lock-on fix is verified from Grim's source. **MANUAL_QA_REQUIRED** |

Budget arithmetic (unit test `TokenBucketTest`):
- a flag every tick for 10 s gets at most 121 of 200 forgiven, and Grim judges the rest;
- a dash every 5 s costing 25 flags is always forgiven whole.

### Run 1: the dashes

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
