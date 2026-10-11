package mn.suld.plugin.content;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.mob.MobDefinition;

import java.util.List;

/**
 * The dungeon ladder, read from {@code content/dungeons.json} (docs/CONTENT_DATA.md): each a wave arena behind a
 * gate ending in a phased boss, in ladder order (each needs the one before it cleared).
 */
public final class DungeonContent {

    private DungeonContent() {
    }

    /** Completion bonus paid to every participant who was not downed. */
    public record Completion(long exp, long coins) {
    }

    // Говийн Булш (Gobi), Баавгайн Үүр (Khangai), Мөсөн Оргил (Altai)
    public static final MobDefinition SAND_KHAN = Content.mob("mob.elsnii_khaan");
    public static final DungeonDefinition GOBI_TOMB = Content.pack().dungeon("dungeon.govi_bulsh");
    public static final MobDefinition FOREST_LORD = Content.mob("mob.oin_ezen");
    public static final DungeonDefinition BEAR_LAIR = Content.pack().dungeon("dungeon.baavgain_uur");
    public static final MobDefinition ICE_KHAN = Content.mob("mob.mosun_khaan");
    public static final DungeonDefinition ICE_PEAK = Content.pack().dungeon("dungeon.mosun_orgil");

    /** Where each dungeon's gate stands (bearing from the spawn, distance) and how its hall looks. */
    public static final List<mn.suld.api.dungeon.hall.DungeonSite> SITES = Content.pack().sites();

    /** Every dungeon in ladder order (Khasar's Den first). */
    public static final List<DungeonDefinition> ALL = Content.pack().dungeons();

    /** The boss mobs (role "boss"). */
    public static final List<MobDefinition> BOSSES = Content.pack().mobsOf("boss");

    /** The dungeon before {@code id} on the ladder (it must be cleared once first), or null for the first. */
    public static DungeonDefinition previous(String id) {
        for (int i = 1; i < ALL.size(); i++) if (ALL.get(i).id().equals(id)) return ALL.get(i - 1);
        return null;
    }

    public static Completion completion(String dungeonId) {
        Completion c = Content.pack().completions().get(dungeonId);
        return c != null ? c : Content.pack().completions().get(ALL.get(0).id());
    }

    /** The wild region a dungeon belongs to (where its quest tracker points). */
    public static String regionOf(String dungeonId) {
        return Content.pack().dungeonRegions().getOrDefault(dungeonId, WorldContent.KHERLEN.id());
    }

    /** Where to run it (shown in the /dungeon window). */
    public static String where(String dungeonId) {
        return Content.pack().dungeonWhere().getOrDefault(dungeonId, "Хэрлэнгийн тал");
    }
}
