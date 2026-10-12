package mn.suld.api.clazz;

/**
 * Who may change class, when, and for what (docs/CLASS_CHANGE.md). Pure: the plugin gathers the facts, this decides.
 * <ul>
 *   <li><b>Grace</b> (a new character, level ≤ 10 and ≤ 180 active minutes): free, no cooldown.</li>
 *   <li><b>Standard</b>: level × 250 coins and a 6-hour cooldown, or one «Хувь Төөргийн Тамга» seal instead of
 *   both.</li>
 *   <li><b>Never</b>: outside Хархорум, within 60 s of a fight, in a dungeon run, a duel or a world event, as a soul
 *   or dead, with fewer than 5 free bag slots, or to the class already played.</li>
 * </ul>
 */
public final class ClassChangeRules {

    public static final int GRACE_MAX_LEVEL = 10;
    public static final long GRACE_MAX_MINUTES = 180;
    public static final long COOLDOWN_MS = 6L * 60 * 60 * 1000;
    public static final long COMBAT_MS = 60_000;
    public static final int COINS_PER_LEVEL = 250;
    public static final int FREE_SLOTS = 5;

    private ClassChangeRules() {
    }

    /** Facts about the player at the moment of the request. */
    public record Request(PlayerClass from, PlayerClass to, int level, long activeMinutes, long coins, int seals,
                          long now, long lastChange, long lastCombat, boolean inCity, boolean inDungeon, boolean inDuel,
                          boolean inWorldEvent, boolean soul, boolean dead, int freeSlots) {
    }

    /** How a change is paid. */
    public enum Payment { GRACE, COINS, SEAL }

    public enum Refusal {
        NO_CLASS("Эхлээд ангиа сонго."),
        SAME_CLASS("Та аль хэдийн энэ ангид байна."),
        NOT_IN_CITY("Анги зөвхөн Хархорумд солино: хотод ирээд Ангийн Бөөтэй уулз."),
        IN_COMBAT("Тулаанаас хойш 60 секунд хүлээ."),
        IN_DUNGEON("Агуйн дотор анги солих боломжгүй."),
        IN_DUEL("Тулааны үеэр анги солих боломжгүй."),
        IN_WORLD_EVENT("Ертөнцийн үйл явдлын үеэр анги солих боломжгүй."),
        SOUL("Сүнс анги сольж чадахгүй: эхлээд бие махбодтоо буц."),
        DEAD("Үхсэн үедээ анги солих боломжгүй."),
        NO_ROOM("Цүнхэнд хангалттай зай алга (дор хаяж 5 зай чөлөөлнө үү)."),
        COOLDOWN("Анги солих хүлээлт дуусаагүй байна."),
        NO_COINS("Зоос хүрэлцэхгүй байна.");

        public final String text;

        Refusal(String text) {
            this.text = text;
        }
    }

    /** The answer: allowed with a payment and price, or refused with a reason. */
    public record Verdict(Refusal refusal, Payment payment, long price, long cooldownLeftMs) {
        public boolean allowed() {
            return refusal == null;
        }
    }

    public static boolean grace(int level, long activeMinutes) {
        return level <= GRACE_MAX_LEVEL && activeMinutes <= GRACE_MAX_MINUTES;
    }

    public static long cost(int level) {
        return (long) Math.max(1, level) * COINS_PER_LEVEL;
    }

    public static long cooldownLeft(long now, long lastChange) {
        if (lastChange <= 0) return 0;
        return Math.max(0, lastChange + COOLDOWN_MS - now);
    }

    public static boolean inCombat(long now, long lastCombat) {
        return lastCombat > 0 && now - lastCombat < COMBAT_MS;
    }

    /** The decision. The hard restrictions come first; then grace, a seal, or coins after the cooldown. */
    public static Verdict check(Request r) {
        long left = cooldownLeft(r.now(), r.lastChange());
        Refusal hard = r.from() == null ? Refusal.NO_CLASS
                : r.to() == r.from() ? Refusal.SAME_CLASS
                : r.dead() ? Refusal.DEAD
                : r.soul() ? Refusal.SOUL
                : r.inDungeon() ? Refusal.IN_DUNGEON
                : r.inDuel() ? Refusal.IN_DUEL
                : r.inWorldEvent() ? Refusal.IN_WORLD_EVENT
                : inCombat(r.now(), r.lastCombat()) ? Refusal.IN_COMBAT
                : !r.inCity() ? Refusal.NOT_IN_CITY
                : r.freeSlots() < FREE_SLOTS ? Refusal.NO_ROOM
                : null;
        if (hard != null) return new Verdict(hard, null, 0, left);
        if (grace(r.level(), r.activeMinutes())) return new Verdict(null, Payment.GRACE, 0, 0);
        if (r.seals() > 0) return new Verdict(null, Payment.SEAL, 0, left);
        if (left > 0) return new Verdict(Refusal.COOLDOWN, null, cost(r.level()), left);
        long price = cost(r.level());
        if (r.coins() < price) return new Verdict(Refusal.NO_COINS, null, price, 0);
        return new Verdict(null, Payment.COINS, price, 0);
    }

    /** «2 ц 15 мин» for a remaining time. */
    public static String format(long ms) {
        long min = (ms + 59_999) / 60_000;
        return min >= 60 ? (min / 60) + " ц " + (min % 60) + " мин" : min + " мин";
    }
}
