package mn.suld.plugin.hud;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.EquipSlot;
import mn.suld.api.item.Equipment;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.skill.ResourcePool;
import mn.suld.api.skill.Spell;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.combat.CombatListener;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.perf.PerfProbe;
import mn.suld.plugin.skill.SkillService;
import mn.suld.plugin.skill.SkillTreeService;
import mn.suld.plugin.ui.Glyphs;
import mn.suld.plugin.ui.StyleFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live in-game HUD of {@link HudService}: reads every player's real state (health, class resource, level and EXP,
 * spells and cooldowns, status effects, coins, skill points, equipment, mount/breath/food, the current target) and
 * draws the bottom panel into the action bar and the target frame into a boss bar, with the {@code suld:hud} glyphs.
 * Players whose client has not loaded the SÜLD pack get the plain-text bar instead.
 *
 * <p>Refresh: event-driven (damage, healing, food, casts, combo clicks, notices → drawn on the next tick) plus a
 * 4-tick pass for countdowns and the crosshair target; a line is only sent when it changed, or every 20 ticks to keep
 * the action bar from fading.
 */
final class HudPanel implements Listener {

    static final int PERIOD_TICKS = 4;
    static final long KEEPALIVE_MS = 1000;
    static final long TARGET_HOLD_MS = 12_000;
    static final long LOOK_HOLD_MS = 3_000;
    static final double TARGET_RANGE = 24;

    private final Plugin plugin;
    private final SuldServices services;
    private SkillService skills;

    private final Set<UUID> dirty = new LinkedHashSet<>();
    private final Map<UUID, Sent> sent = new ConcurrentHashMap<>();
    private final Map<UUID, Toast> toasts = new ConcurrentHashMap<>();
    private final Map<UUID, Chip> hpChips = new ConcurrentHashMap<>();
    private final Map<UUID, Target> targets = new ConcurrentHashMap<>();
    /** The lock-on target (CombatFeel), preferred over whatever the crosshair touches. */
    private java.util.function.Function<Player, java.util.Optional<? extends Entity>> lockOn = p -> java.util.Optional.empty();

    public void lockOn(java.util.function.Function<Player, java.util.Optional<? extends Entity>> f) {
        lockOn = f == null ? p -> java.util.Optional.empty() : f;
    }
    private final Map<UUID, BossBar> targetBars = new ConcurrentHashMap<>();
    private long frame;

    private record Sent(Component line, long at) {
    }

    record Toast(List<HudState.Run> runs, Component original, long until) {
    }

    private static final class Target {
        final UUID entity;
        long until;
        final Chip chip = new Chip();

        Target(UUID entity, long until) {
            this.entity = entity;
            this.until = until;
        }
    }

    /** The pale "damage chip": after any drop it holds the previous health for a moment, then snaps down. */
    static final class Chip {
        static final long HOLD_MS = 700;
        double shown = -1;
        double last = -1;
        long since;

        double update(double hp, long now) {
            if (shown < 0) {
                shown = last = hp;
                return hp;
            }
            if (hp < last) {               // a new drop: keep showing where it was, restart the hold
                shown = Math.max(shown, last);
                since = now;
            }
            last = hp;
            if (hp >= shown || now - since > HOLD_MS) shown = hp;
            return shown;
        }

        /** A hit is about to land (the health is still {@code before}): start the hold from there. */
        void hit(double before, long now) {
            if (shown < before) shown = before;
            if (last < before) last = before;
            since = now;
        }
    }

