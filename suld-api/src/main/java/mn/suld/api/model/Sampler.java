package mn.suld.api.model;

import java.util.List;

/**
 * Poses a rig (docs/MODEL_RENDERER.md §2): a base clip (locomotion) blended with an optional action clip, composed
 * down the hierarchy into model space, then placed in the world frame of the host (body yaw, rig scale) as the
 * transformations the bone displays need. Pure and allocation-light; the plugin calls it a few times a second per rig.
 */
public final class Sampler {

    private static final double[] ZERO3 = {0, 0, 0};
    private static final double[] ONE1 = {1};

    private Sampler() {
    }

    /** A clip being played: its play time in ticks and the weight it is blended in with (0..1). */
    public record Layer(Clip clip, double time, double weight) {
    }

    /** Model-space transform of every bone (index = rig bone index). {@code action} may be null. */
    public static Xf[] pose(Rig rig, Layer base, Layer action) {
        List<Rig.Bone> bones = rig.bones();
        Xf[] model = new Xf[bones.size()];
        double w = action == null ? 0 : Math.max(0, Math.min(1, action.weight()));
        for (int i = 0; i < bones.size(); i++) {
            Rig.Bone b = bones.get(i);
            double[] rot = channel(base, b.id(), 0), pos = channel(base, b.id(), 1), scl = channel(base, b.id(), 2);
            if (w > 0) {
                rot = lerp(rot, channel(action, b.id(), 0), w);
                pos = lerp(pos, channel(action, b.id(), 1), w);
                scl = lerp(scl, channel(action, b.id(), 2), w);
            }
            Vec3 parentPivot = b.parent() < 0 ? Vec3.ZERO : bones.get(b.parent()).pivot();
            Xf local = new Xf(b.pivot().minus(parentPivot).plus(new Vec3(pos[0], pos[1], pos[2])),
                    Quat.euler(b.rest().x() + rot[0], b.rest().y() + rot[1], b.rest().z() + rot[2]), scl[0]);
            model[i] = b.parent() < 0 ? local : model[b.parent()].then(local);
        }
        return model;
    }

    /**
     * The display transformation of every bone: the pose turned to the host's body yaw (Minecraft degrees, 0 faces +Z)
     * and scaled by the rig scale, with each bone's own display scale. Bones without a model are included (unused).
     */
    public static Xf[] display(Rig rig, Xf[] model, double yawDegrees) {
        Xf world = new Xf(Vec3.ZERO, Quat.axisAngle(0, 1, 0, -yawDegrees), rig.scale());
        Xf[] out = new Xf[model.length];
        for (int i = 0; i < model.length; i++) {
            Xf m = world.then(model[i]);
            out[i] = new Xf(m.translation(), m.rotation(), m.scale() * rig.bones().get(i).scale());
        }
        return out;
    }

    private static double[] channel(Layer layer, String bone, int which) {
        double[] dflt = which == 2 ? ONE1 : ZERO3;
        if (layer == null || layer.clip() == null) return dflt;
        Clip.Channels c = layer.clip().bones().get(bone);
        if (c == null) return dflt;
        double t = layer.clip().local(layer.time());
        return switch (which) {
            case 0 -> Clip.sample(c.rot(), t, 3, dflt);
            case 1 -> Clip.sample(c.pos(), t, 3, dflt);
            default -> Clip.sample(c.scl(), t, 1, dflt);
        };
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] + (b[i] - a[i]) * t;
        return out;
    }
}
