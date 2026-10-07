package mn.suld.api.item;

import java.util.Objects;

/** An affix as rolled on one item: which affix and the value it got. */
public record RolledAffix(String affixId, double value) {
    public RolledAffix {
        Objects.requireNonNull(affixId, "affixId");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("value");
    }
}
