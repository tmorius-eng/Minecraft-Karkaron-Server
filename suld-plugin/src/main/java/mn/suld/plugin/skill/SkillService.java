package mn.suld.plugin.skill;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.ComboTracker;
import mn.suld.api.skill.ResourcePool;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.KeystoneKind;
import mn.suld.api.skill.tree.ModKey;
import mn.suld.api.skill.tree.SkillBuild;
import mn.suld.api.skill.tree.StatKey;
import mn.suld.api.skill.tree.TriggerEvent;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.combat.CombatListener;
import mn.suld.plugin.ui.Glyphs;
import mn.suld.plugin.ui.StyleFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class spells, Wynncraft style: while holding your class weapon, a three-click combo (R-L-R, R-R-R, R-L-L, R-R-L;
 * the archer starts with L) casts one of your four spells if your level unlocked it and you have the resource.
 * Spells hurt only hostile mobs, never players. The action bar shows HP, the class resource bar and the combo.
 */
public final class SkillService implements Listener {

    private static final long CLICK_DEBOUNCE_MS = 60;

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<UUID, ComboTracker> combos = new ConcurrentHashMap<>();
    private final Map<UUID, ResourcePool> pools = new ConcurrentHashMap<>();
    private final Map<String, Long> lastClickByType = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCast = new ConcurrentHashMap<>();
    private final Map<UUID, String> notice = new ConcurrentHashMap<>();
    /** The caster's ATK when the spell was cast: swapping weapons while it is still flying changes nothing. */
    private final Map<UUID, Double> castAttack = new ConcurrentHashMap<>();

