package mn.suld.api.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A creature's skeleton (docs/MODEL_RENDERER.md §3 rig.json): bones in parent-before-child order, each with a rest
 * pivot and rotation; bones with {@code model} get a display showing {@code suld:entity/<rig>/<bone>}.
 *
 * @param scale      whole-rig scale
 * @param hostHeight height of the host's hitbox (for name tags and effects)
 */
public record Rig(String id, double scale, double hostHeight, List<Bone> bones, List<Part> parts) {

    /** @param parent index of the parent bone, −1 for a root */
    public record Bone(String id, int parent, Vec3 pivot, Vec3 rest, boolean model, double scale) {
    }

    /** An extra hitbox following a bone. */
    public record Part(String bone, double width, double height) {
    }

    public Rig {
        bones = List.copyOf(bones);
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public int index(String bone) {
        for (int i = 0; i < bones.size(); i++) if (bones.get(i).id().equals(bone)) return i;
        return -1;
    }

    public Map<String, Integer> indices() {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < bones.size(); i++) m.put(bones.get(i).id(), i);
        return m;
    }

    public long displayCount() {
        return bones.stream().filter(Bone::model).count();
    }
}
