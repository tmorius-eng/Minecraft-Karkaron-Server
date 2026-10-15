package mn.suld.plugin.map;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corner minimap stays on the screen while the player moves, turns and fights, and is not redrawn or re-sent
 * more than it needs (docs/WORLD_MAP.md). The client draws it only while one of its frames is in view; the server
 * places the frames for the look it knows, a round trip behind the client's camera.
 */
class MinimapStabilityTest {

    static final double ASPECT = 16.0 / 9;

    /** Minecraft's look vector for (yaw, pitch) in degrees. */
    static double[] dir(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[]{-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** True when some frame of {@code slots}, placed for look (yaw, pitch), is in a camera at (yaw + dYaw, pitch + dPitch). */
    static boolean drawn(double[][] slots, double yaw, double pitch, double dYaw, double dPitch, double fovDeg) {
        double cy = yaw + dYaw, cp = Math.max(-90, Math.min(90, pitch + dPitch));
        double[] f = dir(cy, cp), r = dir(cy + 90, 0), u = dir(cy, cp - 90);
        double tv = Math.tan(Math.toRadians(fovDeg / 2)), th = tv * ASPECT;
        for (double[] s : slots) {
            double[] d = dir(yaw + s[0], Math.max(-90, Math.min(90, pitch + s[1])));
            double z = dot(d, f);
            if (z > 0 && Math.abs(dot(d, r) / z) <= th && Math.abs(dot(d, u) / z) <= tv) return true;
        }
        return false;
    }

    /** Lags the frames must survive: fast turns either way, and quick looks down (a mob at the feet) and up. */
    static boolean survives(double[][] slots, double fov, double maxDown, double maxUp) {
        for (double pitch = -30; pitch <= 30; pitch += 5) {
            for (double dYaw = -90; dYaw <= 90; dYaw += 5) {
                for (double dPitch = -10; dPitch <= 10; dPitch += 5) if (!drawn(slots, 0, pitch, dYaw, dPitch, fov)) return false;
            }
            for (double dPitch = -maxUp; dPitch <= maxDown; dPitch += 2.5) {
                for (double dYaw = -20; dYaw <= 20; dYaw += 5) if (!drawn(slots, 0, pitch, dYaw, dPitch, fov)) return false;
            }
        }
        return true;
    }

    @Test
    void theOldLayoutBlinkedOnAQuickLookDown() {
        double[][] old = {{0, -14}, {-50, -10}, {50, -10}};
        assertTrue(drawn(old, 0, 0, 0, 20, 70), "a 20° look down still showed it");
        assertFalse(drawn(old, 0, 0, 0, 30, 70), "a 30° look down within the lag took every frame out of view");
    }

    @Test
    void theFramesKeepTheMapDrawnThroughTurnsAndLooksUpAndDown() {
        assertTrue(survives(MinimapService.SLOTS, 70, 45, 40), "default field of view");
        assertTrue(survives(MinimapService.SLOTS, 70 * 0.85, 40, 30), "a drawn bow narrows the view by 15%");
    }

    @Test
    void noFrameIsOnTheLineOfFireOrWithinReach() {
        for (double[] s : MinimapService.SLOTS) {
            // the angle between the look and the frame: none near the look, where shots and spells go
            double off = Math.toDegrees(Math.acos(dot(dir(s[0], s[1]), dir(0, 0))));
            assertTrue(off >= 10, "slot " + s[0] + "/" + s[1] + " is " + off + "° off the look");
        }
        assertTrue(MinimapService.DIST > 5.5, "an entity farther than 5 blocks is never clicked");
        // the middle frame, snapped to its block and left up to the slack behind its spot, still stands over the shot
        double over = MinimapService.DIST * Math.sin(Math.toRadians(14)) - (1 + MinimapService.MOVE_SLACK);
        assertTrue(over > 0.4, "the middle frame can sink to " + over + " over the look");
    }

    @Test
    void aFrameDoesNotHopBetweenTwoBlocksOnASmallTurn() {
        // hanging in block (10, 64, -3): its spot wobbling across the block's edge leaves it where it is
        assertFalse(MinimapService.frameMoves(10, 64, -3, 10.5, 64.5, -2.5), "spot in its block");
        assertFalse(MinimapService.frameMoves(10, 64, -3, 11.1, 64.5, -2.5), "0.1 over the east edge");
        assertFalse(MinimapService.frameMoves(10, 64, -3, 9.85, 63.9, -3.2), "0.15 over the west, bottom and north edges");
        assertTrue(MinimapService.frameMoves(10, 64, -3, 11.4, 64.5, -2.5), "0.4 over: it follows");
        assertTrue(MinimapService.frameMoves(10, 64, -3, 10.5, 65.5, -2.5), "a block up: it follows");
    }

    @Test
    void aStepOrATurnRedrawsAtAPaceAndANewMarkAtOnce() {
        int r = MinimapService.REDRAW_TICKS;
        assertTrue(MinimapService.redrawDue(0, false, true, 0), "a new waypoint or pin shows at once");
        assertFalse(MinimapService.redrawDue(MinimapService.MOVE_BLOCKS, false, false, r - 1), "running: not every tick");
        assertTrue(MinimapService.redrawDue(MinimapService.MOVE_BLOCKS, false, false, r), "running: every few ticks");
        assertTrue(MinimapService.redrawDue(0, true, false, r), "a turn");
        assertFalse(MinimapService.redrawDue(1, false, false, r), "one block: not yet");
        assertTrue(MinimapService.redrawDue(1, false, false, MinimapService.SETTLE_TICKS), "one block, then a pause: centred again");
        assertFalse(MinimapService.redrawDue(0, false, false, MinimapService.STALE_TICKS - 1), "standing still: nothing");
        assertTrue(MinimapService.redrawDue(0, false, false, MinimapService.STALE_TICKS), "a refresh now and then");
    }

    @Test
    void sprintingRedrawsAtMostAFewTimesASecond() {
        // 5.6 blocks a second straight on, one tick at a time
        int draws = 0, last = -1000, lastX = 0;
        for (int tick = 0; tick < 200; tick++) {
            int x = (int) Math.floor(tick * 5.6 / 20);
            if (MinimapService.redrawDue(Math.abs(x - lastX), false, false, tick - last)) {
                draws++;
                last = tick;
                lastX = x;
            }
        }
        assertTrue(draws <= 30, draws + " pictures in 10 s of sprinting (a block a step at 4 ticks was ~50)");
        assertTrue(draws >= 20, draws + ": it still keeps up");
    }

    @Test
    void thePlacesAreCollectedOnceAWhileNotOnEveryPicture() {
        AtomicLong clock = new AtomicLong(1_000);
        AtomicInteger reads = new AtomicInteger();
        Supplier<Integer> places = MapPois.cached(reads::incrementAndGet, MapPois.CACHE_MS, clock::get);
        for (int i = 0; i < 50; i++) places.get();
        assertEquals(1, reads.get(), "50 minimap pictures in a row read the world's places once");
        clock.addAndGet(MapPois.CACHE_MS - 1);
        places.get();
        assertEquals(1, reads.get());
        clock.addAndGet(1);
        assertEquals(2, places.get(), "read again after " + MapPois.CACHE_MS + " ms");
    }
}