    public SkillService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::regen, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> actionBar(), 10L, 10L);
    }

    private PlayerClass clazzOf(Player p) {
        return services.profiles().cached(p.getUniqueId()).flatMap(PlayerProfile::playerClass).orElse(null);
    }

    private SkillBuild buildOf(Player p) {
        return SkillTreeService.build(services, p);
    }

    private int poolSize(Player p) {
        PlayerClass c = clazzOf(p);
        SkillBuild b = buildOf(p);
        return (c == null ? 100 : c.resourceMax()) + (int) b.stat(StatKey.RESOURCE_MAX) + (b.has(KeystoneKind.TENGERTEI_KHOLBOGDOKH) ? 40 : 0);
    }

    public ResourcePool pool(Player p) {
        return pools.computeIfAbsent(p.getUniqueId(), k -> new ResourcePool(poolSize(p)));
    }

    /** The skill tree changed the pool's size: keep the current amount (no free refill), cap it at the new size. */
    public void rebuildPool(Player p) {
        ResourcePool old = pools.get(p.getUniqueId());
        int size = poolSize(p);
        if (old == null || old.max() == size) return;
        ResourcePool next = new ResourcePool(size);
        next.spend(size - Math.min(size, old.value()));
        pools.put(p.getUniqueId(), next);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        combos.remove(id);
        lastCast.remove(id);
        notice.remove(id);
        castAttack.remove(id);
        lastClickByType.keySet().removeIf(k -> k.startsWith(id.toString()));
        CombatListener.EMPOWERED_ARROWS.remove(id);
        CombatListener.EMPOWERED_UNTIL.remove(id);
        // the resource pool is kept: logging out and in again must not refill Хил / Сүнс mid-fight
    }

    // ------------------------------------------------------------------ clicks

    private boolean holdsClassWeapon(Player p, PlayerClass c) {
        return services.items().read(p.getInventory().getItemInMainHand())
                .map(i -> i.definitionId().startsWith("weapon.class." + c.id() + ".")).orElse(false);
    }

    public boolean holdsClassWeapon(Player p) {
        PlayerClass c = clazzOf(p);
        return c != null && holdsClassWeapon(p, c);
    }

    /**
     * A right click on a block is often "cancelled" by block protection (the city denies using most blocks): that says
     * nothing about casting, so cancelled interacts still count (the class weapon check decides).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) click(e.getPlayer(), 'L');
        else if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) click(e.getPlayer(), 'R');
    }

    /** A left click always swings the arm (air, block or entity); the debounce merges it with the other events. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(org.bukkit.event.player.PlayerAnimationEvent e) {
        if (e.getAnimationType() == org.bukkit.event.player.PlayerAnimationType.ARM_SWING) click(e.getPlayer(), 'L');
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() == EquipmentSlot.HAND) click(e.getPlayer(), 'R');
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (CombatListener.spellDamage) return;
        if (e.getDamager() instanceof Player p) {
            click(p, 'L');
            PlayerClass c = clazzOf(p);
            if (c == PlayerClass.BAATAR) pool(p).gain(4);   // Хил grows in battle
            if (c == PlayerClass.DARKHAN) pool(p).gain(3);  // the forge heats with every blow
        }
    }

    private void click(Player p, char c) {
        PlayerClass clazz = clazzOf(p);
        if (clazz == null || !holdsClassWeapon(p, clazz)) return;
        long now = System.currentTimeMillis();
        // one physical click can fire two events (interact + swing, or hit + swing): keep the first
        String key = p.getUniqueId() + ":" + c;
        Long last = lastClickByType.put(key, now);
        if (last != null && now - last < CLICK_DEBOUNCE_MS) return;
        ComboTracker t = combos.computeIfAbsent(p.getUniqueId(), k -> new ComboTracker(clazz));
        if (t.clazz() != clazz) {
            t = new ComboTracker(clazz);
            combos.put(p.getUniqueId(), t);
        }
        ComboTracker.Result r = t.click(c, now);
        if (r.kind() == ComboTracker.Kind.PROGRESS) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, r.combo().length() == 1 ? 1.4f : 1.7f);
            actionBar(p);
        } else if (r.kind() == ComboTracker.Kind.COMPLETE) {
            t.spellFor(r.combo()).ifPresent(s -> cast(p, s));
        }
    }

    // ------------------------------------------------------------------ casting

    private void cast(Player p, Spell s) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        if (services.isSoul.test(p.getUniqueId())) { // a soul cannot cast
            say(p, "§bСүнс — ид шид хэрэглэх боломжгүй");
            return;
        }
        if (pr.progression().level() < s.unlockLevel()) {
            say(p, "§c" + s.displayName() + " — түвшин " + s.unlockLevel());
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }
        long now = System.currentTimeMillis();
        Long lc = lastCast.get(p.getUniqueId());
        if (lc != null && now - lc < 400) return;
        SkillBuild build = buildOf(p);
        int cost = costOf(build, s);
        if (!pool(p).spend(cost)) {
            say(p, "§c" + s.displayName() + " — нөөц хүрэлцэхгүй (" + cost + ")");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }
        lastCast.put(p.getUniqueId(), now);
        castAttack.put(p.getUniqueId(), CombatListener.attackOf(services, p));
        say(p, "§e✦ " + s.displayName());
        run(p, s);
        double echo = build.mod(s, ModKey.ECHO_PCT);
        if (echo > 0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100 < echo) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !p.isDead()) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
                    run(p, s);
                }
            }, 12L);
        }
        double shield = build.mod(s, ModKey.SHIELD);
        if (shield > 0) services.skillTree().shield(p, shield, 120);
        double haste = build.mod(s, ModKey.HASTE);
        if (haste > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (haste * 20), 1, false, false, true));
        services.skillTree().fire(p, TriggerEvent.CAST, null);
    }

    /** Mana cost after spell modifiers and cooldown/cost reduction (never below 1). */
    private static int costOf(SkillBuild b, Spell s) {
        double factor = 1 + b.mod(s, ModKey.COST_PCT) / 100.0 - b.stat(StatKey.COST_REDUCTION) / 100.0;
        return Math.max(1, (int) Math.round(s.cost() * Math.max(0.2, factor)));
    }

    private void run(Player p, Spell s) {
        switch (s) {
            case TENGER_TSAVCHILT -> cone(p, s, 4.5, 0.45, Particle.SWEEP_ATTACK, Sound.ENTITY_PLAYER_ATTACK_SWEEP);
            case DAINY_KHASHGIRAAN -> warCry(p, s);
            case DOVTLOKH_USRELT -> leap(p, s);
            case KHAAN_KHAMGAALALT -> buff(p, Sound.ITEM_TOTEM_USE, Particle.TOTEM_OF_UNDYING,
                    new PotionEffect(PotionEffectType.ABSORPTION, 200, 1), new PotionEffect(PotionEffectType.RESISTANCE, 120, 1));
            case CHONYN_NUD -> wolfEye(p, s);
            case OLON_SUM -> volley(p);
            case UKHRAKH_USRELT -> disengage(p, s);
            case TENGERIIN_SUM -> beam(p, s, 30, Particle.END_ROD, Sound.ENTITY_ILLUSIONER_CAST_SPELL);
            case SUNSNII_ZALBIRAL -> heal(p, s, 8, 8);
            case ONGONY_DUUDLAGA -> ongon(p, s);
            case KHENGERGIIN_DUU -> drum(p, s);
            case TENGERIIN_KHAALGA -> skyGate(p, s);
            case GALYN_DAVTALT -> slam(p, s);
            case GAN_BAMBAI -> buff(p, Sound.ITEM_ARMOR_EQUIP_NETHERITE, Particle.CRIT,
                    new PotionEffect(PotionEffectType.RESISTANCE, 120, 1));
            case KHAILSAN_TUMUR -> moltenSpray(p, s);
            case DARKHANY_DARANGUI -> anvil(p, s);
            case KHURDAN_DOVTOLGOO -> charge(p, s);
            case SALKHINY_KHURD -> buff(p, Sound.ENTITY_BREEZE_WIND_BURST, Particle.CLOUD,
                    new PotionEffect(PotionEffectType.SPEED, 120, 2));
            case ZHADNY_SHIDELT -> beam(p, s, 18, Particle.CRIT, Sound.ITEM_TRIDENT_THROW);
            case KHULGIIN_DAIRALT -> stampede(p, s);
        }
    }

    public void say(Player p, String text) {
        notice.put(p.getUniqueId(), text + "§r@" + (System.currentTimeMillis() + 1500));
        actionBar(p);
    }

    // ------------------------------------------------------------------ spell helpers

    private boolean hostile(Entity e, Player caster) {
        return e instanceof LivingEntity le && le.isValid() && !(e instanceof Player) && e != caster
                && (services.mobs().isSuldMob(e) || e instanceof Monster) && !e.getScoreboardTags().contains("city_npc");
    }

    private void hurt(Player p, LivingEntity target, Spell s) {
        Long cast = lastCast.get(p.getUniqueId());
        Double snap = castAttack.get(p.getUniqueId());
        boolean fresh = cast != null && snap != null && System.currentTimeMillis() - cast < 5_000;
        SkillBuild b = buildOf(p);
        double mult = s.damageMultiplier() * (1 + b.mod(s, ModKey.DAMAGE_PCT) / 100.0) * services.skillTree().spellDamageMultiplier(p);
        double dmg = (fresh ? snap : CombatListener.attackOf(services, p)) * mult;
        CombatListener.spellDamage = true;
        try {
            target.damage(dmg, p);
        } finally {
            CombatListener.spellDamage = false;
        }
        riders(p, target, s);
        services.skillTree().fire(p, TriggerEvent.SPELL_HIT, target);
    }

    /** Everything a spell modifier adds to an enemy the spell touched (burn, slow, mark, pull, life, refund...). */
    public void riders(Player p, LivingEntity target, Spell s) {
        SkillBuild b = buildOf(p);
        if (b.isEmpty() || !target.isValid()) return;
        double burn = b.mod(s, ModKey.BURN) + (b.has(KeystoneKind.ALTAN_DOSH) && s.damageMultiplier() > 0 ? 3 : 0);
        if (burn > 0) target.setFireTicks(Math.max(target.getFireTicks(), (int) (burn * 20)));
        double slow = b.mod(s, ModKey.SLOW);
        if (slow > 0) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (slow * 20), 1));
        double weaken = b.mod(s, ModKey.WEAKEN);
        if (weaken > 0) target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int) (weaken * 20), 0));
        double vuln = b.mod(s, ModKey.VULN);
        if (vuln > 0) services.skillTree().mark(target, vuln, 4);
        double heal = b.mod(s, ModKey.HEAL_ON_HIT);
        if (heal > 0) services.skillTree().heal(p, heal);
        double refund = b.mod(s, ModKey.REFUND);
        if (refund > 0) pool(p).gain(refund);
        double up = b.mod(s, ModKey.KNOCKUP);
        if (up > 0) target.setVelocity(target.getVelocity().add(new Vector(0, 0.25 + 0.1 * up, 0)));
        double pull = b.mod(s, ModKey.PULL);
        if (pull > 0) {
            Vector to = p.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
            if (to.lengthSquared() > 1) target.setVelocity(to.normalize().multiply(0.5 * pull).setY(0.2));
        }
    }

    private double r(Player p, Spell s, double radius) {
        return radius * (1 + buildOf(p).mod(s, ModKey.RADIUS_PCT) / 100.0);
    }

    /** Enemies of the player around a point (used by procs and ultimates too). */
    public List<LivingEntity> enemiesAround(Player p, Location c, double radius) {
        return around(p, c, radius);
    }

    /** Damage from a passive spell or ultimate: ATK-based, never replaced by the hit formula, never triggering hit procs. */
    public void damage(Player p, LivingEntity target, double amount) {
        if (!target.isValid()) return;
        boolean was = CombatListener.spellDamage;
        CombatListener.spellDamage = true;
        try {
            target.damage(amount, p);
        } finally {
            CombatListener.spellDamage = was;
        }
    }

    private List<LivingEntity> around(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (hostile(e, p) && e.getLocation().distanceSquared(c) <= r * r) out.add((LivingEntity) e);
        }
        return out;
    }

    private static Color color(Player ignored, Spell s) {
        return switch (s.clazz()) {
            case BAATAR -> Color.fromRGB(255, 90, 70);
            case MERGEN -> Color.fromRGB(120, 230, 120);
            case BOO -> Color.fromRGB(170, 110, 255);
            case DARKHAN -> Color.fromRGB(255, 150, 50);
            case KHULEGCHIN -> Color.fromRGB(90, 170, 255);
        };
    }

    private void cone(Player p, Spell s, double range, double dot, Particle fx, Sound sound) {
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        Location c = p.getLocation().add(0, 1, 0);
        for (LivingEntity e : around(p, c, r(p, s, range))) {
            Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (to.lengthSquared() < 0.01 || to.normalize().dot(dir) >= dot) {
                hurt(p, e, s);
                e.setVelocity(to.normalize().multiply(0.6).setY(0.3));
            }
        }
        for (int i = -2; i <= 2; i++) {
            Vector v = dir.clone().rotateAroundY(Math.toRadians(i * 22)).multiply(2.2);
            p.getWorld().spawnParticle(fx, c.clone().add(v), 1);
        }
        p.getWorld().spawnParticle(Particle.DUST, c.clone().add(dir.clone().multiply(2)), 20, 1.2, 0.3, 1.2, 0,
                new Particle.DustOptions(color(p, s), 1.3f));
        p.getWorld().playSound(c, sound, 1f, 0.9f);
    }

    private void warCry(Player p, Spell s) {
        for (LivingEntity e : around(p, p.getLocation(), r(p, s, 9))) {
            if (e instanceof Mob m) m.setTarget(p);
            riders(p, e, s);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 0));
        p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, p.getLocation().add(0, 2, 0), 8, 0.6, 0.3, 0.6);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 2, 0.5, 2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 60, 40), 1.4f));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.3f);
    }

    private void leap(Player p, Spell s) {
        p.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(1.3).setY(0.65));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1f, 1f);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                t++;
                if ((t > 5 && p.getLocation().clone().subtract(0, 0.2, 0).getBlock().getType().isSolid()) || t > 40 || !p.isOnline()) {
                    Location c = p.getLocation();
                    for (LivingEntity e : around(p, c, r(p, s, 3.8))) {
                        hurt(p, e, s);
                        e.setVelocity(new Vector(0, 0.55, 0));
                    }
                    c.getWorld().spawnParticle(Particle.EXPLOSION, c, 2);
                    c.getWorld().spawnParticle(Particle.BLOCK, c, 40, 1.5, 0.1, 1.5, c.clone().subtract(0, 1, 0).getBlock().getBlockData());
                    c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void buff(Player p, Sound sound, Particle fx, PotionEffect... effects) {
        for (PotionEffect e : effects) p.addPotionEffect(e);
        p.getWorld().spawnParticle(fx, p.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.1);
        p.getWorld().playSound(p.getLocation(), sound, 0.8f, 1.1f);
    }

    private void wolfEye(Player p, Spell s) {
        for (LivingEntity e : around(p, p.getLocation(), r(p, s, 24))) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
            riders(p, e, s);
        }
        CombatListener.EMPOWERED_ARROWS.put(p.getUniqueId(), 3);
        CombatListener.EMPOWERED_UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + 12_000);
        p.getWorld().spawnParticle(Particle.DUST, p.getEyeLocation(), 20, 0.4, 0.2, 0.4, 0, new Particle.DustOptions(Color.fromRGB(255, 220, 80), 1.2f));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 0.5f, 1.4f);
    }

    private void volley(Player p) {
        Vector dir = p.getEyeLocation().getDirection();
        for (int i = -2; i <= 2; i++) {
            Arrow a = p.launchProjectile(Arrow.class, dir.clone().rotateAroundY(Math.toRadians(i * 7)).multiply(2.6));
            a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
            a.addScoreboardTag("suld_volley");
            a.setCritical(true);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.8f);
    }

    private void disengage(Player p, Spell s) {
        for (LivingEntity e : around(p, p.getLocation(), r(p, s, 5))) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            riders(p, e, s);
        }
        p.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(-1.2).setY(0.45));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.5, 0.2, 0.5, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
    }

    private void beam(Player p, Spell s, double range, Particle fx, Sound sound) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult block = p.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double len = block == null ? range : block.getHitPosition().distance(eye.toVector());
        Set<UUID> hit = new HashSet<>();
        for (double d = 0.5; d <= len; d += 0.5) {
            Location at = eye.clone().add(dir.clone().multiply(d));
            p.getWorld().spawnParticle(fx, at, 1, 0, 0, 0, 0);
            for (LivingEntity e : around(p, at, 1.2)) if (hit.add(e.getUniqueId())) hurt(p, e, s);
        }
        p.getWorld().playSound(eye, sound, 1f, 1.2f);
    }

    private void heal(Player p, Spell s, double hp, double radius) {
        double rr = r(p, s, radius);
        double amount = hp * (1 + buildOf(p).mod(s, ModKey.DAMAGE_PCT) / 100.0);
        for (Player ally : p.getWorld().getPlayers()) {
            if (ally.getLocation().distanceSquared(p.getLocation()) > rr * rr) continue;
            services.skillTree().healFrom(p, ally, amount);
            ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 5, 0.4, 0.3, 0.4);
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
    }

    private void ongon(Player p, Spell s) {
        LivingEntity target = null;
        double best = 20 * 20;
        Vector dir = p.getEyeLocation().getDirection();
        for (LivingEntity e : around(p, p.getLocation(), 20)) {
            Vector to = e.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector());
            double d = to.lengthSquared();
            if (d < best && to.normalize().dot(dir) > 0.3) {
                best = d;
                target = e;
            }
        }
        if (target == null) {
            say(p, "§7Онгон зорилгогүй...");
            pool(p).gain(s.cost());
            return;
        }
        LivingEntity t = target;
        new BukkitRunnable() {
            Location at = p.getEyeLocation();
            int n;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (!t.isValid() || n++ > 40) {
                    cancel();
                    return;
                }
                Vector step = t.getEyeLocation().toVector().subtract(at.toVector());
                if (step.length() < 0.8) {
                    hurt(p, t, s);
                    t.getWorld().spawnParticle(Particle.SOUL, t.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0.05);
                    t.getWorld().playSound(t.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 1f);
                    cancel();
                    return;
                }
                at.add(step.normalize().multiply(0.8));
                at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 2, 0.05, 0.05, 0.05, 0);
                at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(170, 110, 255), 1f));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drum(Player p, Spell s) {
        new BukkitRunnable() {
            int beats;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (!p.isOnline() || beats++ >= 3) {
                    cancel();
                    return;
                }
                Location c = p.getLocation();
                for (LivingEntity e : around(p, c, r(p, s, 6))) {
                    hurt(p, e, s);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                }
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 5, 0.3, Math.sin(a) * 5), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(170, 110, 255), 1.4f));
                }
                c.getWorld().playSound(c, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.2f, 0.7f);
            }
        }.runTaskTimer(plugin, 0L, 15L);
    }

    private Location targetPoint(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER, true);
        return r == null ? p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(8))
                : r.getHitPosition().toLocation(p.getWorld());
    }

    private void skyGate(Player p, Spell s) {
        Location c = targetPoint(p, 20);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (t++ < 20) {
                    for (int i = 0; i < 12; i++) {
                        double a = Math.PI * 2 * i / 12 + t * 0.2;
                        c.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(Math.cos(a) * 4, 0.2 + t * 0.05, Math.sin(a) * 4), 1, 0, 0, 0, 0);
                    }
                    return;
                }
                for (LivingEntity e : around(p, c, r(p, s, 5))) hurt(p, e, s);
                for (Player ally : c.getWorld().getPlayers()) {
                    if (ally.getLocation().distanceSquared(c) < Math.pow(r(p, s, 6), 2)) services.skillTree().healFrom(p, ally, 6 * (1 + buildOf(p).mod(s, ModKey.DAMAGE_PCT) / 100.0));
                }
                c.getWorld().spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, Color.WHITE);
                c.getWorld().spawnParticle(Particle.DUST, c, 80, 2.5, 1, 2.5, 0, new Particle.DustOptions(Color.fromRGB(150, 235, 255), 1.6f));
                c.getWorld().playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void slam(Player p, Spell s) {
        Location c = p.getLocation();
        for (LivingEntity e : around(p, c, r(p, s, 4))) {
            hurt(p, e, s);
            e.setFireTicks(60);
        }
        c.getWorld().spawnParticle(Particle.FLAME, c, 60, 2, 0.2, 2, 0.05);
        c.getWorld().spawnParticle(Particle.LAVA, c, 10, 1.5, 0.2, 1.5);
        c.getWorld().playSound(c, Sound.BLOCK_ANVIL_LAND, 0.9f, 0.7f);
    }

    private void moltenSpray(Player p, Spell s) {
        new BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (!p.isOnline() || n++ >= 3) {
                    cancel();
                    return;
                }
                Vector dir = p.getLocation().getDirection().setY(0).normalize();
                Location c = p.getLocation().add(0, 1, 0);
                for (LivingEntity e : around(p, c, r(p, s, 6))) {
                    Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (to.lengthSquared() > 0.01 && to.normalize().dot(dir) > 0.6) {
                        hurt(p, e, s);
                        e.setFireTicks(40);
                    }
                }
                for (double d = 1; d < 6; d += 0.5) c.getWorld().spawnParticle(Particle.FLAME, c.clone().add(dir.clone().multiply(d)), 3, 0.3 * d / 3, 0.2, 0.3 * d / 3, 0.01);
                c.getWorld().playSound(c, Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.2f);
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    private void anvil(Player p, Spell s) {
        Location c = targetPoint(p, 20);
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (t++ < 16) {
                    c.getWorld().spawnParticle(Particle.DUST, c.clone().add(0, 8 - t * 0.5, 0), 6, 0.4, 0.2, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(110, 110, 120), 1.6f));
                    return;
                }
                for (LivingEntity e : around(p, c, r(p, s, 3.5))) hurt(p, e, s);
                c.getWorld().spawnParticle(Particle.EXPLOSION, c, 3, 0.5, 0.2, 0.5);
                c.getWorld().spawnParticle(Particle.LAVA, c, 15, 1.5, 0.3, 1.5);
                c.getWorld().playSound(c, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void charge(Player p, Spell s) {
        p.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(1.6).setY(0.15));
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (!p.isOnline() || t++ > 10) {
                    cancel();
                    return;
                }
                for (LivingEntity e : around(p, p.getLocation(), r(p, s, 1.9))) if (hit.add(e.getUniqueId())) hurt(p, e, s);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 3, 0.2, 0.1, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 1.2f);
    }

    private void stampede(Player p, Spell s) {
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        Location start = p.getLocation();
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                if (t++ > 20) {
                    cancel();
                    return;
                }
                Location at = start.clone().add(dir.clone().multiply(t * 0.7));
                for (LivingEntity e : around(p, at, r(p, s, 2.2))) if (hit.add(e.getUniqueId())) {
                    hurt(p, e, s);
                    e.setVelocity(dir.clone().multiply(0.8).setY(0.4));
                }
                at.getWorld().spawnParticle(Particle.CLOUD, at, 6, 1.2, 0.3, 1.2, 0.02);
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 8, 1, 0.5, 1, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.4f));
                if (t % 4 == 0) at.getWorld().playSound(at, Sound.ENTITY_HORSE_GALLOP, 1f, 0.8f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ resource + action bar

    private void regen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerClass c = clazzOf(p);
            if (c == null) continue;
            double rate = switch (c) {
                case BAATAR -> 2;
                case MERGEN -> 5;
                case BOO -> 6;
                case DARKHAN -> 3;
                case KHULEGCHIN -> p.isSprinting() ? 9 : 4;
            };
            SkillBuild b = buildOf(p);
            pool(p).gain(rate + b.stat(StatKey.RESOURCE_REGEN) + (b.has(KeystoneKind.TENGERTEI_KHOLBOGDOKH) ? 3 : 0));
        }
    }

    private static TextColor resourceColor(PlayerClass c) {
        return switch (c) {
            case BAATAR -> TextColor.fromHexString("#FF5A46");
            case MERGEN -> TextColor.fromHexString("#7CE07C");
            case BOO -> TextColor.fromHexString("#B06BFF");
            case DARKHAN -> TextColor.fromHexString("#FF9A3C");
            case KHULEGCHIN -> TextColor.fromHexString("#5AAFFF");
        };
    }

    private void actionBar() {
        for (Player p : Bukkit.getOnlinePlayers()) actionBar(p);
    }

    /** ❤ HP  ·  resource bar  ·  combo in progress (or the last spell notice). */
    public void actionBar(Player p) {
        PlayerClass c = clazzOf(p);
        if (c == null) return;
        ResourcePool pool = pool(p);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        int maxHp = (int) Math.ceil(max == null ? 20 : max.getValue());
        int hp = Math.min((int) Math.ceil(p.getHealth()), maxHp);
        int filled = (int) Math.round(pool.fraction() * 10);
        TextColor rc = resourceColor(c);
        Component bar = Component.empty().append(StyleFormat.glyph(Glyphs.ICON_HEART)).append(Component.text(" " + hp + "/" + maxHp, NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("    " + c.resourceName().split(" ")[0] + " ", rc, TextDecoration.BOLD))
                .append(Component.text("▰".repeat(filled), rc)).append(Component.text("▱".repeat(10 - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + pool.value(), rc, TextDecoration.BOLD));
        String n = notice.get(p.getUniqueId());
        String tail = null;
        if (n != null) {
            int at = n.lastIndexOf('@');
            if (System.currentTimeMillis() < Long.parseLong(n.substring(at + 1))) tail = n.substring(0, at);
            else notice.remove(p.getUniqueId());
        }
        ComboTracker t = combos.get(p.getUniqueId());
        String combo = t == null ? "" : t.current(System.currentTimeMillis());
        if (!combo.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) sb.append(i < combo.length() ? combo.charAt(i) : '_').append(i < 2 ? "-" : "");
            tail = "§e§l" + sb;
        }
        SkillTreeService tree = services.skillTree();
        if (tail == null && tree != null) {
            // idle line: ultimate state and unspent points (compact, only when relevant)
            StringBuilder sb = new StringBuilder();
            mn.suld.api.skill.tree.Ultimate ult = tree.ultimateOf(p);
            if (ult != null) {
                int cd = tree.ultimateCooldownSeconds(p);
                sb.append(cd > 0 ? "§7F ✦ " + cd + "с" : "§6§lF ✦ бэлэн");
            }
            int avail = tree.available(p);
            if (avail > 0) sb.append(sb.length() > 0 ? "  " : "").append("§b◆").append(avail).append(" оноо");
            if (sb.length() > 0) tail = sb.toString();
        }
        if (tail != null) bar = bar.append(Component.text("    ")).append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(tail)
                .decoration(TextDecoration.BOLD, true));
        p.sendActionBar(bar);
    }
}
