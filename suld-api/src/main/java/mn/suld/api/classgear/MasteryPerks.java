package mn.suld.api.classgear;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.ModKey;
import mn.suld.api.skill.tree.StatKey;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The armour mastery perks at ranks 3 / 6 / 9 (docs/ARMOR_PROGRESSION.md "Armour and the class"). They are build
 * modifiers of the existing skill pipeline — stats and spell modifiers merged into the SkillBuild like an item's — plus
 * two class-resource multipliers the SkillService reads. Where the spec's wording has no exact hook yet the closest
 * existing modifier is used; those substitutions are marked {@code (≈)} in the text and listed in the docs.
 */
public final class MasteryPerks {

    /** One perk: the rank it opens at and what it does. */
    public record Perk(int rank, String text, Map<StatKey, Double> stats, Map<Spell, Map<ModKey, Double>> mods,
                       double resourceGain, double resourceRegen) {
    }

    private static final Map<PlayerClass, List<Perk>> PERKS = new EnumMap<>(PlayerClass.class);

    static {
        PERKS.put(PlayerClass.BAATAR, List.of(
                gain(3, "Тулалдаанд Хил +10 %", 1.10),
                stat(6, "Хилийн дээд хэмжээ +15", StatKey.RESOURCE_MAX, 15),
                mod(9, "Дайны Хашгираан 20 % хямд (буцаалт ≈)", Spell.DAINY_KHASHGIRAAN, ModKey.COST_PCT, -20)));
        PERKS.put(PlayerClass.MERGEN, List.of(
                regen(3, "Төвлөрлийн сэргэлт +10 %", 1.10),
                mod(6, "Чонын Нүдний сумс +25 % хохирол (≈ мултарсны дараах сум)", Spell.CHONYN_NUD, ModKey.DAMAGE_PCT, 25),
                mod(9, "Олон Сум 20 % давтагдана (≈ +1 сум)", Spell.OLON_SUM, ModKey.ECHO_PCT, 20)));
        PERKS.put(PlayerClass.BOO, List.of(
                stat(3, "Сүнсний дээд хэмжээ +20", StatKey.RESOURCE_MAX, 20),
                mod(6, "Сүнсний Залбирал хамгаалалт өгнө (+2 ❤)", Spell.SUNSNII_ZALBIRAL, ModKey.SHIELD, 2),
                mod(9, "Онгоны Дуудлага 15 % давтагдана (≈ +2 сек)", Spell.ONGONY_DUUDLAGA, ModKey.ECHO_PCT, 15)));
        PERKS.put(PlayerClass.DARKHAN, List.of(
                gain(3, "Цохилтын Дөл +10 %", 1.10),
                stat(6, "Дөлний дээд хэмжээ +10 (хэт халалтын босго)", StatKey.RESOURCE_MAX, 10),
                mod(9, "Галын Давталтын хүрээ +25 % (≈ +1 блок)", Spell.GALYN_DAVTALT, ModKey.RADIUS_PCT, 25)));
        PERKS.put(PlayerClass.KHULEGCHIN, List.of(
                regen(3, "Хурдны сэргэлт +15 % (≈ хурд буурах −15 %)", 1.15),
                mod(6, "Хурдан Довтолгоо +1 сек удаашруулна (≈ мэгдэл)", Spell.KHURDAN_DOVTOLGOO, ModKey.SLOW, 1),
                stat(9, "Довтолгооны хүч +5 % (≈ морин дээрх)", StatKey.ATTACK_PCT, 5)));
    }

    private MasteryPerks() {
    }

    private static Perk stat(int r, String t, StatKey k, double v) {
        return new Perk(r, t, Map.of(k, v), Map.of(), 1, 1);
    }

    private static Perk mod(int r, String t, Spell s, ModKey k, double v) {
        return new Perk(r, t, Map.of(), Map.of(s, Map.of(k, v)), 1, 1);
    }

    private static Perk gain(int r, String t, double m) {
        return new Perk(r, t, Map.of(), Map.of(), m, 1);
    }

    private static Perk regen(int r, String t, double m) {
        return new Perk(r, t, Map.of(), Map.of(), 1, m);
    }

    /** Every perk of the class (open or not), in rank order. */
    public static List<Perk> of(PlayerClass c) {
        return c == null ? List.of() : PERKS.getOrDefault(c, List.of());
    }

    /** The perks open at this rank. */
    public static List<Perk> open(PlayerClass c, int rank) {
        return of(c).stream().filter(p -> p.rank() <= rank).toList();
    }

    public static Map<StatKey, Double> stats(PlayerClass c, int rank) {
        Map<StatKey, Double> m = new EnumMap<>(StatKey.class);
        for (Perk p : open(c, rank)) p.stats().forEach((k, v) -> m.merge(k, v, Double::sum));
        return m;
    }

    public static Map<Spell, Map<ModKey, Double>> mods(PlayerClass c, int rank) {
        Map<Spell, Map<ModKey, Double>> m = new EnumMap<>(Spell.class);
        for (Perk p : open(c, rank)) p.mods().forEach((s, mm) -> mm.forEach((k, v) ->
                m.computeIfAbsent(s, x -> new EnumMap<>(ModKey.class)).merge(k, v, Double::sum)));
        return m;
    }

    /** Multiplier of the class resource gained on hit (Баатар Хил, Дархан Дөл). */
    public static double resourceGain(PlayerClass c, int rank) {
        double f = 1;
        for (Perk p : open(c, rank)) f *= p.resourceGain();
        return f;
    }

    /** Multiplier of the class resource's regeneration. */
    public static double resourceRegen(PlayerClass c, int rank) {
        double f = 1;
        for (Perk p : open(c, rank)) f *= p.resourceRegen();
        return f;
    }
}
