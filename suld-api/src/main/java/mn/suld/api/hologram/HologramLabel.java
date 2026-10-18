package mn.suld.api.hologram;

import java.util.List;
import java.util.Map;

/**
 * The text a game object carries (content/holograms.json, «labels»): a dungeon gate, a historic site, a training
 * station, a profession node, a soul beacon. The object decides where it stands; the label says how high above it
 * ({@link #dy}), how it looks and what it says, with placeholders the object fills ({@code {name}}, {@code {level}} …).
 */
public record HologramLabel(String id, double dy, HologramStyle style, Map<String, List<String>> variants) {

    public HologramLabel {
        variants = Map.copyOf(variants);
    }

    /** The lines of {@code variant}, else of the label's fixed text. */
    public List<String> lines(String variant) {
        List<String> l = variant == null ? null : variants.get(variant);
        return l != null ? l : variants.getOrDefault(HologramBoard.TEXT, List.of());
    }
}
