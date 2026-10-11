# tools/loadtest — the Mineflayer bot harness

The functional and load bots the audit, the P1 stabilization and the Хуралдай slice used. They were kept outside the
repository until 2026-10-11 (audit `11-evidence-and-reproducibility.md`: "the bot harness is not in the repository");
this folder is the copy, so "16/16 bots pass" is reproducible.

**Local dev server only.** The bots join with `auth: 'offline'` on `127.0.0.1`; the dev Paper runs with
`auth.allow-insecure-offline-dev-mode: true` and `online-mode=false`. Never point them at the production host.

```bash
cd tools/loadtest/bots && npm install            # mineflayer 4.x
scripts/run-test-server.sh 1.21.11               # the dev Paper (see TESTING.md), console control: srv2ctl.sh
node bots/featbot.js ./srv2ctl.sh server.log     # the functional sweep (tutorial, chat, lock-on, dungeon hall, ...)
node bots/khuraldai-smoke.js ./srv2ctl.sh server.log 25572   # Их Хуралдай: ballot, vote, close, force, finder, resolve, chronicle, PvP policy
node bots/campaign-deliver.js ./srv2ctl.sh server.log test_deliver item.chonon_arisan 60 25572   # a DELIVER_ITEMS campaign to success
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
