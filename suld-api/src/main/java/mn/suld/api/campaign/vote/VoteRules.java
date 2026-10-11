package mn.suld.api.campaign.vote;

import java.util.Optional;

/** Who may vote in Их Хуралдай: a character with a class, past the level gate, on an account old enough. */
public final class VoteRules {

    public enum Denial { NO_CLASS, LEVEL_TOO_LOW, ACCOUNT_TOO_NEW }

    private VoteRules() {
    }

    public static Optional<Denial> eligible(boolean hasClass, int level, int minLevel, long accountAgeDays, int minAccountDays) {
        if (!hasClass) return Optional.of(Denial.NO_CLASS);
        if (level < minLevel) return Optional.of(Denial.LEVEL_TOO_LOW);
        if (accountAgeDays < minAccountDays) return Optional.of(Denial.ACCOUNT_TOO_NEW);
        return Optional.empty();
    }

    public static String reason(Denial d, int minLevel, int minAccountDays) {
        return switch (d) {
            case NO_CLASS -> "Эхлээд ангиа сонго: /class";
            case LEVEL_TOO_LOW -> "Хуралдайд " + minLevel + "-р түвшнээс санал өгнө.";
            case ACCOUNT_TOO_NEW -> "Шинэ дүр " + minAccountDays + " хоногийн дараа санал өгнө.";
        };
    }
}
