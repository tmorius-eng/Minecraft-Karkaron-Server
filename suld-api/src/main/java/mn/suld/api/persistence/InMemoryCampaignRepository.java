package mn.suld.api.persistence;

import mn.suld.api.campaign.CampaignSnapshot;
import mn.suld.api.campaign.Contribution;
import mn.suld.api.campaign.WorldFlag;
import mn.suld.api.campaign.vote.VoteCycle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Volatile campaign storage for MEMORY mode and tests. Synchronised: the contract is the same as the JDBC one. */
public final class InMemoryCampaignRepository implements CampaignRepository {

    private final Map<String, VoteCycle> cycles = new LinkedHashMap<>();
    private final Map<String, Map<UUID, String>> ballots = new HashMap<>();
    private final Map<String, CampaignSnapshot> campaigns = new LinkedHashMap<>();
    private final Map<String, Map<UUID, Contribution>> totals = new HashMap<>();
    private final Map<String, WorldFlag> flags = new LinkedHashMap<>();

    @Override
    public synchronized CompletableFuture<Optional<VoteCycle>> voteCycle(String cycleId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(cycles.get(cycleId)));
    }

    @Override
    public synchronized CompletableFuture<VoteCycle> openVote(String cycleId, List<String> options, long opensAt, long closesAt) {
        VoteCycle c = cycles.computeIfAbsent(cycleId, id -> new VoteCycle(id, options, opensAt, closesAt, null, null, Map.of(), 0, 0));
        return CompletableFuture.completedFuture(c);
    }

    @Override
    public synchronized CompletableFuture<CastResult> castVote(String cycleId, UUID player, String option, int level, long at) {
        VoteCycle c = cycles.get(cycleId);
        if (c == null) return CompletableFuture.completedFuture(CastResult.NO_CYCLE);
        if (c.closed()) return CompletableFuture.completedFuture(CastResult.CLOSED);
        if (!c.options().contains(option)) return CompletableFuture.completedFuture(CastResult.NO_SUCH_OPTION);
        Map<UUID, String> b = ballots.computeIfAbsent(cycleId, k -> new HashMap<>());
        if (b.putIfAbsent(player, option) != null) return CompletableFuture.completedFuture(CastResult.ALREADY_VOTED);
        return CompletableFuture.completedFuture(CastResult.CAST);
    }

    @Override
    public synchronized CompletableFuture<Boolean> reopenVote(String cycleId, List<String> options, long opensAt, long closesAt, long expectedVersion) {
        VoteCycle c = cycles.get(cycleId);
        if (c == null || c.version() != expectedVersion) return CompletableFuture.completedFuture(false);
        cycles.put(cycleId, new VoteCycle(cycleId, options, opensAt, closesAt, null, null, Map.of(), 0, c.version() + 1));
        ballots.remove(cycleId);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Map<String, Integer>> tally(String cycleId) {
        Map<String, Integer> out = new HashMap<>();
        ballots.getOrDefault(cycleId, Map.of()).values().forEach(o -> out.merge(o, 1, Integer::sum));
        return CompletableFuture.completedFuture(out);
    }

    @Override
    public synchronized CompletableFuture<Boolean> closeVote(String cycleId, String winner, Map<String, Integer> tally, long closedAt, long expectedVersion) {
        VoteCycle c = cycles.get(cycleId);
        if (c == null || c.version() != expectedVersion || c.closed()) return CompletableFuture.completedFuture(false);
        int voters = tally.values().stream().mapToInt(Integer::intValue).sum();
        cycles.put(cycleId, new VoteCycle(cycleId, c.options(), c.opensAt(), c.closesAt(), closedAt, winner, tally, voters, c.version() + 1));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<List<VoteCycle>> recentVotes(int limit) {
        List<VoteCycle> all = new ArrayList<>(cycles.values());
        all.sort(Comparator.comparing(VoteCycle::cycleId).reversed());
        return CompletableFuture.completedFuture(all.subList(0, Math.min(limit, all.size())));
    }

    @Override
    public synchronized CompletableFuture<Optional<CampaignSnapshot>> campaign(String cycleId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(campaigns.get(cycleId)));
    }

    @Override
    public synchronized CompletableFuture<Boolean> saveCampaign(CampaignSnapshot s, long expectedVersion) {
        CampaignSnapshot cur = campaigns.get(s.cycleId());
        if (expectedVersion < 0) {
            if (cur != null) return CompletableFuture.completedFuture(false);
            campaigns.put(s.cycleId(), new CampaignSnapshot(s.cycleId(), s.campaignId(), s.state(), s.startsAt(), s.endsAt(), s.resolvedAt(),
                    s.target(), s.total(), s.effectAppliedAt(), 0));
            return CompletableFuture.completedFuture(true);
        }
        if (cur == null || cur.version() != expectedVersion) return CompletableFuture.completedFuture(false);
        campaigns.put(s.cycleId(), new CampaignSnapshot(s.cycleId(), s.campaignId(), s.state(), s.startsAt(), s.endsAt(), s.resolvedAt(),
                s.target(), cur.total(), s.effectAppliedAt(), cur.version() + 1)); // the total is owned by addContributions
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Boolean> replaceCampaign(CampaignSnapshot s, long expectedVersion) {
        CampaignSnapshot cur = campaigns.get(s.cycleId());
        if (cur == null || cur.version() != expectedVersion) return CompletableFuture.completedFuture(false);
        campaigns.put(s.cycleId(), new CampaignSnapshot(s.cycleId(), s.campaignId(), s.state(), s.startsAt(), s.endsAt(), s.resolvedAt(),
                s.target(), s.total(), s.effectAppliedAt(), cur.version() + 1));
        totals.remove(s.cycleId());
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletableFuture<Void> addContributions(String cycleId, List<Contribution> deltas) {
        Map<UUID, Contribution> t = totals.computeIfAbsent(cycleId, k -> new HashMap<>());
        long sum = 0;
        for (Contribution d : deltas) {
            Contribution cur = t.get(d.player());
            t.put(d.player(), cur == null ? new Contribution(d.player(), d.name(), d.clan(), d.units(), null, null)
                    : new Contribution(d.player(), d.name(), d.clan(), cur.units() + d.units(), cur.rewardTier(), cur.claimedAt()));
            sum += d.units();
        }
        CampaignSnapshot c = campaigns.get(cycleId);
        if (c != null) campaigns.put(cycleId, c.withTotal(c.total() + sum));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<List<Contribution>> contributions(String cycleId) {
        List<Contribution> out = new ArrayList<>(totals.getOrDefault(cycleId, Map.of()).values());
        out.sort(Comparator.comparingLong(Contribution::units).reversed().thenComparing(Contribution::name));
        return CompletableFuture.completedFuture(out);
    }

    @Override
    public synchronized CompletableFuture<Void> markReward(String cycleId, UUID player, int tier, Long claimedAt) {
        Map<UUID, Contribution> t = totals.computeIfAbsent(cycleId, k -> new HashMap<>());
        Contribution cur = t.get(player);
        if (cur != null) t.put(player, new Contribution(cur.player(), cur.name(), cur.clan(), cur.units(), tier, claimedAt));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<List<CampaignSnapshot>> recentCampaigns(int limit) {
        List<CampaignSnapshot> all = new ArrayList<>(campaigns.values());
        all.sort(Comparator.comparing(CampaignSnapshot::cycleId).reversed());
        return CompletableFuture.completedFuture(all.subList(0, Math.min(limit, all.size())));
    }

    @Override
    public synchronized CompletableFuture<Void> setFlag(WorldFlag flag) {
        flags.put(flag.key(), flag);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<Void> clearFlag(String key) {
        flags.remove(key);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized CompletableFuture<List<WorldFlag>> flags() {
        return CompletableFuture.completedFuture(new ArrayList<>(flags.values()));
    }
}
