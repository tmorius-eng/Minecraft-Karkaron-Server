package mn.suld.api.campaign;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure in-memory state of the running campaign: the shared total, each player's units and clan, success or expiry.
 * Like {@link mn.suld.api.worldevent.WorldEventRun} but restorable: {@link #restore} rebuilds it from the database
 * totals after a restart. The database is the authoritative total at resolution; this is what the boss bar shows.
 */
public final class CampaignRun {

    private final CampaignDefinition definition;
    private final String cycleId;
    private final Instant startsAt;
    private final Instant endsAt;
    private final Map<UUID, Long> units = new HashMap<>();
    private final Map<UUID, UUID> clanOf = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<String> credited = new HashSet<>();
    private CampaignState state;
    private long total;

    public CampaignRun(CampaignDefinition definition, String cycleId, Instant startsAt, Instant endsAt) {
        this.definition = definition;
        this.cycleId = cycleId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.state = CampaignState.ACTIVE;
    }

    /** Rebuild from the database: the row's state and total, plus every player's units. */
    public static CampaignRun restore(CampaignDefinition def, CampaignSnapshot row, List<Contribution> totals) {
        CampaignRun run = new CampaignRun(def, row.cycleId(), Instant.ofEpochMilli(row.startsAt()), Instant.ofEpochMilli(row.endsAt()));
        run.state = row.state();
        run.total = row.total();
        for (Contribution c : totals) {
            run.units.put(c.player(), c.units());
            run.names.put(c.player(), c.name());
            if (c.clan() != null) run.clanOf.put(c.player(), c.clan());
        }
        return run;
    }

    public CampaignDefinition definition() { return definition; }
    public String cycleId() { return cycleId; }
    public Instant startsAt() { return startsAt; }
    public Instant endsAt() { return endsAt; }
    public CampaignState state() { return state; }
    public boolean isActive() { return state == CampaignState.ACTIVE; }
    public long total() { return total; }
    public Map<UUID, Long> units() { return Collections.unmodifiableMap(units); }
    public long unitsOf(UUID player) { return units.getOrDefault(player, 0L); }
    public int tierOf(UUID player) { return definition.tierOf(unitsOf(player)); }
    public double progress() { return Math.min(1.0, total / (double) definition.goal().target()); }

    public long remainingSeconds(Instant now) {
        return Math.max(0, endsAt.getEpochSecond() - now.getEpochSecond());
    }

    /**
     * Credit {@code amount} units to {@code player}. {@code dedupeKey} (a dungeon run id or a site id) makes the credit
     * count once; null for kills and deliveries.
     *
     * @return true if the units counted (active, matching, not yet expired, not a repeat)
     */
    public boolean record(UUID player, String name, @Nullable UUID clan, CampaignGoal.Type type, String targetId, long amount,
                          @Nullable String dedupeKey, Instant now) {
        if (!isActive() || amount <= 0 || !definition.goal().counts(type, targetId)) return false;
        if (expireIfDue(now)) return false;
        if (dedupeKey != null && !credited.add(player + "|" + dedupeKey)) return false;
        units.merge(player, amount, Long::sum);
        names.put(player, name);
        if (clan != null) clanOf.put(player, clan); else clanOf.remove(player);
        total += amount;
        if (total >= definition.goal().target()) state = CampaignState.SUCCEEDED;
        return true;
    }

    /** @return true exactly when this call moved the campaign from ACTIVE to FAILED by its end time. */
    public boolean expireIfDue(Instant now) {
        if (isActive() && !now.isBefore(endsAt)) {
            state = CampaignState.FAILED;
            return true;
        }
        return false;
    }

    public void cancel() {
        if (isActive()) state = CampaignState.CANCELLED;
    }

    /** Players by units, largest first; ties by name then id so the order is stable. */
    public List<Contribution> ranking() {
        List<Contribution> out = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : units.entrySet()) {
            out.add(new Contribution(e.getKey(), names.getOrDefault(e.getKey(), "?"), clanOf.get(e.getKey()), e.getValue(), null, null));
        }
        out.sort(Comparator.comparingLong(Contribution::units).reversed().thenComparing(Contribution::name).thenComparing(c -> c.player().toString()));
        return out;
    }

    /** Units per clan, from the members' contributions. */
    public Map<UUID, Long> clanUnits() {
        Map<UUID, Long> out = new HashMap<>();
        for (Map.Entry<UUID, Long> e : units.entrySet()) {
            UUID clan = clanOf.get(e.getKey());
            if (clan != null) out.merge(clan, e.getValue(), Long::sum);
        }
        return out;
    }

    /** How many players reached at least tier 1. */
    public int participants() {
        int n = 0;
        for (long u : units.values()) if (definition.tierOf(u) >= 1) n++;
        return n;
    }

    public CampaignSnapshot snapshot(long version, @Nullable Long resolvedAt, @Nullable Long effectAppliedAt) {
        return new CampaignSnapshot(cycleId, definition.id(), state, startsAt.toEpochMilli(), endsAt.toEpochMilli(), resolvedAt,
                definition.goal().target(), total, effectAppliedAt, version);
    }
}
