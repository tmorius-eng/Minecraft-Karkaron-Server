package mn.suld.api.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerTest {

    static void near(Vec3 a, Vec3 b) {
        assertTrue(a.distance(b) < 1e-9, a + " vs " + b);
    }

    @Test
    void eulerOrderIsXThenYThenZ() {
        Quat q = Quat.euler(90, 90, 0); // X first: +Y → +Z; then Y: +Z → +X
        near(q.rotate(new Vec3(0, 1, 0)), new Vec3(1, 0, 0));
        near(Quat.euler(0, 90, 0).rotate(new Vec3(0, 0, 1)), new Vec3(1, 0, 0));
        near(Quat.euler(0, 0, 90).rotate(new Vec3(1, 0, 0)), new Vec3(0, 1, 0));
        assertEquals(0, Quat.IDENTITY.angleTo(Quat.euler(0, 0, 0)), 1e-9);
        assertEquals(90, Quat.IDENTITY.angleTo(Quat.euler(0, 90, 0)), 1e-6);
    }

    @Test
    void transformsComposeExactly() {
        Xf parent = new Xf(new Vec3(1, 0, 0), Quat.euler(0, 90, 0), 2);
        Xf child = new Xf(new Vec3(0, 0, 1), Quat.euler(0, 90, 0), 0.5);
        Vec3 p = new Vec3(0, 0, 1);
        near(parent.then(child).apply(p), parent.apply(child.apply(p)));
    }

    static final Rig ARM = new Rig("arm", 1, 1, List.of(
            new Rig.Bone("root", -1, Vec3.ZERO, Vec3.ZERO, false, 1),
            new Rig.Bone("upper", 0, new Vec3(0, 1, 0), Vec3.ZERO, true, 1),
            new Rig.Bone("lower", 1, new Vec3(0, 1, 1), Vec3.ZERO, true, 2)), List.of());

    @Test
    void hierarchyRotatesChildrenAboutTheParentPivot() {
        Clip swing = new Clip("swing", 20, true, Map.of("upper", new Clip.Channels(
                List.of(new double[] {0, 0, 0, 0}, new double[] {10, 0, 90, 0}), null, null)), List.of());
        Xf[] rest = Sampler.pose(ARM, new Sampler.Layer(swing, 0, 1), null);
        near(rest[2].translation(), new Vec3(0, 1, 1));
        Xf[] turned = Sampler.pose(ARM, new Sampler.Layer(swing, 10, 1), null);
        near(turned[1].translation(), new Vec3(0, 1, 0)); // a bone turns about its own pivot
        near(turned[2].translation(), new Vec3(1, 1, 0)); // the child swings round it: +Z → +X
        Xf[] half = Sampler.pose(ARM, new Sampler.Layer(swing, 5, 1), null);
        assertEquals(45, Quat.IDENTITY.angleTo(half[2].rotation()), 1e-6, "linear keys");
        Xf[] looped = Sampler.pose(ARM, new Sampler.Layer(swing, 25, 1), null);
        assertEquals(45, Quat.IDENTITY.angleTo(looped[2].rotation()), 1e-6, "loops");
    }

    @Test
    void displayFoldsInYawRigScaleAndBoneScale() {
        Xf[] model = Sampler.pose(ARM, new Sampler.Layer(null, 0, 1), null);
        Xf[] d = Sampler.display(ARM, model, 90); // yaw 90 faces −X in Minecraft
        near(d[2].translation(), new Vec3(-1, 1, 0));
        assertEquals(2, d[2].scale(), 1e-9, "bone display scale");
        assertEquals(1, d[1].scale(), 1e-9);
    }

    @Test
    void actionBlendsOverTheBase() {
        Clip base = new Clip("idle", 10, true, Map.of(), List.of());
        Clip act = new Clip("bite", 10, false, Map.of("upper", new Clip.Channels(List.of(new double[] {0, 0, 90, 0}), null, null)),
                List.of(new Clip.Event(4, "hit")));
        assertEquals(45, Quat.IDENTITY.angleTo(Sampler.pose(ARM, new Sampler.Layer(base, 3, 1), new Sampler.Layer(act, 3, 0.5))[1].rotation()), 1e-6);
        assertEquals(List.of(new Clip.Event(4, "hit")), act.eventsBetween(3, 4));
        assertTrue(act.eventsBetween(4, 9).isEmpty());
        assertTrue(act.finished(10));
        Clip loop = new Clip("l", 10, true, Map.of(), List.of(new Clip.Event(2, "step")));
        assertEquals(3, loop.eventsBetween(0, 25).size(), "a looped event fires every cycle");
        assertFalse(loop.finished(1000));
    }

    @Test
    void loaderValidates() {
        String rig = "{\"id\":\"wolf\",\"scale\":1,\"bones\":[{\"id\":\"root\",\"parent\":null,\"pivot\":[0,0,0]},"
                + "{\"id\":\"head\",\"parent\":\"root\",\"pivot\":[0,1,1],\"rest\":[10,0,0],\"model\":true,\"scale\":1.5}]}";
        String clips = "{\"clips\":{\"idle\":{\"length\":20,\"loop\":true,\"bones\":{\"head\":{\"rot\":[[0,0,0,0],[10,5,0,0]]}}},"
                + "\"walk\":{\"length\":16,\"loop\":true},\"death\":{\"length\":30,\"events\":[[20,\"fall\"]]}}}";
        RigLoader.Result r = RigLoader.load(rig, clips);
        assertTrue(r.ok(), r.issues().toString());
        assertEquals(1, r.model().rig().displayCount());
        assertEquals(1, r.model().clip("death").events().size());
        RigLoader.Result bad = RigLoader.load(rig.replace("\"parent\":\"root\"", "\"parent\":\"tail\""),
                clips.replace("\"walk\"", "\"trot\"").replace("\"head\":{\"rot\"", "\"jaw\":{\"rot\""));
        assertFalse(bad.ok());
        assertTrue(bad.issues().stream().anyMatch(s -> s.contains("parent tail")), bad.issues().toString());
        assertTrue(bad.issues().stream().anyMatch(s -> s.contains("missing clip walk")), bad.issues().toString());
    }
}
