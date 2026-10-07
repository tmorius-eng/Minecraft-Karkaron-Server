package mn.suld.plugin.skill;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.event.ClassSelectedEvent;
import mn.suld.api.event.LevelUpEvent;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.ResourcePool;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.KeystoneKind;
import mn.suld.api.skill.tree.SkillAllocation;
import mn.suld.api.skill.tree.SkillBuild;
import mn.suld.api.skill.tree.SkillEngine;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.SkillTree;
import mn.suld.api.skill.tree.SkillTreeLoader;
import mn.suld.api.skill.tree.StatKey;
import mn.suld.api.skill.tree.TriggerEvent;
import mn.suld.api.skill.tree.Ultimate;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.combat.CombatListener;
import mn.suld.plugin.event.SuldDomainBukkitEvent;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The skill-tree runtime. Every player's tree state lives in their {@link PlayerProfile} (UUID keyed, persisted with
 * it); this service caches the derived {@link SkillBuild} per online player (rebuilt only when the tree changes),
 * applies the stat bonuses as attribute modifiers, and runs the event-driven effects: passive procs, keystones,
 * ultimates, damage taken, healing and loot/EXP bonuses. Nothing here runs per tick for every player.
 */
public final class SkillTreeService implements Listener {

    private static final String[] FILES = {"universal", "baatar", "mergen", "boo", "darkhan", "khulegchin"};

    /** Per-player cached state (main thread only). */
    private static final class Runtime {
        PlayerClass clazz;
        SkillTree tree;
        SkillAllocation allocation;
        SkillBuild build = SkillBuild.EMPTY;
        final Map<Integer, Long> procReady = new HashMap<>();
        long ultReady;
        long shieldToken;
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final Path dataDir;
    private volatile Map<PlayerClass, SkillTree> trees = new EnumMap<>(PlayerClass.class);
    private volatile List<SkillTreeLoader.Issue> issues = List.of();
    private final Map<UUID, Runtime> runtime = new ConcurrentHashMap<>();
    /** Enemies marked to take extra damage: entity id -> {until, percent}. */
    private final Map<UUID, double[]> marks = new ConcurrentHashMap<>();
    private SkillService skills;
    private Ultimates ultimates;
    /** True while a proc is dealing its own damage: that damage must not trigger more procs. */
    private boolean dispatching;

    public SkillTreeService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.dataDir = plugin.getDataFolder().toPath().resolve("skills");
    }

    public void skills(SkillService skills) {
        this.skills = skills;
        this.ultimates = new Ultimates(plugin, services, skills, this);
    }

    // ------------------------------------------------------------------ data files

