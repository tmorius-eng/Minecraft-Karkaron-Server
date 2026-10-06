package mn.suld.api.dungeon;

import java.time.Instant;
import java.util.UUID;

/**
 * Mutable in-memory state for one active dungeon run for a specific party.
 * Instances live only in the plugin's service layer; they are not persisted.
 *
 * <p>State machine:
 * <pre>
 *   ENTERING → WAVE (startWave) → WAVE ... → BOSS (enterBoss) → COMPLETE (complete)
 *                                                               → FAILED   (fail)
 * </pre>
 */
public final class DungeonRun {

    private final UUID runId;
    private final String dungeonId;
    private final UUID partyId;
    private final int totalWaves;

    private DungeonRunState state;
    private int currentWave;      // number of waves started so far (1-based while in WAVE)
    private int waveKillCount;    // mobs killed in current wave
    private int waveMobCount;     // total mobs to kill in current wave
    private final Instant startedAt;
    private Instant completedAt;

    public DungeonRun(String dungeonId, UUID partyId, int totalWaves) {
        this.runId = UUID.randomUUID();
        this.dungeonId = dungeonId;
        this.partyId = partyId;
        this.totalWaves = totalWaves;
        this.state = DungeonRunState.ENTERING;
        this.currentWave = 0;
        this.startedAt = Instant.now();
    }

    // --- Accessors ---

    public UUID runId() { return runId; }
    public String dungeonId() { return dungeonId; }
    public UUID partyId() { return partyId; }
    public DungeonRunState state() { return state; }
    public int currentWave() { return currentWave; }
    public int totalWaves() { return totalWaves; }
    public int waveKillCount() { return waveKillCount; }
    public int waveMobCount() { return waveMobCount; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }

    public boolean isActive() {
        return state == DungeonRunState.ENTERING
                || state == DungeonRunState.WAVE
                || state == DungeonRunState.BOSS;
    }

    public boolean isTerminal() {
        return state == DungeonRunState.COMPLETE || state == DungeonRunState.FAILED;
    }

    public boolean isOnLastWave() {
        return currentWave >= totalWaves;
    }

    // --- State transitions ---

    /**
     * Start (or advance to) the next wave.
     * @param mobCount number of mobs in this wave (so the run tracks kills)
     */
    public void startWave(int mobCount) {
        if (state != DungeonRunState.ENTERING && state != DungeonRunState.WAVE) {
            throw new IllegalStateException("Cannot start wave in state " + state);
        }
        state = DungeonRunState.WAVE;
        currentWave++;
        waveKillCount = 0;
        waveMobCount = mobCount;
    }

    /**
     * Record a mob kill in the current wave.
     * @return {@code true} if all wave mobs are now dead (wave is cleared)
     */
    public boolean recordWaveKill() {
        if (state != DungeonRunState.WAVE) return false;
        waveKillCount++;
        return waveKillCount >= waveMobCount;
    }

    /** Transition to the boss phase. */
    public void enterBoss() {
        if (state != DungeonRunState.WAVE && state != DungeonRunState.ENTERING) {
            throw new IllegalStateException("Cannot enter boss phase from state " + state);
        }
        state = DungeonRunState.BOSS;
    }

    /** Complete the dungeon run (boss defeated). */
    public void complete() {
        if (state != DungeonRunState.BOSS) {
            throw new IllegalStateException("Cannot complete from state " + state);
        }
        state = DungeonRunState.COMPLETE;
        completedAt = Instant.now();
    }

    /** Fail the dungeon run (party wiped or fled). */
    public void fail() {
        if (!isActive()) {
            throw new IllegalStateException("Cannot fail a terminal run");
        }
        state = DungeonRunState.FAILED;
        completedAt = Instant.now();
    }

    /** Elapsed seconds since the run started. */
    public long elapsedSeconds() {
        Instant end = completedAt != null ? completedAt : Instant.now();
        return end.getEpochSecond() - startedAt.getEpochSecond();
    }

    @Override
    public String toString() {
        return "DungeonRun{id=" + runId + ", dungeon=" + dungeonId + ", party=" + partyId
                + ", state=" + state + ", wave=" + currentWave + "/" + totalWaves + "}";
    }
}
