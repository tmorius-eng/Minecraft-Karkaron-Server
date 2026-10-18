package mn.suld.plugin.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The atlas's marks kept from one redraw to the next (forensic audit 2026-10-18, P1). A redraw asks for each mark by a
 * stable key and moves the one it already has, instead of removing every marker, label and region name and spawning
 * them again; a pan used to do that to the whole set every 6 ticks. What a redraw no longer asks for comes back from
 * {@link #end} to be removed. Main thread.
 *
 * @param <H> what a mark is (a display entity in the game, anything in a test)
 */
final class MarkPool<H> {

    private final Map<String, H> live = new LinkedHashMap<>();
    private final Set<String> wanted = new HashSet<>();

    /** Starts a redraw: no mark is wanted until it is asked for again. */
    void begin() {
        wanted.clear();
    }

    /**
     * A key not yet used in this redraw: {@code key} itself, else {@code key#2}, {@code key#3}… (two places with the
     * same name at the same spot still get one mark each).
     */
    String unique(String key) {
        if (!wanted.contains(key)) return key;
        for (int n = 2; ; n++) {
            String k = key + "#" + n;
            if (!wanted.contains(k)) return k;
        }
    }

    /** The mark kept under {@code key}, now wanted again, or null when there is none (make one and {@link #put} it). */
    H take(String key) {
        H h = live.get(key);
        if (h != null) wanted.add(key);
        return h;
    }

    /** Keeps {@code mark} under {@code key}, wanted in this redraw. */
    void put(String key, H mark) {
        live.put(key, mark);
        wanted.add(key);
    }

    /** Ends a redraw: the marks it did not ask for, taken out of the pool (the caller removes them). */
    List<H> end() {
        List<H> gone = new ArrayList<>();
        for (Iterator<Map.Entry<String, H>> it = live.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, H> e = it.next();
            if (!wanted.contains(e.getKey())) {
                gone.add(e.getValue());
                it.remove();
            }
        }
        return gone;
    }

    /** Every mark, taken out of the pool (the atlas closes). */
    List<H> clear() {
        List<H> all = new ArrayList<>(live.values());
        live.clear();
        wanted.clear();
        return all;
    }

    int size() {
        return live.size();
    }

    /** True when {@code mark} is one the pool keeps (a chunk that loads again must not lose it to the stray sweep). */
    boolean holds(H mark) {
        return live.containsValue(mark);
    }
}
