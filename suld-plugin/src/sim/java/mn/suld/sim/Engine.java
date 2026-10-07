package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.EquipSlot;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemEconomy;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemType;
import mn.suld.api.loot.LootTier;
import mn.suld.api.loot.Rng;
import mn.suld.api.mob.MobTier;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.Progression;
import mn.suld.api.progression.ProgressionEngine;
import mn.suld.api.quest.QuestType;
import mn.suld.api.skill.tree.StatKey;
import mn.suld.api.style.LevelRewards;
import mn.suld.api.style.Rank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Simulates one player, minute by minute of play, over a number of real days. Deterministic for a seed.
 *
 * <p>Each session the player repeatedly picks the activity with the best value per minute (EXP-equivalent, net of
 * the expected cost of dying), does it in a block, and visits town every 45 active minutes. The numbers of the
 * activities come from the {@link Rules}; the fight model is below ({@link #fight}).
 */
public final class Engine {

    /** Days at which a snapshot is taken (end of that day). */
    public static final int[] CHECKPOINTS = {1, 2, 3, 4, 5, 6, 7, 14, 30, 60, 90, 180};

    public record Result(Profile profile, PlayerClass clazz, List<Map<String, Double>> snaps, SimPlayer player) {
    }

    private final Rules r;
    private final ProposedRules pr;
    private final Profile prof;
    private final SplittableRandom rng;
    private final SimPlayer p;
    private final World w;
    private final ProgressionEngine pe;
    private final ItemCatalog catalog;

    private double t; // absolute minutes since day 0, 00:00
    private double end;
    private int day;
    private double sinceTown;
    private double todayActive;
    private boolean dailyPending;
    private double clan;
    private int rankIndex;
    private double afkFatigueKills;
    private final List<Map<String, Double>> snaps = new ArrayList<>();

    private Engine(Rules r, Profile prof, PlayerClass c, long seed) {
        this.r = r;
        this.pr = r instanceof ProposedRules x ? x : null;
        this.prof = prof;
        this.rng = new SplittableRandom(seed);
        this.w = r.world();
        this.pe = new ProgressionEngine(r.curve());
        this.catalog = r.loot().catalog();
        this.p = new SimPlayer(new UUID(seed, 0x5157L), c, new Gear(catalog));
    }

    public static Result run(Rules r, Profile prof, PlayerClass c, long seed, int days) {
        Engine e = new Engine(r, prof, c, seed);
        e.start();
        int next = 0;
        for (e.day = 0; e.day < days; e.day++) {
            e.p.milestonesToday = 0;
            e.session();
            e.p.dayLevel.add(e.p.level);
            e.p.dayMilestones.add(e.p.milestonesToday);
            e.p.dayLevelFraction.add(e.p.level + (e.p.level >= 60 ? 0 : (double) e.p.expInto / Math.max(1, e.r.curve().expForLevel(e.p.level))));
            while (next < CHECKPOINTS.length && CHECKPOINTS[next] == e.day + 1) {
                e.snaps.add(e.snapshot(e.day + 1));
                next++;
            }
        }
        return new Result(prof, c, e.snaps, e.p);
    }

    // ===================================================================================================== setup

    private void start() {
        if (r.hasClassArmor()) {
            rebuildClassGear();
        } else {
            // live: the starter class weapon (ClassWeapons.starter, tier 1, item level 1)
            ItemDefinition def = catalog.require("weapon.class." + p.clazz.id() + ".1");
            ItemInstance i = r.loot().catalog() == null ? null : new mn.suld.api.item.ItemGenerator(catalog)
                    .generate(def, def.rarity(), 1, Rng.seeded(p.id.getMostSignificantBits()), "starter", p.id);
            p.gear.put(EquipSlot.MAIN_HAND, new Gear.Piece(def, i, Gear.itemPower(def, i), true));
        }
    }

    // ================================================================================================== sessions

    private void session() {
        double start = day * 1440.0 + prof.startHour() * 60;
        end = start + prof.hoursPerDay() * 60;
        if (p.lockUntil >= end) {
            p.lockedMinutes += end - start;
            p.lockStreak++;
            p.lockStreakMax = Math.max(p.lockStreakMax, p.lockStreak);
            return;
        }
        p.lockStreak = 0;
        t = Math.max(start, p.lockUntil);
        p.lockedMinutes += t - start;
        // offline time: rested EXP (proposed)
        if (r.restedPerOfflineHour(p.level) > 0) {
            double offline = day == 0 ? 0 : 24 - prof.hoursPerDay();
            double cap = 1.5 * r.curve().expForLevel(Math.max(1, Math.min(59, p.level)));
            p.restedExp = Math.min(cap, p.restedExp + offline * r.restedPerOfflineHour(p.level));
        }
        clan = prof.archetype().equals("casual") ? (day >= 14 ? 0.02 : 0) : (day >= 7 ? 0.06 : 0);
        todayActive = 0;
        dailyPending = true;
        login();
        while (t < end - 0.5) {
            if (t < p.lockUntil) {
                t = p.lockUntil;
                continue;
            }
            step();
        }
    }

    private void login() {
        if (p.lastLoginDay == day) return;
        p.loginStreak = p.lastLoginDay == day - 1 ? p.loginStreak % 7 + 1 : 1;
        p.lastLoginDay = day;
        grant(r.loginExp(p.loginStreak, p.level), "login", false);
        p.earn(r.loginCoins(p.loginStreak));
    }

    /** One decision: pick an activity and do it. */
    private void step() {
        if (sinceTown >= 45) {
            town();
            return;
        }
        if (dailyPending && todayActive >= 40) daily();
        if (prof.exploit() == Profile.Exploit.AFK) {
            afkBlock();
            return;
        }
        World.Event ev = eventNow();
        if (ev != null && joinEvent(ev)) {
            event(ev);
            return;
        }
        Plan plan = choose();
        switch (plan.kind) {
            case CHAPTER -> chapter();
            case DUNGEON -> dungeonRun(plan.dungeon, plan.mythic);
            case EXPLORE -> explore(plan.zone);
            case GRIND -> grind(plan.zone, 20, null, 0);
            default -> grind(bestZone(), 20, null, 0);
        }
    }

    // ===================================================================================================== policy

    enum Kind { GRIND, DUNGEON, CHAPTER, EXPLORE }

    record Plan(Kind kind, World.Zone zone, World.Dungeon dungeon, int mythic, double value) {
    }

    private Plan choose() {
        Profile.Style s = prof.style();
        Profile.Exploit x = prof.exploit();
        if (x == Profile.Exploit.LOW_DUNGEON_FARM && p.level >= 12) {
            World.Dungeon d = w.dungeons().get(0);
            if (r.dungeonOpen(p, d)) return new Plan(Kind.DUNGEON, null, d, 0, 1);
        }
        if (x == Profile.Exploit.CARRY && p.level >= 5) {
            World.Dungeon best = null;
            for (World.Dungeon d : w.dungeons()) if (!d.heroic() && r.dungeonOpen(p, d)) best = d;
            if (best != null) return new Plan(Kind.DUNGEON, null, best, 0, 1);
        }
        if (x == Profile.Exploit.ZONE_RUSH) {
            World.Zone best = null;
            double bestExp = -1;
            for (World.Zone z : w.zones()) {
                double e = zoneValue(z, true);
                if (e > bestExp) {
                    bestExp = e;
                    best = z;
                }
            }
            return new Plan(Kind.GRIND, best, null, 0, bestExp);
        }
        boolean storyFirst = s != Profile.Style.DUNGEON_FARMER && s != Profile.Style.BOSS_FARMER && x != Profile.Exploit.SINGLE_ACTIVITY;
        if (chapterFeasible() && (storyFirst || storyBlocksDungeons())) return new Plan(Kind.CHAPTER, null, null, 0, 0);

        List<Plan> options = new ArrayList<>();
        World.Zone bz = bestZone();
        double grindValue = zoneValue(bz, false);
        options.add(new Plan(Kind.GRIND, bz, null, 0, grindValue));
        for (World.Dungeon d : w.dungeons()) {
            if (!r.dungeonOpen(p, d)) continue;
            int mythic = d.heroic() ? Math.min(ProposedRules.MYTHIC_TIERS, allHeroicsCleared() ? p.mythicTier + 1 : 0) : 0;
            double v = dungeonValue(d, mythic);
            if (Double.isNaN(v)) continue;
            double weight = switch (s) {
                case DUNGEON_FARMER -> 1.6;
                case BOSS_FARMER -> 1.4;
                case EXPLORER -> 0.8;
                default -> 1.0;
            };
            options.add(new Plan(Kind.DUNGEON, null, d, mythic, v * weight));
        }
        if (pr != null) {
            for (World.Zone z : w.zones()) {
                if (!p.regions.contains(z.id()) || p.landmarks.getOrDefault(z.id(), 0) >= z.landmarks()) continue;
                if (maxDanger(z) > prof.dangerTolerance()) continue;
                double v = exploreValue(z) * (s == Profile.Style.EXPLORER ? 2.0 : 1.0) * (needsExploration() ? 6.0 : 1.0);
                options.add(new Plan(Kind.EXPLORE, z, null, 0, v));
            }
        }
        if (x == Profile.Exploit.SINGLE_ACTIVITY) {
            // the single most efficient activity, forever: the best option ignoring variety
            Plan best = options.get(0);
            for (Plan o : options) if (o.value > best.value) best = o;
            return best;
        }
        options.sort((a, b) -> Double.compare(b.value, a.value));
        if (!prof.optimal() && options.size() > 1 && rng.nextDouble() < 0.25) return options.get(1);
        return options.get(0);
    }

    /** At the cap, the next Ascension rank asks for exploration (rank IV) — the player goes and does it. */
    private boolean needsExploration() {
        if (pr == null || p.level < 60) return false;
        return p.ascension + 1 == 4 || p.mastery[SimPlayer.M_EXPLORE] < 5 && p.ascension >= 3;
    }

    private boolean storyBlocksDungeons() {
        for (World.Dungeon d : w.dungeons()) {
            if (d.heroic() || p.level < d.min()) continue;
            if (p.clears.getOrDefault(d.id(), 0) == 0 && p.chapters <= d.chapterGate()) return true;
        }
        return false;
    }

    private boolean allHeroicsCleared() {
        for (World.Dungeon d : w.dungeons()) if (d.heroic() && !p.heroicClears.contains(d.id())) return false;
        return true;
    }

    // ============================================================================================== fight model

    record Fight(double ttk, double cycle, double danger, double hazard) {
    }

    private int points() {
        return r.skillPoints(p.level, p.chapters, p.regions.size(), p.ascension);
    }

    private double woundFactor() {
        return 1.0 - Math.min(r.woundMax(), p.wound * r.woundPerDeath());
    }

    private double masteryFactor() {
        return 1.0 + 0.0025 * p.masteryTotal() + 0.01 * p.ascension;
    }

    double attack() {
        var b = p.bonus();
        double a = r.baseAttack(p.clazz, p.level) + b.flatDamage();
        a *= 1 + p.stat(StatKey.ATTACK_PCT) / 100.0;
        a *= r.treeMultiplier(points()) * r.hitWeight(p.clazz);
        if (pr != null) a *= 1 + 0.03 * p.temper;
        return a * woundFactor() * masteryFactor() * prof.cal().build();
    }

    double dps() {
        double cc = Math.min(1, 0.05 + p.stat(StatKey.CRIT_CHANCE) / 100.0);
        double cm = 1.5 + p.stat(StatKey.CRIT_DAMAGE) / 100.0;
        double speed = 1 + p.stat(StatKey.ATTACK_SPEED_PCT) / 100.0;
        return attack() * r.hitsPerSecond(p.clazz) * speed * (1 + cc * (cm - 1)) * 1.2; // ×1.2 spells between hits
    }

    double maxHp() {
        double hp = r.baseHealth(p.clazz, p.level) + p.stat(StatKey.HEALTH) + 0.5 * points();
        if (pr != null) hp *= 1 + 0.03 * p.temper;
        return hp * woundFactor() * (pr != null ? masteryFactor() : 1.0) * prof.cal().build();
    }


    private double hitShare(World.Mob m) {
        double s = switch (p.clazz) {
            case BAATAR -> 0.55;
            case DARKHAN -> 0.55;
            case BOO -> 0.5;
            case KHULEGCHIN -> 0.45;
            case MERGEN -> 0.3;
        };
        return m.ranged() ? s * 0.7 : s;
    }

    private double partyRegen = 1.0;

    private double regen() {
        return (0.25 + p.stat(StatKey.HEALTH_REGEN) + (p.clazz == PlayerClass.BOO ? 0.6 : 0)) * partyRegen;
    }

    private double seek() {
        return p.clazz == PlayerClass.KHULEGCHIN ? 5.5 : 7.0;
    }

    /** Hazard of dying in one fight of danger d (damage of the fight / max health). */
    double hazard(double d) {
        double h = 1.2e-4 + 0.5 / (1 + Math.exp(-(d - 1.0) / 0.08));
        return Math.min(0.95, h * prof.cal().deathRate());
    }

    Fight fight(World.Mob m, double engaged, double partyDps) {
        int gap = m.level() - p.level;
        double dps = dps() * r.gapDamageDealt(gap) * partyDps;
        double ttk = m.hp() / Math.max(0.1, dps);
        double dodge = Math.min(0.6, p.stat(StatKey.DODGE_PCT) / 100.0);
        double inc = m.dmg() * r.gapDamageTaken(gap) * (1 - r.mitigation(p.stat(StatKey.ARMOR), m.dmg(), m.level())) * (1 - dodge) * hitShare(m) / m.interval();
        double dmg = Math.max(0, inc * engaged - regen()) * ttk;
        double d = dmg / Math.max(1, maxHp());
        return new Fight(ttk, ttk + seek(), d, hazard(d));
    }

    /** The worst fight a player must take in a zone (lethal elites/champions are avoided, so they do not count). */
    private double maxDanger(World.Zone z) {
        double worst = 0;
        for (World.Mob m : z.mobs()) {
            double d = fight(m, m.elite() ? 1.0 : 1.2, 1.0).danger();
            if (m.elite() && d > 0.9) continue;
            worst = Math.max(worst, d);
        }
        return worst;
    }

    // =========================================================================================== value estimates

    /** At the cap with nothing to spend EXP on (live), EXP is worth nothing: players farm loot instead. */
    private double expWorth() {
        return p.level >= 60 && !r.keepsOverflow() ? 0.0 : 1.0;
    }

    private double expMultiplier(boolean kill) {
        double boost = Math.min(r.boostCap(), clan + (prof.exploit() == Profile.Exploit.SINGLE_ACTIVITY ? 0 : 0));
        double m = 1 + boost;
        if (kill) m *= 1 + (p.stat(StatKey.EXP_PCT) + (prof.optimal() && p.level >= 5 ? 12 : 0)) / 100.0;
        return m * prof.cal().xp();
    }

    private double lostPlayMinutes(double lock) {
        double s = prof.hoursPerDay() * 60;
        if (lock <= s / 2) return lock;
        return lock * prof.hoursPerDay() / 24 + s / 2 * (1 - prof.hoursPerDay() / 24);
    }

    private double deathCost(double expPerMin) {
        double lock = r.deathLockMinutes(p.level, p.ascension);
        double bar = r.deathBarLoss() * p.expInto;
        double wound = r.woundPerDeath() > 0 ? 0.05 * r.woundHealHours() * 60 * expPerMin * 0.5 : 0;
        return lostPlayMinutes(lock) * expPerMin + bar + wound;
    }

    /** EXP per minute of grinding a zone, net of the expected death cost. */
    double zoneValue(World.Zone z, boolean rawExpOnly) {
        double time = 0, exp = 0, haz = 0, worst = 0;
        double eff = prof.efficiency() * prof.cal().killRate();
        for (int i = 0; i < z.mobs().size(); i++) {
            World.Mob m = z.mobs().get(i);
            double wt = z.weights()[i];
            Fight f = fight(m, m.elite() ? 1.0 : 1.2, partyDpsFactor());
            if (m.elite() && f.danger() > 0.9 && prof.party() == 1) {
                time += wt * 0.15;
                continue;
            }
            time += wt * f.cycle() / 60.0 / eff / partyKillRate();
            exp += wt * m.exp() * r.gapExpFactor(m.level() - p.level) * partyShareFactor();
            haz += wt * f.hazard();
            worst = Math.max(worst, f.danger());
        }
        double perMin = exp * expMultiplier(true) * (rawExpOnly ? 1 : expWorth()) / Math.max(1e-6, time);
        perMin = Math.min(perMin, exp * expMultiplier(true) * 18); // spawn ceiling: 18 kills per minute
        if (rawExpOnly) return perMin;
        double hazPerMin = haz / Math.max(1e-6, time);
        double v = perMin - hazPerMin * deathCost(perMin);
        if (worst > prof.dangerTolerance() * 1.6) v *= 0.2; // players do not willingly farm what kills them
        return v;
    }

    World.Zone bestZone() {
        World.Zone best = w.zones().get(0);
        double bv = -Double.MAX_VALUE;
        for (World.Zone z : w.zones()) {
            double v = zoneValue(z, false);
            if (v > bv) {
                bv = v;
                best = z;
            }
        }
        return best;
    }

    private double partyDpsFactor() {
        return prof.party() > 1 ? prof.party() * 0.85 : 1.0;
    }

    /** EXP share of each party kill a member receives (live: the killer only, ≈ 1/n; proposed: party share). */
    private double partyShareFactor() {
        int n = prof.party();
        return n > 1 ? r.partyKillShare(n) : 1.0;
    }

    /** Kills per minute of the party relative to solo: every member finds and fights mobs (spawns are per player). */
    private double partyKillRate() {
        return prof.party() > 1 ? prof.party() * 0.85 : 1.0;
    }

    private double exploreValue(World.Zone z) {
        int lv = (z.min() + z.max()) / 2;
        double exp = 0.02 * r.curve().expForLevel(Math.max(1, Math.min(59, lv))) * r.gapExpFactor(lv - p.level);
        return exp / 6.0 + 0.002 * need();
    }

    private long need() {
        return Math.max(1, r.curve().expForLevel(Math.max(1, Math.min(59, p.level))));
    }

    record RunPlan(double minutes, double success, double alive, int party, double kills, double killExp) {
    }

    /** Expected outcome of one run: duration, P(boss dead), P(player survives), party size used. */
    RunPlan planRun(World.Dungeon d, int mythic) {
        double scale = 1 + 0.12 * mythic;
        if (prof.exploit() == Profile.Exploit.CARRY) {
            // the carrier one-shots everything: a two-player party, near-certain success, short run
            RunPlan c = planWith(d, 2, scale * 0.05);
            return new RunPlan(c.minutes, 0.99, 0.995, 2, c.kills, c.killExp / (scale * 0.05) * scale);
        }
        int rec = pr == null ? 1 : ProposedRules.DUNGEONS.get(Math.min(d.index(), ProposedRules.DUNGEONS.size() - 1)).party();
        RunPlan solo = planWith(d, 1, scale);
        if (prof.party() > 1) return planWith(d, prof.party(), scale);
        if (solo.success * solo.alive >= 0.8 && rec <= 1) return solo;
        RunPlan group = planWith(d, Math.max(2, Math.max(rec, 4)), scale);
        return new RunPlan(group.minutes + 5, group.success, group.alive, group.party, group.kills, group.killExp); // 5 min to form a group
    }

    private RunPlan planWith(World.Dungeon d, int n, double scale) {
        partyRegen = n > 1 ? 1.0 + 0.8 * (n - 1) : 1.0; // groups heal each other (a Бөө, potions, shared aggro)
        try {
            return planWith0(d, n, scale);
        } finally {
            partyRegen = 1.0;
        }
    }

    private RunPlan planWith0(World.Dungeon d, int n, double scale) {
        double partyDps = n * 0.9;
        double seconds = 180 + 60; // queue/setup + walking in
        double survive = 1.0;
        double kills = 0, killExp = 0;
        for (List<World.Mob> wave : d.waves()) {
            double waveTime = 0;
            for (World.Mob m0 : wave) {
                World.Mob m = scaled(m0, scale);
                Fight f = fight(m, 1.3 / n, partyDps);
                waveTime += f.ttk();
                survive *= Math.pow(1 - f.hazard(), 1.0 / n);
                kills++;
                killExp += m.exp();
            }
            seconds += waveTime + 3 + 20; // mobs walk in, regroup
        }
        World.Mob boss = scaled(d.boss(), scale);
        Fight bf = fight(boss, 1.0, partyDps);
        double tankShare = n > 1 ? 0.6 : 1.0;
        double mech = prof.optimal() ? 1.15 : 1.30; // telegraphed mechanics: damage the stats do not prevent
        double dBoss = bf.danger() * tankShare * mech * (pr != null ? 1.0 : 1.0);
        double hBoss = hazard(dBoss);
        survive *= 1 - hBoss;
        seconds += bf.ttk();
        kills++;
        killExp += boss.exp();
        double success = 1.0;
        if (r.enrageWipes() && d.enrageSeconds() > 0 && bf.ttk() > d.enrageSeconds()) success = 0.05;
        if (n > 1) success *= 1 - Math.pow(1 - survive, n); else success *= survive;
        return new RunPlan(seconds / 60.0 * prof.cal().dungeonTime(), success, survive, n, kills, killExp);
    }

    private static World.Mob scaled(World.Mob m, double s) {
        if (s == 1.0) return m;
        return new World.Mob(m.id(), m.name(), m.level(), m.tier(), m.hp() * s, m.dmg() * s, m.interval(), m.ranged(),
                Math.round(m.exp() * (1 + (s - 1) * 0.5)), m.lootTable(), m.coins());
    }

    private double dungeonValue(World.Dungeon d, int mythic) {
        RunPlan rp = planRun(d, mythic);
        double ok = rp.success * rp.alive;
        if (ok < (prof.optimal() ? 0.6 : 0.8)) return Double.NaN;
        double rep = r.repeatFactor(p, d);
        double carry = r.carryFactor(p.level, p.level, d);
        double share = rp.party > 1 ? r.partyKillShare(rp.party) : 1.0;
        double exp = (d.completionExp() * expMultiplier(false) * rep * carry) + rp.killExp * share * expMultiplier(true)
                * r.gapExpFactor(d.boss().level() - p.level);
        int lootLevel = d.heroic() ? 60 : r.dungeonLootLevel(d, p.level);
        double useful = Math.max(0, 1 - Math.max(0, p.level - lootLevel) / 5.0); // loot far below you is vendor trash
        double lootValue = (pr == null ? 0.004 : 0.03) * need() * (1 + (d.heroic() ? 2 : 0)) * rep * useful;
        double first = p.clears.getOrDefault(d.id(), 0) == 0 ? 0.5 * need() : 0;
        if (pr == null && p.level >= 60) lootValue = 0.03 * need() * useful; // live endgame = loot farming
        double v = (exp * ok * expWorth() + lootValue + first) / rp.minutes;
        double hazPerMin = (1 - rp.alive) / rp.minutes;
        return v - hazPerMin * deathCost(v);
    }

    // ================================================================================================= activities

    private void spend(double minutes, String what) {
        t += minutes;
        p.activeMinutes += minutes;
        todayActive += minutes;
        sinceTown += minutes;
        p.minutesBySource.merge(what, minutes, Double::sum);
        if (p.wound > 0) {
            p.woundHeal += minutes;
            if (p.woundHeal >= r.woundHealHours() * 60) {
                p.wound--;
                p.woundHeal = 0;
            }
        }
        if (pr != null) armorXp(0.6 * minutes);
        if (pr != null) mastery(SimPlayer.M_CLASS, 0.1 * minutes);
    }

    private void travel(World.Zone z) {
        if (z != null && p.zoneHere != z.index()) {
            spend(p.clazz == PlayerClass.KHULEGCHIN ? 2.0 : 3.0, "travel");
            p.zoneHere = z.index();
            discover(z);
        }
    }

    private void discover(World.Zone z) {
        if (p.regions.add(z.id())) {
            grant(z.discoveryExp(), "discovery", false);
            armorXp(15);
            mastery(SimPlayer.M_EXPLORE, 300);
        }
    }

    /**
     * Grind a zone for up to {@code minutes}, stopping at a level-up. With a {@code target} (story), only kills of
     * that mob count toward {@code count}; returns the progress made.
     */
    private int grind(World.Zone z, double minutes, String target, int count) {
        travel(z);
        double stop = Math.min(Math.min(end, t + minutes), nextEvent());
        int startLevel = p.level;
        int progress = 0;
        double eff = prof.efficiency() * prof.cal().killRate();
        Fight[] fights = new Fight[z.mobs().size()];
        for (int i = 0; i < fights.length; i++) fights[i] = fight(z.mobs().get(i), z.mobs().get(i).elite() ? 1.0 : 1.2, partyDpsFactor());
        while (t < stop && t >= p.lockUntil) {
            int i = pick(z.weights());
            World.Mob m = z.mobs().get(i);
            Fight f = fights[i];
            if (m.elite() && f.danger() > 0.9 && prof.party() == 1) {
                spend(0.15, "grind"); // seen in time, avoided
                continue;
            }
            double perKill = Math.max(60.0 / 18, f.cycle() / eff) / 60.0 / partyKillRate();
            spend(perKill, "grind");
            if (rng.nextDouble() < f.hazard() * (prof.party() > 1 ? 0.6 : 1.0)) {
                die("grind:" + z.id());
                break;
            }
            kill(m, prof.party());
            if (target == null || target.equals(m.id())) progress++;
            if (count > 0 && progress >= count) break;
            if (p.level != startLevel) break;
        }
        return progress;
    }

    private int pick(double[] weights) {
        double x = rng.nextDouble();
        for (int i = 0; i < weights.length; i++) {
            x -= weights[i];
            if (x < 0) return i;
        }
        return weights.length - 1;
    }

    /** One mob dies: EXP, coins, loot, mastery, armour XP. */
    private void kill(World.Mob m, int party) {
        int gap = m.level() - p.level;
        double share = party > 1 ? r.partyKillShare(party) : 1.0;
        double exp = m.exp() * r.gapExpFactor(gap) * share * expMultiplier(true);
        if (pr != null && afkFatigueKills > 150) exp *= 0.3;
        if (p.restedExp > 0) {
            double bonus = Math.min(p.restedExp, exp);
            p.restedExp -= bonus;
            exp += bonus;
        }
        grant(Math.round(exp), "kill", true);
        p.kills++;
        if (m.elite()) p.eliteKills++;
        p.coinSource = "mob";
        p.earn(Math.round(r.mobCoins(m) * share));
        p.coinSource = "other";
        if (r.gapAllowsGear(gap)) loot(m.lootTable(), m.level(), LootTier.of(m.tier()));
        else if (pr != null) p.addMaterial("item.chonon_arisan", rng.nextDouble() < 0.3 ? 1 : 0);
        if (pr != null) {
            armorXp(m.tier() == MobTier.NORMAL ? 0.2 : m.tier() == MobTier.ELITE ? 1 : 3);
            if (gap >= -3) mastery(SimPlayer.M_COMBAT, m.tier() == MobTier.NORMAL ? 1 : m.tier() == MobTier.ELITE ? 4 : 10);
            mastery(SimPlayer.M_WEAPON, 0.5);
            mastery(SimPlayer.M_CLASS, 0.6);
        }
    }

    private void loot(String table, int level, LootTier tier) {
        Loot.Drop[] drops = r.loot().roll(table, level, tier, p.clazz, rng);
        offer(drops);
        double extra = p.stat(StatKey.LOOT_PCT) / 100.0 * prof.cal().loot() + (prof.cal().loot() - 1.0);
        while (extra > 0 && rng.nextDouble() < Math.min(1, extra)) {
            offer(r.loot().roll(table, level, tier, p.clazz, rng));
            extra -= 1;
        }
    }

    private void offer(Loot.Drop[] drops) {
        for (Loot.Drop d : drops) {
            if (!d.gear()) {
                p.addMaterial(d.def().id(), d.qty());
                continue;
            }
            if (pr != null) {
                if (p.collection.add(d.def().id() + "@" + d.item().rarity().id())) mastery(SimPlayer.M_COLLECT, 40); // each item in each rarity
                if (d.def().setId() != null && p.collection.add("set:" + d.def().id())) mastery(SimPlayer.M_COLLECT, 100);
            } else {
                p.collection.add(d.def().id());
            }
            ItemInstance i = d.item();
            boolean usable = d.def().allows(p.clazz) && Math.max(d.def().levelReq(), i.itemLevel()) <= p.level;
            if (usable && i.rarity().ordinal() >= ItemRarity.RARE.ordinal() && Double.isNaN(p.firstRarity[i.rarity().ordinal()])) {
                p.firstRarity[i.rarity().ordinal()] = p.activeHours();
            }
            if (usable && equip(d.def(), i)) continue;
            if (pr != null && (d.def().type().category() == ItemType.Category.ARMOR || d.def().type().category() == ItemType.Category.WEAPON)) {
                // proposed: loot armour/weapons feed the class gear (salvage into upgrade material)
                ItemEconomy.salvage(catalog, d.def(), i).forEach(p::addMaterial);
                p.salvaged++;
                if (pr != null) mastery(SimPlayer.M_CRAFT, 4);
            } else if (prof.style() == Profile.Style.CRAFTER) {
                ItemEconomy.salvage(catalog, d.def(), i).forEach(p::addMaterial);
                p.salvaged++;
            } else {
                long price = r.sellPrice(d.def(), i);
                if (price > 0) p.bagValue += price;
                else ItemEconomy.salvage(catalog, d.def(), i).forEach(p::addMaterial);
            }
        }
    }

    /** Wear the item if it beats what is in its slot (by item power). Proposed: class gear slots are fixed. */
    private boolean equip(ItemDefinition def, ItemInstance i) {
        double ip = Gear.itemPower(def, i);
        EquipSlot target = null;
        double worst = Double.MAX_VALUE;
        for (EquipSlot s : def.type().slots()) {
            Gear.Piece cur = p.gear.get(s);
            if (cur != null && cur.classGear() && r.hasClassArmor()) continue;
            double cp = cur == null ? 0 : cur.power();
            if (cp < worst) {
                worst = cp;
                target = s;
            }
        }
        if (target == null || ip <= worst * 1.02) return false;
        Gear.Piece old = p.gear.get(target);
        if (old != null && !old.classGear()) p.bagValue += r.sellPrice(old.def(), old.item());
        p.gear.put(target, new Gear.Piece(def, i, ip, false));
        double gp = p.gear.gearPower();
        if (gp >= p.lastGpMilestone * 1.05) {
            p.lastGpMilestone = gp;
            p.milestonesToday++;
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------- dungeons

    private void dungeonRun(World.Dungeon d, int mythic) {
        travel(w.zones().get(Math.min(w.zones().size() - 1, Math.max(0, zoneFor(d)))));
        RunPlan rp = planRun(d, mythic);
        double minutes = Math.min(rp.minutes, Math.max(1, end - t));
        p.dungeonRuns++;
        if (pr != null && mythic > 0) {
            p.spend(Math.min(p.coins, ProposedRules.mythicFee(mythic))); // the entry sigil is crafted
            mastery(SimPlayer.M_CRAFT, 60);
        }
        boolean alive = rng.nextDouble() < rp.alive;
        boolean success = rng.nextDouble() < rp.success / Math.max(1e-6, rp.alive) * (alive ? 1 : 0.5);
        spend(alive ? minutes : minutes * 0.7, "dungeon");
        if (!alive) {
            p.dungeonFails++;
            die("dungeon:" + d.id());
            return;
        }
        if (!success) {
            p.dungeonFails++;
            return;
        }
        double rep = r.repeatFactor(p, d);
        double share = rp.party > 1 ? r.partyKillShare(rp.party) : 1.0;
        int gap = d.boss().level() - p.level;
        // live: wave/boss kill EXP goes to whoever lands the kill (≈ 1/n each); proposed: party share
        double killExp = rp.killExp * (pr == null ? 1.0 / rp.party : share) * expMultiplier(true) * r.gapExpFactor(gap);
        int partyTop = prof.exploit() == Profile.Exploit.CARRY ? 60 : p.level;
        if (prof.exploit() == Profile.Exploit.CARRY) killExp *= pr == null ? 0.0 : r.carryFactor(p.level, partyTop, d); // live: the carrier lands every kill
        double exp = d.completionExp() * expMultiplier(false) * rep * r.carryFactor(p.level, partyTop, d) + killExp;
        grant(Math.round(exp), "dungeon", false);
        p.coinSource = "dungeon";
        p.earn(Math.round(d.coins() * rep));
        p.coinSource = "other";
        int lootLevel = d.heroic() ? 60 : r.dungeonLootLevel(d, p.level);
        if (pr != null) {
            String chest = mythic > 0 ? "loot.p.chest.mythic" : d.rewardTable();
            if (rep > 0.3) loot(chest, lootLevel, mythic > 0 ? LootTier.MYTHIC : d.heroic() ? LootTier.WORLD_EVENT : LootTier.DUNGEON);
            loot(d.boss().lootTable(), lootLevel, d.heroic() ? LootTier.WORLD_EVENT : LootTier.BOSS); // personal boss loot
        } else {
            loot(d.rewardTable(), lootLevel, LootTier.DUNGEON);
            if (rng.nextDouble() < 1.0 / rp.party) loot(d.boss().lootTable(), d.boss().level(), LootTier.BOSS); // killer only
        }
        if (p.clears.merge(d.id(), 1, Integer::sum) == 1) p.milestonesToday++;
        p.recentClears.addLast(d.id());
        while (p.recentClears.size() > 8) p.recentClears.removeFirst();
        p.bossKills.merge(d.boss().id(), 1, Integer::sum);
        if (d.heroic()) {
            p.heroicClears.add(d.id());
            if (Double.isNaN(p.firstEndgameDungeon)) p.firstEndgameDungeon = p.activeHours();
            if (mythic > p.mythicTier) {
                p.mythicTier = mythic;
                p.milestonesToday++;
                mastery(SimPlayer.M_COLLECT, 500); // the tier's trophy
            }
        } else if (d.min() >= 54 && Double.isNaN(p.firstEndgameDungeon) && pr == null) {
            p.firstEndgameDungeon = p.activeHours();
        }
        if (pr != null) {
            boolean firstBoss = p.bossKills.get(d.boss().id()) == 1;
            armorXp(25 * rep);
            mastery(SimPlayer.M_COMBAT, rp.kills * (gap >= -3 ? 1.5 : 0));
            mastery(SimPlayer.M_WEAPON, rp.kills * 0.75);
            mastery(SimPlayer.M_CLASS, 40 * rep + (firstBoss ? 100 : 0));
            mastery(SimPlayer.M_DUNGEON, 60 * (1 + Math.min(d.index(), 9)) * rep);
            mastery(SimPlayer.M_BOSS, firstBoss ? 400 : 40 * rep);
        }
    }

    private int zoneFor(World.Dungeon d) {
        if (pr != null) return ProposedRules.DUNGEONS.get(Math.min(d.index(), ProposedRules.DUNGEONS.size() - 1)).band();
        return Math.min(d.index(), w.zones().size() - 1);
    }

    // ---------------------------------------------------------------------------------------------------- story

    private World.Chapter currentChapter() {
        return p.chapters < w.story().size() ? w.story().get(p.chapters) : null;
    }

    private boolean chapterFeasible() {
        World.Chapter c = currentChapter();
        if (c == null) return false;
        return switch (c.type()) {
            case REACH_LEVEL -> p.level >= c.count();
            case COMPLETE_DUNGEON -> {
                World.Dungeon d = w.dungeon(c.target());
                if (d == null || !r.dungeonOpen(p, d)) yield false;
                RunPlan rp = planRun(d, 0);
                yield rp.success * rp.alive >= 0.5;
            }
            default -> c.zone() < 0 || maxDanger(w.zones().get(c.zone())) <= prof.dangerTolerance() * 1.3;
        };
    }

    private void chapter() {
        World.Chapter c = currentChapter();
        switch (c.type()) {
            case REACH_LEVEL -> complete(c);
            case DISCOVER_LOCATION -> {
                World.Zone z = w.zones().get(Math.max(0, c.zone()));
                travel(z);
                spend(Math.max(c.minutes(), 3), "story");
                complete(c);
            }
            case COMPLETE_DUNGEON -> {
                World.Dungeon d = w.dungeon(c.target());
                int before = p.clears.getOrDefault(d.id(), 0);
                dungeonRun(d, 0);
                if (p.clears.getOrDefault(d.id(), 0) > before) complete(c);
            }
            case KILL_MOB, COLLECT_ITEM -> {
                World.Zone z = w.zones().get(Math.max(0, c.zone()));
                if (c.minutes() > 0 && p.chapterProgress == 0) {
                    travel(z);
                    spend(c.minutes(), "story");
                }
                int got;
                if (c.type() == QuestType.COLLECT_ITEM && c.target() != null && r instanceof LiveRules lr) {
                    got = collect(z, c.target(), c.count() - p.chapterProgress, lr);
                } else {
                    got = grind(z, 25, c.target(), c.count() - p.chapterProgress);
                }
                p.chapterProgress += got;
                if (p.chapterProgress >= c.count()) complete(c);
            }
        }
    }

    private int collect(World.Zone z, String item, int count, LiveRules lr) {
        travel(z);
        double stop = Math.min(end, t + 25);
        int got = 0;
        double eff = prof.efficiency() * prof.cal().killRate();
        while (t < stop && got < count && t >= p.lockUntil) {
            int i = pick(z.weights());
            World.Mob m = z.mobs().get(i);
            Fight f = fight(m, 1.2, partyDpsFactor());
            spend(Math.max(60.0 / 18, f.cycle() / eff) / 60.0, "grind");
            if (rng.nextDouble() < f.hazard()) {
                die("grind:" + z.id());
                break;
            }
            kill(m, prof.party());
            if (rng.nextDouble() < lr.dropChance(m, item)) got++;
        }
        return got;
    }

    private void complete(World.Chapter c) {
        grant(c.exp(), "quest", false);
        p.coinSource = "quest";
        p.earn(c.coins());
        p.coinSource = "other";
        p.chapters++;
        p.chapterProgress = 0;
        p.milestonesToday++;
        if (pr != null) armorXp(20);
    }

    // ----------------------------------------------------------------------------------------- explore / events

    private void explore(World.Zone z) {
        travel(z);
        spend(6, "explore");
        int n = p.landmarks.merge(z.id(), 1, Integer::sum);
        int lv = (z.min() + z.max()) / 2;
        grant(Math.round(0.02 * r.curve().expForLevel(Math.max(1, Math.min(59, lv))) * r.gapExpFactor(lv - p.level)), "explore", false);
        armorXp(5);
        mastery(SimPlayer.M_EXPLORE, n > 8 ? 200 : 100); // hidden places (the last 4) are worth more
        mastery(SimPlayer.M_COLLECT, n > 8 ? 60 : 0);   // lore pages / secrets
        if (n % 4 == 0) p.addMaterial(ProposedRules.BANDS.get(z.index()).material(), 2);
    }

    private World.Event eventNow() {
        for (World.Event e : w.events()) {
            double phase = t % e.everyMinutes();
            if (phase < (e.id().equals("event.world_boss") ? 15.0 : 1.0) && t >= lastEventAt + e.minutes()) return e;
        }
        return null;
    }

    /** The next event start (blocks end there so the player can decide to join). */
    private double nextEvent() {
        double next = Double.MAX_VALUE;
        for (World.Event e : w.events()) {
            double n = (Math.floor(t / e.everyMinutes()) + 1) * e.everyMinutes();
            next = Math.min(next, n);
        }
        return next + 0.01;
    }

    private boolean joinEvent(World.Event e) {
        if (e.id().equals("event.world_boss")) return pr != null && p.level >= 55;
        double v = eventExp(e) / e.minutes();
        return v >= zoneValue(bestZone(), false) * 0.9;
    }

    private double eventExp(World.Event e) {
        if (!e.scaled()) return (e.baseExp() * 1.2 + 10 * 55) * (1 + clan); // wolf raid: payout + ~10 level-2 wolves
        if (e.id().equals("event.world_boss")) return 0.10 * need();
        return 0.02 * need() + zoneValue(bestZone(), true) * 8; // band raid: a small payout plus ten minutes of fighting
    }

    private double lastEventAt = -1e9;

    private void event(World.Event e) {
        lastEventAt = t;
        spend(e.minutes(), "event");
        grant(Math.round(eventExp(e) * (e.scaled() ? 1 : 1)), "event", false);
        if (!e.scaled()) {
            p.earn(Math.round(e.baseCoins() * 1.2));
            return;
        }
        p.earn(40 + 8L * p.level);
        if (e.id().equals("event.world_boss")) {
            p.worldBossKills++;
            loot("loot.p.world_boss", 60, LootTier.WORLD_EVENT);
            mastery(SimPlayer.M_BOSS, 80);
            if (p.worldBossKills <= 20) mastery(SimPlayer.M_COLLECT, 80); // trophies of the first kills
        }
    }

    private void daily() {
        dailyPending = false;
        if (pr != null) {
            grant(Math.round(3 * 0.04 * need()), "daily", false);
            p.earn(3 * (40 + 8L * p.level));
            return;
        }
        // live DailyTasks: 3 tasks of 5–12 kills (avg 8.5) of a mob from regions with min level <= level + 2
        World.Mob best = null;
        for (World.Zone z : w.zones()) if (z.min() <= p.level + 2) for (World.Mob m : z.mobs()) if (best == null || m.exp() > best.exp()) best = m;
        if (best == null) return;
        grant(Math.round(3 * Math.max(10, best.exp() * 8.5 / 2)), "daily", false);
        p.earn(Math.round(3 * (15 * 8.5 + 4 * best.level() * 8.5)));
        spend(8, "daily");
    }

    private void afkBlock() {
        // E5: stands in one spot with an auto-clicker; mobs walk in at half the normal rate
        World.Zone z = bestZone();
        p.zoneHere = z.index();
        double stop = Math.min(end, t + 30);
        while (t < stop) {
            int i = pick(z.weights());
            World.Mob m = z.mobs().get(i);
            Fight f = fight(m, 1.2, 1.0);
            double dt = f.cycle() * 2 / 60.0;
            t += dt;
            todayActive += dt;
            sinceTown += dt;
            if (pr == null) p.activeMinutes += dt; // no tracker in live: everything counts
            afkFatigueKills++;
            if (rng.nextDouble() < f.hazard()) {
                die("afk");
                break;
            }
            kill(m, 1);
        }
    }

    // ------------------------------------------------------------------------------------------------------ town

    private void town() {
        sinceTown = 0;
        afkFatigueKills = 0;
        t += 4;
        p.activeMinutes += 4;
        todayActive += 4;
        // sell the bag and spare materials (keeping the reforge / tier materials)
        p.coinSource = "sell.gear";
        p.earn(p.bagValue);
        p.bagValue = 0;
        p.coinSource = "sell.materials";
        for (Map.Entry<String, Integer> e : new ArrayList<>(p.materials.entrySet())) {
            int keep = keepOf(e.getKey());
            int sell = e.getValue() - keep;
            if (sell <= 0) continue;
            ItemDefinition def = catalog.item(e.getKey()).orElse(null);
            if (def == null) continue;
            p.materials.put(e.getKey(), keep);
            p.earn(Math.round(def.sellValue() * Math.max(1, Math.min(60, p.level)) / 10.0 * sell * 0.5 + def.sellValue() * sell));
        }
        p.coinSource = "other";
        long repair = Math.round(r.repairPerHour(p.level) * 0.75);
        p.spend(Math.min(p.coins, repair));
        if (pr == null) reforge();
        else {
            armorUpgrade();
            temper();
            ascend();
        }
        if (prof.style() == Profile.Style.CRAFTER) craft();
        // the rank ladder (a coin sink most players buy into)
        Rank[] ranks = Rank.values();
        while (rankIndex + 1 < ranks.length && p.level >= ranks[rankIndex + 1].requiredLevel()
                && p.coins >= ranks[rankIndex + 1].cost() * 1.5) {
            p.spend(ranks[rankIndex + 1].cost());
            rankIndex++;
        }
    }

    private int keepOf(String material) {
        return pr != null ? 1_000_000 : 6;
    }

    /** Live smith: +1 item level on the main-hand item up to the player level (Reforge.java), if affordable. */
    private void reforge() {
        if (!prof.optimal()) return;
        Gear.Piece main = p.gear.get(EquipSlot.MAIN_HAND);
        if (main == null || main.item().itemLevel() >= p.level || main.classGear()) return;
        for (int guard = 0; guard < 20 && main.item().itemLevel() < p.level; guard++) {
            int il = main.item().itemLevel();
            long cost = r.reforgeCost(il);
            String mat = il < 8 ? "item.chonon_arisan" : il < 15 ? "item.khilentsiin_khor" : il < 22 ? "item.baavgain_arisan" : "item.mosun_chuluu";
            if (p.coins < cost * 1.3 || !p.takeMaterial(mat, 2)) return;
            p.spend(cost);
            ItemInstance up = new mn.suld.api.item.ItemGenerator(catalog).upgrade(main.item(), main.def());
            main = new Gear.Piece(main.def(), up, Gear.itemPower(main.def(), up), false);
            p.gear.put(EquipSlot.MAIN_HAND, main);
            p.reforges++;
        }
    }

    private void craft() {
        for (var rec : catalog.recipes()) {
            if (rec.levelReq() > p.level) continue;
            if (p.coins < rec.coins() * 2) continue;
            boolean ok = true;
            for (var m : rec.materials().entrySet()) if (p.materials.getOrDefault(m.getKey(), 0) < m.getValue()) ok = false;
            if (!ok) continue;
            for (var m : rec.materials().entrySet()) p.takeMaterial(m.getKey(), m.getValue());
            p.spend(rec.coins());
            ItemDefinition def = catalog.require(rec.resultId());
            ItemRarity lo = rec.minRarity() == null ? def.rarity() : rec.minRarity();
            ItemRarity hi = rec.maxRarity() == null ? lo : rec.maxRarity();
            ItemRarity rar = ItemRarity.values()[lo.ordinal() + rng.nextInt(hi.ordinal() - lo.ordinal() + 1)];
            ItemInstance i = new mn.suld.api.item.ItemGenerator(catalog).generate(def, rar, Math.max(rec.levelReq(), def.levelReq()),
                    Rng.seeded(rng.nextLong()), "craft", p.id);
            p.crafted++;
            if (pr != null) mastery(SimPlayer.M_CRAFT, 30);
            if (!(def.allows(p.clazz) && equip(def, i))) p.earn(r.sellPrice(def, i));
        }
    }

    // ------------------------------------------------------------------------------------- class gear (proposed)

    private void armorXp(double xp) {
        if (pr == null) return;
        double mult = p.armorLevel < p.level - 3 ? 2.0 : 1.0;
        if (prof.exploit() == Profile.Exploit.AFK) mult = 0; // no movement / interaction signal: the tracker gives nothing
        p.armorXp += xp * mult;
        boolean changed = false;
        while (p.armorLevel < p.level && p.armorXp >= ProposedRules.armorNeed(p.armorLevel)) {
            p.armorXp -= ProposedRules.armorNeed(p.armorLevel);
            p.armorLevel++;
            p.milestonesToday++;
            changed = true;
        }
        if (p.armorLevel >= p.level) p.armorXp = Math.min(p.armorXp, ProposedRules.armorNeed(p.armorLevel));
        if (changed) rebuildClassGear();
    }

    private void armorUpgrade() {
        int next = p.armorTier + 1;
        if (next < ProposedRules.TIER_ARMOR_LEVEL.length && p.armorLevel >= ProposedRules.TIER_ARMOR_LEVEL[next]
                && p.clears.getOrDefault(ProposedRules.TIER_DUNGEON[next], 0) > 0
                && (next < 6 || p.ascension >= 3)
                && p.coins >= ProposedRules.TIER_COINS[next]) {
            String mat = ProposedRules.BANDS.get(ProposedRules.TIER_BAND[next]).material();
            if (p.takeMaterial(mat, 5 * next) || p.takeMaterial("item.tengeriin_chuluu", 3 * next)) {
                p.spend(ProposedRules.TIER_COINS[next]);
                p.armorTier = next;
                p.milestonesToday++;
                mastery(SimPlayer.M_COLLECT, 200); // a new visual tier of the class set
                p.armorEnhance = 0;
                rebuildClassGear();
            }
        }
        while (p.armorEnhance < ProposedRules.MAX_ENHANCE) {
            long cost = 40L * p.armorLevel * (p.armorEnhance + 1) * p.armorTier;
            if (p.coins < cost * 2) break;
            p.spend(cost);
            p.armorEnhance++;
            rebuildClassGear();
        }
    }

    private void temper() {
        if (p.level < 60) return;
        while (p.temper < 10) {
            int cost = 3 * (p.temper + 1);
            if (p.coins < ProposedRules.temperCoins(p.temper) || p.materials.getOrDefault("item.tengeriin_chuluu", 0) < cost) return;
            p.takeMaterial("item.tengeriin_chuluu", cost);
            p.spend(ProposedRules.temperCoins(p.temper));
            p.temper++;
            p.tempers++;
            p.milestonesToday++;
            mastery(SimPlayer.M_CRAFT, 300);
        }
    }

    private static final EquipSlot[] CLASS_SLOTS = {EquipSlot.HEAD, EquipSlot.CHEST, EquipSlot.LEGS, EquipSlot.FEET, EquipSlot.MAIN_HAND};

    private void rebuildClassGear() {
        ItemRarity want = ProposedRules.TIER_RARITY[p.armorTier];
        mn.suld.api.item.ItemGenerator gen = new mn.suld.api.item.ItemGenerator(catalog);
        for (EquipSlot s : CLASS_SLOTS) {
            ItemDefinition def = proxy(s, p.armorLevel, want);
            ItemRarity rar = clamp(want, def.rarity(), def.maxRarity());
            int il = Math.max(def.levelReq(), p.armorLevel);
            ItemInstance i = gen.generate(def, rar, il, Rng.seeded(p.id.getMostSignificantBits() ^ (s.ordinal() * 31L + il * 977L + p.armorTier)), "class", p.id);
            double power = Gear.itemPower(def, i) * (1 + 0.02 * p.armorEnhance) * 1.08; // class set bonus
            p.gear.put(s, new Gear.Piece(def, i, power, true));
        }
    }

    private static ItemRarity clamp(ItemRarity r, ItemRarity lo, ItemRarity hi) {
        if (r.ordinal() < lo.ordinal()) return lo;
        if (r.ordinal() > hi.ordinal()) return hi;
        return r;
    }

    /** The definition whose stat budget stands in for the class piece (same item level and rarity budget). */
    private ItemDefinition proxy(EquipSlot slot, int level, ItemRarity want) {
        ItemDefinition best = null;
        for (ItemDefinition d : catalog.items()) {
            if (!d.type().slots().contains(slot) || d.levelReq() > level || !d.allows(p.clazz)) continue;
            if (slot == EquipSlot.MAIN_HAND && !weaponTypeOf(p.clazz).equals(d.type())) continue;
            if (d.rarity() == ItemRarity.UNIQUE) continue;
            // the live class-weapon definitions have fixed stats (no per-level growth); the proposed class weapon
            // scales with the armour level, so a scaling definition of the same type stands in for it
            if (d.id().startsWith("weapon.class.")) continue;
            if (best == null || score(d, want) > score(best, want)) best = d;
        }
        if (best == null) best = catalog.require("weapon.class." + p.clazz.id() + ".1");
        return best;
    }

    private static double score(ItemDefinition d, ItemRarity want) {
        boolean reaches = d.canRoll(want);
        return (reaches ? 1000 : 0) + d.levelReq();
    }

    private static ItemType weaponTypeOf(PlayerClass c) {
        return switch (c) {
            case BAATAR -> ItemType.SWORD;
            case MERGEN -> ItemType.BOW;
            case BOO -> ItemType.STAFF;
            case DARKHAN -> ItemType.AXE;
            case KHULEGCHIN -> ItemType.SPEAR;
        };
    }

    // ---------------------------------------------------------------------------------------- mastery / ascension

    private void mastery(int track, double xp) {
        if (pr == null || xp <= 0) return;
        p.masteryXp[track] += xp;
        while (p.mastery[track] < 10 && p.masteryXp[track] >= ProposedRules.masteryNeed(p.mastery[track]) && milestone(track, p.mastery[track] + 1)) {
            p.masteryXp[track] -= ProposedRules.masteryNeed(p.mastery[track]);
            p.mastery[track]++;
            p.milestonesToday++;
        }
    }

    /** Varied objectives per rank, so a rank is never "the same thing 50 000 times". */
    private boolean milestone(int track, int rank) {
        return switch (track) {
            case SimPlayer.M_DUNGEON -> p.distinctClears() >= Math.min(10, rank) && (rank < 8 || !p.heroicClears.isEmpty());
            case SimPlayer.M_BOSS -> p.bossKills.size() >= Math.min(12, rank) && (rank < 9 || p.worldBossKills > 0);
            case SimPlayer.M_EXPLORE -> p.regions.size() >= Math.min(8, rank) && landmarksTotal() >= 4 * rank;
            case SimPlayer.M_CRAFT -> p.crafted + p.tempers + p.salvaged / 20 >= 2 * rank;
            case SimPlayer.M_COLLECT -> p.collection.size() >= 6 * rank;
            default -> p.level >= Math.min(60, 6 * rank);
        };
    }

    private int landmarksTotal() {
        int n = 0;
        for (int v : p.landmarks.values()) n += v;
        return n;
    }

    /** Ascension rank requirements (docs/ASCENSION_SPEC.md); each rank also spends Тэнгэрийн оноо. */
    boolean ascensionReady(int rank) {
        double gp = p.gear.gearPower(), par = r.parGearPower(60);
        boolean ok = switch (rank) {
            case 1 -> p.level >= 60 && p.chapters >= w.story().size() && p.distinctClears() >= 9 && gp >= 0.9 * par;
            case 2 -> p.distinctClears() >= 10 + 1 && p.masteryTotal() >= 20;
            case 3 -> p.heroicClears.size() >= 3 && p.worldBossKills >= 1 && gp >= par && p.mastery[SimPlayer.M_COMBAT] >= 5;
            case 4 -> p.regions.size() >= w.zones().size() && landmarksTotal() >= 0.8 * 12 * w.zones().size() && p.mastery[SimPlayer.M_EXPLORE] >= 5;
            case 5 -> p.mastery[SimPlayer.M_CRAFT] >= 6 && p.temper >= 5;
            case 6 -> p.mastery[SimPlayer.M_CLASS] >= 7 && dps() * Math.sqrt(maxHp()) >= 1.1 * parRating();
            case 7 -> p.mythicTier >= 1 && gp >= 1.1 * par;
            case 8 -> p.mythicTier >= 2 && p.wound == 0;
            case 9 -> p.masteryMin() >= 5 && p.masteryTotal() >= 60 && p.mythicTier >= 3;
            case 10 -> p.mythicTier >= 4 && p.clears.getOrDefault("dungeon.tengeriin_ordon", 0) >= 3 && gp >= 1.25 * par;
            default -> false;
        };
        return ok && p.overflowExp >= pr.ascensionCost(rank - 1) && p.coins >= ProposedRules.ascensionCoins(rank - 1);
    }

    private double parRatingCache = -1;

    private double parRating() {
        if (parRatingCache < 0) parRatingCache = 0.6 * dps() * Math.sqrt(maxHp()); // fixed at the first check
        return parRatingCache;
    }

    private void ascend() {
        while (p.ascension < 10 && ascensionReady(p.ascension + 1)) {
            p.overflowExp -= Math.round(pr.ascensionCost(p.ascension));
            p.spend(ProposedRules.ascensionCoins(p.ascension));
            p.ascension++;
            p.milestonesToday++;
            p.hoursAtAscension[p.ascension] = p.activeHours();
        }
    }

    // ------------------------------------------------------------------------------------------- exp and death

    private void grant(long amount, String source, boolean kill) {
        if (amount <= 0) return;
        if (p.level < 60) p.expBySource.merge(source, (double) amount, Double::sum);
        else p.overflowBySource.merge(source, (double) amount, Double::sum);
        if (p.level >= 60) {
            if (r.keepsOverflow()) p.overflowExp += amount;
            return;
        }
        int before = p.level;
        ExpGainResult res = pe.grant(new Progression(p.level, p.expInto), amount);
        p.level = res.after().level();
        p.expInto = res.after().expIntoLevel();
        p.totalExp += amount - res.wastedExp();
        if (r.keepsOverflow() && res.wastedExp() > 0) p.overflowExp += res.wastedExp();
        if (p.level > before) p.milestonesToday++;
        if (before < 20 && p.level >= 20) p.atLevel20 = dungeonCounts();
        for (int lv = before + 1; lv <= p.level; lv++) {
            p.hoursAtLevel.putIfAbsent(lv, p.activeHours());
            String was = p.coinSource;
            p.coinSource = "levelReward";
            LevelRewards.at(lv).ifPresent(rw -> p.earn(rw.coins()));
            p.coinSource = was;
        }
        if (p.level != before) {
            p.gear.levelChanged();
            p.powerAtLevel.putIfAbsent(p.level, new double[]{dps(), maxHp(), p.stat(StatKey.ARMOR), regen()});
        }
        if (!r.hasClassArmor() && p.level != before) classWeaponUpgrade();
    }

    /** Live ClassWeapons.upgrade: tier by level (1/10/25/45), regenerated at the player's level. */
    private void classWeaponUpgrade() {
        int tier = p.level >= 45 ? 4 : p.level >= 25 ? 3 : p.level >= 10 ? 2 : 1;
        Gear.Piece cur = p.gear.get(EquipSlot.MAIN_HAND);
        String id = "weapon.class." + p.clazz.id() + "." + tier;
        if (cur != null && cur.def().id().equals(id)) return;
        if (cur != null && !cur.classGear()) return; // wielding a looted weapon; the class weapon upgrades in the bag
        ItemDefinition def = catalog.require(id);
        ItemInstance i = new mn.suld.api.item.ItemGenerator(catalog).generate(def, def.rarity(), p.level, Rng.seeded(rng.nextLong()), "upgrade", p.id);
        p.gear.put(EquipSlot.MAIN_HAND, new Gear.Piece(def, i, Gear.itemPower(def, i), true));
    }

    private void die(String where) {
        p.deaths++;
        p.deathsBy.merge(where.contains(":") ? where.substring(0, where.indexOf(':')) + "@L" + (p.level / 10 * 10) : where, 1, Integer::sum);
        long lost = Math.round(p.expInto * r.deathBarLoss());
        p.expInto -= lost;
        p.expLostToDeath += lost;
        for (Map.Entry<String, Integer> e : p.materials.entrySet()) e.setValue((int) Math.floor(e.getValue() * (1 - r.deathMaterialLoss())));
        if (r.woundPerDeath() > 0) {
            p.wound = (int) Math.min(Math.round(r.woundMax() / r.woundPerDeath()), p.wound + 1);
            p.woundHeal = 0;
        }
        p.spend(Math.min(p.coins, Math.round(r.repairPerHour(p.level) * 3)));
        double lock = r.deathLockMinutes(p.level, p.ascension);
        p.lockUntil = t + lock;
        p.lockedMinutes += Math.min(lock, Math.max(0, end - t));
    }

    // -------------------------------------------------------------------------------------------------- snapshot

    private Map<String, Double> snapshot(int dayNo) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("day", (double) dayNo);
        m.put("level", (double) p.level);
        m.put("levelFraction", p.level + (p.level >= 60 ? 0 : (double) p.expInto / Math.max(1, r.curve().expForLevel(p.level))));
        m.put("totalExp", (double) p.totalExp);
        m.put("activeHours", p.activeHours());
        m.put("skillPoints", (double) points());
        m.put("gearScore", (double) p.gear.gearScore());
        m.put("gearPower", p.gear.gearPower());
        m.put("gearPowerPar", p.gear.gearPower() / Math.max(1, r.parGearPower(p.level)));
        m.put("highestRarity", (double) p.gear.highest().ordinal());
        m.put("normalRarity", (double) p.gear.median().ordinal());
        int[] dc = dungeonCounts();
        m.put("dungeonsOpen", (double) dc[0]);
        m.put("dungeonsClearable", (double) dc[1]);
        m.put("dungeonsTotal", (double) w.dungeons().stream().filter(d -> !d.heroic()).count());
        m.put("dungeonsCleared", (double) p.distinctClears() - p.heroicClears.size());
        m.put("heroicCleared", (double) p.heroicClears.size());
        m.put("bossesDefeated", (double) p.bossKills.size());
        m.put("storyPct", 100.0 * p.chapters / Math.max(1, w.story().size()));
        m.put("regions", (double) p.regions.size());
        m.put("regionsTotal", (double) w.zones().size());
        m.put("masteryTotal", (double) p.masteryTotal());
        m.put("coins", (double) p.coins);
        m.put("coinsEarned", (double) p.coinsEarned);
        m.put("coinsSpent", (double) p.coinsSpent);
        m.put("crafting", (double) (p.crafted + p.reforges + p.tempers));
        m.put("ascension", (double) p.ascension);
        m.put("armorLevel", (double) (r.hasClassArmor() ? p.armorLevel : 0));
        m.put("armorTier", (double) (r.hasClassArmor() ? p.armorTier : 0));
        m.put("mythicTier", (double) p.mythicTier);
        m.put("deaths", (double) p.deaths);
        m.put("lockedHours", p.lockedMinutes / 60.0);
        m.put("endgamePct", endgamePct());
        m.put("maxGearPct", 100.0 * Math.min(1, p.gear.gearPower() * (1 + 0.03 * p.temper) / gpMax()));
        m.put("dps", dps());
        m.put("hp", maxHp());
        return m;
    }

    /** {open, realistically clearable (P ≥ 0.8 with the group the player would use), total} of the normal ladder. */
    int[] dungeonCounts() {
        int open = 0, clearable = 0, total = 0;
        for (World.Dungeon d : w.dungeons()) {
            if (d.heroic()) continue;
            total++;
            if (r.dungeonOpen(p, d)) {
                open++;
                RunPlan rp = planRun(d, 0);
                if (rp.success * rp.alive >= 0.8) clearable++;
            }
        }
        return new int[]{open, clearable, total};
    }

    /** Gear power of the best possible kit: mythic class gear at 60 +5, mythic loot slots, temper +10. */
    double gpMax() {
        double cls = 5 * 60 * ItemRarity.MYTHIC.statMultiplier() * 1.0 * 1.10 * 1.08;
        double loot = 3 * 60 * ItemRarity.MYTHIC.statMultiplier() * 1.0;
        return r.hasClassArmor() ? (cls + loot) * 1.30 : 8 * 60 * ItemRarity.MYTHIC.statMultiplier();
    }

    private double endgamePct() {
        if (pr == null) return 0;
        double gp = Math.min(1, p.gear.gearPower() * (1 + 0.03 * p.temper) / gpMax());
        return 100 * (0.4 * p.ascension / 10.0 + 0.2 * p.mythicTier / 10.0 + 0.2 * gp + 0.2 * p.masteryTotal() / 80.0);
    }
}
