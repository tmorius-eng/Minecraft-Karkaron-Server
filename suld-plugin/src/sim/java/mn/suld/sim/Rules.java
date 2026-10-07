package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.progression.LevelCurve;

/**
 * Everything that differs between the game as it is ({@link LiveRules}) and the proposed balance
 * ({@link ProposedRules}). The defaults here ARE the live behaviour; each one names the code it mirrors, so a change
 * in the plugin shows up as a broken golden test rather than a silently wrong simulation.
 */
public abstract class Rules {

    public abstract String name();

    public abstract LevelCurve curve();

    public abstract World world();

    public abstract Loot loot();

    // ------------------------------------------------------------------------------------------------- experience

    /** Kill EXP multiplier for a level gap (mob − player). Live: none ({@code MobDefinition.scaledExp}). */
    public double gapExpFactor(int gap) {
        return 1.0;
    }

    /** Whether a kill at this gap may roll gear at all. Live: always. */
    public boolean gapAllowsGear(int gap) {
        return true;
    }

    /** Upper bound of the additive boost total (clan + relic + EXP%). Live: none (ProgressionBoosts.java:19-25). */
    public double boostCap() {
        return Double.POSITIVE_INFINITY;
    }

    /** EXP share of each member of a party of {@code n} for one kill. Live: the killer takes everything. */
    public double partyKillShare(int n) {
        return 1.0 / n; // the killer gets it all; spread evenly over who lands kills, on average 1/n each
    }

    /** Daily login EXP (DailyReward.java:36-38). */
    public long loginExp(int day, int level) {
        return mn.suld.api.reward.DailyReward.exp(day, level);
    }

    public long loginCoins(int day) {
        return mn.suld.api.reward.DailyReward.coins(day);
    }

    /** EXP for an hour of rested bonus (proposed catch-up); live has none. */
    public double restedPerOfflineHour(int level) {
        return 0;
    }

    /** Extra EXP multiplier for players far behind the server (proposed catch-up). */
    public double catchUpFactor(int level, int serverLevel) {
        return 1.0;
    }

    /** EXP past the cap converted into Ascension progress ("Тэнгэрийн оноо"); live discards it. */
    public boolean keepsOverflow() {
        return false;
    }

    // ------------------------------------------------------------------------------------------------ player power

    /** Base max health. Live: vanilla 20 for every class (PlayerClass.baseHealth is display-only). */
    public double baseHealth(PlayerClass c, int level) {
        return 20.0;
    }

    /** Base attack before gear (CombatListener.attackOf:59-68). */
    public double baseAttack(PlayerClass c, int level) {
        return c.baseAttack() + (level - 1) * 0.75;
    }

    /**
     * Effective hits per second of the class's basic attack. Live: SÜLD damage ignores the attack-cooldown charge, so
     * every melee weapon is capped only by the 0.5 s hurt invulnerability (CombatListener.onHit0); bows need a full
     * draw (~1 s for full damage).
     */
    public double hitsPerSecond(PlayerClass c) {
        return c == PlayerClass.MERGEN ? 1.0 : 1.7;
    }

    /** Damage multiplier of a hit (heavy weapons hit harder when the cooldown is respected; live: 1). */
    public double hitWeight(PlayerClass c) {
        return 1.0;
    }

    /** Skill-tree offensive multiplier for the points spent (desk: +0.8 % per point, cap 80 points). */
    public double treeMultiplier(int points) {
        return 1.0 + 0.008 * Math.min(points, 80);
    }

    /**
     * Fraction of a mob hit the player's armour absorbs. Live: the ARMOR stat becomes the vanilla armour attribute,
     * whose reduction saturates at 80 % (20 points) — armour past ~25–40 does nothing.
     */
    public double mitigation(double armor, double dmg, int mobLevel) {
        double a = Math.max(0, armor);
        return Math.max(0, Math.min(0.8, Math.max(a / 5.0, a - dmg / 2.0) / 25.0));
    }

    /** Player damage multiplier against a mob {@code gap} levels above (proposed level suppression). */
    public double gapDamageDealt(int gap) {
        return 1.0;
    }

    /** Damage taken multiplier from a mob {@code gap} levels above. */
    public double gapDamageTaken(int gap) {
        return 1.0;
    }

