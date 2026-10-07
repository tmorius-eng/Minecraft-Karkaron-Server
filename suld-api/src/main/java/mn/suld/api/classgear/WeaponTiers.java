package mn.suld.api.classgear;

/**
 * Class weapon tiers T1–T6: the weapon follows the player level and steps up at the same levels as the armour tiers
 * (1 / 12 / 24 / 36 / 48 / 60), so weapon and armour have the same progression depth. Shared by the plugin
 * (ClassWeapons) and the simulator.
 */
public final class WeaponTiers {

    public static final int MAX_TIER = 6;
    /** Player level at which tier t (1-based) is reached; index 0 unused. */
    public static final int[] LEVEL = {0, 1, 12, 24, 36, 48, 60};

    private WeaponTiers() {
    }

    public static int tierFor(int level) {
        int t = 1;
        for (int i = 1; i < LEVEL.length; i++) if (level >= LEVEL[i]) t = i;
        return t;
    }
}
