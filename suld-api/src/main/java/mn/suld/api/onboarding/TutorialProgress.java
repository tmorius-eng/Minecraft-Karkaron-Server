package mn.suld.api.onboarding;

import java.util.List;

/**
 * Where a player stands in the SÜLD tutorial (docs/TUTORIAL.md): {@code NOT_STARTED → IN_PROGRESS(step) → COMPLETED},
 * and which steps have already paid their reward. A replay ({@code /tutorial restart}) walks the steps again but
 * never pays a step twice, so the tutorial cannot be farmed. Stored as one short string in the player's data.
 */
public record TutorialProgress(State state, int step, long rewarded) {

    public enum State { NOT_STARTED, IN_PROGRESS, COMPLETED }

    /** The steps in order: what to do, how it is shown, the reward (EXP, coins) paid the first time only. */
    public enum Step {
        CHOOSE_CLASS("Ангиа сонго", "Хүдэлмэрчин NPC эсвэл /class — Баатар, Мэргэн, Бөө, Дархан, Хүлэгчин", 0, 0),
        HOLD_WEAPON("Ангийн зэвсгээ барь", "Зэвсэг чинь гарт чинь байх ёстой (хаях боломжгүй, түвшин ахих тусам хувирна)", 40, 20),
        OPEN_SKILLS("Чадварын модоо нээ", "/skills — Тэнгэрийн мод: оноогоо зангилаанд зарцуул", 60, 25),
        LOCK_ON("Байгаа түгжих", "Зэвсэг гартай Q дар — ойрын дайсанд түгжигдэнэ, дахин Q — суллана", 60, 25),
        FIRST_KILL("Анхны ан", "Хотын хаалгаар гараад тал нутгийн нэг амьтан ал", 120, 40),
        OPEN_QUEST("Аяны замаа хар", "/quest — Түүхийн бүлэг, зорилго, шагнал", 60, 25),
        FIND_GATE("Хасарын Агуйн хаалгыг ол", "/dungeon list — хаалганы байрлал, луужин чамайг чиглүүлнэ", 200, 75);

        private final String title;
        private final String hint;
        private final long exp;
        private final long coins;

        Step(String title, String hint, long exp, long coins) {
            this.title = title;
            this.hint = hint;
            this.exp = exp;
            this.coins = coins;
        }

        public String title() { return title; }
        public String hint() { return hint; }
        public long exp() { return exp; }
        public long coins() { return coins; }
    }

    public static final List<Step> STEPS = List.of(Step.values());
    public static final TutorialProgress FRESH = new TutorialProgress(State.NOT_STARTED, 0, 0);

    /** Completion bonus, paid once. */
    public static final long FINISH_EXP = 300, FINISH_COINS = 150;

    public TutorialProgress {
        if (state == null) state = State.NOT_STARTED;
        step = Math.max(0, Math.min(STEPS.size(), step));
    }

    public Step current() {
        return state == State.IN_PROGRESS && step < STEPS.size() ? STEPS.get(step) : null;
    }

    public TutorialProgress start() {
        return new TutorialProgress(State.IN_PROGRESS, 0, rewarded);
    }

    /** True when the current step's reward has not been paid yet (and the finish bit for the last one). */
    public boolean unpaid(int stepIndex) {
        return (rewarded & (1L << stepIndex)) == 0;
    }

    public boolean finishUnpaid() {
        return unpaid(STEPS.size());
    }

    /** The current step is done: the next step (or COMPLETED), with this step marked as paid. */
    public TutorialProgress advance() {
        if (state != State.IN_PROGRESS) return this;
        long paid = rewarded | (1L << step);
        int next = step + 1;
        if (next >= STEPS.size()) return new TutorialProgress(State.COMPLETED, STEPS.size(), paid | (1L << STEPS.size()));
        return new TutorialProgress(State.IN_PROGRESS, next, paid);
    }

    /** {@code state:step:rewardedHex}. */
    public String encode() {
        return state.name() + ":" + step + ":" + Long.toHexString(rewarded);
    }

    public static TutorialProgress decode(String s) {
        if (s == null || s.isBlank()) return FRESH;
        String[] p = s.split(":");
        try {
            return new TutorialProgress(State.valueOf(p[0]), Integer.parseInt(p[1]), Long.parseUnsignedLong(p[2], 16));
        } catch (RuntimeException e) {
            return FRESH; // a damaged value starts over (rewards are only ever paid against the stored mask)
        }
    }
}
