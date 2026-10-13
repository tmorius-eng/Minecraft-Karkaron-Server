package mn.suld.api.death;

/**
 * A soul's «Тэнгэрийн сорил» seen from outside (menus, the HUD, the bots): not in one, fighting it, or how the last
 * one ended. The fight's own rules are {@link DeathTrial}; this folds its {@link DeathTrial.Outcome} into four states.
 */
public enum SpiritTrialState {
    NOT_IN_TRIAL,
    FIGHTING,
    PASSED,
    FAILED;

    /** The state a trial outcome leaves the soul in. A trial stopped because the soul state ended is no trial at all. */
    public static SpiritTrialState of(DeathTrial.Outcome o) {
        return switch (o) {
            case RUNNING -> FIGHTING;
            case WON -> PASSED;
            case TIMED_OUT, FALLEN, LEFT -> FAILED;
            case LOCK_ENDED -> NOT_IN_TRIAL;
        };
    }

    /** True for a state a new trial may start from (no wait after a failed one since 2026-10-13). */
    public boolean mayStart() {
        return this != FIGHTING;
    }
}
