package mn.suld.api.worldevent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure state of one world event: shared progress, per-player contribution, expiry, and the
 * ranked reward split. Time is always passed in, so behaviour is deterministic under test.
 */
public final class WorldEventRun {

    /** Bonus multipliers for the top three contributors; everyone else qualifying gets 1.0. */
    private static final double[] PODIUM = {1.5, 1.3, 1.2};

    private final WorldEventDefinition definition;
    private final Instant startedAt;
    private final Instant endsAt;
    private final Map<UUID, Integer> contributions = new HashMap<>();
    private WorldEventState state = WorldEventState.ACTIVE;
    private int kills;

    public WorldEventRun(WorldEventDefinition definition, Instant startedAt) {
        this.definition = definition;
        this.startedAt = startedAt;
        this.endsAt = startedAt.plusSeconds(definition.durationSeconds());
    }

    public WorldEventDefinition definition() { return definition; }
    public WorldEventState state() { return state; }
    public boolean isActive() { return state == WorldEventState.ACTIVE; }
    public int kills() { return kills; }
    public Instant startedAt() { return startedAt; }
    public Map<UUID, Integer> contributions() { return Collections.unmodifiableMap(contributions); }

    public long remainingSeconds(Instant now) {
        return Math.max(0, endsAt.getEpochSecond() - now.getEpochSecond());
    }

    public double progress() {
        return Math.min(1.0, kills / (double) definition.targetKills());
    }

    /**
     * Credit a kill of {@code mobId} by {@code player}.
     * @return true if this kill counted (target mob, event active and not yet expired)
     */
    public boolean recordKill(UUID player, String mobId, Instant now) {
        if (!isActive() || !definition.targetMobIds().contains(mobId)) return false;
        if (expireIfDue(now)) return false;
        kills++;
        contributions.merge(player, 1, Integer::sum);
        if (kills >= definition.targetKills()) {
            state = WorldEventState.SUCCEEDED;
        }
        return true;
    }

    /** @return true exactly when this call moved the event from ACTIVE to FAILED by timeout. */
    public boolean expireIfDue(Instant now) {
        if (isActive() && !now.isBefore(endsAt)) {
            state = WorldEventState.FAILED;
            return true;
        }
        return false;
    }

    public void cancel() {
        if (isActive()) state = WorldEventState.FAILED;
    }

    /** Ranked rewards for qualifying players; empty unless the event SUCCEEDED. */
    public List<EventReward> rewards() {
        if (state != WorldEventState.SUCCEEDED) return List.of();
        List<Map.Entry<UUID, Integer>> eligible = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : contributions.entrySet()) {
            if (e.getValue() >= definition.minContribution()) eligible.add(e);
        }
        eligible.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(e -> e.getKey().toString()));
        List<EventReward> out = new ArrayList<>(eligible.size());
        for (int i = 0; i < eligible.size(); i++) {
            double mult = i < PODIUM.length ? PODIUM[i] : 1.0;
            Map.Entry<UUID, Integer> e = eligible.get(i);
            out.add(new EventReward(e.getKey(), i + 1, e.getValue(),
                    Math.round(definition.rewardExp() * mult), Math.round(definition.rewardCurrency() * mult)));
        }
        return out;
    }
}
