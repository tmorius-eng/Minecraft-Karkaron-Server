package mn.suld.api.worldbuild;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which chunks the pre-generator makes, and in what order (docs/world/PREGENERATION.md). A plan is a list of jobs in
 * priority order (P0 spawn and Kharkhorum first, P5 the open wilderness last); a job is a square of chunks around one
 * or more centres (one centre for a place, a chain of centres for a travel corridor). Inside a square the chunks are
 * walked in an outward spiral, so the middle of every place exists first.
 * <p>
 * A chunk already covered by an earlier job (or an earlier centre of the same job) is skipped without touching the
 * world, so overlapping jobs never generate a chunk twice; chunks outside the border are skipped too. The walk is a
 * plain cursor (job, centre, spiral step), so progress is two numbers that survive restarts. Pure; no Bukkit.
 */
public final class PregenPlan {

    public enum Priority { P0, P1, P2, P3, P4, P5 }

    /** A square of {@code radius} chunks (so (2r+1)^2 chunks) around each centre, in chunk coordinates. */
    public record Job(String id, Priority priority, int radius, List<int[]> centers) {
        public Job {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(priority, "priority");
            if (radius < 0 || radius > 4096) throw new IllegalArgumentException("radius out of range: " + radius);
            if (centers == null || centers.isEmpty()) throw new IllegalArgumentException("job " + id + " has no centre");
            centers = List.copyOf(centers);
        }

        public long perCenter() {
            long side = 2L * radius + 1;
            return side * side;
        }

        public static Job square(String id, Priority p, int cx, int cz, int radius) {
            return new Job(id, p, radius, List.of(new int[]{cx, cz}));
        }

