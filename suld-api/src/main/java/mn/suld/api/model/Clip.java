package mn.suld.api.model;

import java.util.List;
import java.util.Map;

/**
 * An animation clip (docs/MODEL_RENDERER.md §3 clips.json): per-bone keyframe channels (Euler rotation delta,
 * position delta in blocks, uniform scale), linear interpolation, and named events at ticks.
 */
public record Clip(String name, int length, boolean loop, Map<String, Channels> bones, List<Event> events) {

    public record Event(int tick, String name) {
    }

    /** One bone's keyframes; each key is {@code [tick, values…]}, sorted by tick. */
    public record Channels(List<double[]> rot, List<double[]> pos, List<double[]> scl) {

        public Channels {
            rot = rot == null ? List.of() : List.copyOf(rot);
            pos = pos == null ? List.of() : List.copyOf(pos);
            scl = scl == null ? List.of() : List.copyOf(scl);
        }
    }

    public Clip {
        length = Math.max(1, length);
        bones = bones == null ? Map.of() : Map.copyOf(bones);
        events = events == null ? List.of() : List.copyOf(events);
    }

    /** The clip-local time for a play time {@code t} (ticks since start): looped or held at the end. */
    public double local(double t) {
        if (loop) {
            double m = t % length;
            return m < 0 ? m + length : m;
        }
        return Math.max(0, Math.min(length, t));
    }

    public boolean finished(double t) {
        return !loop && t >= length;
    }

    /** Events crossed when play time moves from {@code from} (exclusive) to {@code to} (inclusive). */
    public List<Event> eventsBetween(double from, double to) {
        if (events.isEmpty() || to <= from) return List.of();
        List<Event> out = new java.util.ArrayList<>();
        if (!loop) {
            for (Event e : events) if (e.tick() > from && e.tick() <= to) out.add(e);
            return out;
        }
        for (double base = Math.floor(from / length) * length; base <= to; base += length) {
            for (Event e : events) {
                double at = base + e.tick();
                if (at > from && at <= to) out.add(e);
            }
        }
        return out;
    }

    /** Sample a channel at clip-local time {@code t}: {@code width} values, or {@code dflt} when there are no keys. */
    static double[] sample(List<double[]> keys, double t, int width, double[] dflt) {
        if (keys.isEmpty()) return dflt;
        if (t <= keys.get(0)[0]) return slice(keys.get(0), width);
        double[] last = keys.get(keys.size() - 1);
        if (t >= last[0]) return slice(last, width);
        for (int i = 1; i < keys.size(); i++) {
            double[] b = keys.get(i);
            if (t <= b[0]) {
                double[] a = keys.get(i - 1);
                double f = b[0] == a[0] ? 1 : (t - a[0]) / (b[0] - a[0]);
                double[] out = new double[width];
                for (int k = 0; k < width; k++) out[k] = a[k + 1] + (b[k + 1] - a[k + 1]) * f;
                return out;
            }
        }
        return slice(last, width);
    }

    private static double[] slice(double[] key, int width) {
        double[] out = new double[width];
        System.arraycopy(key, 1, out, 0, width);
        return out;
    }
}
