package mn.suld.api.relic;

/** Ownership history events (stored in {@code suld_world_unique_history.event}). */
public enum RelicEvent {
    CREATED,
    SHRINE_SET,
    DISCOVERED,      // claimed at the shrine
    SEIZED,          // taken by the player who killed the bearer
    RETURNED,        // bearer died to a non-player / could not pass it on
    EXPIRED,         // bearer offline too long
    ADMIN_GRANTED,
    ADMIN_RETURNED,
    RECOVERED        // admin re-issued the physical copy (generation bumped)
}
