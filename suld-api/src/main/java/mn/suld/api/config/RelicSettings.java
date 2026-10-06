package mn.suld.api.config;

/**
 * World-unique relic settings (Vertical Slice 4).
 *
 * @param enabled              master switch
 * @param autoPlaceMinRadius   shrines without a location are placed automatically on the surface,
 * @param autoPlaceMaxRadius   at a random distance in [min, max] blocks from the world spawn
 * @param offlineReturnHours   a bearer offline longer than this loses the relic back to its shrine
 * @param ritualSeconds        how long a claim ritual at the shrine takes (interrupted by damage/moving)
 * @param hintCooldownSeconds  cooldown of /relic hint
 */
public record RelicSettings(boolean enabled, int autoPlaceMinRadius, int autoPlaceMaxRadius,
                            int offlineReturnHours, int ritualSeconds, int hintCooldownSeconds) {

    public RelicSettings {
        autoPlaceMinRadius = Math.max(64, autoPlaceMinRadius);
        autoPlaceMaxRadius = Math.max(autoPlaceMinRadius + 32, autoPlaceMaxRadius);
        offlineReturnHours = Math.max(1, offlineReturnHours);
        ritualSeconds = Math.max(3, Math.min(60, ritualSeconds));
        hintCooldownSeconds = Math.max(0, hintCooldownSeconds);
    }

    public static RelicSettings defaults() {
        return new RelicSettings(true, 600, 1200, 72, 10, 300);
    }
}