    /** Writes the bundled data files that the server folder does not have yet, so admins can edit them. */
    private void extractDefaults() {
        try {
            Files.createDirectories(dataDir);
            for (String name : FILES) {
                Path target = dataDir.resolve(name + ".json");
                if (Files.exists(target)) continue;
                try (InputStream in = SkillTreeService.class.getResourceAsStream("/skills/" + name + ".json")) {
                    if (in == null) throw new IOException("bundled skills/" + name + ".json is missing from the jar");
                    Files.write(target, in.readAllBytes());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Cannot prepare " + dataDir + ": " + e.getMessage(), e);
        }
    }

    /**
     * (Re)load the data files from the server folder. A class whose files have problems keeps its previous tree (or
     * the bundled one at startup); every problem is logged with file and field and returned.
     */
    public List<SkillTreeLoader.Issue> reload() {
        extractDefaults();
        SkillTreeLoader.Result fromDisk = SkillTreeLoader.loadAll(SkillTreeLoader.directory(dataDir));
        Map<PlayerClass, SkillTree> next = new EnumMap<>(PlayerClass.class);
        SkillTreeLoader.Result bundled = null;
        for (PlayerClass c : PlayerClass.values()) {
            SkillTree t = fromDisk.trees().get(c);
            if (t == null) t = trees.get(c);
            if (t == null) {
                if (bundled == null) bundled = SkillTreeLoader.loadAll(SkillTreeLoader.classpath());
                t = bundled.trees().get(c);
            }
            if (t == null) throw new IllegalStateException("no skill tree available for " + c + " (bundled data is broken)");
            next.put(c, t);
        }
        trees = next;
        issues = List.copyOf(fromDisk.issues());
        for (SkillTreeLoader.Issue i : issues) plugin.getLogger().severe("[skills] " + i);
        int nodes = next.values().stream().mapToInt(t -> t.nodes().size() - 1).sum();
        plugin.getLogger().info("[skills] " + nodes + " nodes loaded for " + next.size() + " classes"
                + (issues.isEmpty() ? "" : " — " + issues.size() + " problem(s), see above"));
        for (Player p : Bukkit.getOnlinePlayers()) attach(p);
        return issues;
    }

    /** Validate the server's data files without applying them (what /skillsadmin validate reports). */
    public List<SkillTreeLoader.Issue> validate() {
        extractDefaults();
        return SkillTreeLoader.loadAll(SkillTreeLoader.directory(dataDir)).issues();
    }

    public List<SkillTreeLoader.Issue> issues() {
        return issues;
    }

    public void start() {
        reload();
    }

    public void stop() {
        for (Player p : Bukkit.getOnlinePlayers()) clearAttributes(p);
        runtime.clear();
    }

    public Path dataDir() {
        return dataDir;
    }

    public SkillTree tree(PlayerClass c) {
        return trees.get(c);
    }

    // ------------------------------------------------------------------ per-player state

    private PlayerProfile profile(Player p) {
        return services.profiles().cached(p.getUniqueId()).orElse(null);
    }

    public SkillTree tree(Player p) {
        PlayerProfile pr = profile(p);
        return pr == null || pr.playerClass().isEmpty() ? null : trees.get(pr.playerClass().get());
    }

    public SkillEngine.Context context(Player p) {
        PlayerProfile pr = profile(p);
        int level = pr == null ? 1 : pr.progression().level();
        int chapters = 0;
        if (pr != null && !pr.questState().questId().isEmpty()) {
            int idx = services.quests().chain().indexOf(pr.questState().questId());
            if (idx >= 0) chapters = idx + (pr.questState().completed() ? 1 : 0);
        }
        int discovered = services.styles().cached(p.getUniqueId()).map(s -> Long.bitCount(s.discovered())).orElse(0);
        return new SkillEngine.Context(level, chapters, discovered);
    }

    public int total(Player p) {
        PlayerProfile pr = profile(p);
        return pr == null ? 0 : SkillEngine.total(pr, context(p));
    }

    public int spent(Player p) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        return pr == null || t == null ? 0 : SkillEngine.spent(pr, t);
    }

    public int available(Player p) {
        return Math.max(0, total(p) - spent(p));
    }

    public SkillAllocation allocation(Player p) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        return pr == null || t == null ? null : SkillEngine.allocation(pr, t);
    }

    /** The active build of an online player (empty for players without a class). */
    public SkillBuild build(Player p) {
        Runtime r = runtime.get(p.getUniqueId());
        return r == null ? SkillBuild.EMPTY : r.build;
    }

    public static SkillBuild build(SuldServices services, Player p) {
        SkillTreeService s = services.skillTree();
        return s == null ? SkillBuild.EMPTY : s.build(p);
    }

    /** Bind a joined (or re-classed, or reloaded) player: normalise the stored build, cache it, apply its stats. */
    public void attach(Player p) {
        PlayerProfile pr = profile(p);
        if (pr == null || pr.playerClass().isEmpty()) {
            runtime.remove(p.getUniqueId());
            clearAttributes(p);
            return;
        }
        SkillTree t = trees.get(pr.playerClass().get());
        int refunded = SkillEngine.normalise(pr, t, context(p));
        if (refunded > 0) p.sendMessage(Messages.info("Чадварын мод шинэчлэгдлээ: " + refunded + " оноо буцаагдлаа."));
        Runtime r = runtime.computeIfAbsent(p.getUniqueId(), k -> new Runtime());
        r.clazz = pr.playerClass().get();
        r.tree = t;
        refreshRuntime(p, pr, r);
    }

    private void refreshRuntime(Player p, PlayerProfile pr, Runtime r) {
        r.allocation = SkillEngine.allocation(pr, r.tree);
        r.build = r.allocation.build();
        applyAttributes(p, r.build);
        // resource pool size follows the build
        skills.rebuildPool(p);
    }

    /** Call after any change to a player's tree: refresh derived state, save, update the HUD. */
    public void afterChange(Player p) {
        PlayerProfile pr = profile(p);
        Runtime r = runtime.get(p.getUniqueId());
        if (pr == null || r == null) return;
        refreshRuntime(p, pr, r);
        services.profiles().save(pr);
        services.hud().update(p, pr);
        skills.actionBar(p);
    }

    // ------------------------------------------------------------------ attributes

    private static NamespacedKey key(String id) {
        return new NamespacedKey("suld", "skill_" + id);
    }

