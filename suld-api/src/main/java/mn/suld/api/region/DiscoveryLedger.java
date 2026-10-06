package mn.suld.api.region;

import java.util.HashSet;
import java.util.Set;

/**
 * A player's discovered regions. {@link #discover} answers "is this the first time?" — the
 * persistent store (PRIMARY KEY player+region) is what makes the reward exactly-once across
 * sessions and servers; this in-memory set just avoids asking it on every step.
 */
public final class DiscoveryLedger {

    private final Set<String> discovered = new HashSet<>();

    public DiscoveryLedger(Set<String> alreadyDiscovered) {
        discovered.addAll(alreadyDiscovered);
    }

    public boolean discover(String regionId) {
        return discovered.add(regionId);
    }

    public boolean has(String regionId) {
        return discovered.contains(regionId);
    }

    public Set<String> snapshot() {
        return Set.copyOf(discovered);
    }
}
