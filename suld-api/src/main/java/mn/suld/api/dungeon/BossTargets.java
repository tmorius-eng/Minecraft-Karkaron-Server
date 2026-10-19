package mn.suld.api.dungeon;

/**
 * Whom a boss may aim an ability at (BOSS-01, audit 2026-10-19). A spectator or a creative builder is not in the
 * fight, the dead are gone and a soul walks its ritual: none of them steers the boss's target choice, takes its
 * knockback, slow or blindness, or counts as "someone is near". The damage guard in the brains stays as a second line.
 */
public final class BossTargets {

    private BossTargets() {
    }

    /**
     * @param survivalOrAdventure the player's game mode is SURVIVAL or ADVENTURE (never SPECTATOR or CREATIVE)
     * @param dead                the player entity is dead
     * @param soul                the player is a soul (DeathService)
     */
    public static boolean eligible(boolean survivalOrAdventure, boolean dead, boolean soul) {
        return survivalOrAdventure && !dead && !soul;
    }
}