    private static void setModifier(Player p, Attribute attribute, String id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        NamespacedKey k = key(id);
        AttributeModifier old = inst.getModifier(k);
        if (old != null) inst.removeModifier(old);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(k, amount, op));
    }

    private void applyAttributes(Player p, SkillBuild b) {
        double health = b.stat(StatKey.HEALTH);
        AttributeInstance maxHp = p.getAttribute(Attribute.MAX_HEALTH);
        double base = maxHp == null ? 20 : maxHp.getBaseValue();
        if (b.has(KeystoneKind.TENGERTEI_KHOLBOGDOKH)) health -= (base + health) * 0.25;
        setModifier(p, Attribute.MAX_HEALTH, "health", health, AttributeModifier.Operation.ADD_NUMBER);
        double move = b.stat(StatKey.MOVE_PCT) / 100.0;
        if (b.has(KeystoneKind.TALYN_SALKHI)) move -= 0.10;
        setModifier(p, Attribute.MOVEMENT_SPEED, "move", move, AttributeModifier.Operation.ADD_SCALAR);
        setModifier(p, Attribute.ARMOR, "armor", b.stat(StatKey.ARMOR), AttributeModifier.Operation.ADD_NUMBER);
        setModifier(p, Attribute.KNOCKBACK_RESISTANCE, "knockback", Math.min(1.0, b.stat(StatKey.KB_RESIST) / 100.0), AttributeModifier.Operation.ADD_NUMBER);
        setModifier(p, Attribute.BLOCK_BREAK_SPEED, "mining", b.stat(StatKey.MINING_SPEED_PCT) / 100.0, AttributeModifier.Operation.ADD_SCALAR);
        AttributeInstance now = p.getAttribute(Attribute.MAX_HEALTH);
        if (now != null && p.getHealth() > now.getValue()) p.setHealth(now.getValue());
    }

    private void clearAttributes(Player p) {
        setModifier(p, Attribute.MAX_HEALTH, "health", 0, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(p, Attribute.MOVEMENT_SPEED, "move", 0, AttributeModifier.Operation.ADD_SCALAR);
        setModifier(p, Attribute.ARMOR, "armor", 0, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(p, Attribute.KNOCKBACK_RESISTANCE, "knockback", 0, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(p, Attribute.BLOCK_BREAK_SPEED, "mining", 0, AttributeModifier.Operation.ADD_SCALAR);
        setModifier(p, Attribute.MAX_ABSORPTION, "shield", 0, AttributeModifier.Operation.ADD_NUMBER);
    }

    // ------------------------------------------------------------------ join / quit / progression events

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        attach(p);
        // the intro runs a little later so it is not buried by the join messages
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            PlayerProfile pr = profile(p);
            if (pr == null || pr.playerClass().isEmpty()) return;
            int avail = available(p);
            if (spent(p) == 0 && avail > 0) {
                p.sendMessage(Messages.accent("◆ Танд " + avail + " чадварын оноо байна — /skills эсвэл /tree гэж бичиж чадварын газрын зургаа нээ!"));
            } else if (avail > 0) {
                p.sendMessage(Messages.info("◆ " + avail + " зарцуулаагүй чадварын оноо — /skills"));
            }
        }, 80L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        runtime.remove(id);
        marks.remove(id);
        if (ultimates != null) ultimates.forget(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        // a respawned player entity starts with fresh attributes: put the build's bonuses back
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) attach(p);
        }, 2L);
    }

    @EventHandler
    public void onDomain(SuldDomainBukkitEvent e) {
        if (e.payload() instanceof ClassSelectedEvent ev) {
            Player p = Bukkit.getPlayer(ev.player());
            if (p != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    attach(p);
                    p.sendMessage(Messages.accent("◆ Чадварын мод нээгдлээ: түвшин ахих бүрт, түүхийн эрэл дуусгаж, шинэ газар нээхэд оноо ирнэ."));
                    p.sendMessage(Messages.info("Оноогоо /skills (газрын зураг) дээр зарцуулж, шид болон идэвхгүй чадвараа сонго. Улаан холбоос = зөвхөн нэгийг сонгоно."));
                });
            }
        } else if (e.payload() instanceof LevelUpEvent ev) {
            Player p = Bukkit.getPlayer(ev.player());
            if (p == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                int gained = mn.suld.api.skill.tree.SkillPoints.forLevel(ev.toLevel()) - mn.suld.api.skill.tree.SkillPoints.forLevel(ev.fromLevel());
                if (gained > 0 && tree(p) != null) {
                    p.sendMessage(Messages.accent("◆ +" + gained + " чадварын оноо! (нийт " + available(p) + " зарцуулаагүй) — /skills"));
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
                }
                services.hud().update(p, profile(p));
            });
        }
    }

    // ------------------------------------------------------------------ changes made by the player

    public SkillAllocation.Check unlock(Player p, SkillNode n) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        if (pr == null || t == null) return new SkillAllocation.Check(SkillAllocation.Why.NOT_CONNECTED, n, null, 0);
        SkillAllocation.Check c = SkillEngine.unlock(pr, t, n, context(p));
        if (c.ok()) {
            afterChange(p);
            p.playSound(p.getLocation(), n.keystone() ? Sound.BLOCK_BEACON_ACTIVATE : Sound.ENTITY_PLAYER_LEVELUP, 0.7f, n.keystone() ? 1.2f : 1.7f);
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
        }
        return c;
    }

    public SkillAllocation.Check refund(Player p, SkillNode n) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        if (pr == null || t == null) return new SkillAllocation.Check(SkillAllocation.Why.NOT_UNLOCKED, n, null, 0);
        SkillAllocation.Check c = SkillEngine.refund(pr, t, n);
        if (c.ok()) {
            afterChange(p);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.6f);
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
        }
        return c;
    }

    // respec policy -----------------------------------------------------------------------------------------------

    public long respecCooldownMs() {
        return plugin.getConfig().getLong("skills.respec.cooldown-seconds", 300) * 1000L;
    }

    public int buildSlots() {
        return Math.max(1, plugin.getConfig().getInt("skills.builds.slots", 3));
    }

    public int freeResetLevel() {
        return plugin.getConfig().getInt("skills.respec.free-level", mn.suld.api.skill.tree.SkillPoints.FREE_RESET_LEVEL);
    }

    /** Coins a full or partial refund costs: free for new characters, otherwise per refunded point. */
    public long respecCost(Player p, int refundedPoints) {
        PlayerProfile pr = profile(p);
        if (pr == null || pr.progression().level() <= freeResetLevel()) return 0;
        return (long) Math.max(0, plugin.getConfig().getLong("skills.respec.coins-per-point", 25)) * refundedPoints;
    }

    public enum ResetOutcome { OK, NOTHING, COOLDOWN, NO_COINS, NO_CLASS }

    public record ResetResult(ResetOutcome outcome, long coins, int refunded, int waitSeconds) {
    }

    /** Refund the whole tree, or one category, paying the coin cost unless an Orb of Oblivion is used. */
    public ResetResult reset(Player p, mn.suld.api.skill.tree.SkillCategory category, boolean useOrb) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        if (pr == null || t == null) return new ResetResult(ResetOutcome.NO_CLASS, 0, 0, 0);
        synchronized (pr) {
            SkillAllocation before = SkillEngine.allocation(pr, t);
            SkillAllocation after = category == null ? SkillAllocation.empty(t) : before.withoutCategory(category);
            int refunded = before.spent() - after.spent();
            if (refunded <= 0) return new ResetResult(ResetOutcome.NOTHING, 0, 0, 0);
            long coins = useOrb ? 0 : respecCost(p, refunded);
            if (pr.currency() < coins) return new ResetResult(ResetOutcome.NO_COINS, coins, refunded, 0);
            long cd = useOrb ? 0 : respecCooldownMs();
            SkillEngine.Result r = category == null ? SkillEngine.resetAll(pr, t, System.currentTimeMillis(), cd)
                    : SkillEngine.resetCategory(pr, t, category, System.currentTimeMillis(), cd);
            if (r.outcome() == SkillEngine.Outcome.COOLDOWN) return new ResetResult(ResetOutcome.COOLDOWN, coins, refunded, Integer.parseInt(r.detail()));
            if (!r.ok()) return new ResetResult(ResetOutcome.NOTHING, 0, 0, 0);
            if (coins > 0) pr.addCurrency(-coins);
            afterChange(p);
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.8f);
            return new ResetResult(ResetOutcome.OK, coins, refunded, 0);
        }
    }

    public SkillEngine.Result saveBuild(Player p, String name) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        if (pr == null || t == null) return new SkillEngine.Result(SkillEngine.Outcome.INVALID, "no class");
        SkillEngine.Result r = SkillEngine.saveBuild(pr, t, name, buildSlots());
        if (r.ok()) services.profiles().save(pr);
        return r;
    }

    public SkillEngine.Result loadBuild(Player p, String name) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        if (pr == null || t == null) return new SkillEngine.Result(SkillEngine.Outcome.INVALID, "no class");
        SkillEngine.Result r = SkillEngine.loadBuild(pr, t, name, context(p), System.currentTimeMillis(), respecCooldownMs());
        if (r.ok()) afterChange(p);
        return r;
    }

    public SkillEngine.Result deleteBuild(Player p, String name) {
        PlayerProfile pr = profile(p);
        if (pr == null) return new SkillEngine.Result(SkillEngine.Outcome.INVALID, "no class");
        SkillEngine.Result r = SkillEngine.deleteBuild(pr, name);
        if (r.ok()) services.profiles().save(pr);
        return r;
    }

    /** Administrator tools: they work on an online player's cached profile and then refresh it like a normal change. */
    public SkillAllocation.Check adminUnlock(Player p, SkillNode n) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        if (pr == null || t == null) return new SkillAllocation.Check(SkillAllocation.Why.NOT_CONNECTED, n, null, 0);
        SkillAllocation.Check c = SkillEngine.forceUnlock(pr, t, n);
        if (c.ok()) afterChange(p);
        return c;
    }

    public void adminGrant(Player p, int amount) {
        PlayerProfile pr = profile(p);
        if (pr == null) return;
        SkillEngine.grant(pr, amount);
        afterChange(p);
    }

    public boolean adminReset(Player p) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        if (pr == null || t == null) return false;
        SkillEngine.Result r = SkillEngine.resetAll(pr, t, System.currentTimeMillis(), 0);
        afterChange(p);
        return r.ok();
    }

    /** QA tool: runs the passive spells of {@code event} for a player right now (nearest enemy within 12 blocks as the target). */
    public int adminFire(Player p, TriggerEvent event) {
        LivingEntity nearest = null;
        double best = 144;
        for (LivingEntity e : skills.enemiesAround(p, p.getLocation(), 12)) {
            double d = e.getLocation().distanceSquared(p.getLocation());
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        Runtime r = runtime.get(p.getUniqueId());
        if (r != null) r.procReady.clear();
        fire(p, event, nearest);
        return r == null ? 0 : r.build.procs(event).size();
    }

    // ------------------------------------------------------------------ stats used by the combat code

    public double attackMultiplier(Player p) {
        return 1 + build(p).stat(StatKey.ATTACK_PCT) / 100.0;
    }

    public double critChance(Player p) {
        return build(p).stat(StatKey.CRIT_CHANCE) / 100.0;
    }

    public double critMultiplier(Player p) {
        return 1.5 + build(p).stat(StatKey.CRIT_DAMAGE) / 100.0;
    }

    public double expMultiplier(Player p) {
        return 1 + build(p).stat(StatKey.EXP_PCT) / 100.0;
    }

    /** Whether the loot table should be rolled one more time (LOOT_PCT is the chance in percent). */
    public boolean extraLootRoll(Player p) {
        double c = build(p).stat(StatKey.LOOT_PCT);
        return c > 0 && ThreadLocalRandom.current().nextDouble() * 100 < c;
    }

    public double spellDamageMultiplier(Player p) {
        SkillBuild b = build(p);
        double v = 1 + b.stat(StatKey.SPELL_DAMAGE) / 100.0;
        if (b.has(KeystoneKind.ALTAN_DOSH)) v += 0.15;
        return v;
    }

    /** Heals through the regain event so keystones and other plugins see it; {@code power} scales with HEAL_POWER. */
    public void heal(Player target, double amount) {
        double scaled = amount * (1 + build(target).stat(StatKey.HEAL_POWER) / 100.0);
        healRaw(target, scaled);
    }

    /** Healing from a caster's spell: the caster's HEAL_POWER applies, the receiver's keystones still do. */
    public void healFrom(Player caster, Player target, double amount) {
        double scaled = amount * (1 + build(caster).stat(StatKey.HEAL_POWER) / 100.0);
        healRaw(target, scaled);
    }

    private void healRaw(Player target, double amount) {
        if (amount <= 0 || target.isDead()) return;
        target.heal(amount, EntityRegainHealthEvent.RegainReason.CUSTOM);
    }

    // ------------------------------------------------------------------ procs

    /** Fire every passive spell of {@code event}: chance, per-node cooldown (reduced by cooldown reduction), effect. */
    public void fire(Player p, TriggerEvent event, LivingEntity target) {
        if (dispatching) return;
        Runtime r = runtime.get(p.getUniqueId());
        if (r == null || p.isDead()) return;
        List<SkillBuild.IndexedProc> procs = r.build.procs(event);
        if (procs.isEmpty()) return;
        long now = System.currentTimeMillis();
        double cdr = Math.min(60, r.build.stat(StatKey.COOLDOWN_REDUCTION)) / 100.0;
        for (SkillBuild.IndexedProc ip : procs) {
            Effect.Proc proc = ip.proc();
            Long ready = r.procReady.get(ip.node());
            if (ready != null && now < ready) continue;
            if (proc.chance() < 100 && ThreadLocalRandom.current().nextDouble() * 100 >= proc.chance()) continue;
            if (proc.cooldown() > 0) r.procReady.put(ip.node(), now + (long) (proc.cooldown() * 1000 * (1 - cdr)));
            dispatching = true;
            try {
                run(p, proc, target);
            } finally {
                dispatching = false;
            }
        }
    }

    private void run(Player p, Effect.Proc proc, LivingEntity target) {
        double a = proc.a(), b = proc.b();
        switch (proc.kind()) {
            case HEAL -> {
                heal(p, a);
                p.getWorld().spawnParticle(org.bukkit.Particle.HEART, p.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3);
            }
            case SHIELD -> shield(p, a, (int) (b * 20));
            case SPEED -> p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, (int) (b * 20), (int) a - 1, false, false, true));
            case STRENGTH -> p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, (int) (b * 20), (int) a - 1, false, false, true));
            case RESIST -> p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, (int) (b * 20), (int) a - 1, false, false, true));
            case RESOURCE -> skills.pool(p).gain(a);
            case AOE -> {
                var c = p.getLocation();
                for (LivingEntity e : skills.enemiesAround(p, c, b)) skills.damage(p, e, CombatListener.attackOf(services, p) * a);
                p.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, c.clone().add(0, 1, 0), 3, b / 3, 0.2, b / 3);
                p.getWorld().playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1f);
            }
            case IGNITE -> {
                if (target != null) target.setFireTicks((int) (a * 20));
                else for (LivingEntity e : skills.enemiesAround(p, p.getLocation(), 4)) e.setFireTicks((int) (a * 20));
            }
            case SLOW_AREA -> {
                for (LivingEntity e : skills.enemiesAround(p, p.getLocation(), b <= 0 ? 5 : b)) {
                    e.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, (int) (a * 20), 1));
                }
            }
            case BONUS -> {
                if (target != null && target.isValid()) {
                    skills.damage(p, target, CombatListener.attackOf(services, p) * a);
                    target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getEyeLocation(), 8, 0.3, 0.3, 0.3, 0.1);
                }
            }
            case SMITE -> {
                if (target != null && target.isValid()) {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    skills.damage(p, target, CombatListener.attackOf(services, p) * a);
                }
            }
            case CHAIN -> {
                if (target == null) return;
                LivingEntity next = null;
                double best = 36;
                for (LivingEntity e : skills.enemiesAround(p, target.getLocation(), 6)) {
                    if (e.equals(target)) continue;
                    double d = e.getLocation().distanceSquared(target.getLocation());
                    if (d < best) {
                        best = d;
                        next = e;
                    }
                }
                if (next != null) {
                    skills.damage(p, next, CombatListener.attackOf(services, p) * a);
                    var from = target.getEyeLocation();
                    var to = next.getEyeLocation().toVector().subtract(from.toVector());
                    int steps = (int) Math.min(12, to.length() * 2);
                    for (int i = 1; i <= steps; i++) {
                        p.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, from.clone().add(to.clone().multiply(i / (double) steps)), 1, 0, 0, 0, 0);
                    }
                }
            }
            case CLEANSE -> {
                for (var type : new org.bukkit.potion.PotionEffectType[]{org.bukkit.potion.PotionEffectType.POISON, org.bukkit.potion.PotionEffectType.WITHER,
                        org.bukkit.potion.PotionEffectType.SLOWNESS, org.bukkit.potion.PotionEffectType.WEAKNESS, org.bukkit.potion.PotionEffectType.BLINDNESS,
                        org.bukkit.potion.PotionEffectType.NAUSEA, org.bukkit.potion.PotionEffectType.HUNGER}) {
                    p.removePotionEffect(type);
                }
                p.setFireTicks(0);
            }
            case SHOVE -> {
                for (LivingEntity e : skills.enemiesAround(p, p.getLocation(), b <= 0 ? 4 : b)) {
                    var v = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() > 0.01) e.setVelocity(v.normalize().multiply(0.4 + a * 0.2).setY(0.3));
                }
            }
        }
    }

    /**
     * Absorption hearts that fade after {@code ticks}; a newer shield replaces an older one. Absorption is capped by the
     * MAX_ABSORPTION attribute (0 for players), so the shield raises that cap with a temporary modifier first.
     */
    public void shield(Player p, double hp, int ticks) {
        Runtime r = runtime.get(p.getUniqueId());
        long token = r == null ? 0 : ++r.shieldToken;
        setModifier(p, Attribute.MAX_ABSORPTION, "shield", hp, AttributeModifier.Operation.ADD_NUMBER);
        p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), hp));
        p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0.02);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Runtime now = runtime.get(p.getUniqueId());
            if (p.isOnline() && now != null && now.shieldToken == token) {
                p.setAbsorptionAmount(0);
                setModifier(p, Attribute.MAX_ABSORPTION, "shield", 0, AttributeModifier.Operation.ADD_NUMBER);
            }
        }, Math.max(20, ticks));
    }

    // ------------------------------------------------------------------ combat events

    private static boolean hostile(org.bukkit.entity.Entity e) {
        return e instanceof LivingEntity && !(e instanceof Player) && !e.getScoreboardTags().contains("city_npc");
    }

    /** Damage the player takes: reductions, keystone, dodge (before the damage is applied). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Runtime r = runtime.get(p.getUniqueId());
        if (r == null || r.build.isEmpty()) return;
        SkillBuild b = r.build;
        double dodge = b.stat(StatKey.DODGE_PCT);
        boolean physical = e instanceof EntityDamageByEntityEvent;
        if (physical && dodge > 0 && ThreadLocalRandom.current().nextDouble() * 100 < Math.min(40, dodge)) {
            e.setCancelled(true);
            p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 0.8f, 1.6f);
            return;
        }
        double reduction = b.stat(StatKey.DAMAGE_REDUCTION);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 20 : max.getValue();
        if (b.has(KeystoneKind.MUNKH_TESVER) && p.getHealth() / maxHp < 0.30) reduction += 35;
        if (reduction > 0) e.setDamage(e.getDamage() * (1 - Math.min(75, reduction) / 100.0));
    }

    /** After the damage: passive spells (damaged / low health) and the damage reflected by thorns. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamagedAfter(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Runtime r = runtime.get(p.getUniqueId());
        if (r == null || r.build.isEmpty()) return;
        LivingEntity source = e instanceof EntityDamageByEntityEvent ev && ev.getDamager() instanceof LivingEntity le ? le : null;
        double thorns = r.build.stat(StatKey.THORNS) + (ultimates == null ? 0 : ultimates.extraThorns(p.getUniqueId()));
        if (thorns > 0 && source != null && hostile(source) && !dispatching && e.getFinalDamage() > 0) {
            dispatching = true;
            try {
                skills.damage(p, source, e.getFinalDamage() * thorns / 100.0);
            } finally {
                dispatching = false;
            }
        }
        fire(p, TriggerEvent.DAMAGED, source);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 20 : max.getValue();
        if ((p.getHealth() - e.getFinalDamage()) / maxHp <= 0.35) fire(p, TriggerEvent.LOW_HP, source);
    }

    /** The player's own hits (melee and arrows): lifesteal, marks, hit/crit passive spells. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMarkedVictim(EntityDamageByEntityEvent e) {
        if (marks.isEmpty() || !hostile(e.getEntity())) return;
        double[] m = marks.get(e.getEntity().getUniqueId());
        if (m == null) return;
        if (System.currentTimeMillis() > (long) m[0]) {
            marks.remove(e.getEntity().getUniqueId());
            return;
        }
        if (attackerOf(e) != null) e.setDamage(e.getDamage() * (1 + m[1] / 100.0));
    }

    private static Player attackerOf(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof AbstractArrow a && a.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (CombatListener.spellDamage || dispatching || !hostile(e.getEntity())) return;
        Player p = attackerOf(e);
        if (p == null) return;
        Runtime r = runtime.get(p.getUniqueId());
        if (r == null || r.build.isEmpty()) return;
        LivingEntity victim = (LivingEntity) e.getEntity();
        double steal = r.build.stat(StatKey.LIFESTEAL);
        if (steal > 0) healRaw(p, e.getFinalDamage() * steal / 100.0);
        if (e.getDamager() instanceof AbstractArrow arrow && arrow.getScoreboardTags().contains("suld_volley")) {
            skills.riders(p, victim, mn.suld.api.skill.Spell.OLON_SUM);
        }
        fire(p, TriggerEvent.HIT, victim);
        if (CombatListener.CRIT_AT.remove(victim.getUniqueId()) != null) fire(p, TriggerEvent.CRIT, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        if (!hostile(e.getEntity()) || e.getEntity().getKiller() == null) return;
        marks.remove(e.getEntity().getUniqueId());
        fire(e.getEntity().getKiller(), TriggerEvent.KILL, null);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking()) fire(e.getPlayer(), TriggerEvent.SNEAK, null);
    }

    /** Keystone Мөнх Тэсвэр: the price of its resistance is weaker healing. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && build(p).has(KeystoneKind.MUNKH_TESVER)) e.setAmount(e.getAmount() * 0.5);
    }

    /** Keystone Алтан Дөш: gear lasts longer. */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (build(e.getPlayer()).has(KeystoneKind.ALTAN_DOSH) && ThreadLocalRandom.current().nextDouble() < 0.60) e.setCancelled(true);
    }

    /** Keystone Талын Салхи: the steppe horse runs far faster under its rider. */
    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getMount() instanceof AbstractHorse horse)) return;
        if (!build(p).has(KeystoneKind.TALYN_SALKHI)) return;
        setModifier2(horse, 0.60);
    }

    private static void setModifier2(AbstractHorse horse, double amount) {
        AttributeInstance inst = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst == null) return;
        NamespacedKey k = key("steppe_wind");
        AttributeModifier old = inst.getModifier(k);
        if (old != null) inst.removeModifier(old);
        inst.addTransientModifier(new AttributeModifier(k, amount, AttributeModifier.Operation.ADD_SCALAR));
    }

    // ------------------------------------------------------------------ marks (spell modifier "VULN")

    public void mark(LivingEntity target, double percent, int seconds) {
        marks.merge(target.getUniqueId(), new double[]{System.currentTimeMillis() + seconds * 1000L, percent},
                (old, nw) -> nw[1] >= old[1] ? nw : old);
        target.getWorld().spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER, target.getEyeLocation().add(0, 0.4, 0), 3, 0.2, 0.1, 0.2);
    }

    // ------------------------------------------------------------------ ultimate (F)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        Runtime r = runtime.get(p.getUniqueId());
        if (r == null || r.build.ultimate() == null || !skills.holdsClassWeapon(p)) return;
        e.setCancelled(true); // F is the ultimate key for players who learned one
        castUltimate(p, r);
    }

    public int ultimateCooldownSeconds(Player p) {
        Runtime r = runtime.get(p.getUniqueId());
        if (r == null) return 0;
        long left = r.ultReady - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    public Ultimate ultimateOf(Player p) {
        return build(p).ultimate();
    }

    private void castUltimate(Player p, Runtime r) {
        long now = System.currentTimeMillis();
        if (services.isSoul.test(p.getUniqueId())) {
            skills.say(p, "§bСүнс — дуулал хэрэглэх боломжгүй");
            return;
        }
        if (now < r.ultReady) {
            skills.say(p, "§c" + r.build.ultimate().displayName() + " — " + ultimateCooldownSeconds(p) + " сек");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }
        int cost = plugin.getConfig().getInt("skills.ultimate.resource-cost", 60);
        ResourcePool pool = skills.pool(p);
        if (!pool.spend(Math.min(cost, pool.max()))) {
            skills.say(p, "§c" + r.build.ultimate().displayName() + " — нөөц хүрэлцэхгүй (" + cost + ")");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }
        double cdr = Math.min(60, r.build.stat(StatKey.COOLDOWN_REDUCTION)) / 100.0;
        r.ultReady = now + (long) (Ultimate.COOLDOWN_SECONDS * 1000L * (1 - cdr));
        skills.say(p, "§6✦✦ " + r.build.ultimate().displayName());
        ultimates.cast(p, r.build.ultimate());
    }

    // ------------------------------------------------------------------ search / lookup (commands and the map)

    /** Nodes of the player's tree whose name, id, effect text or tag contains {@code query} (case-insensitive). */
    public List<SkillNode> search(Player p, String query) {
        SkillTree t = tree(p);
        List<SkillNode> out = new ArrayList<>();
        if (t == null || query == null || query.isBlank()) return out;
        String q = query.toLowerCase(java.util.Locale.ROOT).trim();
        SkillAllocation a = allocation(p);
        for (SkillNode n : t.nodes()) {
            if (n.root() || (a != null && !a.visible(n))) continue;
            StringBuilder text = new StringBuilder(n.name()).append(' ').append(n.id()).append(' ').append(n.category().label());
            for (var l : mn.suld.api.skill.tree.EffectText.lines(n)) text.append(' ').append(l.text());
            for (String tag : n.tags()) text.append(' ').append(tag);
            if (text.toString().toLowerCase(java.util.Locale.ROOT).contains(q)) out.add(n);
        }
        return out;
    }

    public Plugin plugin() {
        return plugin;
    }
}
