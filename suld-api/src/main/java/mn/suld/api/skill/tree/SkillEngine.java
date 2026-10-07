package mn.suld.api.skill.tree;

import mn.suld.api.profile.PlayerProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The skill-tree operations on a player's profile: points, unlock, refund, reset, builds. Pure (no Bukkit),
 * so every rule is unit-tested; the plugin only supplies the progression context and the side effects.
 */
public final class SkillEngine {

    private SkillEngine() {
    }

    /** What the economy reads from the rest of the game. */
    public record Context(int level, int finishedChapters, int discoveredRegions) {
    }

    public static final Pattern BUILD_NAME = Pattern.compile("[\\p{L}\\p{N}_-]{1,16}");

    public enum Outcome { OK, BAD_NAME, NO_SLOT, NOT_FOUND, INVALID, COOLDOWN, NOTHING }

    public record Result(Outcome outcome, String detail) {
        public boolean ok() { return outcome == Outcome.OK; }
    }

    // ------------------------------------------------------------------ points

    public static SkillAllocation allocation(PlayerProfile p, SkillTree tree) {
        return SkillAllocation.decode(tree, p.skillState().ranks()).allocation();
    }

    public static int total(PlayerProfile p, Context c) {
        return SkillPoints.total(c.level(), c.finishedChapters(), c.discoveredRegions(), p.skillState().granted());
    }

    public static int spent(PlayerProfile p, SkillTree tree) {
        return allocation(p, tree).spent();
    }

    public static int available(PlayerProfile p, SkillTree tree, Context c) {
        return Math.max(0, total(p, c) - spent(p, tree));
    }

    // ------------------------------------------------------------------ nodes

    public static SkillAllocation.Check canUnlock(PlayerProfile p, SkillTree tree, SkillNode n, Context c) {
        return allocation(p, tree).canUnlock(n, c.level(), available(p, tree, c));
    }

    /** Spend points on one rank of {@code n}; the profile changes only when the check passes. */
    public static SkillAllocation.Check unlock(PlayerProfile p, SkillTree tree, SkillNode n, Context c) {
        synchronized (p) {
            SkillAllocation a = allocation(p, tree);
            SkillAllocation.Check check = a.canUnlock(n, c.level(), Math.max(0, total(p, c) - a.spent()));
            if (check.ok()) store(p, a.plusRank(n), "");
            return check;
        }
    }

    public static SkillAllocation.Check refund(PlayerProfile p, SkillTree tree, SkillNode n) {
        synchronized (p) {
            SkillAllocation a = allocation(p, tree);
            SkillAllocation.Check check = a.canRefund(n);
            if (check.ok()) store(p, a.minusRank(n), "");
            return check;
        }
    }

    /** Administrator unlock: ignores points and level but still keeps the build consistent (path, rival, requirements). */
    public static SkillAllocation.Check forceUnlock(PlayerProfile p, SkillTree tree, SkillNode n) {
        synchronized (p) {
            SkillAllocation a = allocation(p, tree);
            SkillAllocation.Check check = a.canUnlock(n, 1000, Integer.MAX_VALUE);
            if (check.ok()) store(p, a.plusRank(n), "");
            return check;
        }
    }

    private static void store(PlayerProfile p, SkillAllocation a, String keepActive) {
        SkillState s = p.skillState();
        // editing the tree leaves the saved build it started from: the HUD must not claim a build that no longer matches
        p.skillState(s.withRanks(a.encode()).withBuilds(s.builds(), keepActive));
    }

    // ------------------------------------------------------------------ reset

