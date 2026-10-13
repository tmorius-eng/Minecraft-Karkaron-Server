#!/usr/bin/env python3
"""Dev-server campaigns for the campaign bots (campaign-effects.js, dungeon-clear.js, campaign-deliver.js).

    tools/loadtest/dev/make_dev_campaigns.py <server dir>        write <server>/plugins/SULD/content/campaigns.json
    tools/loadtest/dev/make_dev_campaigns.py <server dir> --remove

The file is the bundled content/campaigns.json plus small test campaigns (target 60, so a bot reaches them in one
run), written where ContentLoader.serverOrBundled reads a server's own copy. Never ship it: --remove takes it away.
"""
import copy
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
BUNDLED = ROOT / "suld-plugin/src/main/resources/content/campaigns.json"


def test(base, cid, name, goal, effect):
    c = copy.deepcopy(base)
    c.update({"id": "campaign." + cid, "name": name, "description": "Хөгжүүлэлтийн шалгалтын аян (dev server only).",
              "goal": goal, "tiers": [1, 2, 3], "effect": effect, "cooldownCycles": 0})
    c.pop("requires", None)
    return c


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    target = pathlib.Path(sys.argv[1]) / "plugins/SULD/content/campaigns.json"
    if "--remove" in sys.argv:
        target.unlink(missing_ok=True)
        print("removed", target)
        return
    data = json.loads(BUNDLED.read_text(encoding="utf-8"))
    base = data["campaigns"][0]
    deliver = {"type": "DELIVER_ITEMS", "targets": ["item.chonon_arisan"], "target": 60}
    data["campaigns"] += [
        test(base, "test_kill", "Шалгалт: чоно", {"type": "KILL_MOBS", "targets": ["mob.goviin_chono"], "target": 60},
             {"type": "UNLOCK_EVENT", "ref": "event.chonyn_dovtolgoo"}),
        test(base, "test_prop", "Шалгалт: хөшөө", deliver,
             {"type": "PROP", "ref": "stone_man", "anchor": "site.khodoo_aral", "dx": 10, "dz": 0, "yaw": 90}),
        test(base, "test_npc", "Шалгалт: жинчин", deliver,
             {"type": "NPC", "ref": "merchant.caravan", "anchor": "merchant.general", "dx": 0, "dz": 5}),
        test(base, "test_deliver", "Шалгалт: хүргэлт", deliver,
             {"type": "PROP", "ref": "deer_stone", "anchor": "site.khodoo_aral", "dx": -10, "dz": 0, "yaw": 270}),
        test(base, "test_clear", "Шалгалт: агуй", {"type": "CLEAR_DUNGEON", "targets": ["dungeon.khasar_den"], "target": 60},
             {"type": "PROP", "ref": "deer_stone", "anchor": "site.uushig", "dx": -6, "dz": 0, "yaw": 270}),
    ]
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    print("wrote", target, len(data["campaigns"]), "campaigns")


if __name__ == "__main__":
    main()