    // --------------------------------------------------------------------------------------------------- dungeons

    /** Whether the player may start the dungeon (DungeonService.start:107-169: level only). */
    public boolean dungeonOpen(SimPlayer p, World.Dungeon d) {
        return p.level >= d.min() && !d.heroic();
    }

    /** Item level of the dungeon's reward roll (DungeonService.java:352). */
    public int dungeonLootLevel(World.Dungeon d, int playerLevel) {
        return Math.max(d.min(), playerLevel);
    }

    /** Reward multiplier for repeating the same dungeon (live: none). */
    public double repeatFactor(SimPlayer p, World.Dungeon d) {
        return 1.0;
    }

    /** Multiplier on a carried member's rewards (member level far below the party / dungeon). Live: none. */
    public double carryFactor(int memberLevel, int partyMax, World.Dungeon d) {
        return 1.0;
    }

    /** Whether a boss that reaches its enrage time wipes the party (BossService.java:76-86: live no). */
    public boolean enrageWipes() {
        return false;
    }

    // ------------------------------------------------------------------------------------------------------- death

    /** Real-world minutes a death keeps the player out of the game (live: 30 s soul, config.yml:44). */
    public double deathLockMinutes(int level, int ascension) {
        return 0.5;
    }

    /** Fraction of the current level bar lost (DeathRules.java:22-26). */
    public double deathBarLoss() {
        return 0.10;
    }

    /** Effective-stat penalty added per death on class gear (proposed death wound; live 0). */
    public double woundPerDeath() {
        return 0;
    }

    public double woundMax() {
        return 0;
    }

    /** Active play hours that heal one wound step. */
    public double woundHealHours() {
        return 1;
    }

    /** Fraction of carried material stacks lost on death (config.yml:48 loot-loss-fraction). */
    public double deathMaterialLoss() {
        return 0.5;
    }

    // ------------------------------------------------------------------------------------------------- skill points

    /** Skill points (SkillPoints.total). */
    public int skillPoints(int level, int chapters, int regions, int ascension) {
        return mn.suld.api.skill.tree.SkillPoints.total(level, chapters, regions, 0);
    }

    // ------------------------------------------------------------------------------------------------------ economy

    /** Coins a mob drops directly (live: none, CombatListener.java:160-207). */
    public long mobCoins(World.Mob m) {
        return 0;
    }

    /** Coins spent per active hour on repairs at a level (live: 5 + ceil(damage/8) on worn gear; desk ≈ 20/h). */
    public double repairPerHour(int level) {
        return 20;
    }

    /** What a merchant pays for an item (ItemEconomy.sellPrice). */
    public long sellPrice(mn.suld.api.item.ItemDefinition def, mn.suld.api.item.ItemInstance i) {
        return mn.suld.api.item.ItemEconomy.sellPrice(def, i);
    }

    /** Reforge cost of one item level (Reforge.java: 25L + 25). */
    public long reforgeCost(int itemLevel) {
        return 25L * itemLevel + 25;
    }

    // ------------------------------------------------------------------------------------------------ progression

    public boolean hasMastery() {
        return false;
    }

    public boolean hasAscension() {
        return false;
    }

    public boolean hasClassArmor() {
        return false;
    }

    /** The gear power the game expects at a level (proposed spec; live uses it for reporting only). */
    public double parGearPower(int level) {
        double r = level < 10 ? 0.5 : level < 20 ? 1.2 : level < 30 ? 2.0 : level < 40 ? 2.5 : level < 50 ? 3.0 : level < 60 ? 3.5 : 4.0;
        double mult = rarityStatMultiplierAt(r);
        return 8 * level * mult * 0.875;
    }

    /** Interpolated rarity stat multiplier for a fractional rarity ordinal. */
    static double rarityStatMultiplierAt(double ordinal) {
        ItemRarity[] rs = ItemRarity.values();
        int lo = (int) Math.floor(ordinal);
        int hi = Math.min(lo + 1, 6);
        double f = ordinal - lo;
        return rs[lo].statMultiplier() * (1 - f) + rs[hi].statMultiplier() * f;
    }

    /** Dungeon boss rarity band override (proposed); null keeps the loot table's own tier. */
    public String describe() {
        return name();
    }
}
