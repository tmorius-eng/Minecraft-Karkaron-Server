package mn.suld.api.campaign;

import org.jetbrains.annotations.Nullable;

/**
 * A world-state flag ({@code suld_world_state}): the single source of truth for what campaigns changed in the world.
 *
 * @param expiresCycle the first cycle the flag no longer applies in, or null for a permanent change
 */
public record WorldFlag(String key, String value, String sourceCycle, @Nullable String expiresCycle, long setAt) {

    public WorldFlag {
        if (key == null || key.isBlank() || key.length() > 64) throw new IllegalArgumentException("flag key");
        value = value == null ? "" : value;
    }

    /** True when the flag still applies in {@code cycleId} (cycle ids sort chronologically as strings). */
    public boolean liveIn(String cycleId) {
        return expiresCycle == null || cycleId.compareTo(expiresCycle) < 0;
    }
}