    public static int respecWaitSeconds(PlayerProfile p, long now, long cooldownMs) {
        long left = p.skillState().lastRespec() + cooldownMs - now;
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    /** Refund everything (the profile's level, EXP and coins are never touched). */
    public static Result resetAll(PlayerProfile p, SkillTree tree, long now, long cooldownMs) {
        synchronized (p) {
            if (allocation(p, tree).spent() == 0) return new Result(Outcome.NOTHING, "");
            int wait = respecWaitSeconds(p, now, cooldownMs);
            if (wait > 0) return new Result(Outcome.COOLDOWN, String.valueOf(wait));
            SkillState s = p.skillState();
            p.skillState(s.withRanks("").withBuilds(s.builds(), "").withRespec(now));
            return new Result(Outcome.OK, "");
        }
    }

    public static Result resetCategory(PlayerProfile p, SkillTree tree, SkillCategory category, long now, long cooldownMs) {
        synchronized (p) {
            SkillAllocation a = allocation(p, tree);
            SkillAllocation after = a.withoutCategory(category);
            if (after.spent() == a.spent()) return new Result(Outcome.NOTHING, "");
            int wait = respecWaitSeconds(p, now, cooldownMs);
            if (wait > 0) return new Result(Outcome.COOLDOWN, String.valueOf(wait));
            SkillState s = p.skillState();
            p.skillState(s.withRanks(after.encode()).withBuilds(s.builds(), "").withRespec(now));
            return new Result(Outcome.OK, String.valueOf(a.spent() - after.spent()));
        }
    }

    /** Remove nodes the player no longer qualifies for (lost levels, edited data files). Returns refunded points. */
    public static int normalise(PlayerProfile p, SkillTree tree, Context c) {
        synchronized (p) {
            SkillAllocation.Decoded d = SkillAllocation.decode(tree, p.skillState().ranks());
            SkillAllocation fixed = d.allocation().trimmed(c.level(), total(p, c));
            String encoded = fixed.encode();
            if (encoded.equals(p.skillState().ranks()) && d.dropped().isEmpty()) return 0;
            int refunded = d.allocation().spent() - fixed.spent();
            p.skillState(p.skillState().withRanks(encoded));
            return Math.max(0, refunded);
        }
    }

    // ------------------------------------------------------------------ builds

    public static Result saveBuild(PlayerProfile p, SkillTree tree, String name, int maxSlots) {
        synchronized (p) {
            if (name == null || !BUILD_NAME.matcher(name).matches()) return new Result(Outcome.BAD_NAME, "");
            SkillState s = p.skillState();
            if (!s.builds().containsKey(name) && s.builds().size() >= maxSlots) return new Result(Outcome.NO_SLOT, String.valueOf(maxSlots));
            Map<String, String> next = new LinkedHashMap<>(s.builds());
            next.put(name, allocation(p, tree).encode());
            p.skillState(s.withBuilds(next, name));
            return new Result(Outcome.OK, name);
        }
    }

    public static Result deleteBuild(PlayerProfile p, String name) {
        synchronized (p) {
            SkillState s = p.skillState();
            if (!s.builds().containsKey(name)) return new Result(Outcome.NOT_FOUND, name);
            Map<String, String> next = new LinkedHashMap<>(s.builds());
            next.remove(name);
            p.skillState(s.withBuilds(next, s.activeBuild().equals(name) ? "" : s.activeBuild()));
            return new Result(Outcome.OK, name);
        }
    }

    /**
     * Switch to a saved build. It must still be valid for the current tree (nodes exist, connected, requirements and
     * rivals respected) and affordable at the current level and points; otherwise nothing changes. Loading is a
     * respec and so shares its cooldown, except when switching to an identical build.
     */
    public static Result loadBuild(PlayerProfile p, SkillTree tree, String name, Context c, long now, long cooldownMs) {
        synchronized (p) {
            SkillState s = p.skillState();
            String stored = s.builds().get(name);
            if (stored == null) return new Result(Outcome.NOT_FOUND, name);
            SkillAllocation.Decoded d = SkillAllocation.decode(tree, stored);
            if (!d.dropped().isEmpty()) return new Result(Outcome.INVALID, "unknown nodes: " + String.join(", ", d.dropped()));
            SkillAllocation a = d.allocation();
            SkillNode bad = a.firstInconsistent();
            if (bad != null) return new Result(Outcome.INVALID, "broken path at " + bad.id());
            if (!a.fits(c.level(), total(p, c))) return new Result(Outcome.INVALID, "not affordable at this level");
            boolean same = a.encode().equals(allocation(p, tree).encode());
            if (!same) {
                int wait = respecWaitSeconds(p, now, cooldownMs);
                if (wait > 0) return new Result(Outcome.COOLDOWN, String.valueOf(wait));
            }
            SkillState next = s.withRanks(a.encode()).withBuilds(s.builds(), name);
            p.skillState(same ? next : next.withRespec(now));
            return new Result(Outcome.OK, name);
        }
    }

    public static List<String> buildNames(PlayerProfile p) {
        return List.copyOf(p.skillState().builds().keySet());
    }

    // ------------------------------------------------------------------ admin

    public static void grant(PlayerProfile p, int amount) {
        synchronized (p) {
            SkillState s = p.skillState();
            p.skillState(s.withGranted(Math.max(0, s.granted() + amount)));
        }
    }
}
