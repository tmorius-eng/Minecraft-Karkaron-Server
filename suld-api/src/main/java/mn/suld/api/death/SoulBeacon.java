package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;

import java.util.Objects;
import java.util.UUID;

/**
 * «Сүнсний Гэрэлт»: a beam where a player fell (docs/DEATH_AND_RECOVERY.md). For {@code death.beacon.minutes} the
 * soul may walk back to it; reaching it gives back {@code death.beacon.refund} of the EXP the death took, once. It
 * does not end the soul state (that is «Тэнгэрийн сорил» or the ovoo ritual). Persisted ({@code suld_soul_beacons}),
 * so a restart or a reconnect keeps it; the claim is a compare-and-set in storage, so it pays once whatever happens.
 * Pure value type: every change returns a new one.
 *
 * @param deathId   the death it belongs to (also its storage key)
 * @param expLost   the EXP the death took (the refund is a share of it)
 * @param claimedAt null until the soul reached it
 */
public record SoulBeacon(UUID deathId, UUID player, String world, int x, int y, int z, long expLost, long createdAt,
                         long expiresAt, Long claimedAt) {

    /** Horizontal reach, in blocks, from the beam's centre. */
    public static final double REACH = 3.0;
    /** Vertical reach, in blocks. */
    public static final double REACH_Y = 4.0;

    public SoulBeacon {
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(player, "player");
        world = world == null ? "" : world;
        if (expLost < 0) throw new IllegalArgumentException("expLost < 0: " + expLost);
        if (expiresAt < createdAt) throw new IllegalArgumentException("expires before it is made");
    }

    /** A new beacon for a death at {@code now}, standing {@code s.beaconMinutes()}. */
    public static SoulBeacon of(UUID deathId, UUID player, String world, int x, int y, int z, long expLost, long now, DeathSettings s) {
        return new SoulBeacon(deathId, player, world, x, y, z, expLost, now, now + Math.round(s.beaconMinutes() * 60_000), null);
    }

    /** The EXP reaching it gives back: {@code floor(expLost × refund)}. */
    public long refund(DeathSettings s) {
        return (long) Math.floor(expLost * s.beaconRefund());
    }

    public boolean claimed() {
        return claimedAt != null;
    }

    /** Still standing and not claimed at {@code now}. */
    public boolean open(long now) {
        return !claimed() && now < expiresAt;
    }

    /** Milliseconds it still stands (0 once gone). */
    public long remaining(long now) {
        return open(now) ? expiresAt - now : 0;
    }

    /** True when a player at ({@code px}, {@code py}, {@code pz}) in {@code inWorld} stands at the beam. */
    public boolean reached(String inWorld, double px, double py, double pz) {
        if (!world.equals(inWorld)) return false;
        return Math.hypot(px - (x + 0.5), pz - (z + 0.5)) <= REACH && Math.abs(py - y) <= REACH_Y;
    }

    /** This beacon claimed at {@code now}; refuses a beacon that is gone or already claimed. */
    public SoulBeacon claim(long now) {
        if (!open(now)) throw new IllegalStateException("beacon " + deathId + " is not open");
        return new SoulBeacon(deathId, player, world, x, y, z, expLost, createdAt, expiresAt, now);
    }
}
