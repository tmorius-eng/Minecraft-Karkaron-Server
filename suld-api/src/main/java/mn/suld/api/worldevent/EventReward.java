package mn.suld.api.worldevent;

import java.util.UUID;

/** Reward for one qualifying participant. {@code rank} is 1-based by contribution. */
public record EventReward(UUID playerId, int rank, int contribution, long exp, long currency) {
}
