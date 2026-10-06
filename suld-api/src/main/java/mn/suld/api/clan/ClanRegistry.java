package mn.suld.api.clan;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Every clan rule in one pure, unit-tested place: unique names/tags, invites with expiry,
 * rank permissions, leadership invariants, capacity by clan level, and contribution.
 * Main-thread only. The plugin layer persists whatever this registry changes.
 */
public final class ClanRegistry {

    /** Result plus the clan it concerns (may be null on failure). */
    public record Action(ClanResult result, Clan clan) {
        public boolean ok() { return result.success(); }
    }

    /** A contribution that was credited; {@code levelsGained > 0} means the clan levelled up. */
    public record Contribution(Clan clan, long amount, int levelsGained) {
    }

    private record Invite(UUID clanId, UUID inviter, Instant expiresAt) {
    }

    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 24;
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}]+( [\\p{L}\\p{N}]+)*");
    private static final Pattern TAG = Pattern.compile("[\\p{Lu}\\p{N}]{2,5}");

    private final Clock clock;
    private final Duration inviteTtl;
    private final Map<UUID, Clan> clans = new HashMap<>();
    private final Map<String, UUID> byNameKey = new HashMap<>();
    private final Map<String, UUID> byTag = new HashMap<>();
    private final Map<UUID, UUID> memberToClan = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    public ClanRegistry(Clock clock, Duration inviteTtl) {
        this.clock = clock;
        this.inviteTtl = inviteTtl;
    }

    // ------------------------------------------------------------- validation

    /** Trimmed, single-spaced name, or empty if invalid. */
    public static Optional<String> normalizeName(String raw) {
        if (raw == null) return Optional.empty();
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.length() < NAME_MIN || name.length() > NAME_MAX || !NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    /** Upper-cased tag (Latin or Cyrillic letters/digits, 2-5), or empty if invalid. */
    public static Optional<String> normalizeTag(String raw) {
        if (raw == null) return Optional.empty();
        String tag = raw.trim().toUpperCase(Locale.ROOT);
        return TAG.matcher(tag).matches() ? Optional.of(tag) : Optional.empty();
    }

    private static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- queries

    public Optional<Clan> clanOf(UUID player) {
        UUID id = memberToClan.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(clans.get(id));
    }

    public Optional<Clan> byTag(String tag) {
        return normalizeTag(tag).map(byTag::get).map(clans::get);
    }

    public Optional<Clan> byId(UUID id) {
        return Optional.ofNullable(clans.get(id));
    }

    public Collection<Clan> all() {
        return List.copyOf(clans.values());
    }

    public boolean hasPendingInvite(UUID player) {
        Invite inv = invites.get(player);
        return inv != null && clock.instant().isBefore(inv.expiresAt());
    }

    /** Tag of the clan that invited {@code player}, if an invite is pending. */
    public Optional<String> pendingInviteTag(UUID player) {
        Invite inv = invites.get(player);
        if (inv == null || !clock.instant().isBefore(inv.expiresAt())) return Optional.empty();
        return byId(inv.clanId()).map(Clan::tag);
    }

    // ------------------------------------------------------------ persistence

    /** Index a clan loaded from storage. Rejects anything that would break uniqueness. */
    public void register(Clan clan) {
        String key = nameKey(clan.name());
        if (clans.containsKey(clan.id()) || byNameKey.containsKey(key) || byTag.containsKey(clan.tag())) {
            throw new IllegalStateException("Duplicate clan on load: " + clan);
        }
        for (ClanMember m : clan.members()) {
            if (memberToClan.containsKey(m.playerId())) {
                throw new IllegalStateException("Player " + m.playerId() + " is in two clans");
            }
        }
        clans.put(clan.id(), clan);
        byNameKey.put(key, clan.id());
        byTag.put(clan.tag(), clan.id());
        clan.members().forEach(m -> memberToClan.put(m.playerId(), clan.id()));
    }

    // -------------------------------------------------------------- lifecycle

    public Action create(UUID founder, String founderName, String rawName, String rawTag) {
        if (memberToClan.containsKey(founder)) return new Action(ClanResult.ALREADY_IN_CLAN, clanOf(founder).orElse(null));
        Optional<String> name = normalizeName(rawName);
        if (name.isEmpty()) return new Action(ClanResult.INVALID_NAME, null);
        Optional<String> tag = normalizeTag(rawTag);
        if (tag.isEmpty()) return new Action(ClanResult.INVALID_TAG, null);
        if (byNameKey.containsKey(nameKey(name.get()))) return new Action(ClanResult.NAME_TAKEN, null);
        if (byTag.containsKey(tag.get())) return new Action(ClanResult.TAG_TAKEN, null);

        Clan clan = Clan.found(founder, founderName, name.get(), tag.get(), clock.instant());
        register(clan);
        invites.remove(founder);
        return new Action(ClanResult.CREATED, clan);
    }

    public Action invite(UUID inviter, UUID target) {
        Clan clan = clanOf(inviter).orElse(null);
        if (clan == null) return new Action(ClanResult.NOT_IN_CLAN, null);
        if (inviter.equals(target)) return new Action(ClanResult.SELF_TARGET, clan);
        if (!clan.member(inviter).orElseThrow().rank().canInvite()) return new Action(ClanResult.NO_PERMISSION, clan);
        if (memberToClan.containsKey(target)) return new Action(ClanResult.TARGET_IN_CLAN, clan);
        if (clan.isFull()) return new Action(ClanResult.CLAN_FULL, clan);
        invites.put(target, new Invite(clan.id(), inviter, clock.instant().plus(inviteTtl)));
        return new Action(ClanResult.INVITED, clan);
    }

    public Action accept(UUID player, String playerName) {
        Invite inv = invites.remove(player);
        if (inv == null) return new Action(ClanResult.NO_INVITE, null);
        if (!clock.instant().isBefore(inv.expiresAt())) return new Action(ClanResult.INVITE_EXPIRED, null);
        if (memberToClan.containsKey(player)) return new Action(ClanResult.ALREADY_IN_CLAN, clanOf(player).orElse(null));
        Clan clan = clans.get(inv.clanId());
        if (clan == null) return new Action(ClanResult.CLAN_GONE, null);
        if (!clan.add(player, playerName, clock.instant())) return new Action(ClanResult.CLAN_FULL, clan);
        memberToClan.put(player, clan.id());
        return new Action(ClanResult.JOINED, clan);
    }

    /** Leave. A leader may only leave as the last member (which disbands the clan). */
    public Action leave(UUID player) {
        Clan clan = clanOf(player).orElse(null);
        if (clan == null) return new Action(ClanResult.NOT_IN_CLAN, null);
        if (clan.leader().playerId().equals(player)) {
            if (clan.size() > 1) return new Action(ClanResult.LEADER_MUST_TRANSFER, clan);
            drop(clan);
            return new Action(ClanResult.DISBANDED, clan);
        }
        clan.remove(player);
        memberToClan.remove(player);
        return new Action(ClanResult.LEFT, clan);
    }

    /** Officers may kick members; the leader may kick anyone. Never upward or sideways. */
    public Action kick(UUID actor, UUID target) {
        Clan clan = clanOf(actor).orElse(null);
        if (clan == null) return new Action(ClanResult.NOT_IN_CLAN, null);
        if (actor.equals(target)) return new Action(ClanResult.SELF_TARGET, clan);
        Optional<ClanMember> victim = clan.member(target);
        if (victim.isEmpty()) return new Action(ClanResult.NOT_A_MEMBER, clan);
        ClanRank actorRank = clan.member(actor).orElseThrow().rank();
        if (!actorRank.canInvite() || !actorRank.outranks(victim.get().rank())) {
            return new Action(ClanResult.NO_PERMISSION, clan);
        }
        clan.remove(target);
        memberToClan.remove(target);
        return new Action(ClanResult.KICKED, clan);
    }

    public Action promote(UUID actor, UUID target) {
        Action check = leaderAction(actor, target);
        if (check != null) return check;
        Clan clan = clanOf(actor).orElseThrow();
        if (clan.member(target).orElseThrow().rank() != ClanRank.MEMBER) return new Action(ClanResult.ALREADY_MAX_RANK, clan);
        clan.setRank(target, ClanRank.OFFICER);
        return new Action(ClanResult.PROMOTED, clan);
    }

    public Action demote(UUID actor, UUID target) {
        Action check = leaderAction(actor, target);
        if (check != null) return check;
        Clan clan = clanOf(actor).orElseThrow();
        if (clan.member(target).orElseThrow().rank() != ClanRank.OFFICER) return new Action(ClanResult.ALREADY_MIN_RANK, clan);
        clan.setRank(target, ClanRank.MEMBER);
        return new Action(ClanResult.DEMOTED, clan);
    }

    public Action transfer(UUID actor, UUID target) {
        Action check = leaderAction(actor, target);
        if (check != null) return check;
        Clan clan = clanOf(actor).orElseThrow();
        clan.transferLeadership(target);
        return new Action(ClanResult.TRANSFERRED, clan);
    }

    public Action disband(UUID actor) {
        Clan clan = clanOf(actor).orElse(null);
        if (clan == null) return new Action(ClanResult.NOT_IN_CLAN, null);
        if (!clan.leader().playerId().equals(actor)) return new Action(ClanResult.NO_PERMISSION, clan);
        drop(clan);
        return new Action(ClanResult.DISBANDED, clan);
    }

    /** Credit clan EXP earned by a member. Empty if the player has no clan. */
    public Optional<Contribution> contribute(UUID player, long amount) {
        if (amount <= 0) return Optional.empty();
        return clanOf(player).map(clan -> new Contribution(clan, amount, clan.contribute(player, amount)));
    }

    /** Keep the stored member name fresh (players can rename their Minecraft account). */
    public void updateName(UUID player, String name) {
        clanOf(player).ifPresent(c -> c.rename(player, name));
    }

    // ---------------------------------------------------------------- helpers

    /** Common checks for leader-only actions on another member; null when allowed. */
    private Action leaderAction(UUID actor, UUID target) {
        Clan clan = clanOf(actor).orElse(null);
        if (clan == null) return new Action(ClanResult.NOT_IN_CLAN, null);
        if (!clan.leader().playerId().equals(actor)) return new Action(ClanResult.NO_PERMISSION, clan);
        if (actor.equals(target)) return new Action(ClanResult.SELF_TARGET, clan);
        if (!clan.contains(target)) return new Action(ClanResult.NOT_A_MEMBER, clan);
        return null;
    }

    private void drop(Clan clan) {
        clan.members().forEach(m -> memberToClan.remove(m.playerId()));
        clans.remove(clan.id());
        byNameKey.remove(nameKey(clan.name()));
        byTag.remove(clan.tag());
        invites.values().removeIf(inv -> inv.clanId().equals(clan.id()));
    }
}
