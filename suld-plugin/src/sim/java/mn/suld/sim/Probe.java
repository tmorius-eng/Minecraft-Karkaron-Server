package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;

import java.util.Map;

/** Development aid: prints single trajectories ({@code java mn.suld.sim.Probe live|proposed [archetype] [class] [days]}). */
public final class Probe {

    public static void main(String[] args) {
        String rules = args.length > 0 ? args[0] : "live";
        String arch = args.length > 1 ? args[1] : "hardcore";
        PlayerClass c = args.length > 2 ? PlayerClass.valueOf(args[2].toUpperCase()) : PlayerClass.BAATAR;
        int days = args.length > 3 ? Integer.parseInt(args[3]) : 30;
        Rules r = rules.equals("live") ? new LiveRules() : new ProposedRules();
        Profile prof = switch (arch) {
            case "casual" -> Profile.casual();
            case "active" -> Profile.active();
            default -> Profile.hardcore();
        };
        if (args.length > 4 && args[4].equals("party4")) prof = prof.party(4);
        if (args.length > 4 && args[4].equals("carry")) prof = prof.exploit(Profile.Exploit.CARRY);
        long t0 = System.nanoTime();
        Engine.Result res = Engine.run(r, prof, c, 42, days);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        for (Map<String, Double> s : res.snaps()) {
            System.out.printf("d%-4.0f L%-5.2f h%-6.1f GP%-6.0f par%-4.2f rar%.0f/%.0f dun %.0f/%.0f/%.0f ch%.0f%% reg%.0f coins %.0f(+%.0f/-%.0f) deaths %.0f lock %.1fh AL%.0f T%.0f asc%.0f m%.0f end%.0f%% dps %.1f hp %.0f%n",
                    s.get("day"), s.get("levelFraction"), s.get("activeHours"), s.get("gearPower"), s.get("gearPowerPar"),
                    s.get("normalRarity"), s.get("highestRarity"), s.get("dungeonsOpen"), s.get("dungeonsClearable"), s.get("dungeonsCleared"),
                    s.get("storyPct"), s.get("regions"), s.get("coins"), s.get("coinsEarned"), s.get("coinsSpent"), s.get("deaths"),
                    s.get("lockedHours"), s.get("armorLevel"), s.get("armorTier"), s.get("ascension"), s.get("masteryTotal"),
                    s.get("endgamePct"), s.get("dps"), s.get("hp"));
        }
        SimPlayer p = res.player();
        System.out.println("hours at level: " + new java.util.TreeMap<>(p.hoursAtLevel));
        System.out.println("exp by source: " + p.expBySource);
        System.out.println("minutes by source: " + p.minutesBySource);
        System.out.println("kills " + p.kills + " runs " + p.dungeonRuns + " fails " + p.dungeonFails + " clears " + p.clears);
        System.out.println("asc hours " + java.util.Arrays.toString(p.hoursAtAscension) + " mastery " + java.util.Arrays.toString(p.mastery)
                + " landmarks " + p.landmarks + " temper " + p.temper + " mythic " + p.mythicTier + " wb " + p.worldBossKills
                + " overflow " + p.overflowExp + " coins " + p.coins + " heroic " + p.heroicClears.size() + " regions " + p.regions.size());
        System.out.println("overflow by " + p.overflowBySource);
        System.out.println("deaths by: " + p.deathsBy + " fails " + p.dungeonFails + "/" + p.dungeonRuns);
        System.out.println("coins by source: " + p.coinsBySource + " spent " + p.coinsSpent);
        System.out.println("first rarity (h): " + java.util.Arrays.toString(p.firstRarity));
        System.out.println("bonus flat " + p.bonus().flatDamage() + " keys " + p.bonus().statKeys());
        p.gear.worn().forEach((k, v) -> System.out.println("  " + k + " " + v.def().id() + " " + v.item().rarity() + " L" + v.item().itemLevel() + " ip " + Math.round(v.power()) + " " + v.item().stats() + " aff " + v.item().affixes()));
        System.out.println("ms " + ms);
    }
}
