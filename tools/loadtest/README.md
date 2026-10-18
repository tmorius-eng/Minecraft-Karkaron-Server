# tools/loadtest — the Mineflayer bot harness

The functional and load bots the audit, the P1 stabilization and the Хуралдай slice used. They were kept outside the
repository until 2026-10-11 (audit `11-evidence-and-reproducibility.md`: "the bot harness is not in the repository");
this folder is the copy, so "16/16 bots pass" is reproducible.

**Local dev server only.** The bots join with `auth: 'offline'` on `127.0.0.1`; the dev Paper runs with
`auth.allow-insecure-offline-dev-mode: true` and `online-mode=false`. Never point them at the production host.

```bash
cd tools/loadtest/bots && npm install            # mineflayer 4.x
scripts/run-test-server.sh 1.21.11               # the dev Paper (see TESTING.md), console control: srv2ctl.sh
tools/loadtest/regress.sh ./srv3ctl.sh srv3/logs/latest.log 25572   # every pass/fail bot in order, one table, exit 1 on a FAIL
DEV_CAMPAIGNS=1 tools/loadtest/regress.sh ./srv3ctl.sh srv3/logs/latest.log 25572   # + the 4 dev-campaign bots (dev/make_dev_campaigns.py, server restarts)
# 31 runs (+4 with DEV_CAMPAIGNS, +1 with DEV_CRASH). Bot names use 5 random digits: an offline UUID comes from the name, and a reused
# name is an old profile, not a fresh player. trial-lockend.js: the lock ends mid-trial; permbot.js: staff commands refuse a player.
# soulreform.js: the death reform (docs/DEATH_AND_RECOVERY.md) — a free beginner death, the beam and its 70 % refund, the soul's
# rise at an ovoo, a broken and a finished ovoo ritual, 19 checks; trialbot.js also checks the won trial's Сүнсний Хэлтэрхий.
# trainingbot.js: the training grounds (docs/TRAINING_GROUNDS.md) from the class window to leaving, 24 checks;
# heroicbot.js: heroic keys (docs/HEROIC_DUNGEONS.md) — the gates, a timed +1 and +3 (Цагаан Зуд forced, ops the bot for
# the run, deops after), a failed +2, the jade spent at the smith (it answers the smith's dialog with a raw
# custom_click_action packet), 19 checks; it leaves the pack prompt unanswered so the HUD is plain text it can read.
# authbot.js: the hybrid login (docs/AUTHENTICATION.md §2b) on the dev pair of hybrid-dev.sh, not on srv3 and not in
# regress.sh: DB_PASS=… tools/loadtest/hybrid-dev.sh <dir> setup <suld.jar> <paper.jar>; hybrid-dev.sh <dir> start;
# node bots/authbot.js 25580 25581 <dir>/server/logs/latest.log. It answers AuthMe's pre-join dialog with a raw
# configuration-phase custom_click_action; 11 checks (register, relog, wrong password, waiting, twin, /premium, direct
# connect, the log's mode line and audits).
# trainingpay.js (DEV_CAMPAIGNS=1): a campaign resolved during training pays the real character on leaving; a trainee hurts no mob;
# trainingcrash.js (DEV_CRASH=1): SIGKILL during training, restart, the bag and spot come back — KILLS THE DEV SERVER.
# Trial bots count only spirit wolves (named «Сүнсний чоно», glowing): a natural wolf pack can live at the test spot.
# atlasbot.js: /map (docs/WORLD_MAP.md) — the stage, the 25 map frames and their pixels (map packets), D pans, the wheel zooms,
# no region named close up, all eight at the whole world (wholly on the wall, none over another), the «Агуй» tab filters,
# Shift closes; 13 checks.
# mapcbot.js: the corner minimap, pins and the waypoint (docs/WORLD_MAP.md part C) — the three owner-only frames facing up,
# the map's marker row, the frames follow a teleport and a turn (above the line of fire), an arrow through one flies on,
# /map minimap, hidden in the atlas, the waypoint bar and label, /map stop, a pin stored, back after a relog (database),
# removed; 13 checks.
# partymapbot.js: a party on the map — two bots in one party: B as a light blue arrow on A's minimap (on the rim when far),
# B's name on A's atlas that moves with B, nothing of B once B leaves the party; 7 checks.
# skyclickbot.js: a click in a sky window is the window's own — as Мэргэн, a right click on the ground shoots, in the atlas
# and in /menu it shoots nothing and an R-L-R casts nothing, back on the ground it shoots again; 4 checks.
# questregionbot.js: each Act II kill chapter's quest bar names the region it is played in (staff /suld quest); 7 checks.
# healthbot.js: the health survives a relog and a server restart (it restarts the dev server once); 3 checks.
# sitesbot.js: a historic site (Аураг) is built once and remembered across a restart (it restarts the dev server once); 2 checks.
# honorbot.js: Тэнгэрийн Зэрэг IV–X — the VII–IX back aura, IV's lighter duel hits, the VI lightning (none at V), X by the
# real rite on the monument and in the Chronicle, the entry hidden again; 7 checks (it leaves a hidden Chronicle row).
# professionbot.js: Мэргэжил in the wild — a tier-I vein gathers one ore and is then spent, walking away breaks a gather,
# tier II asks for level 13, a herb patch, a herder's pen (a horse's hair, a sheep's wool, the sheep shorn), refining
# once and all, a SÜLD wool block never placed, the crafts after a relog and the pen after a restart (it restarts the
# dev server once); 16 checks.
# achievebot.js: achievements and the pass «Хуралдайн Зам» (docs/ACHIEVEMENTS_AND_PASS.md) — a fresh player has none, a first
# clear and thirteen clears (with the title), each tier announced, a region found in the wild, the daily cap, tier 5's free
# reward claimed once, the heroic track opened by staff and its two rewards, both windows, all of it after a relog; 13
# checks (deeds are recorded with the staff /suld achievement, the game's own path; it resets the player at the end).
# paybot.js <srvctl.sh> [port] [stubPort] (NOT in regress.sh: it needs the server set up for it): the Bonum credit store
# against tools/payments/bonum_stub.py (a local stand-in, never Bonum) — the server started with payment.bonum enabled,
# base-url http://127.0.0.1:<stubPort> and the stub's three dev BONUM_* variables; QR_PY names a python with OpenCV
# (tools/payments/qr_decode.py reads the map's pixels). /buy → the QR map in hand (it decodes to the stub's code), an
# unsigned, a tampered and a wrong-amount webhook grant nothing, the signed one grants once with the line, three replays
# change nothing, the map cannot be dropped and is taken once paid, 2 open intents at most, the staff line, a relog;
# 16 checks (docs/PAYMENT_ARCHITECTURE.md §Testing).
# firstbootbot.js <srvctl.sh> <server dir>: a fresh world's first boot (NOT in regress.sh: it DELETES the world of <server dir>
# and refuses unless the directory holds a FIRSTBOOT_SCRATCH file — a throwaway copy with the default config, never a real
# world): every dungeon gate built once and only after Kharkhorum sets the spawn, the sites and ovoos move with it, a restart
# builds nothing again; 6 checks.
node bots/featbot.js ./srv2ctl.sh server.log     # the functional sweep (tutorial, chat, lock-on, dungeon hall, ...)
node bots/khuraldai-smoke.js ./srv2ctl.sh server.log 25572   # Их Хуралдай: ballot, vote, close, force, finder, resolve, chronicle, PvP policy
node bots/campaign-deliver.js ./srv2ctl.sh server.log test_deliver item.chonon_arisan 60 25572   # a DELIVER_ITEMS campaign to success
node bots/dungeon-clear.js ./srv2ctl.sh server.log 25572      # Саарлын Агуй to completion: campaign unit + chronicle (dev campaign test_clear)
node bots/campaign-effects.js ./srv2ctl.sh server.log 25572   # plaza boards, kill contributions, PROP and NPC effects (dev campaigns test_kill/test_prop/test_npc)
node bots/mmo50.js 50                            # 50 role-mixed bots for the capacity run (docs/perf/WORLD_50.md)
```

Every script prints `PASS`/`FAIL` lines to stderr and one JSON summary to stdout. `<srv2ctl.sh> cmd <console command>`
runs a console command on the dev server (the scripts call it for `/tp`, `/suld exp`, `/khuraldai open`, …); the
`logfile` argument is the server log the scripts grep for `[campaign]`, `[chronicle]`, `[khuraldai]` lines.

The class menu has a confirm page (ONB-006): a script clicks the class slot (10–14), then «Батлах» in slot 11.

The dev server's SÜLD config for the bots: `auth.allow-insecure-offline-dev-mode: true` and `khuraldai.min-account-days: 0`
(fresh test accounts may vote at once). `campaign-deliver.js` expects a running campaign with a reachable target, e.g. a
dev-only `campaign.test_deliver` (DELIVER_ITEMS, target 60, tiers 1/2/3) in the server's `plugins/SULD/content/campaigns.json`.

Port: the audit's dev server listened on 25571 (most scripts), the protocol probe `bot.js` on 25570; pass the port
as the last argument where a script takes one.
