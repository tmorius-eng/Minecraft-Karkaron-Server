package mn.suld.plugin.worldbuild;

import mn.suld.api.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent state of one city-slice build, saved atomically as JSON in the plugin folder
 * ({@code worldbuild/<slice>.json}): where it stands, what was compiled, how far placement got
 * (resume cursor), review status, and the metrics of the run.
 */
final class BuildState {

    enum Status { PLANNED, BUILDING, PAUSED, BUILT, APPROVED, LOCKED, ROLLED_BACK, FAILED }

    String slice;
    String world;
    int anchorX, anchorY, anchorZ;
    String fingerprint = "";
    Status status = Status.PLANNED;
    int passIndex;          // index into the pass order of the next unit to place
    int chunkIndex;         // index into that pass's chunk list
    long blocksPlaced;
    long blocksCleared;
    long blocksSkipped;
    int chunksTouched;
    long startedAt, finishedAt, buildMillis;
    double worstMspt, mstpSum;
    long msptSamples;
    String note = "";
    int[] originalSpawn;    // world spawn before the build (restored by rollback)

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slice", slice);
        m.put("world", world);
        m.put("anchor", new long[]{anchorX, anchorY, anchorZ});
        m.put("fingerprint", fingerprint);
        m.put("status", status.name());
        m.put("pass_index", passIndex);
        m.put("chunk_index", chunkIndex);
        m.put("blocks_placed", blocksPlaced);
        m.put("blocks_cleared", blocksCleared);
        m.put("blocks_skipped", blocksSkipped);
        m.put("chunks_touched", chunksTouched);
        m.put("started_at", startedAt);
        m.put("finished_at", finishedAt);
        m.put("build_millis", buildMillis);
        m.put("worst_mspt", worstMspt);
        m.put("avg_mspt", msptSamples == 0 ? 0 : mstpSum / msptSamples);
        m.put("note", note);
        if (originalSpawn != null) m.put("original_spawn", new long[]{originalSpawn[0], originalSpawn[1], originalSpawn[2]});
        return m;
    }

    static BuildState load(Path file) throws IOException {
        Map<String, Object> m = Json.object(Json.parse(Files.readString(file)));
        BuildState s = new BuildState();
        s.slice = (String) m.get("slice");
        s.world = (String) m.get("world");
        var a = Json.array(m.get("anchor"));
        s.anchorX = Json.integer(a.get(0));
        s.anchorY = Json.integer(a.get(1));
        s.anchorZ = Json.integer(a.get(2));
        s.fingerprint = (String) m.getOrDefault("fingerprint", "");
        s.status = Status.valueOf((String) m.get("status"));
        s.passIndex = Json.integer(m.get("pass_index"));
        s.chunkIndex = Json.integer(m.get("chunk_index"));
        s.blocksPlaced = ((Number) m.getOrDefault("blocks_placed", 0L)).longValue();
        s.blocksCleared = ((Number) m.getOrDefault("blocks_cleared", 0L)).longValue();
        s.blocksSkipped = ((Number) m.getOrDefault("blocks_skipped", 0L)).longValue();
        s.chunksTouched = Json.integer(m.getOrDefault("chunks_touched", 0L));
        s.startedAt = ((Number) m.getOrDefault("started_at", 0L)).longValue();
        s.finishedAt = ((Number) m.getOrDefault("finished_at", 0L)).longValue();
        s.buildMillis = ((Number) m.getOrDefault("build_millis", 0L)).longValue();
        s.worstMspt = ((Number) m.getOrDefault("worst_mspt", 0L)).doubleValue();
        s.note = (String) m.getOrDefault("note", "");
        if (m.get("original_spawn") != null) {
            var o = Json.array(m.get("original_spawn"));
            s.originalSpawn = new int[]{Json.integer(o.get(0)), Json.integer(o.get(1)), Json.integer(o.get(2))};
        }
        return s;
    }

    void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, toJson(toMap()), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static String toJson(Object o) {
        if (o == null) return "null";
        if (o instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        if (o instanceof long[] a) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) b.append(i == 0 ? "" : ", ").append(a[i]);
            return b.append(']').toString();
        }
        if (o instanceof Map<?, ?> m) {
            StringBuilder b = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                b.append(i++ == 0 ? "  " : ",\n  ").append(toJson(String.valueOf(e.getKey()))).append(": ").append(toJson(e.getValue()));
            }
            return b.append("\n}\n").toString();
        }
        return toJson(String.valueOf(o));
    }
}
