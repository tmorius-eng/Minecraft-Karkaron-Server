package mn.suld.api.pvp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure bookkeeping of consented fights: a challenge waits {@code requestTtl} for an answer; an accepted duel is open
 * for {@code duelTtl} or until one side dies, leaves or ends it. One challenge per challenger, one open duel per
 * player. Single-threaded (main-thread) use is assumed.
 */
public final class DuelRegistry {

    public enum Result { CHALLENGED, ALREADY_PENDING, TARGET_BUSY, SELF, NO_CHALLENGE, EXPIRED, ACCEPTED, ENDED, NOT_DUELING, DISABLED }

    /** An accepted duel between two players. */
    public record Duel(UUID a, UUID b, Instant startedAt, Instant endsAt) {
        public boolean involves(UUID p) {
            return a.equals(p) || b.equals(p);
        }

        public UUID other(UUID p) {
            return a.equals(p) ? b : a;
        }
    }

    private record Challenge(UUID from, UUID to, Instant expiresAt) {
    }

    private final Clock clock;
    private final Duration requestTtl;
    private final Duration duelTtl;
    /** by challenged player */
    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final Map<UUID, Duel> duelsByPlayer = new HashMap<>();

    public DuelRegistry(Clock clock, Duration requestTtl, Duration duelTtl) {
        this.clock = clock;
        this.requestTtl = requestTtl;
        this.duelTtl = duelTtl;
    }

    public Result challenge(UUID from, UUID to) {
        if (from.equals(to)) return Result.SELF;
        expire();
        if (duelsByPlayer.containsKey(from) || duelsByPlayer.containsKey(to)) return Result.TARGET_BUSY;
        Challenge pending = challenges.get(to);
        if (pending != null && pending.from().equals(from)) return Result.ALREADY_PENDING;
        // a challenge each way counts as an acceptance
        Challenge reverse = challenges.get(from);
        if (reverse != null && reverse.from().equals(to)) {
            challenges.remove(from);
            open(from, to);
            return Result.ACCEPTED;
        }
        challenges.put(to, new Challenge(from, to, clock.instant().plus(requestTtl)));
        return Result.CHALLENGED;
    }

    /** {@code player} accepts the challenge addressed to them; the challenger is returned on success. */
    public Optional<UUID> accept(UUID player) {
        expire();
        Challenge c = challenges.remove(player);
        if (c == null || duelsByPlayer.containsKey(player) || duelsByPlayer.containsKey(c.from())) return Optional.empty();
        open(c.from(), player);
        return Optional.of(c.from());
    }

    public Result decline(UUID player) {
        return challenges.remove(player) == null ? Result.NO_CHALLENGE : Result.ENDED;
    }

    /** Who challenged {@code player}, while the challenge is still open. */
    public Optional<UUID> pendingFrom(UUID player) {
        expire();
        Challenge c = challenges.get(player);
        return c == null ? Optional.empty() : Optional.of(c.from());
    }

    private void open(UUID a, UUID b) {
        Instant now = clock.instant();
        Duel d = new Duel(a, b, now, now.plus(duelTtl));
        duelsByPlayer.put(a, d);
        duelsByPlayer.put(b, d);
    }

    /** True while an accepted, unexpired duel binds the two. */
    public boolean dueling(UUID a, UUID b) {
        Duel d = duelsByPlayer.get(a);
        return d != null && d.involves(b) && clock.instant().isBefore(d.endsAt());
    }

    public Optional<Duel> duelOf(UUID player) {
        Duel d = duelsByPlayer.get(player);
        return d == null || !clock.instant().isBefore(d.endsAt()) ? Optional.empty() : Optional.of(d);
    }

    /** End the duel {@code player} is in (death, quit, {@code /duel end}); returns it if there was one. */
    public Optional<Duel> end(UUID player) {
        Duel d = duelsByPlayer.remove(player);
        if (d == null) return Optional.empty();
        duelsByPlayer.remove(d.other(player));
        return Optional.of(d);
    }

    /** Drop lapsed challenges and duels; returns the duels that just lapsed (for a notice). */
    public List<Duel> expire() {
        Instant now = clock.instant();
        challenges.values().removeIf(c -> !now.isBefore(c.expiresAt()));
        List<Duel> lapsed = new ArrayList<>();
        for (Duel d : new ArrayList<>(duelsByPlayer.values())) {
            if (!now.isBefore(d.endsAt()) && duelsByPlayer.remove(d.a()) != null) {
                duelsByPlayer.remove(d.b());
                lapsed.add(d);
            }
        }
        return lapsed;
    }
}
