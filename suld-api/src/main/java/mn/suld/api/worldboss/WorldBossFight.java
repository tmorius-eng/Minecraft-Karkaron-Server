package mn.suld.api.worldboss;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One world-boss fight, pure and clock-injected (docs/WORLD_BOSSES.md). Damage ranks the fighters; time spent at the
 * fight decides who is rewarded, so a healer or a shield who deals little still earns the full reward (the GW2
 * lesson: everyone who took part gets the whole prize, the podium only adds on top).
 */
public final class WorldBossFight {

    /** Fighters the health is sized for at most: 200× (the WORLD_BOSS tier) is a crowd of twenty. */
    public static final int CROWD_MAX = 20;
    /** Fewest fighters the health is sized for: a world boss is never a solo fight. */
    public static final int CROWD_MIN = 2;
    /** Podium on top of the full reward, by damage rank. */
    public static final double[] PODIUM = {1.5, 1.3, 1.2};

    public record Fighter(UUID id, String name, double damage, long presentSeconds) {
    }

    public record Reward(UUID player, String name, int rank, double damage, double multiplier) {
    }

    private final WorldBossDefinition definition;
    private final String runId;
    private final Instant startedAt;
    private final Map<UUID, Fighter> fighters = new LinkedHashMap<>();

    public WorldBossFight(WorldBossDefinition definition, String runId, Instant startedAt) {
        this.definition = definition;
        this.runId = runId;
        this.startedAt = startedAt;
    }

    public WorldBossDefinition definition() { return definition; }
    public String runId() { return runId; }
    public Instant startedAt() { return startedAt; }

    /** The share of the tier's full health a boss sized for {@code fighters} gets: 2 → 0.1 … 20 or more → 1.0. */
    public static double healthScale(int fighters) {
        return Math.max(CROWD_MIN, Math.min(CROWD_MAX, fighters)) / (double) CROWD_MAX;
    }

    public long elapsedSeconds(Instant now) {
        return Math.max(0, now.getEpochSecond() - startedAt.getEpochSecond());
    }

    /** Past its duration: the boss withdraws. */
    public boolean timedOut(Instant now) {
        return elapsedSeconds(now) >= definition.durationSeconds();
    }

    /** Damage that landed (capped by the caller at the health the boss still had: an overkill counts once). */
    public void damage(UUID player, String name, double amount) {
        if (player == null || !(amount > 0)) return;
        Fighter f = fighters.get(player);
        fighters.put(player, new Fighter(player, name, (f == null ? 0 : f.damage()) + amount, f == null ? 0 : f.presentSeconds()));
    }

    /** {@code seconds} more at the fight (alive, with a class, within reach). */
    public void present(UUID player, String name, long seconds) {
        if (player == null || seconds <= 0) return;
        Fighter f = fighters.get(player);
        fighters.put(player, new Fighter(player, name, f == null ? 0 : f.damage(), (f == null ? 0 : f.presentSeconds()) + seconds));
    }

    public double totalDamage() {
        double t = 0;
        for (Fighter f : fighters.values()) t += f.damage();
        return t;
    }

    /** Everyone who touched the fight, most damage first (ties: longest at the fight, then name). */
    public List<Fighter> ranking() {
        List<Fighter> out = new ArrayList<>(fighters.values());
        out.sort(Comparator.comparingDouble(Fighter::damage).reversed()
                .thenComparing(Comparator.comparingLong(Fighter::presentSeconds).reversed())
                .thenComparing(Fighter::name));
        return out;
    }

    /**
     * Rewarded: at the fight for at least a third of it (at most 60 s asked), and either a real share of the damage
     * (0.5 %) or there for half of it (the healer and the shield). Standing at the edge for ten seconds earns nothing.
     */
    public static boolean eligible(Fighter f, long elapsedSeconds, double totalDamage) {
        long need = Math.min(60, Math.max(1, elapsedSeconds / 3));
        if (f.presentSeconds() < need) return false;
        boolean dealt = totalDamage > 0 && f.damage() >= totalDamage * 0.005;
        return dealt || f.presentSeconds() * 2 >= elapsedSeconds;
    }

    /** The rewards of a won fight: every eligible fighter, ranked by damage, the first three with the podium on top. */
    public List<Reward> rewards(Instant now) {
        long elapsed = elapsedSeconds(now);
        double total = totalDamage();
        List<Reward> out = new ArrayList<>();
        for (Fighter f : ranking()) {
            if (!eligible(f, elapsed, total)) continue;
            int rank = out.size() + 1;
            // the podium is a damage ranking: someone who dealt none (a small fight's healer) gets the full reward, no more
            boolean podium = rank <= PODIUM.length && f.damage() > 0;
            out.add(new Reward(f.id(), f.name(), rank, f.damage(), podium ? PODIUM[rank - 1] : 1.0));
        }
        return out;
    }
}
