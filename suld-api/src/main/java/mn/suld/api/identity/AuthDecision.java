package mn.suld.api.identity;

import java.util.Optional;
import java.util.UUID;

/** Outcome of {@link AuthPolicy#evaluate}. {@code reason} is safe to show to the player. */
public record AuthDecision(boolean allowed, Code code, String reason) {

    public enum Code {
        ALLOWED,
        ALLOWED_INSECURE_DEV,
        ALLOWED_HYBRID,
        DENIED_SERVER_INSECURE,
        DENIED_UNAUTHENTICATED_UUID,
        DENIED_INVALID_NAME,
        DENIED_NAME_TAKEN,
        DENIED_AUTH_BRIDGE_UNAVAILABLE
    }

    static AuthDecision allow(Code code) {
        return new AuthDecision(true, code, "");
    }

    static AuthDecision deny(Code code, String reason) {
        return new AuthDecision(false, code, reason);
    }

    /**
     * A name belongs to the profile that holds it (case ignored): {@code owner} is the UUID of the stored profile
     * whose name equals {@code name} without regard to case, if any. Another UUID asking for that name is refused,
     * so {@code dorj} can never shadow {@code Dorj}.
     */
    public static AuthDecision checkNameOwner(UUID joining, Optional<UUID> owner) {
        if (owner.isPresent() && !owner.get().equals(joining)) {
            return deny(Code.DENIED_NAME_TAKEN, "Энэ нэр өөр тоглогчийнх байна (том жижиг үсгээ шалгана уу). "
                    + "/ This name belongs to another player (check the letter case).");
        }
        return allow(Code.ALLOWED);
    }

    /** The server is set up for AuthMe but the bridge to it does not work: nobody is let in on a name alone. */
    public static AuthDecision bridgeUnavailable() {
        return deny(Code.DENIED_AUTH_BRIDGE_UNAVAILABLE,
                "Нэвтрэлтийн систем түр ажиллахгүй байна. Дараа дахин орно уу. / Login is unavailable right now.");
    }
}
