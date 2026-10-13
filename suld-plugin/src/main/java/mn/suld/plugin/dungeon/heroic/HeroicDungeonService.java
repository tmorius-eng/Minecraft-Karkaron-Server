package mn.suld.plugin.dungeon.heroic;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.heroic.HeroicLadder;
import mn.suld.api.dungeon.heroic.SteppeAffix;
import mn.suld.api.item.AffixReroll;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.loot.LootDrop;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.persistence.HeroicRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.hud.HudService;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * «Тэнгэрийн Гэсгээлт Агуй» (docs/HEROIC_DUNGEONS.md): heroic keys on top of the dungeon runs. DungeonService owns the
 * run; this service owns what is heroic about it: the keys (V24, cached per online player), the gate to open a key,
 * the lift of every hall mob's hits to level 60 × the key ({@link #onHeroicHit}), the four steppe affixes, the clock on
 * the HUD, and the result (keys, «Эртний Хас Чулуу», the run log). Main thread only; storage is async.
 */
public final class HeroicDungeonService implements Listener {

    /** «Цагаан Зуд»: a fire carried in either hand keeps its bearer warm. */
    private static final Set<Material> HEAT_ITEMS = EnumSet.of(Material.TORCH, Material.SOUL_TORCH, Material.LANTERN,
            Material.SOUL_LANTERN, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);
    /**
     * …and so does an open fire nearby. Not the halls' lanterns: they light every hall a few blocks apart (HallTheme), so
     * counting them would leave no one cold (2026-10-13, heroicbot: the first blizzard found the bot warm by a lantern).
     */
    private static final Set<Material> HEAT_BLOCKS = EnumSet.of(Material.TORCH, Material.WALL_TORCH, Material.SOUL_TORCH,
            Material.SOUL_WALL_TORCH, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE, Material.SOUL_FIRE,
            Material.LAVA, Material.MAGMA_BLOCK);

    private final Plugin plugin;
    private final SuldServices services;
    private final HeroicRepository repo;
    private final NamespacedKey zudKey;
    private final Map<UUID, Map<String, HeroicRepository.Key>> keys = new ConcurrentHashMap<>();
    /** Staff QA: the affixes of a leader's next heroic run instead of the week's ({@code /dungeon heroic <агуй> <түлхүүр> ZUD,GAN}). */
    private final Map<UUID, List<SteppeAffix>> forced = new java.util.HashMap<>();

    public HeroicDungeonService(Plugin plugin, SuldServices services, HeroicRepository repo) {
        this.plugin = plugin;
        this.services = services;
        this.repo = repo;
        this.zudKey = new NamespacedKey(plugin, "heroic_zud");
    }

    public void start() {
        for (Player p : Bukkit.getOnlinePlayers()) load(p.getUniqueId());
    }

    // ---- keys -----------------------------------------------------------------------------------------------------

    private void load(UUID id) {
        repo.keys(id).whenComplete((m, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "[heroic] keys of " + id + " not read", ex);
                return;
            }
            if (Bukkit.getPlayer(id) == null) return; // left before the read came back
            // keys only rise: a run finished while the read was on its way (cached already) is never lowered by it
            Map<String, HeroicRepository.Key> mine = keys.computeIfAbsent(id, x -> new ConcurrentHashMap<>());
            m.forEach((dungeon, stored) -> mine.merge(dungeon, stored, HeroicRepository.Key::max));
        }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        load(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        keys.remove(e.getPlayer().getUniqueId());
        forced.remove(e.getPlayer().getUniqueId());
        clearZud(e.getPlayer());
    }

    /** True once the player's keys are read (a key cannot be opened before). */
    public boolean loaded(UUID player) {
        return keys.containsKey(player);
    }

    /** The player's keys in a dungeon (+1 open when never run there). */
    public HeroicRepository.Key key(UUID player, String dungeonId) {
        Map<String, HeroicRepository.Key> m = keys.get(player);
        HeroicRepository.Key k = m == null ? null : m.get(dungeonId);
        return k == null ? HeroicRepository.Key.fresh(dungeonId) : k;
    }

    /** The week the affixes rotate by (the campaign week). */
    public String weekId() {
        return services.campaigns().cycleId();
    }

    /** This week's affixes of a key. */
    public List<SteppeAffix> affixes(int key) {
        return HeroicLadder.affixes(key, weekId());
    }

    // ---- opening a key ----------------------------------------------------------------------------------------------

