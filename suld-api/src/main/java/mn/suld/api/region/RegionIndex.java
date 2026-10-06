package mn.suld.api.region;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Point-in-region lookup. Pure; regions are few, so a priority-ordered scan is fastest. */
public final class RegionIndex {

    private final List<RegionDefinition> byPriority;

    public RegionIndex(List<RegionDefinition> regions) {
        Set<String> ids = new HashSet<>();
        for (RegionDefinition r : regions) {
            if (!ids.add(r.id())) throw new IllegalArgumentException("duplicate region id " + r.id());
        }
        List<RegionDefinition> sorted = new ArrayList<>(regions);
        sorted.sort(Comparator.comparingInt(RegionDefinition::priority).reversed().thenComparing(RegionDefinition::id));
        this.byPriority = List.copyOf(sorted);
    }

    /** The highest-priority region containing the point (relative to the anchor), if any. */
    public Optional<RegionDefinition> at(double dx, double dz) {
        for (RegionDefinition r : byPriority) {
            if (r.shape().contains(dx, dz)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public Optional<RegionDefinition> byId(String id) {
        return byPriority.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public List<RegionDefinition> all() {
        return byPriority;
    }
}
