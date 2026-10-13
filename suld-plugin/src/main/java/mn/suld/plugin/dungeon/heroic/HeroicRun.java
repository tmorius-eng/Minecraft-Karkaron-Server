package mn.suld.plugin.dungeon.heroic;

import mn.suld.api.dungeon.heroic.HeroicLadder;
import mn.suld.api.dungeon.heroic.SteppeAffix;
import mn.suld.api.mob.MobDefinition;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The heroic part of one dungeon run (docs/HEROIC_DUNGEONS.md): its key, the week's affixes, the clock and the deaths.
 * Held by the run in DungeonService, read on the main thread only. Its mobs stand at {@link HeroicLadder#MOB_LEVEL}:
 * their health is set at spawn ({@link #health}), their hits are lifted by {@link #damageFactor} (HeroicDungeonService).
 */
public final class HeroicRun {

    public final UUID runId = UUID.randomUUID();
    public final String dungeonId;
    public final int key;
    public final Set<SteppeAffix> affixes;
    public final String weekId;
    public final int limitSeconds;
    public final long startedAt = System.currentTimeMillis();
    /** Every member who fell (each counts once: a fallen member cannot come back into the hall). */
    public int deaths;
    long nextZudAt;
    long nextStrikeAt;
    boolean warnedLimit;

    HeroicRun(String dungeonId, int key, List<SteppeAffix> affixes, String weekId, int limitSeconds) {
        this.dungeonId = dungeonId;
        this.key = HeroicLadder.clamp(key);
        this.affixes = affixes.isEmpty() ? EnumSet.noneOf(SteppeAffix.class) : EnumSet.copyOf(affixes);
        this.weekId = weekId;
        this.limitSeconds = limitSeconds;
        this.nextZudAt = startedAt + SteppeAffix.ZUD_PERIOD_SECONDS * 1000L;
    }

    public boolean has(SteppeAffix a) {
        return affixes.contains(a);
    }

    /** A hall mob's max health in this run: the mob at level 60, × the key's health multiplier. */
    public double health(MobDefinition def) {
        return def.atLevel(HeroicLadder.MOB_LEVEL).scaledHealth() * HeroicLadder.healthMultiplier(key);
    }

    /**
     * What one of this run's mobs' hits are multiplied by: from its content level to level 60 (the attack curve), × the
     * key's damage multiplier. Every hit path (melee, arrows, boss phases, brain abilities) is a multiple of the mob's
     * content attack, so one factor lifts them all.
     */
    public double damageFactor(MobDefinition def) {
        double base = def.scaledAttack();
        double lifted = def.atLevel(HeroicLadder.MOB_LEVEL).scaledAttack();
        return (base > 0 ? lifted / base : 1.0) * HeroicLadder.damageMultiplier(key);
    }

    /** Seconds since the run began. */
    public double elapsed(long now) {
        return Math.max(0, now - startedAt) / 1000.0;
    }

    /** The clock the run is judged by (elapsed + the death penalties). */
    public double clock(long now) {
        return HeroicLadder.clock(elapsed(now), deaths);
    }

    /** The affixes for a line of text: «Цагаан Зуд, Чонын Омог», or «—». */
    public String affixNames() {
        if (affixes.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        for (SteppeAffix a : affixes) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(a.displayName());
        }
        return sb.toString();
    }

    /** The affix ids for storage («ZUD,GAN»). */
    public String affixIds() {
        StringBuilder sb = new StringBuilder();
        for (SteppeAffix a : affixes) {
            if (!sb.isEmpty()) sb.append(',');
            sb.append(a.name());
        }
        return sb.toString();
    }
}