    /**
     * Why the party may not run {@code def} at {@code key}, or null: every member level 60 and the dungeon cleared once,
     * the key open to the leader.
     */
    public Component refusal(Player leader, DungeonDefinition def, int key, Collection<UUID> members) {
        if (key < HeroicLadder.MIN_KEY || key > HeroicLadder.MAX_KEY) {
            return Messages.error("Түлхүүр +" + HeroicLadder.MIN_KEY + "…+" + HeroicLadder.MAX_KEY + " байна.");
        }
        if (!loaded(leader.getUniqueId())) return Messages.error("Түлхүүрийн мэдээлэл ачаалж байна — түр хүлээгээд дахин оролдоно уу.");
        int open = key(leader.getUniqueId(), def.id()).open();
        if (!HeroicLadder.mayOpen(key, open)) {
            return Messages.error("«" + def.displayName() + "»-д та +" + open + " хүртэлх түлхүүр нээж чадна (+" + key + " биш).");
        }
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            PlayerProfile pr = services.profiles().cached(id).orElse(null);
            if (p == null || pr == null) return Messages.error("Багийн гишүүн онлайн биш байна.");
            if (pr.progression().level() < HeroicLadder.PLAYER_LEVEL) {
                return Messages.error(p.getName() + ": баатарлаг агуй " + HeroicLadder.PLAYER_LEVEL + "-р түвшнээс нээгдэнэ.");
            }
            if (!services.dungeons().cleared(p, def.id())) {
                return Messages.error(p.getName() + " эхлээд «" + def.displayName() + "»-г энгийнээр нэг удаа давах ёстой.");
            }
        }
        return null;
    }

    /** The heroic part of a run of {@code def} at {@code key} led by {@code leader}, from this week's affixes. */
    public HeroicRun open(DungeonDefinition def, int key, UUID leader) {
        int limit = HeroicLadder.timeLimitSeconds(def.totalWaves(), def.bossDefinition().enrageSeconds());
        String week = weekId();
        List<SteppeAffix> affixes = forced.remove(leader);
        return new HeroicRun(def.id(), key, affixes != null ? affixes : HeroicLadder.affixes(key, week), week, limit);
    }

    /** Staff QA: {@code leader}'s next heroic run has these affixes (null clears it). */
    public void force(UUID leader, List<SteppeAffix> affixes) {
        if (affixes == null) forced.remove(leader);
        else forced.put(leader, List.copyOf(affixes));
    }

    /** The banner line when a heroic run begins. */
    public Component intro(HeroicRun hr) {
        return Component.text("Баатарлаг +" + hr.key + " · " + format(hr.limitSeconds) + " · ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(hr.affixes.isEmpty() ? "нөхцөлгүй" : hr.affixNames(), NamedTextColor.AQUA));
    }

    // ---- the lift and «Чонын Омог» --------------------------------------------------------------------------------

    /**
     * Every hit of a heroic hall's mob on a player, after its content-level amount is set (NORMAL): × the lift to level
     * 60 and the key (and «Чонын Омог»'s pack bonus). HIGH, multiplicative, so its order among the other HIGH reductions
     * does not matter.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeroicHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) src = shooter;
        if (src instanceof Player) return;
        HeroicRun hr = services.dungeons().heroicOf(src.getUniqueId()).orElse(null);
        if (hr == null) return;
        MobDefinition def = services.mobs().mobId(src).map(SuldContent::mobFor).orElse(null);
        if (def == null) return;
        double f = hr.damageFactor(def);
        if (hr.has(SteppeAffix.CHONYN_OMOG)) f *= SteppeAffix.packMultiplier(pack(src, hr));
        e.setDamage(e.getDamage() * f);
    }

    /** Other living mobs of the same heroic run within «Чонын Омог»'s radius. */
    private int pack(Entity src, HeroicRun hr) {
        int n = 0;
        for (Entity near : src.getNearbyEntities(SteppeAffix.PACK_RADIUS, SteppeAffix.PACK_RADIUS, SteppeAffix.PACK_RADIUS)) {
            if (near instanceof LivingEntity le && !le.isDead() && !(near instanceof Player)
                    && services.dungeons().heroicOf(near.getUniqueId()).orElse(null) == hr) n++;
        }
        return n;
    }

    // ---- «Галт Ган» -----------------------------------------------------------------------------------------------

    /** A heroic hall mob died (DungeonService, before it forgets it): «Галт Ган» bursts it into embers. */
    public void mobDied(HeroicRun hr, LivingEntity mob, Collection<Player> fighters) {
        if (!hr.has(SteppeAffix.GAN)) return;
        MobDefinition def = services.mobs().mobId(mob).map(SuldContent::mobFor).orElse(null);
        if (def == null) return;
        Location at = mob.getLocation();
        World w = at.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.LAVA, at.clone().add(0, 0.5, 0), 18, 0.8, 0.3, 0.8, 0);
        w.spawnParticle(Particle.FLAME, at.clone().add(0, 0.5, 0), 40, 1.2, 0.4, 1.2, 0.03);
        w.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.7f);
        double dmg = def.atLevel(HeroicLadder.MOB_LEVEL).scaledAttack() * HeroicLadder.damageMultiplier(hr.key) * SteppeAffix.EMBER_DAMAGE;
        mn.suld.plugin.skill.SkillTreeService tree = services.skillTree();
        for (Player p : fighters) {
            if (!p.getWorld().equals(w) || p.getLocation().distanceSquared(at) > SteppeAffix.EMBER_RADIUS * SteppeAffix.EMBER_RADIUS) continue;
            // no damager on this hit, so CombatListener.onMitigate does not see it: SÜLD armour is applied here
            double armor = tree == null ? 0 : tree.build(p).stat(mn.suld.api.skill.tree.StatKey.ARMOR);
            p.damage(dmg * (1 - mn.suld.api.balance.CombatRules.mitigation(Math.max(0, armor), HeroicLadder.MOB_LEVEL)));
            p.setFireTicks(Math.max(p.getFireTicks(), 40));
        }
    }

    /** «Галт Ган» halves the class resource's regeneration of a player in such a run (SkillService). */
    public double regenFactor(Player p) {
        HeroicRun hr = services.dungeons().heroicOfPlayer(p.getUniqueId()).orElse(null);
        return hr != null && hr.has(SteppeAffix.GAN) ? SteppeAffix.GAN_REGEN : 1.0;
    }

    // ---- the run's ticker: the clock, «Цагаан Зуд», «Тэнгэрийн Ниргэлт» ---------------------------------------------

    /** Every 10 ticks of a heroic run (DungeonService.tick). */
    public void tick(HeroicRun hr, List<Player> fighters, boolean bossFight) {
        long now = System.currentTimeMillis();
        double clock = hr.clock(now);
        boolean late = clock > hr.limitSeconds;
        Component line = Component.text("Баатарлаг +" + hr.key + " · " + format(clock) + " / " + format(hr.limitSeconds)
                + (hr.deaths > 0 ? " · унасан " + hr.deaths + " (+" + hr.deaths * HeroicLadder.DEATH_PENALTY_SECONDS + " с)" : "")
                + (hr.has(SteppeAffix.ZUD) ? " · зуд " + Math.max(0, (hr.nextZudAt - now + 999) / 1000) + " с" : ""),
                late ? NamedTextColor.RED : NamedTextColor.GOLD);
        for (Player p : fighters) HudService.notice(p, line);
        if (late && !hr.warnedLimit) {
            hr.warnedLimit = true;
            for (Player p : fighters) p.sendMessage(Messages.error("⌛ Хугацаа хэтэрлээ: дуусгавал агуйн шагнал ирнэ, гэхдээ түлхүүр ахихгүй, хас чулуу олдохгүй."));
        }
        if (hr.has(SteppeAffix.ZUD) && now >= hr.nextZudAt) {
            hr.nextZudAt = now + SteppeAffix.ZUD_PERIOD_SECONDS * 1000L;
            for (Player p : fighters) zud(p);
        }
        if (hr.has(SteppeAffix.TENGERIIN_NIRGELT) && bossFight) {
            if (hr.nextStrikeAt == 0) hr.nextStrikeAt = now + 4000; // a moment after the boss wakes
            if (now >= hr.nextStrikeAt) {
                hr.nextStrikeAt = now + SteppeAffix.STRIKE_PERIOD_SECONDS * 1000L;
                for (Player p : fighters) strike(p.getLocation().clone());
            }
        }
    }

    /** «Цагаан Зуд» passes over {@code p}: warm by a heat source, or frozen. */
    private void zud(Player p) {
        Location at = p.getLocation();
        World w = at.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1.5, 0), 40, 2, 1, 2, 0.02);
        if (warm(p)) {
            w.spawnParticle(Particle.SMALL_FLAME, at.clone().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.01);
            HudService.notice(p, Component.text("Цагаан Зуд: гал таныг дулаацууллаа", NamedTextColor.GOLD));
            return;
        }
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(zudKey) == null) {
            speed.addTransientModifier(new AttributeModifier(zudKey, -SteppeAffix.ZUD_SLOW,
                    AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
            Bukkit.getScheduler().runTaskLater(plugin, () -> clearZud(p), SteppeAffix.ZUD_SLOW_SECONDS * 20L);
        }
        p.setFreezeTicks(Math.max(p.getFreezeTicks(), 120));
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.damage((max == null ? 20 : max.getValue()) * SteppeAffix.ZUD_FROSTBITE);
        p.playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
        p.sendMessage(Messages.error("Цагаан Зуд: гал, бамбар, дэнлүүгүй тул хөлдлөө (40 % удаан " + SteppeAffix.ZUD_SLOW_SECONDS
                + " с). Гартаа бамбар эсвэл дэнлүү барь."));
    }

    private void clearZud(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(zudKey) != null) speed.removeModifier(zudKey);
    }

    /** A heat source in either hand, or a lit one within {@link SteppeAffix#ZUD_HEAT_RADIUS} blocks. */
    private boolean warm(Player p) {
        if (HEAT_ITEMS.contains(p.getInventory().getItemInMainHand().getType())
                || HEAT_ITEMS.contains(p.getInventory().getItemInOffHand().getType())) return true;
        int r = (int) Math.ceil(SteppeAffix.ZUD_HEAT_RADIUS);
        Block c = p.getLocation().getBlock();
        double r2 = SteppeAffix.ZUD_HEAT_RADIUS * SteppeAffix.ZUD_HEAT_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r2) continue;
                    Block b = c.getRelative(dx, dy, dz);
                    if (!HEAT_BLOCKS.contains(b.getType())) continue;
                    if (b.getBlockData() instanceof Lightable l && !l.isLit()) continue;
                    return true;
                }
            }
        }
        return false;
    }

    /** «Тэнгэрийн Ниргэлт»: a mark at {@code at}, lightning on it {@link SteppeAffix#STRIKE_TELEGRAPH_MS} ms later. */
    private void strike(Location at) {
        World w = at.getWorld();
        if (w == null) return;
        Particle.DustOptions red = new Particle.DustOptions(org.bukkit.Color.fromRGB(0xFF, 0x30, 0x30), 1.4f);
        long periods = SteppeAffix.STRIKE_TELEGRAPH_MS / 250;
        for (int i = 0; i < periods; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int a = 0; a < 24; a++) {
                    double t = Math.PI * 2 * a / 24;
                    w.spawnParticle(Particle.DUST, at.clone().add(Math.cos(t) * SteppeAffix.STRIKE_RADIUS, 0.15,
                            Math.sin(t) * SteppeAffix.STRIKE_RADIUS), 1, 0, 0, 0, 0, red);
                }
            }, i * 5L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            w.strikeLightningEffect(at);
            for (Player p : w.getPlayers()) {
                Location pl = p.getLocation();
                if (Math.hypot(pl.getX() - at.getX(), pl.getZ() - at.getZ()) > SteppeAffix.STRIKE_RADIUS || Math.abs(pl.getY() - at.getY()) > 3) continue;
                if (services.dungeons().heroicOfPlayer(p.getUniqueId()).isEmpty()) continue;
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                p.damage((max == null ? 20 : max.getValue()) * SteppeAffix.STRIKE_DAMAGE);
            }
        }, SteppeAffix.STRIKE_TELEGRAPH_MS / 50);
    }

    // ---- the result -------------------------------------------------------------------------------------------------

    /**
     * A heroic run ended. {@code rewarded} are the members present and standing at a clear (none on a failure): a timed
     * key opens the next ones for each of them and pays the jade. Every run is logged once.
     */
    public void finished(HeroicRun hr, DungeonDefinition def, Collection<UUID> members, List<Player> rewarded, boolean cleared) {
        long now = System.currentTimeMillis();
        double elapsed = hr.elapsed(now);
        int clock = (int) Math.round(hr.clock(now));
        HeroicLadder.Outcome o = HeroicLadder.outcome(cleared, elapsed, hr.deaths, hr.limitSeconds);
        int jade = HeroicLadder.jade(hr.key, o);
        for (Player p : rewarded) {
            UUID id = p.getUniqueId();
            HeroicRepository.Key k = key(id, def.id());
            int open = HeroicLadder.openAfter(k.open(), hr.key, o, elapsed, hr.deaths, hr.limitSeconds);
            int best = o == HeroicLadder.Outcome.TIMED ? Math.max(k.best(), hr.key) : k.best();
            int seconds = o != HeroicLadder.Outcome.TIMED ? k.bestSeconds()
                    : hr.key > k.best() ? clock : hr.key == k.best() ? (k.bestSeconds() == 0 ? clock : Math.min(k.bestSeconds(), clock)) : k.bestSeconds();
            keys.computeIfAbsent(id, x -> new ConcurrentHashMap<>()).put(def.id(), new HeroicRepository.Key(def.id(), open, best, seconds));
            repo.raise(id, def.id(), open, o == HeroicLadder.Outcome.TIMED ? hr.key : 0, clock, now).whenComplete((v, ex) -> {
                if (ex != null) plugin.getLogger().log(Level.WARNING, "[heroic] key of " + id + " not stored", ex);
            });
            if (jade > 0) giveJade(p, jade, hr);
            p.sendMessage(summary(hr, o, clock, open, jade));
        }
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || rewarded.contains(p)) continue;
            // fallen or gone at the end (or a failed run): like the normal reward, nothing moves for them
            int open = key(id, def.id()).open();
            p.sendMessage(o == HeroicLadder.Outcome.FAILED ? summary(hr, o, clock, open, 0)
                    : Messages.info("Баатарлаг +" + hr.key + ": баг тань " + format(clock) + "-д дуусгалаа, гэхдээ та эцэст нь зогсож байгаагүй тул "
                    + "түлхүүр тань хэвээр (+" + open + " хүртэл)."));
        }
        StringBuilder names = new StringBuilder();
        for (UUID id : members) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            if (n == null) continue;
            if (!names.isEmpty()) names.append(',');
            names.append(n);
        }
        plugin.getLogger().info("[heroic] " + def.id() + " +" + hr.key + " " + o + " " + format(clock) + " / " + format(hr.limitSeconds)
                + " deaths " + hr.deaths + " affixes " + (hr.affixes.isEmpty() ? "-" : hr.affixIds()) + " jade " + jade + " members " + names);
        repo.logRun(new HeroicRepository.Run(hr.runId, def.id(), hr.key, o, clock, hr.deaths, hr.affixIds(), names.toString(), hr.weekId, now))
                .whenComplete((v, ex) -> {
                    if (ex != null) plugin.getLogger().log(Level.WARNING, "[heroic] run " + hr.runId + " not logged", ex);
                });
        if (o == HeroicLadder.Outcome.TIMED && hr.key >= 10) {
            Bukkit.broadcast(Component.text("✦ «" + def.displayName() + "» баатарлаг +" + hr.key + " " + format(clock) + "-д давагдлаа: ",
                    NamedTextColor.GOLD, TextDecoration.BOLD).append(Component.text(names.toString().replace(",", ", "), NamedTextColor.WHITE)));
        }
        for (UUID id : members) {
            services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("heroic_run", id, Map.<String, Object>of(
                    "dungeon", def.id(), "key", hr.key, "outcome", o.name(), "clock", clock, "deaths", hr.deaths, "affixes", hr.affixIds())));
        }
    }

    private Component summary(HeroicRun hr, HeroicLadder.Outcome o, int clock, int open, int jade) {
        return switch (o) {
            case TIMED -> Messages.success("Баатарлаг +" + hr.key + " хугацаандаа: " + format(clock) + " / " + format(hr.limitSeconds)
                    + " · одоо +" + open + " хүртэл нээлттэй · +" + jade + " Эртний Хас Чулуу");
            case OVERTIME -> Messages.info("Баатарлаг +" + hr.key + " давлаа, гэхдээ хугацаа хэтэрсэн: " + format(clock) + " / "
                    + format(hr.limitSeconds) + " · түлхүүр ахисангүй");
            case FAILED -> Messages.error("Баатарлаг +" + hr.key + " бүтсэнгүй · түлхүүр хэвээр (+" + open + " хүртэл)");
        };
    }

    private void giveJade(Player p, int n, HeroicRun hr) {
        mn.suld.plugin.item.ItemService items = services.itemService();
        ItemDefinition def = items == null ? null : items.catalog().item(AffixReroll.JADE).orElse(null);
        if (def == null) {
            plugin.getLogger().warning("[heroic] " + AffixReroll.JADE + " missing from the item catalog: no jade paid");
            return;
        }
        items.give(p, List.of(new LootDrop(items.generate(def, null, 1, p.getUniqueId(), "heroic:" + hr.dungeonId + ":" + hr.runId), n)), "heroic");
    }

    /** m:ss */
    public static String format(double seconds) {
        long s = Math.max(0, Math.round(seconds));
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
