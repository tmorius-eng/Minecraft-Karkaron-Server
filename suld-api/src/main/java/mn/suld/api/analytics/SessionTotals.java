package mn.suld.api.analytics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregate counters of one play session (docs/ANALYTICS.md): bumped by hooks that already fire (one add each, no
 * per-tick work), turned into one {@code session_end} event when the player leaves. Not thread-safe: main thread.
 */
public final class SessionTotals {

    private final long startedAt;
    public long activeMinutes;
    public long combatMinutes;
    public long mobsDefeated;
    public double damageDealt;
    public double damageTaken;
    public long questsCompleted;
    public long dungeonRuns;
    public long dungeonClears;
    public long bossesDefeated;
    public long discoveries;
    public long exp;
    public double armorXp;
    public double masteryXp;
    public long deaths;
    public long itemsLooted;
    public long rareItems;
    public long revives;

    public SessionTotals(long startedAtMillis) {
        this.startedAt = startedAtMillis;
    }

    public long startedAt() {
        return startedAt;
    }

    /** The totals as event attributes (rounded; seconds of session time up to {@code endMillis}). */
    public Map<String, Object> attributes(long endMillis) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("started_at", startedAt);
        m.put("duration_s", Math.max(0, (endMillis - startedAt) / 1000));
        m.put("active_min", activeMinutes);
        m.put("combat_min", combatMinutes);
        m.put("mobs", mobsDefeated);
        m.put("dmg_dealt", Math.round(damageDealt));
        m.put("dmg_taken", Math.round(damageTaken));
        m.put("quests", questsCompleted);
        m.put("dungeon_runs", dungeonRuns);
        m.put("dungeon_clears", dungeonClears);
        m.put("bosses", bossesDefeated);
        m.put("discoveries", discoveries);
        m.put("exp", exp);
        m.put("armor_xp", Math.round(armorXp * 10) / 10.0);
        m.put("mastery_xp", Math.round(masteryXp * 10) / 10.0);
        m.put("items", itemsLooted);
        m.put("rare_items", rareItems);
        m.put("deaths", deaths);
        m.put("revives", revives);
        return m;
    }
}
