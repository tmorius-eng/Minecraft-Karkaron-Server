package mn.suld.api.persistence;

import mn.suld.api.quest.SideQuestRecord;
import mn.suld.api.quest.SideQuestStatus;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Side quests in memory (tests, and storage type "memory"). */
public final class InMemorySideQuestRepository implements SideQuestRepository {

    private final Map<UUID, Map<String, SideQuestRecord>> rows = new HashMap<>();

    @Override
    public synchronized CompletableFuture<Map<String, SideQuestRecord>> load(UUID player) {
        return CompletableFuture.completedFuture(new LinkedHashMap<>(rows.getOrDefault(player, Map.of())));
    }

    @Override
    public synchronized CompletableFuture<Boolean> start(UUID player, String questId, long now) {
        Map<String, SideQuestRecord> mine = rows.computeIfAbsent(player, k -> new LinkedHashMap<>());
        if (mine.containsKey(questId)) return CompletableFuture.completedFuture(false);
        mine.put(questId, SideQuestRecord.started(questId, now));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Void> progress(UUID player, String questId, int progress) {
        SideQuestRecord r = rows.getOrDefault(player, Map.of()).get(questId);
        if (r != null && r.active()) rows.get(player).put(questId, r.withProgress(progress));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Boolean> complete(UUID player, String questId, int progress, long now) {
        SideQuestRecord r = rows.getOrDefault(player, Map.of()).get(questId);
        if (r == null || r.status() != SideQuestStatus.ACTIVE) return CompletableFuture.completedFuture(false);
        rows.get(player).put(questId, r.handedIn(progress, now));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Boolean> claimReward(UUID player, String questId, long now) {
        SideQuestRecord r = rows.getOrDefault(player, Map.of()).get(questId);
        if (r == null || !r.owed()) return CompletableFuture.completedFuture(false);
        rows.get(player).put(questId, r.paid(now));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Void> unclaimReward(UUID player, String questId, long claimedAt) {
        SideQuestRecord r = rows.getOrDefault(player, Map.of()).get(questId);
        if (r != null && r.done() && r.paidAt() == claimedAt) rows.get(player).put(questId, r.paid(0));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Boolean> abandon(UUID player, String questId) {
        Map<String, SideQuestRecord> mine = rows.get(player);
        SideQuestRecord r = mine == null ? null : mine.get(questId);
        if (r == null || !r.active()) return CompletableFuture.completedFuture(false);
        mine.remove(questId);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Boolean> reset(UUID player, String questId) {
        Map<String, SideQuestRecord> mine = rows.get(player);
        return CompletableFuture.completedFuture(mine != null && mine.remove(questId) != null);
    }
}
