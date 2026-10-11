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
import mn.suld.api.skill.tree.SkillPoints;
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
    /** Ultimate cooldowns of players who logged out while it was running (relogging must not reset it). */
    private final Map<UUID, Long> ultHeld = new ConcurrentHashMap<>();
    /** Enemies marked to take extra damage: entity id -> {until, percent}. */
    private final Map<UUID, double[]> marks = new ConcurrentHashMap<>();
    /** Player -> node id -> when its last rank was unlocked (a refund within the grace is a free misclick undo). */
    private final Map<UUID, Map<String, Long>> recentUnlocks = new ConcurrentHashMap<>();
    private static final long REFUND_GRACE_MS = 120_000;
    private SkillService skills;
    private Ultimates ultimates;
    /** How many times a passive spell of each trigger actually ran (QA measures chance and wiring with it). */
    public final Map<TriggerEvent, java.util.concurrent.atomic.AtomicInteger> activations = new EnumMap<>(TriggerEvent.class);

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
    /**
     * Put the bundled trees into the server folder and keep them current. A file the owner never edited (its hash is
     * the one recorded when it was written) follows the bundled version on every update; an edited file is kept and
     * the new bundled version is written next to it as {@code <name>.json.new}. Files from before the hash record
     * existed are backed up to {@code .bak} once and updated (they were the old defaults: the satellites of the
     * full-screen tree would otherwise never reach existing servers).
     */
    private void extractDefaults() {
        try {
            Files.createDirectories(dataDir);
            for (String name : FILES) {
                Path target = dataDir.resolve(name + ".json");
                Path stamp = dataDir.resolve("." + name + ".bundled.sha256");
                byte[] bundled;
                try (InputStream in = SkillTreeService.class.getResourceAsStream("/skills/" + name + ".json")) {
                    if (in == null) throw new IOException("bundled skills/" + name + ".json is missing from the jar");
                    bundled = in.readAllBytes();
                }
                String want = sha256(bundled);
                if (!Files.exists(target)) {
                    Files.write(target, bundled);
                    Files.writeString(stamp, want);
                    continue;
                }
                String have = sha256(Files.readAllBytes(target));
                if (have.equals(want)) {
                    if (!Files.exists(stamp)) Files.writeString(stamp, want);
                    continue;
                }
                String recorded = Files.exists(stamp) ? Files.readString(stamp).strip() : null;
                if (recorded == null || recorded.equals(have)) {
                    if (recorded == null) Files.copy(target, dataDir.resolve(name + ".json.bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.write(target, bundled);
                    Files.writeString(stamp, want);
                    plugin.getLogger().info("[skills] " + name + ".json updated to the bundled version" + (recorded == null ? " (old copy in " + name + ".json.bak)" : ""));
                } else {
                    Files.write(dataDir.resolve(name + ".json.new"), bundled);
                    plugin.getLogger().warning("[skills] " + name + ".json was edited on this server; kept. The new bundled version is in " + name + ".json.new");
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Cannot prepare " + dataDir + ": " + e.getMessage(), e);
        }
    }

    private static String sha256(byte[] b) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
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
        List<SkillTreeLoader.Issue> all = new ArrayList<>(fromDisk.issues());
        all.addAll(iconIssues(next));
        issues = List.copyOf(all);
        for (SkillTreeLoader.Issue i : issues) plugin.getLogger().severe("[skills] " + i);
        int nodes = next.values().stream().mapToInt(t -> t.nodes().size() - 1).sum();
        plugin.getLogger().info("[skills] " + nodes + " nodes loaded for " + next.size() + " classes"
                + (issues.isEmpty() ? "" : " — " + issues.size() + " problem(s), see above"));
        for (Player p : Bukkit.getOnlinePlayers()) attach(p);
        return issues;
    }

    /** Validate the server's data files without applying them (what /skillsadmin validate reports), including node icons. */
    public List<SkillTreeLoader.Issue> validate() {
        extractDefaults();
        SkillTreeLoader.Result r = SkillTreeLoader.loadAll(SkillTreeLoader.directory(dataDir));
        List<SkillTreeLoader.Issue> all = new ArrayList<>(r.issues());
        all.addAll(iconIssues(r.trees()));
        return all;
    }

    /** A node whose icon is not an item this server knows would silently show as paper: report it with file and field. */
    private static List<SkillTreeLoader.Issue> iconIssues(Map<PlayerClass, SkillTree> trees) {
        List<SkillTreeLoader.Issue> out = new ArrayList<>();
        for (Map.Entry<PlayerClass, SkillTree> e : trees.entrySet()) {
            int i = 0;
            for (SkillNode n : e.getValue().nodes()) {
                org.bukkit.Material m = org.bukkit.Material.matchMaterial(n.icon());
                boolean universal = n.tags().contains("universal");
                if (m == null || !m.isItem()) {
                    out.add(new SkillTreeLoader.Issue(universal ? "universal.json" : SkillTreeLoader.fileOf(e.getKey()),
                            "nodes[id=" + n.id() + "].icon", "'" + n.icon() + "' is not an item on this server"));
                }
                i++;
            }
        }
        return out;
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

    /**
     * Free points, memoised per player on everything they depend on (the skill state, level, quest chapter and
     * discoveries): the HUD asks every few ticks and the full-screen tree every tick, and each uncached call
     * re-decoded the allocation string and walked the tree.
     */
    private record PointsKey(Object skillState, int level, String quest, boolean done, long discovered) {
    }

    private final Map<UUID, Map.Entry<PointsKey, Integer>> availableMemo = new ConcurrentHashMap<>();

    public int available(Player p) {
        PlayerProfile pr = profile(p);
        if (pr == null) return 0;
        PointsKey key = new PointsKey(pr.skillState(), pr.progression().level(), pr.questState().questId(), pr.questState().completed(),
                services.styles().cached(p.getUniqueId()).map(mn.suld.api.style.PlayerStyle::discovered).orElse(0L));
        Map.Entry<PointsKey, Integer> hit = availableMemo.get(p.getUniqueId());
        if (hit != null && hit.getKey().equals(key)) return hit.getValue();
        int v = Math.max(0, total(p) - spent(p));
        availableMemo.put(p.getUniqueId(), Map.entry(key, v));
        return v;
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
        Runtime r = runtime.computeIfAbsent(p.getUniqueId(), k -> {
            Runtime fresh = new Runtime();
            Long held = ultHeld.remove(k); // a relog keeps the ultimate's cooldown
            if (held != null) fresh.ultReady = held;
            return fresh;
        });
        r.clazz = pr.playerClass().get();
        r.tree = t;
        refreshRuntime(p, pr, r);
    }

    /** Item passives are indexed from here so their cooldowns never collide with the tree's node indices. */
    public static final int ITEM_PROC_INDEX = 100_000;
    /** Mastery perks carry no procs today; their index range is reserved apart from items. */
    public static final int MASTERY_PROC_INDEX = 200_000;

    private void refreshRuntime(Player p, PlayerProfile pr, Runtime r) {
        r.allocation = SkillEngine.allocation(pr, r.tree);
        // one build for the combat engine: what the tree gives plus what the equipment gives
        mn.suld.plugin.item.EquipmentService eq = services.equipment();
        mn.suld.api.item.Equipment.Bonus gear = eq == null ? mn.suld.api.item.Equipment.Bonus.NONE : eq.compute(p);
        r.build = r.allocation.build().plus(gear.statKeys(), gear.mods(), gear.procs(), ITEM_PROC_INDEX);
        // armour mastery perks (ranks 3 / 6 / 9): stats and spell modifiers through the same build
        mn.suld.api.clazz.PlayerClass clazz = pr.playerClass().orElse(null);
        int rank = pr.classGear().mastery();
        if (rank >= 3 && clazz != null) {
            r.build = r.build.plus(mn.suld.api.classgear.MasteryPerks.stats(clazz, rank), mn.suld.api.classgear.MasteryPerks.mods(clazz, rank),
                    java.util.List.of(), MASTERY_PROC_INDEX);
        }
        applyAttributes(p, r.build);
        // resource pool size follows the build
        skills.rebuildPool(p);
    }

    /** The equipment changed: rebuild the stats (no save: the inventory is the game's, accessories are saved where changed). */
    public void equipmentChanged(Player p) {
        PlayerProfile pr = profile(p);
        Runtime r = runtime.get(p.getUniqueId());
        if (pr == null || r == null) {
            mn.suld.plugin.item.EquipmentService eq = services.equipment();
            if (eq != null) eq.compute(p); // no class yet: the bonus is kept, but there is no build to carry it
            return;
        }
        refreshRuntime(p, pr, r);
        services.hud().refresh(p);
    }

    /** Call after any change to a player's tree: refresh derived state, save, update the HUD. */
    public void afterChange(Player p) {
        PlayerProfile pr = profile(p);
        Runtime r = runtime.get(p.getUniqueId());
        if (pr == null || r == null) return;
        refreshRuntime(p, pr, r);
        services.profiles().save(pr);
        services.hud().update(p, pr);
        services.hud().refresh(p);
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
        setModifier(p, Attribute.ATTACK_SPEED, "attack_speed", b.stat(StatKey.ATTACK_SPEED_PCT) / 100.0, AttributeModifier.Operation.ADD_SCALAR);
        AttributeInstance now = p.getAttribute(Attribute.MAX_HEALTH);
        if (now != null && p.getHealth() > now.getValue()) p.setHealth(now.getValue());
    }

    private void clearAttributes(Player p) {
        setModifier(p, Attribute.MAX_HEALTH, "health", 0, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(p, Attribute.MOVEMENT_SPEED, "move", 0, AttributeModifier.Operation.ADD_SCALAR);
        setModifier(p, Attribute.ARMOR, "armor", 0, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(p, Attribute.KNOCKBACK_RESISTANCE, "knockback", 0, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(p, Attribute.BLOCK_BREAK_SPEED, "mining", 0, AttributeModifier.Operation.ADD_SCALAR);
        setModifier(p, Attribute.ATTACK_SPEED, "attack_speed", 0, AttributeModifier.Operation.ADD_SCALAR);
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
                p.sendMessage(Messages.accent("◆ Танд " + avail + " чадварын оноо байна — /skills гэж бичиж чадварын газрын зургаа нээ!"));
            } else if (avail > 0) {
                p.sendMessage(Messages.info("◆ " + avail + " зарцуулаагүй чадварын оноо — /skills"));
            }
        }, 80L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Runtime gone = runtime.remove(id);
        long now = System.currentTimeMillis();
        if (gone != null && gone.ultReady > now) ultHeld.put(id, gone.ultReady);
        if (ultHeld.size() > 512) ultHeld.values().removeIf(t -> t <= now);
        marks.remove(id);
        recentUnlocks.remove(id);
        availableMemo.remove(id);
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
                    p.sendMessage(Messages.info("Оноогоо /skills (газрын зураг; эсвэл ангийн зэвсгээ барьж Shift + баруун товч) дээр зарцуулж, шид болон идэвхгүй чадвараа сонго. Улаан холбоос = зөвхөн нэгийг сонгоно."));
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
        // a node not yet connected to the build learns the cheapest chain up to it in one click (SkillAllocation.pathTo)
        SkillEngine.PathResult res = SkillEngine.unlockPath(pr, t, n, context(p));
        SkillAllocation.Check c = res.check();
        if (c.ok()) {
            Map<String, Long> mine = recentUnlocks.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());
            long now = System.currentTimeMillis();
            mine.values().removeIf(t0 -> now - t0 >= REFUND_GRACE_MS); // only the grace window matters
            for (SkillNode m : res.learned()) mine.put(m.id(), now);
            if (res.learned().size() > 1) {
                p.sendMessage(Messages.accent("◆ Зам нээгдлээ: " + res.learned().size() + " чадвар («" + n.name() + "» хүртэл), "
                        + SkillAllocation.cost(res.learned()) + " оноо"));
            }
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
        // a refund is a respec (docs/SKILL_TREE_ARCHITECTURE.md §7): it costs the per-point respec price, except a
        // rank unlocked in the last two minutes (a misclick) and below the free-reset level
        Map<String, Long> recent = recentUnlocks.get(p.getUniqueId());
        Long at = recent == null ? null : recent.get(n.id());
        long coins = at != null && System.currentTimeMillis() - at < REFUND_GRACE_MS ? 0 : respecCost(p, n.cost()); // per point, like a reset
        SkillAllocation.Check c;
        synchronized (pr) {
            if (pr.currency() < coins) {
                c = new SkillAllocation.Check(SkillAllocation.Why.COINS, n, null, (int) Math.min(Integer.MAX_VALUE, coins));
            } else {
                c = SkillEngine.refund(pr, t, n);
                if (c.ok() && coins > 0) pr.addCurrency(-coins);
            }
        }
        if (c.ok()) {
            if (recent != null && coins == 0) recent.remove(n.id());
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

    /** Loading a build is a respec of the points it takes away: those cost the respec price like a reset. */
    public SkillEngine.Result loadBuild(Player p, String name) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        if (pr == null || t == null) return new SkillEngine.Result(SkillEngine.Outcome.INVALID, "no class");
        synchronized (pr) {
            long coins = 0;
            String stored = pr.skillState().builds().get(name);
            if (stored != null) {
                SkillAllocation cur = SkillEngine.allocation(pr, t), next = SkillAllocation.decode(t, stored).allocation();
                int removed = 0;
                for (SkillNode n : t.nodes()) removed += Math.max(0, cur.rank(n) - next.rank(n)) * n.cost(); // points, not ranks
                coins = respecCost(p, removed);
                if (pr.currency() < coins) return new SkillEngine.Result(SkillEngine.Outcome.INVALID, coins + " ₮ хэрэгтэй");
            }
            SkillEngine.Result r = SkillEngine.loadBuild(pr, t, name, context(p), System.currentTimeMillis(), respecCooldownMs());
            if (r.ok()) {
                if (coins > 0) pr.addCurrency(-coins);
                afterChange(p);
            }
            return r;
        }
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

    // ------------------------------------------------------------------ QA support (used by SkillQa)

    /** Rebuild a player's derived state from the profile without saving or normalising (the QA suite sets ranks directly). */
    public void qaRefresh(Player p) {
        PlayerProfile pr = profile(p);
        if (pr == null || pr.playerClass().isEmpty()) return;
        Runtime r = runtime.computeIfAbsent(p.getUniqueId(), k -> new Runtime());
        r.clazz = pr.playerClass().get();
        r.tree = trees.get(r.clazz);
        refreshRuntime(p, pr, r);
    }

    /** Make every passive spell and the ultimate ready again. */
    public void qaResetTimers(Player p) {
        Runtime r = runtime.get(p.getUniqueId());
        if (r != null) {
            r.procReady.clear();
            r.ultReady = 0;
        }
    }

    /** Runs the passive spells of {@code event} at {@code target} after making them all ready. */
    public void adminFireAt(Player p, TriggerEvent event, LivingEntity target) {
        qaResetTimers(p);
        fire(p, event, target);
    }

    /** Like {@link #adminFireAt} but keeps the cooldowns as they are (to observe them). */
    public void adminFireAtNoReset(Player p, TriggerEvent event, LivingEntity target) {
        fire(p, event, target);
    }

    /**
     * Puts a player in a known skill-tree situation for manual or automated client QA.
     * <ul>
     *   <li>{@code fresh}: level 1, empty tree, no granted points (what a new player sees)</li>
     *   <li>{@code rich}: level 60, empty tree, 60 points to click through</li>
     *   <li>{@code states0}: like {@code states} but with no free point, so every unlearned node shows "needs points" (classes
     *       without a cost-2 node reachable from the build, which {@code states} needs for that state)</li>
     *   <li>{@code states}: level 14 with a build that shows every node state on the first screens: learned (2/3), maxed,
     *       available, needs points, level locked, prerequisite missing, excluded by a red link, locked, plus a keystone</li>
     * </ul>
     * Returns a description, or null for an unknown scenario.
     */
    public String qaKit(Player p, String scenario) {
        PlayerProfile pr = profile(p);
        SkillTree t = tree(p);
        if (pr == null || t == null) return null;
        synchronized (pr) {
            pr.skillState(pr.skillState().withRanks("").withGranted(0));
            switch (scenario) {
                case "fresh" -> pr.progression(new mn.suld.api.progression.Progression(1, 0));
                case "rich" -> {
                    pr.progression(new mn.suld.api.progression.Progression(60, 0));
                    SkillEngine.grant(pr, 60 - total(p));
                }
                case "states", "states0" -> {
                    pr.progression(new mn.suld.api.progression.Progression(14, 0));
                    String[] path = {"l1", "l1", "m1", "m1", "m1", "l2", "l3", "l4", "l2b", "l2b", "l5b", "l6", "l7"};
                    for (String id : path) SkillEngine.forceUnlock(pr, t, t.node(id));
                    // exactly one free point: cost-2 nodes then need points, cost-1 nodes are available
                    int spent = SkillEngine.spent(pr, t);
                    int base = SkillPoints.total(14, context(p).finishedChapters(), context(p).discoveredRegions(), 0);
                    SkillEngine.grant(pr, Math.max(0, spent + (scenario.equals("states0") ? 0 : 1) - base));
                }
                default -> {
                    return null;
                }
            }
        }
        afterChange(p);
        return scenario + ": level " + context(p).level() + ", " + available(p) + " free points, " + spent(p) + " spent";
    }

    public void qaClearMarks() {
        marks.clear();
    }

    /** Casts the player's ultimate through the normal rules (cost, cooldown); true if it was cast. */
    public boolean qaCastUltimate(Player p) {
        Runtime r = runtime.get(p.getUniqueId());
        if (r == null || r.build.ultimate() == null || System.currentTimeMillis() < r.ultReady) return false;
        castUltimate(p, r);
        return System.currentTimeMillis() < r.ultReady;
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
    /** The loot-chance stat (%), raising rare-drop chances (LootContext.lootBonus). */
    public double lootPct(Player p) {
        return Math.max(0, build(p).stat(StatKey.LOOT_PCT));
    }

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

    /** Health regeneration from equipment (once per second, with the resource regeneration). */
    public void regenerate(Player p, double amount) {
        if (amount > 0 && !p.isDead() && p.getHealth() > 0) healRaw(p, amount);
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
            activations.computeIfAbsent(event, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
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

    /** Adds {@code hp} of absorption on top of what the player already has (spell modifier SHIELD); it fades after {@code ticks}. */
    public void shieldAdd(Player p, double hp, int ticks) {
        shield(p, hp, ticks, true);
    }

    /**
     * Absorption hearts that fade after {@code ticks}; a newer shield replaces an older one. Absorption is capped by the
     * MAX_ABSORPTION attribute (0 for players), so the shield raises that cap with a temporary modifier first.
     */
    public void shield(Player p, double hp, int ticks) {
        shield(p, hp, ticks, false);
    }

    private void shield(Player p, double hp, int ticks, boolean additive) {
        Runtime r = runtime.get(p.getUniqueId());
        long token = r == null ? 0 : ++r.shieldToken;
        setModifier(p, Attribute.MAX_ABSORPTION, "shield", hp, AttributeModifier.Operation.ADD_NUMBER);
        p.setAbsorptionAmount(additive ? p.getAbsorptionAmount() + hp : Math.max(p.getAbsorptionAmount(), hp));
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
        long t = mn.suld.plugin.perf.PerfProbe.start();
        try {
            onDamaged0(e);
        } finally {
            mn.suld.plugin.perf.PerfProbe.stop("combat.damaged", t);
        }
    }

    private void onDamaged0(EntityDamageEvent e) {
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
        long t = mn.suld.plugin.perf.PerfProbe.start();
        try {
            onDamagedAfter0(e);
        } finally {
            mn.suld.plugin.perf.PerfProbe.stop("combat.damaged_after", t);
        }
    }

    private void onDamagedAfter0(EntityDamageEvent e) {
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
