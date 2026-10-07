package mn.suld.api.profile;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.progression.Progression;
import mn.suld.api.quest.QuestState;
import mn.suld.api.item.EquipmentState;
import mn.suld.api.skill.tree.SkillState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A player's persistent SULD character.
 *
 * <p>The profile aggregates identity, the chosen {@link PlayerClass}, and
 * {@link Progression}. It is a mutable domain entity, but every piece of
 * composite state is swapped through synchronized methods so a reader never
 * observes a torn update. Each state-changing call bumps {@link #version()},
 * which the persistence layer uses for optimistic locking (an important part of
 * the anti-duplication strategy — see DATABASE.md).
 *
 * <p>Higher-level serialization of access (one writer per player) is the
 * responsibility of the profile service; this class only guarantees its own
 * internal consistency.
 */
public final class PlayerProfile {

    private final UUID playerId;
    private final Instant createdAt;

    // Guarded by `this`.
    private String name;
    private PlayerClass playerClass; // null until the player picks one
    private Progression progression;
    private Instant lastSeenAt;
    private long version;
    private boolean dirty;
    private long currency;
    private QuestState questState;
    private SkillState skillState;
    private EquipmentState equipment;
    private mn.suld.api.classgear.ClassGear classGear;
    private mn.suld.api.activity.ActiveMinutes activeMinutes;

    private PlayerProfile(UUID playerId, String name, PlayerClass playerClass,
                          Progression progression, Instant createdAt, Instant lastSeenAt,
                          long version, long currency, QuestState questState, SkillState skillState,
                          EquipmentState equipment, mn.suld.api.classgear.ClassGear classGear,
                          mn.suld.api.activity.ActiveMinutes activeMinutes) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.name = Objects.requireNonNull(name, "name");
        this.playerClass = playerClass;
        this.progression = Objects.requireNonNull(progression, "progression");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        this.version = version;
        this.currency = Math.max(0, currency);
        this.questState = questState == null ? QuestState.NONE : questState;
        this.skillState = skillState == null ? SkillState.NONE : skillState;
        this.equipment = equipment == null ? EquipmentState.NONE : equipment;
        this.classGear = classGear == null ? mn.suld.api.classgear.ClassGear.NONE : classGear;
        this.activeMinutes = activeMinutes == null ? mn.suld.api.activity.ActiveMinutes.NONE : activeMinutes;
    }

    /** Create a brand-new profile for a first-time player (no class yet). */
    public static PlayerProfile createNew(UUID playerId, String name, Instant now) {
        Objects.requireNonNull(now, "now");
        return new PlayerProfile(playerId, name, null, Progression.initial(), now, now, 0L, 0L, QuestState.NONE, SkillState.NONE, EquipmentState.NONE, null, null);
    }

    /** Rehydrate a profile loaded from storage. Used by persistence adapters. */
    public static PlayerProfile restore(UUID playerId, String name, @Nullable PlayerClass playerClass,
                                        Progression progression, Instant createdAt, Instant lastSeenAt,
                                        long version, long currency, QuestState questState,
                                        @Nullable SkillState skillState) {
        return restore(playerId, name, playerClass, progression, createdAt, lastSeenAt, version, currency, questState, skillState, null);
    }

    public static PlayerProfile restore(UUID playerId, String name, @Nullable PlayerClass playerClass,
                                        Progression progression, Instant createdAt, Instant lastSeenAt,
                                        long version, long currency, QuestState questState,
                                        @Nullable SkillState skillState, @Nullable EquipmentState equipment) {
        return restore(playerId, name, playerClass, progression, createdAt, lastSeenAt, version, currency, questState, skillState, equipment, null, null);
    }

    public static PlayerProfile restore(UUID playerId, String name, @Nullable PlayerClass playerClass,
                                        Progression progression, Instant createdAt, Instant lastSeenAt,
                                        long version, long currency, QuestState questState,
                                        @Nullable SkillState skillState, @Nullable EquipmentState equipment,
                                        mn.suld.api.classgear.@Nullable ClassGear classGear,
                                        mn.suld.api.activity.@Nullable ActiveMinutes activeMinutes) {
        return new PlayerProfile(playerId, name, playerClass, progression, createdAt, lastSeenAt,
                version, currency, questState, skillState, equipment, classGear, activeMinutes);
    }

    public @NotNull UUID playerId() {
        return playerId;
    }

    public @NotNull Instant createdAt() {
        return createdAt;
    }

    public synchronized @NotNull String name() {
        return name;
    }

    public synchronized void name(String name) {
        Objects.requireNonNull(name, "name");
        if (!name.equals(this.name)) {
            this.name = name;
            touchInternal();
        }
    }

    public synchronized @NotNull Optional<PlayerClass> playerClass() {
        return Optional.ofNullable(playerClass);
    }

    public synchronized boolean hasSelectedClass() {
        return playerClass != null;
    }

    /**
     * Select the character's class. Classes are chosen once at character
     * creation; changing them is an administrative action, not a normal one.
     *
     * @return {@code true} if the class was set, {@code false} if one was
     *         already chosen (no change made)
     */
    public synchronized boolean selectClass(PlayerClass chosen) {
        Objects.requireNonNull(chosen, "chosen");
        if (this.playerClass != null) {
            return false;
        }
        this.playerClass = chosen;
        touchInternal();
        return true;
    }

    /** Administrative override of the class (e.g. an admin reset). */
    public synchronized void forceClass(@Nullable PlayerClass chosen) {
        this.playerClass = chosen;
        touchInternal();
    }

    public synchronized @NotNull Progression progression() {
        return progression;
    }

    public synchronized void progression(Progression progression) {
        this.progression = Objects.requireNonNull(progression, "progression");
        touchInternal();
    }

    public synchronized long currency() {
        return currency;
    }

    public synchronized void currency(long value) {
        this.currency = Math.max(0, value);
        touchInternal();
    }

    /** Add (or subtract, if negative) currency; never goes below zero. */
    public synchronized long addCurrency(long delta) {
        this.currency = Math.max(0, this.currency + delta);
        touchInternal();
        return this.currency;
    }

    public synchronized @NotNull QuestState questState() {
        return questState;
    }

    public synchronized void questState(QuestState questState) {
        this.questState = questState == null ? QuestState.NONE : questState;
        touchInternal();
    }

    public synchronized @NotNull SkillState skillState() {
        return skillState;
    }

    public synchronized void skillState(SkillState skillState) {
        this.skillState = skillState == null ? SkillState.NONE : skillState;
        touchInternal();
    }

    /** The accessory slots (the rest of the equipment is in the player's inventory). */
    public synchronized @NotNull EquipmentState equipment() {
        return equipment;
    }

    public synchronized void equipment(EquipmentState equipment) {
        this.equipment = equipment == null ? EquipmentState.NONE : equipment;
        touchInternal();
    }

    /** The class gear record (docs/CLASS_GEAR_SYSTEM.md): armour level, tier, enhancement, piece identities. */
    public synchronized mn.suld.api.classgear.@NotNull ClassGear classGear() {
        return classGear;
    }

    public synchronized void classGear(mn.suld.api.classgear.ClassGear classGear) {
        this.classGear = classGear == null ? mn.suld.api.classgear.ClassGear.NONE : classGear;
        touchInternal();
    }

    /** Validated active minutes per category (docs/ACTIVE_PLAYTIME_SPEC.md). */
    public synchronized mn.suld.api.activity.@NotNull ActiveMinutes activeMinutes() {
        return activeMinutes;
    }

    public synchronized void activeMinutes(mn.suld.api.activity.ActiveMinutes activeMinutes) {
        this.activeMinutes = activeMinutes == null ? mn.suld.api.activity.ActiveMinutes.NONE : activeMinutes;
        touchInternal();
    }

    public synchronized @NotNull Instant lastSeenAt() {
        return lastSeenAt;
    }

    /** Update the "last seen" timestamp without otherwise mutating the profile. */
    public synchronized void touch(Instant now) {
        this.lastSeenAt = Objects.requireNonNull(now, "now");
        this.dirty = true;
    }

    /** Monotonic version counter for optimistic concurrency control. */
    public synchronized long version() {
        return version;
    }

    /** Whether the profile has unsaved changes. */
    public synchronized boolean isDirty() {
        return dirty;
    }

    /** Everything that is stored, read under one lock so the row never mixes moments. */
    public record Snapshot(UUID playerId, String name, PlayerClass playerClass, Progression progression, Instant createdAt,
                           Instant lastSeenAt, long version, long currency, QuestState questState, SkillState skillState,
                           EquipmentState equipment, mn.suld.api.classgear.ClassGear classGear,
                           mn.suld.api.activity.ActiveMinutes activeMinutes) {
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(playerId, name, playerClass, progression, createdAt, lastSeenAt, version, currency, questState, skillState, equipment, classGear, activeMinutes);
    }

    /**
     * Mark the profile persisted at the given version. Called by the persistence layer after a successful write.
     * A change made after the snapshot was taken (a higher in-memory version) keeps the profile dirty and the
     * version where it is, so it is written by the next save instead of being forgotten.
     */
    public synchronized void markPersisted(long persistedVersion) {
        if (persistedVersion >= this.version) {
            this.version = persistedVersion;
            this.dirty = false;
        }
    }

    private void touchInternal() {
        this.version++;
        this.dirty = true;
    }

    @Override
    public String toString() {
        synchronized (this) {
            return "PlayerProfile{id=" + playerId
                    + ", name=" + name
                    + ", class=" + playerClass
                    + ", progression=" + progression
                    + ", version=" + version + '}';
        }
    }
}