    HudPanel(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    void skills(SkillService skills) {
        this.skills = skills;
    }

    private long staggerTick;

    /** Measurement switch (/suldperf hud off): skips drawing so the HUD's cost can be A/B-measured. */
    volatile boolean enabled = true;

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::flushDirty, 1L, 1L);
        // every player is redrawn once per PERIOD_TICKS, but a quarter of them on each tick (it used to be everyone on
        // the same tick: an ~8 ms spike every 4th tick at 100 players); events still redraw at once via flushDirty
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long t = PerfProbe.start();
            long tick = ++staggerTick;
            if (tick % PERIOD_TICKS == 0) frame++;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (mn.suld.plugin.perf.Stagger.due(p.getUniqueId(), tick, PERIOD_TICKS)) render(p, false);
            }
            PerfProbe.stop("hud.panel_tick", t);
        }, 1L, 1L);
    }

    // ------------------------------------------------------------------ API used by HudService

    void refresh(Player p) {
        synchronized (dirty) {
            dirty.add(p.getUniqueId());
        }
    }

    void toast(Player p, Component message, long millis) {
        String text = PlainTextComponentSerializer.plainText().serialize(message).strip();
        if (text.isEmpty()) return;
        TextColor color = firstColor(message);
        toasts.put(p.getUniqueId(), new Toast(List.of(new HudState.Run(text, color == null ? NamedTextColor.WHITE : color)), message,
                System.currentTimeMillis() + millis));
        if (!glyphHud(p)) p.sendActionBar(message); // plain clients see the message itself
        refresh(p);
    }

    private static TextColor firstColor(Component c) {
        if (c.color() != null && c instanceof TextComponent t && !t.content().isBlank()) return c.color();
        for (Component child : c.children()) {
            TextColor x = firstColor(child);
            if (x != null) return x;
        }
        return c.color();
    }

    void forget(UUID id) {
        lastEventRender.remove(id);
        sent.remove(id);
        toasts.remove(id);
        hpChips.remove(id);
        targets.remove(id);
        BossBar b = targetBars.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (b != null && p != null) p.hideBossBar(b);
    }

    private void flushDirty() {
        List<UUID> now;
        synchronized (dirty) {
            if (dirty.isEmpty()) return;
            now = new ArrayList<>(dirty);
            dirty.clear();
        }
        long tick = Bukkit.getCurrentTick();
        for (UUID id : now) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                lastEventRender.remove(id);
                continue;
            }
            // at most one event redraw per player every 2 ticks (10 Hz): regen and cooldowns mark players dirty almost
            // every tick in a fight, and the action bar cannot show faster than that anyway; the rest waits a tick
            Long last = lastEventRender.get(id);
            if (last != null && tick - last < 2) {
                synchronized (dirty) {
                    dirty.add(id);
                }
                continue;
            }
            lastEventRender.put(id, tick);
            render(p, true);
        }
    }

    private final Map<UUID, Long> lastEventRender = new java.util.HashMap<>();

    /** True when the player's client has the SÜLD pack loaded (the glyph HUD needs its font). */
    boolean glyphHud(Player p) {
        var packs = services.resourcePacks();
        return packs != null && packs.statusOf(p.getUniqueId()) == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
    }

    // ------------------------------------------------------------------ rendering

    void render(Player p, boolean urgent) {
        if (!enabled) return;
        if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
            hideTarget(p);
            return;
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        long t0 = PerfProbe.start();
        long now = System.currentTimeMillis();
        boolean glyphs = glyphHud(p);
        Component line;
        if (glyphs) {
            HudState s = state(p, pr, now);
            line = HudComposer.panel(s, (frame & 1) == 0);
            target(p, now);
        } else {
            line = plainBar(p, pr, now);
            hideTarget(p);
        }
        Sent last = sent.get(p.getUniqueId());
        if (line != null && (last == null || !last.line().equals(line) || now - last.at() > KEEPALIVE_MS)) {
            Toast toast = toasts.get(p.getUniqueId());
            boolean plainToastShowing = !glyphs && toast != null && toast.until() > now;
            if (!plainToastShowing) p.sendActionBar(line);
            sent.put(p.getUniqueId(), new Sent(line, now));
        }
        PerfProbe.stop(urgent ? "hud.render_event" : "hud.render", t0);
    }

    HudState state(Player p, PlayerProfile pr, long now) {
        var maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = maxAttr == null ? 20 : maxAttr.getValue();
        double hp = Math.min(p.getHealth(), maxHp);
        double chip = hpChips.computeIfAbsent(p.getUniqueId(), k -> new Chip()).update(hp, now);

        HudState.LowBar low;
        if (p.getVehicle() instanceof AbstractHorse horse) {
            var hm = horse.getAttribute(Attribute.MAX_HEALTH);
            low = new HudState.LowBar(HudState.LowKind.MOUNT, horse.getHealth(), hm == null ? 20 : hm.getValue());
        } else if (p.getRemainingAir() < p.getMaximumAir()) {
            low = new HudState.LowBar(HudState.LowKind.AIR, Math.max(0, p.getRemainingAir()) / 30.0, p.getMaximumAir() / 30.0);
        } else {
            low = new HudState.LowBar(HudState.LowKind.FOOD, p.getFoodLevel(), 20);
        }

        PlayerClass clazz = pr.playerClass().orElse(null);
        int res = 0, resMax = 0;
        if (clazz != null && skills != null) {
            ResourcePool pool = skills.pool(p);
            res = pool.value();
            resMax = pool.max();
        }
        Progression prog = pr.progression();
        var engine = services.progression().engine();
        boolean maxLevel = engine.expToNextLevel(prog) <= 0;
        return new HudState(hp, maxHp, p.getAbsorptionAmount(), chip, low, res, resMax, clazz, prog.level(),
                maxLevel ? 1 : engine.progressFraction(prog), maxLevel,
                slots(p, clazz, prog.level(), res), buffs(p), line(p, pr, clazz, now));
    }

    private List<HudState.Slot> slots(Player p, PlayerClass clazz, int level, int resource) {
        if (clazz == null || skills == null) return List.of();
        List<HudState.Slot> out = new ArrayList<>();
        TextColor color = HudComposer.resourceColor(clazz);
        List<Spell> spells = new ArrayList<>();
        for (Spell s : Spell.values()) if (s.clazz() == clazz) spells.add(s);
        spells.sort(Comparator.comparingInt(Spell::slot));
        for (Spell s : spells) {
            HudState.SlotState st;
            double cd = skills.cooldownLeft(p, s);
            if (level < Spell.UNLOCK_LEVEL[s.slot()]) st = HudState.SlotState.LOCKED;
            else if (cd > 0.05) st = HudState.SlotState.COOLDOWN;
            else if (resource < skills.costFor(p, s)) st = HudState.SlotState.NO_RESOURCE;
            else st = HudState.SlotState.READY;
            // a locked slot carries its unlock level in place of a cooldown (the HUD prints it)
            out.add(new HudState.Slot(s.slot(), st, st == HudState.SlotState.LOCKED ? Spell.UNLOCK_LEVEL[s.slot()] : cd, color));
        }
        SkillTreeService tree = services.skillTree();
        if (tree != null && tree.ultimateOf(p) != null) {
            int cd = tree.ultimateCooldownSeconds(p);
            out.add(new HudState.Slot(0, cd > 0 ? HudState.SlotState.COOLDOWN : HudState.SlotState.READY, cd, color));
        }
        return out;
    }

    private static String secs(PotionEffect e) {
        if (e.isInfinite() || e.getDuration() > 20 * 3600) return "";
        int s = (int) Math.ceil(e.getDuration() / 20.0);
        return s >= 60 ? (s / 60) + "M" : Integer.toString(s);
    }

    List<HudState.Buff> buffs(Player p) {
        List<HudState.Buff> out = new ArrayList<>();
        if (services.isSoul.test(p.getUniqueId())) out.add(new HudState.Buff("SOUL", ""));
        double wound = services.woundFactor.applyAsDouble(p.getUniqueId());
        if (wound < 0.999) out.add(new HudState.Buff("WOUND", "-" + Math.round(100 * (1 - wound)) + "%"));
        if (services.equipment() != null && services.equipment().bonus(p).inactive().containsValue(Equipment.Inactive.BROKEN)) {
            out.add(new HudState.Buff("BROKEN", ""));
        }
        if (p.getFireTicks() > 0) out.add(new HudState.Buff("FIRE", Integer.toString((int) Math.ceil(p.getFireTicks() / 20.0))));
        Object[][] order = {
                {PotionEffectType.POISON, "POISON"}, {PotionEffectType.WITHER, "WITHER"}, {PotionEffectType.SLOWNESS, "SLOW"},
                {PotionEffectType.WEAKNESS, "WEAKNESS"}, {PotionEffectType.HUNGER, "HUNGER"}};
        for (Object[] o : order) {
            PotionEffect e = p.getPotionEffect((PotionEffectType) o[0]);
            if (e != null) out.add(new HudState.Buff((String) o[1], secs(e)));
        }
        Integer arrows = CombatListener.EMPOWERED_ARROWS.get(p.getUniqueId());
        Long until = CombatListener.EMPOWERED_UNTIL.get(p.getUniqueId());
        if (arrows != null && arrows > 0 && (until == null || until > System.currentTimeMillis())) out.add(new HudState.Buff("EMPOWER", "x" + arrows));
        Object[][] good = {
                {PotionEffectType.STRENGTH, "STRENGTH"}, {PotionEffectType.SPEED, "SPEED"}, {PotionEffectType.REGENERATION, "REGEN"},
                {PotionEffectType.ABSORPTION, "SHIELD"}, {PotionEffectType.RESISTANCE, "RESIST"}, {PotionEffectType.HASTE, "HASTE"},
                {PotionEffectType.JUMP_BOOST, "JUMP"}, {PotionEffectType.NIGHT_VISION, "NIGHT"}};
        for (Object[] o : good) {
            PotionEffect e = p.getPotionEffect((PotionEffectType) o[0]);
            if (e != null) out.add(new HudState.Buff((String) o[1], secs(e)));
        }
        if (services.relics() != null && services.relics().borneBy(p.getUniqueId()).isPresent()) out.add(new HudState.Buff("RELIC", ""));
        if (services.inCity(p.getLocation())) out.add(new HudState.Buff("SAFE", ""));
        return out;
    }

    private static final TextColor COIN = TextColor.color(0xFFD24A);
    private static final TextColor POINTS = TextColor.color(0x9FF3FF);
    private static final TextColor DIM = TextColor.color(0xC8C8C8);
    private static final TextColor COMBO = TextColor.color(0xFFE066);

    List<HudState.Run> line(Player p, PlayerProfile pr, PlayerClass clazz, long now) {
        String combo = skills == null ? "" : skills.comboInProgress(p);
        if (!combo.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) sb.append(i < combo.length() ? combo.charAt(i) : '_').append(i < 2 ? " - " : "");
            return List.of(new HudState.Run(sb.toString(), COMBO));
        }
        Toast toast = toasts.get(p.getUniqueId());
        if (toast != null) {
            if (toast.until() > now) return toast.runs();
            toasts.remove(p.getUniqueId());
        }
        if (clazz == null) return List.of(new HudState.Run("Ангиа сонго — /class", COMBO));
        List<HudState.Run> runs = new ArrayList<>();
        runs.add(new HudState.Run(clazz.displayName(), HudComposer.resourceColor(clazz)));
        runs.add(new HudState.Run("  ₮ " + String.format("%,d", pr.currency()), COIN));
        SkillTreeService tree = services.skillTree();
        int points = tree == null ? 0 : tree.available(p);
        if (points > 0) runs.add(new HudState.Run("  ◆ " + points, POINTS));
        if (services.equipment() != null) {
            Equipment.Bonus b = services.equipment().bonus(p);
            Map<EquipSlot, mn.suld.api.item.ItemInstance> worn = services.equipment().worn(p);
            if (!worn.isEmpty()) runs.add(new HudState.Run("  ⚔ " + Equipment.gearScore(worn, b.inactive()), DIM));
        }
        return runs;
    }

    // ------------------------------------------------------------------ target frame

    private void target(Player p, long now) {
        long t0 = PerfProbe.start();
        Entity locked = lockOn.apply(p).orElse(null);
        Entity look = locked != null ? locked : p.getTargetEntity((int) TARGET_RANGE, false);
        if (targetable(p, look)) {
            Target cur = targets.get(p.getUniqueId());
            if (cur == null || !cur.entity.equals(look.getUniqueId())) targets.put(p.getUniqueId(), new Target(look.getUniqueId(), now + LOOK_HOLD_MS));
            else cur.until = Math.max(cur.until, now + LOOK_HOLD_MS);
        }
        Target t = targets.get(p.getUniqueId());
        Entity e = t == null ? null : Bukkit.getEntity(t.entity);
        if (t == null || t.until < now || !(e instanceof LivingEntity le) || !le.isValid() || le.isDead()
                || !le.getWorld().equals(p.getWorld()) || le.getLocation().distanceSquared(p.getLocation()) > 48 * 48) {
            if (t != null) targets.remove(p.getUniqueId());
            hideTarget(p);
            PerfProbe.stop("hud.target", t0);
            return;
        }
        var ma = le.getAttribute(Attribute.MAX_HEALTH);
        double scale = mn.suld.plugin.mob.MobService.hpScale(le); // design numbers for a mob above the HP ceiling
        double max = (ma == null ? Math.max(1, le.getHealth()) : ma.getValue()) * scale;
        double hp = Math.min(le.getHealth() * scale, max);
        HudComposer.Target info = describe(le, hp, max, t.chip.update(hp, now));
        BossBar bar = targetBars.computeIfAbsent(p.getUniqueId(), k -> {
            BossBar b = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            p.showBossBar(b);
            return b;
        });
        Component title = HudComposer.target(info);
        if (!title.equals(bar.name())) bar.name(title);
        bar.progress((float) Math.max(0, Math.min(1, hp / max)));
        PerfProbe.stop("hud.target", t0);
    }

    private void hideTarget(Player p) {
        BossBar b = targetBars.remove(p.getUniqueId());
        if (b != null) p.hideBossBar(b);
    }

    static boolean targetable(Player viewer, Entity e) {
        if (!(e instanceof LivingEntity le) || e instanceof ArmorStand || e.equals(viewer)) return false;
        // an invisible host rendered by a model rig is a visible creature (docs/MODEL_RENDERER.md)
        if (le.isInvisible() && !e.getScoreboardTags().contains(mn.suld.plugin.model.ModelService.HOST_TAG)) return false;
        return !(e instanceof Player other) || other.getGameMode() != GameMode.SPECTATOR;
    }

    private HudComposer.Target describe(LivingEntity le, double hp, double max, double chip) {
        String id = services.mobs() == null ? null : services.mobs().mobId(le).orElse(null);
        MobDefinition def = id == null ? null : SuldContent.mobFor(id);
        if (def != null) {
            HudComposer.Tier tier = switch (def.tier()) {
                case NORMAL -> HudComposer.Tier.NORMAL;
                case ELITE, CHAMPION -> HudComposer.Tier.ELITE;
                default -> HudComposer.Tier.BOSS;
            };
            return new HudComposer.Target(def.displayName(), def.level(), hp, max, chip, tier, true);
        }
        if (le instanceof Player other) {
            int lvl = services.profiles().cached(other.getUniqueId()).map(x -> x.progression().level()).orElse(0);
            return new HudComposer.Target(other.getName(), lvl, hp, max, chip, HudComposer.Tier.NORMAL, false);
        }
        String name = le.customName() == null ? null : PlainTextComponentSerializer.plainText().serialize(le.customName());
        if (name == null || name.isBlank()) name = prettyType(le.getType().getKey().getKey());
        return new HudComposer.Target(name, 0, hp, max, chip, HudComposer.Tier.NORMAL, le instanceof org.bukkit.entity.Monster);
    }

    private static String prettyType(String key) {
        return switch (key) {
            case "wolf" -> "Чоно";
            case "zombie" -> "Зомби";
            case "skeleton" -> "Араг яс";
            case "spider" -> "Аалз";
            case "creeper" -> "Крийпер";
            case "horse" -> "Морь";
            case "sheep" -> "Хонь";
            case "cow" -> "Үхэр";
            case "pig" -> "Гахай";
            case "chicken" -> "Тахиа";
            case "goat" -> "Ямаа";
            case "camel" -> "Тэмээ";
            case "villager" -> "Иргэн";
            default -> key.replace('_', ' ');
        };
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        long now = System.currentTimeMillis();
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;
        if (damager instanceof Player attacker && e.getEntity() instanceof LivingEntity victim && targetable(attacker, victim)) {
            Target t = targets.get(attacker.getUniqueId());
            if (t == null || !t.entity.equals(victim.getUniqueId())) {
                t = new Target(victim.getUniqueId(), now + TARGET_HOLD_MS);
                targets.put(attacker.getUniqueId(), t);
            } else {
                t.until = now + TARGET_HOLD_MS;
            }
            t.chip.hit(mn.suld.plugin.mob.MobService.trueHealth(victim), now);
            refresh(attacker);
        }
        if (e.getEntity() instanceof Player victim && damager instanceof LivingEntity attacker && targetable(victim, attacker)) {
            Target t = targets.get(victim.getUniqueId());
            if (t == null || t.until < now) targets.put(victim.getUniqueId(), new Target(attacker.getUniqueId(), now + TARGET_HOLD_MS));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            hpChips.computeIfAbsent(p.getUniqueId(), k -> new Chip()).hit(p.getHealth(), System.currentTimeMillis());
            refresh(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p) refresh(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) refresh(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPack(PlayerResourcePackStatusEvent e) {
        sent.remove(e.getPlayer().getUniqueId());
        refresh(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        forget(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ plain clients (no pack)

    /** The text bar for clients without the SÜLD pack: ❤ HP · resource · combo / notice / ultimate and points. */
    private Component plainBar(Player p, PlayerProfile pr, long now) {
        PlayerClass c = pr.playerClass().orElse(null);
        if (c == null || skills == null) return null;
        ResourcePool pool = skills.pool(p);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        int maxHp = (int) Math.ceil(max == null ? 20 : max.getValue());
        int hp = Math.min((int) Math.ceil(p.getHealth()), maxHp);
        int filled = (int) Math.round(pool.fraction() * 10);
        TextColor rc = HudComposer.resourceColor(c);
        Component bar = Component.empty().append(StyleFormat.glyph(Glyphs.ICON_HEART)).append(Component.text(" " + hp + "/" + maxHp, NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("    " + c.resourceName().split(" ")[0] + " ", rc, TextDecoration.BOLD))
                .append(Component.text("▰".repeat(filled), rc)).append(Component.text("▱".repeat(10 - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + pool.value(), rc, TextDecoration.BOLD));
        String combo = skills.comboInProgress(p);
        String tail = null;
        if (!combo.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) sb.append(i < combo.length() ? combo.charAt(i) : '_').append(i < 2 ? "-" : "");
            tail = "§e§l" + sb;
        }
        SkillTreeService tree = services.skillTree();
        if (tail == null && tree != null) {
            StringBuilder sb = new StringBuilder();
            if (tree.ultimateOf(p) != null) {
                int cd = tree.ultimateCooldownSeconds(p);
                sb.append(cd > 0 ? "§7F ✦ " + cd + "с" : "§6§lF ✦ бэлэн");
            }
            int avail = tree.available(p);
            if (avail > 0) sb.append(sb.length() > 0 ? "  " : "").append("§b◆").append(avail).append(" оноо");
            if (sb.length() > 0) tail = sb.toString();
        }
        if (tail != null) bar = bar.append(Component.text("    ")).append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(tail).decoration(TextDecoration.BOLD, true));
        return bar;
    }

    /** Read-only view for tests and the QA command: which players currently have a target frame. */
    Set<UUID> withTarget() {
        return new HashSet<>(targetBars.keySet());
    }
}
