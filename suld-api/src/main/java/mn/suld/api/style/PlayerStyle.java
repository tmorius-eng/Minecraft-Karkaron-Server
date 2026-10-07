package mn.suld.api.style;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A player's rank, owned cosmetics, equipped cosmetics and claimed level rewards. Thread-safe (the chat thread
 * reads it); every change marks it dirty for the next save.
 */
public final class PlayerStyle {

    /** Immutable copy for persistence. */
    public record Snapshot(UUID player, Rank rank, Set<String> owned, String tag, String nameColor, String chatColor,
                           String joinMessage, long claimedLevels, long credits, long discovered, long dailyDay, int dailyStreak, long taskDay, String taskProgress,
                           String aura, String trail, String killEffect) {
    }

    private final UUID player;
    private Rank rank = Rank.ARD;
    private final Set<String> owned = new LinkedHashSet<>();
    private String tag;
    private String nameColor;
    private String chatColor;
    private String joinMessage;
    private String aura;
    private String trail;
    private String killEffect;
    private long claimedLevels;
    private long discovered;
    private long dailyDay;
    private int dailyStreak;
    private long taskDay;
    private String taskProgress = "";
    private long credits;
    private boolean dirty;

    public PlayerStyle(UUID player) {
        this.player = player;
        for (Cosmetic c : CosmeticCatalog.ALL) if ("default".equals(c.source())) owned.add(c.id());
    }

    public static PlayerStyle restore(Snapshot s) {
        PlayerStyle p = new PlayerStyle(s.player());
        p.rank = s.rank() == null ? Rank.ARD : s.rank();
        for (String id : s.owned()) if (CosmeticCatalog.byId(id).isPresent()) p.owned.add(id);
        p.tag = p.ownedOrNull(s.tag());
        p.nameColor = p.ownedOrNull(s.nameColor());
        p.chatColor = p.ownedOrNull(s.chatColor());
        p.joinMessage = p.ownedOrNull(s.joinMessage());
        p.aura = p.ownedOrNull(s.aura());
        p.trail = p.ownedOrNull(s.trail());
        p.killEffect = p.ownedOrNull(s.killEffect());
        p.claimedLevels = s.claimedLevels();
        p.discovered = s.discovered();
        p.dailyDay = s.dailyDay();
        p.dailyStreak = Math.max(0, s.dailyStreak());
        p.taskDay = s.taskDay();
        p.taskProgress = s.taskProgress() == null ? "" : s.taskProgress();
        p.credits = Math.max(0, s.credits());
        return p;
    }

    private String ownedOrNull(String id) {
        return id != null && owned.contains(id) ? id : null;
    }

    public UUID player() { return player; }

    public synchronized Rank rank() { return rank; }

    public synchronized void rank(Rank r) {
        rank = r;
        dirty = true;
    }

    public synchronized boolean owns(String id) { return owned.contains(id); }

    public synchronized Set<String> owned() { return Collections.unmodifiableSet(new LinkedHashSet<>(owned)); }

    /** Grant a cosmetic; returns false if it was already owned or does not exist. */
    public synchronized boolean grant(String id) {
        if (CosmeticCatalog.byId(id).isEmpty() || !owned.add(id)) return false;
        dirty = true;
        return true;
    }

    public synchronized long ownedIn(Cosmetic.Category category) {
        return owned.stream().map(CosmeticCatalog::byId).flatMap(Optional::stream).filter(c -> c.category() == category).count();
    }

    /** The equipped cosmetic of a category (EMOJI has none: every owned emoji works). */
    public synchronized Optional<Cosmetic> equipped(Cosmetic.Category category) {
        String id = switch (category) {
            case TAG -> tag;
            case NAME_COLOR -> nameColor;
            case CHAT_COLOR -> chatColor;
            case JOIN_MESSAGE -> joinMessage;
            case AURA -> aura;
            case TRAIL -> trail;
            case KILL_EFFECT -> killEffect;
            case EMOJI -> null;
        };
        return id == null ? Optional.empty() : CosmeticCatalog.byId(id);
    }

    /** Equip an owned cosmetic, or unequip the category when {@code id} is null. Returns false if not owned. */
    public synchronized boolean equip(Cosmetic.Category category, String id) {
        if (id != null) {
            Cosmetic c = CosmeticCatalog.byId(id).orElse(null);
            if (c == null || c.category() != category || !owned.contains(id)) return false;
        }
        switch (category) {
            case TAG -> tag = id;
            case NAME_COLOR -> nameColor = id;
            case CHAT_COLOR -> chatColor = id;
            case JOIN_MESSAGE -> joinMessage = id;
            case AURA -> aura = id;
            case TRAIL -> trail = id;
            case KILL_EFFECT -> killEffect = id;
            case EMOJI -> {
                return false;
            }
        }
        dirty = true;
        return true;
    }

    public synchronized long claimedLevels() { return claimedLevels; }

    public synchronized boolean claimLevel(int level) {
        if (LevelRewards.claimed(claimedLevels, level)) return false;
        claimedLevels = LevelRewards.withClaimed(claimedLevels, level);
        dirty = true;
        return true;
    }

    /** Bit N set = region N (in world-content order) has been discovered; its EXP is paid exactly once. */
    public synchronized long discovered() { return discovered; }

    /** Mark region {@code index} (0..63) discovered; false if it already was. */
    public synchronized boolean discover(int index) {
        if (index < 0 || index > 63) return false;
        long bit = 1L << index;
        if ((discovered & bit) != 0) return false;
        discovered |= bit;
        dirty = true;
        return true;
    }

    /** Epoch day of the last daily-reward claim (0 = never). */
    public synchronized long dailyDay() { return dailyDay; }

    public synchronized int dailyStreak() { return dailyStreak; }

    /** Record a daily claim for {@code day} as streak day {@code streak}; false if that day was already claimed. */
    public synchronized boolean claimDaily(long day, int streak) {
        if (dailyDay == day) return false;
        dailyDay = day;
        dailyStreak = streak;
        dirty = true;
        return true;
    }

    /** Daily-task progress ("3,0,12") for {@code day}; empty for any other day. */
    public synchronized String taskProgress(long day) { return taskDay == day ? taskProgress : ""; }

    public synchronized void taskProgress(long day, String progress) {
        taskDay = day;
        taskProgress = progress == null ? "" : progress;
        dirty = true;
    }

    /**
     * Store credits (Сүлд Кредит) as last read from storage. Display only: credits change exclusively through
     * {@link mn.suld.api.persistence.StyleRepository#addCredits} (an atomic database update), never by saving
     * this object, so a grant can never be lost to a concurrent save.
     */
    public synchronized long credits() { return credits; }

    public synchronized void creditsCache(long value) { credits = Math.max(0, value); }

    public synchronized boolean isDirty() { return dirty; }

    public synchronized Snapshot snapshotAndClean() {
        dirty = false;
        return new Snapshot(player, rank, new LinkedHashSet<>(owned), tag, nameColor, chatColor, joinMessage, claimedLevels, credits, discovered, dailyDay, dailyStreak, taskDay, taskProgress, aura, trail, killEffect);
    }

    public synchronized void markDirty() { dirty = true; }
}
