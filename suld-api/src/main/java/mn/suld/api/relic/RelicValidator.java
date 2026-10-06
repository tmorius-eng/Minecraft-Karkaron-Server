package mn.suld.api.relic;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reconciles the relic copies physically present in one inventory with the authoritative
 * records. This is the anti-duplication core: whatever exploit produced a copy (dupe glitch,
 * creative clone, restored backup, edited NBT), only the single current-generation copy in
 * the bearer's own inventory survives; everything else is removed, and a bearer who lost
 * their copy gets it re-issued.
 */
public final class RelicValidator {

    /** A relic-tagged stack found at {@code slot}. */
    public record Found(int slot, String key, UUID itemUuid, long generation, int amount) {
    }

    public enum Reason { UNKNOWN_RELIC, COUNTERFEIT_UUID, NOT_BEARER, STALE_GENERATION, EXTRA_COPY, IN_CONTAINER }

    public record Removal(int slot, String key, Reason reason) {
    }

    /**
     * @param removals     stacks to delete
     * @param fixAmount    slots holding the legitimate copy with amount != 1 (set to 1)
     * @param issue        relic keys the holder bears but does not physically carry
     */
    public record Plan(List<Removal> removals, List<Integer> fixAmount, Set<String> issue) {
        public boolean clean() {
            return removals.isEmpty() && fixAmount.isEmpty() && issue.isEmpty();
        }
    }

    private RelicValidator() {
    }

    /**
     * @param holder the player whose own inventory this is, or null for any container/other
     *               storage (where a relic may never be)
     */
    public static Plan plan(@Nullable UUID holder, List<Found> found, Map<String, RelicRecord> records) {
        List<Removal> removals = new ArrayList<>();
        List<Integer> fix = new ArrayList<>();
        Set<String> kept = new HashSet<>();
        for (Found f : found) {
            RelicRecord r = records.get(f.key());
            Reason reason;
            if (r == null) {
                reason = Reason.UNKNOWN_RELIC;
            } else if (!r.itemUuid().equals(f.itemUuid())) {
                reason = Reason.COUNTERFEIT_UUID;
            } else if (holder == null) {
                reason = Reason.IN_CONTAINER;
            } else if (!r.isOwnedBy(holder)) {
                reason = Reason.NOT_BEARER;
            } else if (f.generation() != r.version()) {
                reason = Reason.STALE_GENERATION;
            } else if (!kept.add(f.key())) {
                reason = Reason.EXTRA_COPY;
            } else {
                if (f.amount() != 1) fix.add(f.slot());
                continue;
            }
            removals.add(new Removal(f.slot(), f.key(), reason));
        }
        Set<String> issue = new LinkedHashSet<>();
        if (holder != null) {
            for (RelicRecord r : records.values()) {
                if (r.isOwnedBy(holder) && !kept.contains(r.key())) issue.add(r.key());
            }
        }
        return new Plan(removals, fix, issue);
    }
}
