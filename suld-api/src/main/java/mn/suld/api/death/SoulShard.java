package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;

import java.util.Objects;
import java.util.UUID;

/**
 * «Сүнсний Хэлтэрхий» (2026-10-13): what a won «Тэнгэрийн сорил» leaves in the soul's hand. Bound to the player who
 * won it and fading after {@link #LIFETIME_DAYS} days, it heals part of the death wound when used: its {@link Tier}'s
 * active minutes of healing at once ({@link Wound#heal}). It never revives, buys or trades anything.
 *
 * @param boundTo   the only player who may use it
 * @param expiresAt epoch ms after which it is gone
 */
public record SoulShard(Tier tier, UUID boundTo, long expiresAt) {

    public static final int LIFETIME_DAYS = 7;

    /** The shard's strength: active minutes of wound healing. */
    public enum Tier {
        /** From «Тэнгэрийн сорил»: a third of a wound step at the shipped 180 min. */
        FAINT(60),
        /** Reserved for harder content (heroic halls): one step. */
        BRIGHT(180),
        /** Reserved for the hardest content: three steps. */
        RADIANT(540);

        private final int healMinutes;

        Tier(int healMinutes) {
            this.healMinutes = healMinutes;
        }

        public int healMinutes() {
            return healMinutes;
        }
    }

    public SoulShard {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(boundTo, "boundTo");
    }

    /** A shard won at {@code now}. */
    public static SoulShard won(Tier tier, UUID player, long now) {
        return new SoulShard(tier, player, now + LIFETIME_DAYS * 86_400_000L);
    }

    public boolean expired(long now) {
        return now >= expiresAt;
    }

    /** Whether {@code player} may use it at {@code now} on {@code wound} (bound to them, not faded, a wound to heal). */
    public boolean usable(UUID player, long now, Wound wound) {
        return boundTo.equals(player) && !expired(now) && wound.stacks() > 0;
    }

    /** The wound after using it. */
    public Wound apply(Wound wound, DeathSettings s) {
        return wound.heal(s, tier.healMinutes());
    }
}
