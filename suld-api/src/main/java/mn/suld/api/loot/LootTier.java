package mn.suld.api.loot;

import mn.suld.api.mob.MobTier;

import java.util.Locale;
import java.util.Optional;

/**
 * Where loot comes from. Each tier has a rarity band in the catalog ({@code items/tiers.json}): which rarities a
 * generated item of that source can have and how likely each is. Nothing about the bands is hard-coded here.
 */
public enum LootTier {
    NORMAL, ELITE, CHAMPION, MYTHIC, BOSS, WORLD_EVENT, DUNGEON, QUEST, CHEST, CRAFT;

    /** The loot tier of a mob of this power tier. */
    public static LootTier of(MobTier t) {
        return switch (t) {
            case NORMAL -> NORMAL;
            case ELITE -> ELITE;
            case CHAMPION -> CHAMPION;
            case MYTHIC -> MYTHIC;
            case BOSS -> BOSS;
            case WORLD_BOSS -> WORLD_EVENT;
        };
    }

    public static Optional<LootTier> byName(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(valueOf(s.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
