package mn.suld.api.model;

/**
 * A rigid transform with uniform scale: {@code p ↦ translation + rotation · (scale · p)}. Composition is exact for
 * uniform scales, which is all a rig uses.
 */
public record Xf(Vec3 translation, Quat rotation, double scale) {

    public static final Xf IDENTITY = new Xf(Vec3.ZERO, Quat.IDENTITY, 1);

    /** {@code this ∘ child}: the child's transform expressed in this one's parent space. */
    public Xf then(Xf child) {
        return new Xf(translation.plus(rotation.rotate(child.translation.times(scale))), rotation.times(child.rotation).normalized(),
                scale * child.scale);
    }

    public Vec3 apply(Vec3 p) {
        return translation.plus(rotation.rotate(p.times(scale)));
    }
}
