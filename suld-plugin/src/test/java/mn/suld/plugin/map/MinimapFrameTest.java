package mn.suld.plugin.map;

import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.GlowItemFrame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The minimap's frames are never seen as frames, never dropped and never left behind (docs/WORLD_MAP.md): how a frame
 * is made before it is first sent, who never gets one, and which frames are orphans to remove.
 */
class MinimapFrameTest {

    /** A frame that only records what was set on it, in order. */
    private static GlowItemFrame recorder(Map<String, List<Object>> calls, List<String> order) {
        return (GlowItemFrame) Proxy.newProxyInstance(GlowItemFrame.class.getClassLoader(), new Class<?>[]{GlowItemFrame.class}, (proxy, m, args) -> {
            order.add(m.getName());
            calls.put(m.getName(), args == null ? List.of() : java.util.Arrays.asList(args));
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return m.getName().equals("addScoreboardTag");
            if (r == float.class) return 0f;
            if (r == int.class) return 0;
            return null;
        });
    }

    @Test
    void aFrameIsHiddenInvisibleFixedAndNeverSaved() {
        Map<String, List<Object>> calls = new HashMap<>();
        List<String> order = new ArrayList<>();
        MinimapService.dress(recorder(calls, order), null);
        // hidden from every player: only its owner's showEntity sends it (the spawn packet goes to no one else)
        assertEquals(List.of(false), calls.get("setVisibleByDefault"));
        // the frame itself is not drawn, only the map in it (which the pack's shader moves to the corner)
        assertEquals(List.of(false), calls.get("setVisible"));
        // never written to the chunk: a crash or a restart leaves nothing behind
        assertEquals(List.of(false), calls.get("setPersistent"));
        // no drop, no break, no turning by a click; no damage, no sound
        assertEquals(List.of(true), calls.get("setFixed"));
        assertEquals(List.of(0f), calls.get("setItemDropChance"));
        assertEquals(List.of(true), calls.get("setInvulnerable"));
        assertEquals(List.of(true), calls.get("setSilent"));
        assertEquals(List.of(MinimapService.TAG), calls.get("addScoreboardTag"));
        assertEquals(List.of(BlockFace.UP, true), calls.get("setFacingDirection"));
        assertEquals(List.of(Rotation.NONE), calls.get("setRotation"));
        // the map goes in without the frame's place/drop events, and only once the frame is already hidden
        assertEquals(false, calls.get("setItem").get(1));
        assertTrue(order.indexOf("setVisibleByDefault") < order.indexOf("setItem"), "hidden before the map goes in");
    }

    @Test
    void bedrockPlayersNeverGetFrames() {
        // Floodgate: new UUID(0, xuid). Geyser answers the pack request but cannot run the Java core shader, so the
        // frames would hang in the air in front of every Bedrock player
        assertTrue(MinimapService.bedrock(new UUID(0, 2535428123456789L)));
        assertFalse(MinimapService.bedrock(UUID.fromString("fabfdda1-1796-38d7-9bd3-da21ec70c435"))); // offline (v3), the playtest's
        assertFalse(MinimapService.bedrock(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"))); // online (v4)
    }

    @Test
    void onlyUnownedMinimapFramesAreOrphans() {
        assertTrue(MinimapService.orphan(Set.of(MinimapService.TAG), false));
        assertFalse(MinimapService.orphan(Set.of(MinimapService.TAG), true));
        // another plugin's glow frame (or the atlas sky's) is never touched
        assertFalse(MinimapService.orphan(Set.of("suld_atlas"), false));
        assertFalse(MinimapService.orphan(Set.of(), false));
    }
}