        /**
         * A corridor from chunk (ax, az) to (bx, bz), {@code halfWidth} chunks either side of the line: centres every
         * {@code halfWidth + 1} chunks along it, so neighbouring squares overlap by one row (no gaps, little re-check).
         */
        public static Job corridor(String id, Priority p, int ax, int az, int bx, int bz, int halfWidth) {
            double len = Math.hypot(bx - ax, bz - az);
            int stride = Math.max(1, halfWidth + 1);
            int n = Math.max(1, (int) Math.ceil(len / stride));
            List<int[]> c = new ArrayList<>(n + 1);
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n;
                c.add(new int[]{(int) Math.round(ax + (bx - ax) * t), (int) Math.round(az + (bz - az) * t)});
            }
            return new Job(id, p, halfWidth, c);
        }
    }

    /** Where the walk stands: job index, centre index within the job, spiral step within that centre's square. */
    public record Cursor(int job, int center, long step) {
        public static final Cursor START = new Cursor(0, 0, 0);
    }

    /** One result of {@link #next}: a chunk to generate (when {@code chunk} is non-null) and the cursor after it. */
    public record Step(int[] chunk, Cursor after, int skipped) {
        public boolean done() {
            return chunk == null && after == null;
        }
    }

    /** Which chunks may be generated at all (the border); chunk coordinates. */
    @FunctionalInterface
    public interface ChunkFilter {
        boolean wanted(int cx, int cz);
    }

    private final List<Job> jobs;
    private final ChunkFilter filter;

    public PregenPlan(List<Job> jobs, ChunkFilter filter) {
        List<Job> sorted = new ArrayList<>(jobs);
        sorted.sort(Comparator.comparing(Job::priority)); // stable: same-priority jobs keep their order
        this.jobs = List.copyOf(sorted);
        this.filter = filter == null ? (x, z) -> true : filter;
    }

    public List<Job> jobs() {
        return jobs;
    }

    /** Upper bound of chunks in the plan (overlaps counted twice); cheap. */
    public long upperBound() {
        long n = 0;
        for (Job j : jobs) n += j.perCenter() * j.centers().size();
        return n;
    }

    /** Exact number of distinct wanted chunks. Walks the whole plan: call it off the main thread for big plans. */
    public long countUnique() {
        Set<Long> seen = new HashSet<>();
        for (Job j : jobs) {
            for (int[] c : j.centers()) {
                for (int dx = -j.radius(); dx <= j.radius(); dx++) {
                    for (int dz = -j.radius(); dz <= j.radius(); dz++) {
                        int x = c[0] + dx, z = c[1] + dz;
                        if (filter.wanted(x, z)) seen.add(key(x, z));
                    }
                }
            }
        }
        return seen.size();
    }

    /** A stable fingerprint of the plan (jobs, radii, centres): saved progress only resumes onto the same plan. */
    public String fingerprint() {
        long h = 1125899906842597L;
        for (Job j : jobs) {
            h = 31 * h + j.id().hashCode();
            h = 31 * h + j.priority().ordinal();
            h = 31 * h + j.radius();
            for (int[] c : j.centers()) h = 31 * (31 * h + c[0]) + c[1];
        }
        return Long.toHexString(h);
    }

    /**
     * The next chunk after {@code at}, skipping (at most {@code maxSkips}) chunks that an earlier job or centre covers
     * or the filter rejects. Returns a step with no chunk but a cursor when the skip budget ran out (call again next
     * tick), and {@link Step#done()} when the plan is finished.
     */
    public Step next(Cursor at, int maxSkips) {
        int job = at.job(), center = at.center();
        long step = at.step();
        int skipped = 0;
        while (job < jobs.size()) {
            Job j = jobs.get(job);
            if (center >= j.centers().size()) {
                job++;
                center = 0;
                step = 0;
                continue;
            }
            if (step >= j.perCenter()) {
                center++;
                step = 0;
                continue;
            }
            int[] c = j.centers().get(center);
            int[] off = spiral(step);
            int x = c[0] + off[0], z = c[1] + off[1];
            step++;
            if (filter.wanted(x, z) && !coveredEarlier(job, center, x, z)) {
                return new Step(new int[]{x, z}, new Cursor(job, center, step), skipped);
            }
            if (++skipped >= maxSkips) return new Step(null, new Cursor(job, center, step), skipped);
        }
        return new Step(null, null, skipped);
    }

    /** True when chunk (x, z) belongs to a job before {@code job}, or to an earlier centre of the same job. */
    boolean coveredEarlier(int job, int center, int x, int z) {
        for (int i = 0; i <= job; i++) {
            Job j = jobs.get(i);
            int r = j.radius();
            int last = i == job ? center : j.centers().size();
            for (int k = 0; k < last; k++) {
                int[] c = j.centers().get(k);
                if (Math.abs(x - c[0]) <= r && Math.abs(z - c[1]) <= r) return true;
            }
        }
        return false;
    }

    /** Chunks finished before the cursor's job (upper bound, for progress per job). */
    public long doneBefore(Cursor c) {
        long n = 0;
        for (int i = 0; i < Math.min(c.job(), jobs.size()); i++) n += jobs.get(i).perCenter() * jobs.get(i).centers().size();
        if (c.job() < jobs.size()) n += jobs.get(c.job()).perCenter() * c.center() + c.step();
        return n;
    }

    /**
     * The {@code i}-th cell of an outward square spiral around (0, 0): 0 is the centre, ring k (k >= 1) holds the 8k
     * cells at Chebyshev distance k, so the first (2r+1)^2 cells are exactly the square of radius r.
     */
    public static int[] spiral(long i) {
        if (i < 0) throw new IllegalArgumentException("negative index");
        if (i == 0) return new int[]{0, 0};
        long k = (long) Math.ceil((Math.sqrt(i + 1.0) - 1) / 2);
        // guard floating-point error at ring boundaries
        while ((2 * k - 1) * (2 * k - 1) > i) k--;
        while ((2 * k + 1) * (2 * k + 1) <= i) k++;
        long t = i - (2 * k - 1) * (2 * k - 1);
        long side = t / (2 * k), off = t % (2 * k);
        int kk = (int) k, o = (int) off;
        return switch ((int) side) {
            case 0 -> new int[]{kk, -kk + 1 + o};
            case 1 -> new int[]{kk - 1 - o, kk};
            case 2 -> new int[]{-kk, kk - 1 - o};
            default -> new int[]{-kk + 1 + o, -kk};
        };
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
