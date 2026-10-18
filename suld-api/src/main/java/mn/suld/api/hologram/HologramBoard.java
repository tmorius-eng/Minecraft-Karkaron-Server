package mn.suld.api.hologram;

import java.util.List;
import java.util.Map;

/**
 * A hologram that stands on its own in the world (content/holograms.json, «boards»): where it stands, how it looks and
 * what it says. Nothing about it is in code; a {@link #source} only fills its placeholders with live values.
 *
 * @param anchor    a city point (WorldBuildService.pointLocation, e.g. {@code spawn}, {@code event.plaza}) or
 *                  {@code city} (the city's middle, on the ground)
 * @param placement OFFSET: at the anchor plus (dx, dy, dz); ARC: in the direction (dx, dz) from the anchor, at the first
 *                  clear radius from {@link #radius}, dy above it; ABOVE: on top of board {@link #above}, dy above its
 *                  top line, whatever its height
 * @param clear     a board that would stand in a build moves in (OFFSET: to half the offset) or is not drawn
 * @param ground    OFFSET only: dy counts from the ground under the board, not from the anchor's height
 * @param sparkle   a few gold sparks while a player is near (so new players notice it)
 * @param tag       an extra scoreboard tag (bots and commands find a board by it); null: none
 * @param source    the live values its text shows ({@link HologramLoader#SOURCES}); null: a fixed text
 * @param variants  its lines by variant: {@link #TEXT} for a fixed board, the source's variants otherwise
 * @param items     a list placeholder's line for each entry ({@code {names}} → one line per envoy)
 * @param headers   lines shown before a list's entries, only when it has any
 */
public record HologramBoard(String id, String anchor, Placement placement, double dx, double dy, double dz, double radius,
                            String above, boolean clear, boolean ground, boolean sparkle, String tag, HologramStyle style,
                            String source, Map<String, List<String>> variants, Map<String, String> items,
                            Map<String, List<String>> headers) {

    /** The variant of a board without a source. */
    public static final String TEXT = "text";

    public enum Placement { OFFSET, ARC, ABOVE }

    public HologramBoard {
        variants = Map.copyOf(variants);
        items = Map.copyOf(items);
        headers = Map.copyOf(headers);
    }

    /** The lines of {@code variant}, else of the fixed text. */
    public List<String> lines(String variant) {
        List<String> l = variant == null ? null : variants.get(variant);
        return l != null ? l : variants.getOrDefault(TEXT, List.of());
    }
}
