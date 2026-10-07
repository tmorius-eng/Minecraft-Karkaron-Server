package mn.suld.plugin.skill.qa;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.ResourcePool;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.SkillTree;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.combat.CombatFeedback;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.skill.SkillService;
import mn.suld.plugin.skill.SkillTreeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Plumbing of the QA suite: a tick-stepped sequencer, the sky arena with measurable dummies, and the helpers that put a
 * player in a known state. Nothing here changes game rules; it only builds the situations the measurements need.
 */
final class QaKit {

    interface Step {
        void run() throws Exception;
    }

    /** The largest max health an entity can have. Damage is measured by the listener below, not by missing health. */
    static final double DUMMY_HEALTH = 1024;

    final Plugin plugin;
    final SuldServices services;
    final SkillTreeService st;
    final SkillService skills;
    final Player p;
    final PlayerProfile pr;
    final World world;
    final Location origin;

    final List<LivingEntity> dummies = new ArrayList<>();
    /** Final damage each dummy has taken since it was last reset (what really arrived after every modifier). */
    final Map<UUID, Double> dealt = new HashMap<>();
    /** Damage events each dummy has taken since it was last reset. */
    final Map<UUID, Integer> events = new HashMap<>();

    /** Records the final damage of every hit on a dummy and tops the dummy up, so none can die or run dry. */
    final class DamageCounter implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDamage(EntityDamageEvent e) {
            if (!(e.getEntity() instanceof LivingEntity d) || !dummyIds.contains(d.getUniqueId())) return;
            dealt.merge(d.getUniqueId(), e.getFinalDamage(), Double::sum);
            events.merge(d.getUniqueId(), 1, Integer::sum);
            topUp(d);
        }
    }

    final java.util.Set<UUID> dummyIds = new java.util.HashSet<>();
    final DamageCounter counter = new DamageCounter();
    final double[][] slot;       // where each dummy stands (x, z relative to the origin)
    private final Deque<Object> steps = new ArrayDeque<>();
    private int waiting;
    private boolean running;
    private Runnable whenDone = () -> { };

    QaKit(Plugin plugin, SuldServices services, SkillTreeService st, SkillService skills, Player p, PlayerProfile pr, int dummyCount) {
        this.plugin = plugin;
        this.services = services;
        this.st = st;
        this.skills = skills;
        this.p = p;
        this.pr = pr;
        this.world = p.getWorld();
        this.origin = new Location(world, 3000.5, 200, 3000.5, 0f, 0f);
        this.slot = new double[dummyCount][2];
        Bukkit.getPluginManager().registerEvents(counter, plugin);
    }

    // ------------------------------------------------------------------ sequencer

    void add(Step s) {
        steps.add(s);
    }

    void wait(int ticks) {
        steps.add(Integer.valueOf(Math.max(1, ticks)));
    }

    /** Runs the queued steps one tick at a time; a step that throws is recorded and the run goes on. */
    void start(java.util.function.Consumer<Throwable> onError, Runnable done, Runnable onAbort) {
        this.whenDone = done;
        running = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    steps.clear();
                    cancel();
                    running = false;
                    onAbort.run();
                    return;
                }
                if (waiting > 0) {
                    waiting--;
                    return;
                }
                int budget = 400; // steps per tick before yielding
                while (budget-- > 0) {
                    Object next = steps.poll();
                    if (next == null) {
                        cancel();
                        running = false;
                        whenDone.run();
                        return;
                    }
                    if (next instanceof Integer t) {
                        waiting = t - 1;
                        return;
                    }
                    try {
                        ((Step) next).run();
                    } catch (Throwable ex) {
                        onError.accept(ex);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ arena

    /** A stone floor under the origin, high above any terrain, so every test starts on the same flat ground. */
    void buildArena() {
        for (int dx = -40; dx <= 40; dx += 16) {
            for (int dz = -40; dz <= 60; dz += 16) {
                int cx = (origin.getBlockX() + dx) >> 4, cz = (origin.getBlockZ() + dz) >> 4;
                world.getChunkAt(cx, cz);
                if (world.addPluginChunkTicket(cx, cz, plugin)) tickets.add(new int[]{cx, cz}); // keeps the arena (and the dummies) from unloading
            }
        }
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 60; z++) {
                world.getBlockAt(origin.getBlockX() + x, 199, origin.getBlockZ() + z).setType(Material.STONE, false);
                for (int y = 200; y <= 203; y++) world.getBlockAt(origin.getBlockX() + x, y, origin.getBlockZ() + z).setType(Material.AIR, false);
            }
        }
        CombatFeedback.quiet = true;
        // an open sky arena at night spawns real monsters that would kill the player and pollute every measurement
        spawnRule = world.getGameRuleValue(org.bukkit.GameRule.SPAWN_MONSTERS);
        world.setGameRule(org.bukkit.GameRule.SPAWN_MONSTERS, false);
        purgeMonsters();
    }

    private Boolean spawnRule;

    /** Removes every monster (not our dummies) near the arena. */
    void purgeMonsters() {
        for (var e : world.getNearbyEntities(origin, 80, 40, 80)) {
            if (e instanceof org.bukkit.entity.Monster m && !dummyIds.contains(m.getUniqueId())) m.remove();
        }
    }

    private final List<int[]> tickets = new ArrayList<>();

    void clearArena() {
        CombatFeedback.quiet = false;
        if (spawnRule != null) world.setGameRule(org.bukkit.GameRule.SPAWN_MONSTERS, spawnRule);
        for (int[] t : tickets) world.removePluginChunkTicket(t[0], t[1], plugin);
        tickets.clear();
        org.bukkit.event.HandlerList.unregisterAll(counter);
        for (LivingEntity d : dummies) d.remove();
        dummies.clear();
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 60; z++) world.getBlockAt(origin.getBlockX() + x, 199, origin.getBlockZ() + z).setType(Material.AIR, false);
        }
    }

    void spawnDummies() {
        for (int i = 0; i < slot.length; i++) {
            Location at = origin.clone().add(0, 0, 0);
            LivingEntity d = services.mobs().spawn(SuldContent.ORKHON_CHONO, at);
            dummies.add(d);
            dummyIds.add(d.getUniqueId());
        }
    }

    /** After the mob service has applied its own stats (one tick later): make the dummies inert and effectively unkillable. */
    void configureDummies() {
        for (LivingEntity d : dummies) {
            if (d instanceof Wolf w) w.setAngry(false);
            d.setAI(false);
            d.setCollidable(false);
            d.setSilent(true);
            d.setRemoveWhenFarAway(false);
            d.setCustomNameVisible(false);
            var max = d.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) max.setBaseValue(DUMMY_HEALTH);
            topUp(d);
            d.setMaximumNoDamageTicks(0);
            d.setNoDamageTicks(0);
        }
    }

    /** Stand dummy {@code i} at the layout position, healed, clean and still. */
    void place(int i, double x, double z) {
        slot[i][0] = x;
        slot[i][1] = z;
        LivingEntity d = dummies.get(i);
        d.teleport(origin.clone().add(x, 0, z));
        reset(d);
    }

    /**
     * Full health for a dummy. A wolf puts its max health back to the vanilla value whenever it feels like it (the mob
     * service re-applies SÜLD's after spawning only), so the cap is restored first.
     */
    void topUp(LivingEntity d) {
        var max = d.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && max.getBaseValue() != DUMMY_HEALTH) max.setBaseValue(DUMMY_HEALTH);
        d.setHealth(Math.min(DUMMY_HEALTH, max == null ? DUMMY_HEALTH : max.getValue()));
    }

    void reset(LivingEntity d) {
        dealt.put(d.getUniqueId(), 0.0);
        events.put(d.getUniqueId(), 0);
        topUp(d);
        d.setFireTicks(0);
        for (PotionEffect e : new ArrayList<>(d.getActivePotionEffects())) d.removePotionEffect(e.getType());
        d.setVelocity(new org.bukkit.util.Vector());
        d.setNoDamageTicks(0);
    }

    /** Out of every spell's reach but inside the ticketed arena (an unloaded chunk would remove the dummy). */
    void park(int i) {
        place(i, 20 + (i % 10) * 2, -38 + (i / 10) * 3);
    }

    void resetDummies() {
        for (int i = 0; i < dummies.size(); i++) {
            LivingEntity d = dummies.get(i);
            d.teleport(origin.clone().add(slot[i][0], 0, slot[i][1]));
            reset(d);
        }
        st.qaClearMarks();
    }

    /** Final damage the dummy has taken since its last reset. */
    double deficit(LivingEntity d) {
        return dealt.getOrDefault(d.getUniqueId(), 0.0);
    }

    // ------------------------------------------------------------------ player state

    double maxHealth() {
        var a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    /** Full health, no effects, full resource, at the origin facing +Z, every timer ready. */
    void resetPlayer() {
        // thousands of resets run inside one tick in the statistical checks: only touch what is out of place, because every
        // teleport or velocity change makes the client answer with packets (and a flood gets the player kicked)
        if (p.getFireTicks() > 0) p.setFireTicks(0);
        if (!p.getActivePotionEffects().isEmpty()) {
            for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        }
        if (p.getAbsorptionAmount() != 0) p.setAbsorptionAmount(0);
        p.setHealth(maxHealth());
        if (p.getFoodLevel() != 10) p.setFoodLevel(10); // below 18: no natural regeneration to pollute healing measurements
        p.setNoDamageTicks(0);
        if (p.getVelocity().lengthSquared() > 1e-4) p.setVelocity(new org.bukkit.util.Vector());
        Location now = p.getLocation();
        if (now.distanceSquared(origin) > 0.04 || Math.abs(now.getYaw()) > 0.5 || Math.abs(now.getPitch()) > 0.5) p.teleport(origin);
        ResourcePool pool = skills.pool(p);
        pool.gain(1e9);
        st.qaResetTimers(p);
    }

    void emptyPool() {
        ResourcePool pool = skills.pool(p);
        pool.spend(pool.value());
    }

    /** Sets the learned ranks directly (the graph rules are unit-tested; here only the effect of the nodes matters). */
    void ranks(String encoded) {
        pr.skillState(pr.skillState().withRanks(encoded));
        st.qaRefresh(p);
    }

    void learn(SkillNode n) {
        ranks(n.id() + "=" + n.maxRank());
    }

    void unlearn() {
        ranks("");
    }

    void setClass(mn.suld.api.clazz.PlayerClass c) {
        pr.forceClass(c);
        st.attach(p);
        skills.rebuildPool(p);
    }

    SkillTree tree() {
        return st.tree(p);
    }

    /** One player hit on a dummy through the normal damage pipeline; returns the health it lost. */
    double hit(LivingEntity d) {
        d.setNoDamageTicks(0);
        double before = deficit(d);
        d.damage(1.0, p);
        return deficit(d) - before;
    }

    /** One hit on the player from a mob, through the normal damage pipeline; returns the health lost. */
    double hurtPlayer(double amount, LivingEntity source) {
        p.setNoDamageTicks(0);
        double before = p.getHealth() + p.getAbsorptionAmount();
        p.damage(amount, source);
        return before - (p.getHealth() + p.getAbsorptionAmount());
    }
}
