package mn.suld.api.item;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The steppe consumables (audit 2026-10-14 IT-12 / IT-20): what each {@link ItemType#CONSUMABLE} item does, its
 * cooldown and how its effect is sized. Pure: the plugin's ConsumableService applies it (one used per right click,
 * taken from the hand and written to the player file before the effect runs, so a crash never keeps both).
 */
public final class Consumables {

    private Consumables() {
    }

    public enum Kind {
        /** Айраг: a share of the class resource back at once, then faster resource regeneration for a while. */
        RESOURCE,
        /** Талын Эмийн Ургамал: a share of max health back over a few seconds; poison, wither and slowness cleansed. */
        HEAL_OVER_TIME,
        /** Арц Хүж: less damage from the steppe's spirits for a while (using it again refreshes, never stacks). */
        WARD,
        /** Дарханы Билүү: a share of the held weapon's (else the worn armour's) durability back, away from the city. */
        REPAIR
    }

    /**
     * @param amount   RESOURCE: share of the pool restored; HEAL_OVER_TIME: share of max health over the duration;
     *                 WARD: damage taken from spirits is reduced by this share; REPAIR: share of max durability restored
     * @param bonus    RESOURCE: extra resource regeneration while it lasts (0.10 = +10 %); otherwise 0
     */
    public record Consumable(String itemId, Kind kind, int cooldownSeconds, int durationSeconds, double amount, double bonus,
                             String description) {
    }

    public static final Consumable AIRAG = new Consumable("item.airag_flask", Kind.RESOURCE, 30, 60, 0.25, 0.10,
            "Ангийн нөөцийн 25% сэргээж, 60 сек нөөц 10% хурдан нөхөгдөнө");
    public static final Consumable SALVE = new Consumable("item.steppe_salve", Kind.HEAL_OVER_TIME, 20, 5, 0.20, 0,
            "5 сек дотор амин хүчний 20% эдгээж, хор, хатаалт, удаашралыг арилгана");
    public static final Consumable INCENSE = new Consumable("item.ovoo_incense", Kind.WARD, 30, 180, 0.08, 0,
            "180 сек сүнснүүдийн хохирлыг 8% бууруулна (давхардахгүй, хугацаа шинэчлэгдэнэ)");
    public static final Consumable WHETSTONE = new Consumable("item.darkhan_whetstone", Kind.REPAIR, 10, 0, 0.35, 0,
            "Гартаа барьсан зэвсэг (эсвэл өмссөн хуяг)-ийн бат бэхийн 35%-ийг засна — Хархорумаас гадна");

    public static final List<Consumable> ALL = List.of(AIRAG, SALVE, INCENSE, WHETSTONE);
    private static final Map<String, Consumable> BY_ID = ALL.stream().collect(Collectors.toUnmodifiableMap(Consumable::itemId, Function.identity()));

    public static Optional<Consumable> of(String itemId) {
        return Optional.ofNullable(itemId == null ? null : BY_ID.get(itemId));
    }

    /** Milliseconds before {@code c} may be used again after a use at {@code lastUseMs} (null = never used); 0 = ready. */
    public static long cooldownLeftMs(Consumable c, Long lastUseMs, long nowMs) {
        if (lastUseMs == null) return 0;
        return Math.max(0, lastUseMs + c.cooldownSeconds() * 1000L - nowMs);
    }

    /** Resource points Айраг gives back at once from a pool of {@code poolMax}. */
    public static double resourceRestore(Consumable c, int poolMax) {
        return Math.max(0, poolMax) * c.amount();
    }

    /** Health the salve heals each second, over its duration, for a body of {@code maxHealth}. */
    public static double healPerSecond(Consumable c, double maxHealth) {
        return c.durationSeconds() <= 0 ? 0 : Math.max(0, maxHealth) * c.amount() / c.durationSeconds();
    }

    /** Durability points the whetstone takes off {@code damage}, for an item of {@code maxDurability}: never below 0. */
    public static int repairedDamage(Consumable c, int damage, int maxDurability) {
        int fix = (int) Math.ceil(Math.max(0, maxDurability) * c.amount());
        return Math.max(0, damage - fix);
    }

    /** A spirit's hit on a warded player. */
    public static double warded(Consumable c, double damage) {
        return damage * (1 - c.amount());
    }

    /** Whether a SÜLD mob id is one of the steppe's spirits (сүнс, савдаг, лус) the incense wards against. */
    public static boolean spirit(String mobId) {
        if (mobId == null) return false;
        return mobId.contains("suns") || mobId.contains("savdag") || mobId.contains("lus");
    }
}
