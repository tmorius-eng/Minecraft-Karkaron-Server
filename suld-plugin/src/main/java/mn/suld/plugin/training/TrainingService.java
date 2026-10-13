package mn.suld.plugin.training;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.clazz.ClassChangeRules;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.EquipmentState;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.EffectText;
import mn.suld.api.skill.tree.SkillNode;
import mn.suld.api.skill.tree.SkillTree;
import mn.suld.api.training.TrainingBuilds;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.item.PlayerDataSaves;
import mn.suld.plugin.skill.SkillService;
import mn.suld.plugin.skill.SkillTreeService;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Pages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * «Дайчдын Дасгалын Талбай» (docs/TRAINING_GROUNDS.md): any player, even one who has not chosen a class yet, tries any
 * of the five classes at level 60 with its top gear on the training Wardens in Хархорум, and sees what every hit, spell
 * and passive does ({@link TrainingCoach}). Nothing of it lasts:
 * <ul>
 *   <li>the character is a stand-in ({@link PlayerProfile#ephemeralCopy()}) that the profile service serves in place of
 *   the real profile and never writes; the real profile is saved before the swap and comes back unchanged;</li>
 *   <li>the player's things and the spot they stood on go into their own player data first, written at once, so a crash
 *   or a restart puts both back on the next join; the kit is bound to the player, marked, and taken back on leaving
 *   (anything else that reached the bag meanwhile is kept);</li>
 *   <li>spell waits, the resource and the ultimate's timer are kept aside and put back: the yard never refreshes them;</li>
 *   <li>no damage reaches a trainee, only {@code /dasgal} and the horse work, nothing can be dropped, picked up, traded or
 *   opened, and stepping out of the yard ends it.</li>
 * </ul>
 */
public final class TrainingService implements Listener, TabExecutor {

    /** The commands a trainee may use. */
    private static final Set<String> ALLOWED = Set.of("dasgal", "дасгал", "training", "mori", "horse", "mount");

    private final Plugin plugin;
    private final SuldServices services;
    private final SkillService skills;
    private final SkillTreeService tree;
    final TrainingYard yard;
    private final TrainingKit kit;
    final TrainingCoach coach;
    private final NamespacedKey stashKey, returnKey;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** Closes the sky windows (/menu, /skills) before a player is moved: set by the plugin. */
    public volatile java.util.function.Consumer<Player> closeWindows = p -> { };

    /** One trainee. */
    static final class Session {
        final PlayerProfile real;
        PlayerProfile stand;
        PlayerClass clazz;
        int variant;
        final Location back;
        final double share;
        final int food;
        final SkillService.Held held;
        final SkillTreeService.Timers timers;
        final List<PotionEffect> effects;
        final mn.suld.api.training.TrainingStats stats = new mn.suld.api.training.TrainingStats();
        final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        final long since = System.currentTimeMillis();

        Session(PlayerProfile real, Location back, double share, int food, SkillService.Held held, SkillTreeService.Timers timers,
                List<PotionEffect> effects) {
            this.real = real;
            this.back = back;
            this.share = share;
            this.food = food;
            this.held = held;
            this.timers = timers;
            this.effects = effects;
        }
    }

    public TrainingService(Plugin plugin, SuldServices services, SkillService skills, SkillTreeService tree) {
        this.plugin = plugin;
        this.services = services;
        this.skills = skills;
        this.tree = tree;
        this.kit = new TrainingKit(services);
        this.yard = new TrainingYard(plugin, services, c -> {
            try {
                return kit.icon(c);
            } catch (RuntimeException e) {
                return null;
            }
        });
        this.coach = new TrainingCoach(plugin, services, skills, tree, this);
        this.stashKey = new NamespacedKey(plugin, "training_stash");
        this.returnKey = new NamespacedKey(plugin, "training_return");
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    /** Puts the yard up where the config says (plot A by default), once the worlds are loaded. */
    public void start() {
        if (!plugin.getConfig().getBoolean("training.enabled", true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location c = configured();
            if (c == null) {
                plugin.getLogger().warning("[training] no world " + plugin.getConfig().getString("training.world") + ": the yard is not set up");
                return;
            }
            yard.start(c);
            coach.start();
            plugin.getLogger().info("[training] Дайчдын Дасгалын Талбай at " + c.getWorld().getName() + " " + c.getBlockX() + " " + c.getBlockY() + " " + c.getBlockZ());
        }, 40L);
        mn.suld.plugin.combat.CombatFeedback.labelled = TrainingYard::dummy;
    }

    private Location configured() {
        var cfg = plugin.getConfig();
        String wn = cfg.getString("training.world", "");
        World w = wn == null || wn.isBlank() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(wn);
        if (w == null) return null;
        return new Location(w, cfg.getInt("training.x", -63), cfg.getInt("training.y", 72), cfg.getInt("training.z", -92));
    }

    /** Plugin disable: every trainee back to their own character, the yard taken down. */
    public void shutdown() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) leave(p, "Сервер унтарч байна.", true);
        }
        coach.stop();
        yard.stop();
    }

    /** True while {@code id} trains. */
    public boolean training(UUID id) {
        return sessions.containsKey(id);
    }

    Session session(UUID id) {
        return sessions.get(id);
    }

    Map<UUID, Session> sessions() {
        return sessions;
    }

    // ------------------------------------------------------------------------------------------------ entering

    /** Why {@code p} cannot start training now, or null. */
    String blocked(Player p) {
        UUID id = p.getUniqueId();
        PlayerProfile pr = services.profiles().cached(id).orElse(null);
        if (pr == null) return "Профайл ачааллагдаж байна, түр хүлээгээд дахин оролдоно уу.";
        if (yard.centreOrNull() == null) return "Дасгалын талбай бэлэн биш байна.";
        if (p.isDead()) return "Амилсны дараа.";
        if (services.isSoul != null && services.isSoul.test(id)) return "Сүнс дасгал хийхгүй: эхлээд амил (/trial эсвэл хугацаа дуусахыг хүлээ).";
        if (services.inDeathTrial.test(id)) return "Тэнгэрийн сорилын үеэр боломжгүй.";
        if (pr.classTrial() != null || services.classTrial != null && services.classTrial.inTrial(id)) return "Ангийн Сорилтын үеэр боломжгүй.";
        if (services.dungeons() != null && services.dungeons().isInAnyRun(id)) return "Агуйн дотор боломжгүй.";
        if (services.duels() != null && services.duels().duelOf(id).isPresent()) return "Тулааны (duel) үеэр боломжгүй.";
        if (services.relics() != null && services.relics().borneBy(id).isPresent()) return "Сүлдийг тээж яваа үед дасгал хийхгүй.";
        if (services.worldEvents() != null && services.worldEvents().active().map(r -> r.contributions().containsKey(id)).orElse(false)) {
            return "Ертөнцийн үйл явдлын үеэр боломжгүй.";
        }
        long since = services.classChange == null ? Long.MAX_VALUE : System.currentTimeMillis() - services.classChange.lastCombat(id);
        if (since < ClassChangeRules.COMBAT_MS) return "Тулаанаас гараад " + ClassChangeRules.format(ClassChangeRules.COMBAT_MS - since) + "-ын дараа.";
        return null;
    }

    /** Start training as {@code c} with build {@code variant} (0 = A), or switch to it while training. */
    public boolean enter(Player p, PlayerClass c, int variant) {
        Session have = sessions.get(p.getUniqueId());
        if (have != null) return switchTo(p, have, c, variant);
        String why = blocked(p);
        if (why != null) {
            p.sendMessage(Messages.error(why));
            return false;
        }
        SkillTree t = tree.tree(c);
        if (t == null) {
            p.sendMessage(Messages.error("Энэ ангийн чадварын мод ачааллагдаагүй байна."));
            return false;
        }
        PlayerProfile real = services.profiles().cached(p.getUniqueId()).orElseThrow();
        closeWindows.accept(p);
        p.closeInventory();
        if (p.isInsideVehicle()) p.leaveVehicle(); // the horse service sends a steppe horse away a moment later
        Location back = p.getLocation().clone();
        List<PotionEffect> effects = new ArrayList<>(p.getActivePotionEffects());
        Session s = new Session(real, back, mn.suld.plugin.clazz.ClassChangeService.healthShare(p), p.getFoodLevel(), skills.hold(p), tree.timers(p), effects);
        // 1. the player's own things and the spot they stood on, into their own data, on disk now
        stash(p, back);
        PlayerDataSaves.now(p);
        // 2. the real profile written as it is, then the stand-in served in its place
        services.profiles().save(real);
        sessions.put(p.getUniqueId(), s);
        if (!dress(p, s, c, variant)) {
            sessions.remove(p.getUniqueId());
            unstash(p, true);
            PlayerDataSaves.now(p);
            p.sendMessage(Messages.error("Дасгал эхэлсэнгүй: дахин оролдоно уу."));
            return false;
        }
        if (!yard.inside(p.getLocation(), 0)) p.teleport(yard.entrance());
        p.setFallDistance(0);
        p.showBossBar(s.bar);
        coach.begin(p, s);
        p.showTitle(Title.title(Component.text("Дайчдын Дасгалын Талбай", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(c.displayName() + " · 60-р түвшин · Build " + (variant == 0 ? "A" : "B"), NamedTextColor.WHITE)));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "training.enter", p.getUniqueId().toString(),
                c.id() + " " + (variant == 0 ? "A" : "B") + " real=" + real.playerClass().map(PlayerClass::id).orElse("-")));
        return true;
    }

    private boolean switchTo(Player p, Session s, PlayerClass c, int variant) {
        if (s.clazz == c && s.variant == variant) {
            p.sendMessage(Messages.info("Та аль хэдийн " + c.displayName() + " · Build " + (variant == 0 ? "A" : "B") + "-ээр дасгал хийж байна."));
            return true;
        }
        if (tree.tree(c) == null) return false;
        if (!dress(p, s, c, variant)) {
            p.sendMessage(Messages.error("Солигдсонгүй."));
            return false;
        }
        coach.begin(p, s);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.1f);
        return true;
    }

    /** The kit and the stand-in for {@code c} / {@code variant}: in the bag, in the profile cache, in every stat. */
    private boolean dress(Player p, Session s, PlayerClass c, int variant) {
        try {
            SkillTree t = tree.tree(c);
            TrainingKit.Items items = kit.items(p, c);
            EquipmentState acc = kit.accessories(p);
            PlayerProfile stand = kit.standIn(s.real, p, c, t, variant, items.gear(), acc, tree.context(p));
            // the stand-in in the cache: the old one (a switch) gives way to the real one first, for no time at all
            if (s.stand != null && !services.profiles().putBack(s.stand, s.real)) return false;
            if (!services.profiles().standIn(s.real, stand)) {
                plugin.getLogger().warning("[training] " + p.getName() + ": the cached profile is not the real one; training refused");
                return false;
            }
            s.stand = stand;
            s.clazz = c;
            s.variant = variant;
            s.stats.reset();
            PlayerInventory inv = p.getInventory();
            inv.clear();
            inv.setItem(0, items.weapon());
            items.armour().forEach((piece, stack) -> {
                switch (piece) {
                    case HELMET -> inv.setHelmet(stack);
                    case CHESTPLATE -> inv.setChestplate(stack);
                    case LEGGINGS -> inv.setLeggings(stack);
                    case BOOTS -> inv.setBoots(stack);
                }
            });
            inv.setItem(7, button(Material.WRITABLE_BOOK, "Дасгалын цэс", "Анги, build солих · тоо · гарах"));
            inv.setItem(8, button(Material.BARRIER, "Гарах", "Өөрийн дүр, эд зүйлээ авч буцна"));
            inv.setHeldItemSlot(0);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            p.setAbsorptionAmount(0);
            p.setFireTicks(0);
            tree.attach(p);
            tree.applyBaseHealth(p);
            tree.qaResetTimers(p);
            services.equipment().compute(p);
            tree.equipmentChanged(p);
            skills.fresh(p);
            mn.suld.plugin.item.BowQuiver.give(p);
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max == null ? 20 : max.getValue());
            p.setFoodLevel(20);
            if (services.hud() != null) {
                services.hud().teamChanged(p);
                services.hud().update(p, stand);
            }
            s.bar.color(barColour(c));
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("[training] " + p.getName() + ": " + c + " failed: " + ex);
            ex.printStackTrace();
            return false;
        }
    }

    private static BossBar.Color barColour(PlayerClass c) {
        return switch (c) {
            case BAATAR -> BossBar.Color.RED;
            case MERGEN -> BossBar.Color.GREEN;
            case BOO -> BossBar.Color.PURPLE;
            case DARKHAN -> BossBar.Color.YELLOW;
            case KHULEGCHIN -> BossBar.Color.BLUE;
        };
    }

    private static ItemStack button(Material m, String name, String what) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(what, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Баруун товшилт", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        s.setItemMeta(meta);
        return TrainingKit.mark(s);
    }

    // ------------------------------------------------------------------------------------------------ leaving

    /** End training now: the real character, its things and its spot back. {@code teleport} false keeps the player here. */
    public void leave(Player p, String why, boolean teleport) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) {
            if (p.getPersistentDataContainer().has(stashKey, PersistentDataType.BYTE_ARRAY)) unstash(p, teleport);
            return;
        }
        p.hideBossBar(s.bar);
        coach.end(p);
        boolean back = services.profiles().putBack(s.stand, s.real);
        if (!back) plugin.getLogger().warning("[training] " + p.getName() + ": the stand-in was no longer cached (the real profile is the stored one)");
        // whatever reached the bag meanwhile that is not the kit (a reward, a delivery) is kept
        List<ItemStack> extras = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && !it.getType().isAir() && !TrainingKit.marked(it)) extras.add(it);
        }
        ItemStack cursor = p.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir() && !TrainingKit.marked(cursor)) extras.add(cursor);
        p.setItemOnCursor(null);
        p.closeInventory();
        unstash(p, false);
        for (ItemStack left : p.getInventory().addItem(extras.toArray(new ItemStack[0])).values()) {
            Item dropped = p.getWorld().dropItem(s.back, left);
            dropped.setOwner(p.getUniqueId());
        }
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        for (PotionEffect e : s.effects) p.addPotionEffect(e);
        p.setAbsorptionAmount(0);
        p.setFireTicks(0);
        tree.attach(p);
        tree.applyBaseHealth(p);
        tree.timers(p, s.timers);
        services.equipment().compute(p);
        tree.equipmentChanged(p);
        skills.restore(p, s.held);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        double m = max == null ? 20 : max.getValue();
        if (!p.isDead()) p.setHealth(Math.max(1, Math.min(m, s.share * m)));
        p.setFoodLevel(s.food);
        if (services.hud() != null) {
            services.hud().teamChanged(p);
            services.hud().update(p, s.real);
        }
        PlayerDataSaves.now(p);
        if (teleport) {
            p.teleport(s.back);
            p.setFallDistance(0);
        }
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "training.leave", p.getUniqueId().toString(),
                s.clazz.id() + " seconds=" + (System.currentTimeMillis() - s.since) / 1000 + " total=" + Math.round(s.stats.total())));
        p.sendMessage(Messages.success(why + " Өөрийн дүр, эд зүйл тань буцлаа" + (s.real.playerClass().isPresent()
                ? " (" + s.real.playerClass().get().displayName() + ", " + s.real.progression().level() + "-р түвшин)." : ".")));
        if (s.real.playerClass().isEmpty() && teleport && p.isOnline()) {
            // a new player who tried before choosing: the class window again, on the class just tried
            PlayerClass tried = s.clazz;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && services.profiles().cached(p.getUniqueId()).map(pr -> pr.playerClass().isEmpty()).orElse(false)) {
                    services.classSelectionGui().openConfirm(p, tried);
                }
            }, 10L);
        }
    }

    // ------------------------------------------------------------------------------------------------ the stash

    private void stash(Player p, Location back) {
        byte[] bytes = ItemStack.serializeItemsAsBytes(p.getInventory().getContents());
        var pdc = p.getPersistentDataContainer();
        pdc.set(stashKey, PersistentDataType.BYTE_ARRAY, bytes);
        pdc.set(returnKey, PersistentDataType.STRING, back.getWorld().getName() + ";" + back.getX() + ";" + back.getY() + ";" + back.getZ() + ";"
                + back.getYaw() + ";" + back.getPitch());
    }

    /** The stashed things back into the (cleared) inventory; with {@code teleport}, to the stored spot too. */
    private void unstash(Player p, boolean teleport) {
        var pdc = p.getPersistentDataContainer();
        byte[] bytes = pdc.get(stashKey, PersistentDataType.BYTE_ARRAY);
        Location back = storedReturn(p);
        PlayerInventory inv = p.getInventory();
        inv.clear();
        if (bytes != null) {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
            inv.setContents(java.util.Arrays.copyOf(items, inv.getContents().length));
        }
        pdc.remove(stashKey);
        pdc.remove(returnKey);
        if (teleport && back != null) p.teleport(back);
    }

    private Location storedReturn(Player p) {
        String s = p.getPersistentDataContainer().get(returnKey, PersistentDataType.STRING);
        if (s == null) return null;
        String[] f = s.split(";");
        World w = f.length == 6 ? Bukkit.getWorld(f[0]) : null;
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]), Float.parseFloat(f[4]), Float.parseFloat(f[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------ pages

    /** Not training: the five classes; training: what can be changed. */
    void menu(Player p) {
        Session s = sessions.get(p.getUniqueId());
        List<Component> lines = new ArrayList<>();
        List<Pages.Button> buttons = new ArrayList<>();
        if (s == null) {
            lines.add(Pages.line("Анги сонгоод 60-р түвшин, хамгийн сайн хуяг, зэвсэгтэйгээр Warden дээр туршина."));
            lines.add(Pages.dim("Цохилт, ид шид, идэвхгүй чадвар бүрийн хохирол, DPS-ээ харна. Гарахад өөрийн дүр, эд зүйл тань буцна."));
        } else {
            lines.add(Pages.line("Одоо: " + s.clazz.displayName() + " · Build " + (s.variant == 0 ? "A" : "B") + " · 60-р түвшин"));
            lines.add(Pages.dim("DPS " + mn.suld.api.training.TrainingText.number(s.stats.dps(System.currentTimeMillis())) + " · нийт "
                    + mn.suld.api.training.TrainingText.number(s.stats.total())));
            buttons.add(Pages.action("Build " + (s.variant == 0 ? "B" : "A") + " руу", "Нөгөө keystone, ultimate-тай", pl -> enter(pl, s.clazz, 1 - s.variant)));
            buttons.add(Pages.action("Тоо дэлгэрэнгүй", "Эх үүсвэр бүр, идэвхгүй чадвар бүрийн давтамж", coach::statsPage));
            buttons.add(Pages.action("Тоо тэглэх", "DPS, нийт хохирлыг шинээр тоолно", pl -> coach.reset(pl)));
        }
        for (PlayerClass c : PlayerClass.values()) {
            buttons.add(Pages.action(c.displayName(), TrainingYard.ROLE.get(c), pl -> station(pl, c)));
        }
        if (s != null) buttons.add(Pages.action("Гарах", "Өөрийн дүр, эд зүйлтэйгээ буцна", pl -> leave(pl, "Дасгал дууслаа.", true)));
        Pages.text(p, "Дайчдын Дасгалын Талбай", lines, buttons, null);
    }

    /** A class station: what builds A and B are, and the buttons to try them. */
    void station(Player p, PlayerClass c) {
        SkillTree t = tree.tree(c);
        if (t == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Pages.line(c.displayName() + " — " + TrainingYard.ROLE.get(c) + " · нөөц: " + resource(c)));
        StringBuilder spells = new StringBuilder();
        for (Spell s : Spell.of(c)) spells.append(spells.length() == 0 ? "" : " · ").append(s.keys()).append(' ').append(s.displayName());
        lines.add(Pages.dim(spells.toString()));
        for (int v = 0; v < TrainingBuilds.VARIANTS; v++) {
            TrainingBuilds.Build b = TrainingBuilds.of(t, v);
            lines.add(Pages.line("Build " + b.letter() + ": " + (b.keystone() == null ? "—" : b.keystone().name()) + " · F: "
                    + (b.ultimate() == null ? "—" : b.ultimate().name())));
            if (b.keystone() != null) lines.add(Pages.dim(firstLine(b.keystone(), EffectText.Kind.KEYSTONE)));
            if (b.ultimate() != null) lines.add(Pages.dim(firstLine(b.ultimate(), EffectText.Kind.ULTIMATE)));
            StringBuilder ps = new StringBuilder("Идэвхгүй: ");
            List<SkillNode> passives = TrainingBuilds.passives(b.allocation());
            for (int i = 0; i < passives.size(); i++) ps.append(i == 0 ? "" : ", ").append(passives.get(i).name());
            lines.add(Pages.dim(ps.toString()));
        }
        List<Pages.Button> buttons = new ArrayList<>();
        buttons.add(Pages.action("⚔ Build A-аар турших", null, pl -> enter(pl, c, 0)));
        buttons.add(Pages.action("⚔ Build B-ээр турших", null, pl -> enter(pl, c, 1)));
        Session s = sessions.get(p.getUniqueId());
        PlayerProfile real = s != null ? s.real : services.profiles().cached(p.getUniqueId()).orElse(null);
        if (real != null && real.playerClass().isEmpty()) {
            buttons.add(Pages.action("✔ Энэ ангийг сонгох", "Дасгалаас гараад " + c.displayName() + " ангийг батлах цонх", pl -> {
                if (sessions.containsKey(pl.getUniqueId())) leave(pl, "Анги сонгох руу.", true);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (pl.isOnline()) services.classSelectionGui().openConfirm(pl, c);
                }, 12L);
            }));
        }
        Pages.text(p, c.displayName() + " · дасгал", lines, buttons, this::menu);
    }

    private static String firstLine(SkillNode n, EffectText.Kind kind) {
        for (EffectText.Line l : EffectText.lines(n)) if (l.kind() == kind) return l.text();
        return n.description();
    }

    static String resource(PlayerClass c) {
        String r = c.resourceName();
        int slash = r.indexOf(" / ");
        return slash > 0 ? r.substring(0, slash) : r;
    }

    // ------------------------------------------------------------------------------------------------ command

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("yard")) return yardCommand(sender, a);
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (a.length == 0) {
            menu(p);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "leave", "гарах" -> {
                if (sessions.containsKey(p.getUniqueId())) leave(p, "Дасгал дууслаа.", true);
                else p.sendMessage(Messages.info("Та дасгал хийгээгүй байна. Эхлэх: /dasgal"));
            }
            case "stats", "тоо" -> coach.statsPage(p);
            case "reset" -> coach.reset(p);
            default -> {
                PlayerClass c = PlayerClass.byId(sub).orElse(null);
                if (c == null) {
                    p.sendMessage(Messages.info("/dasgal [baatar|mergen|boo|darkhan|khulegchin] [a|b] · /dasgal leave · /dasgal stats"));
                    return true;
                }
                int v = a.length > 1 && a[1].equalsIgnoreCase("b") ? 1 : 0;
                enter(p, c, v);
            }
        }
        return true;
    }

    /** Staff: {@code /dasgal yard info|set|respawn} (the blocks: {@code build|undo}, YardBlocks). */
    private boolean yardCommand(CommandSender sender, String[] a) {
        if (!sender.hasPermission("suld.admin.training")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return true;
        }
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "info";
        switch (sub) {
            case "set" -> {
                if (!(sender instanceof Player p)) return true;
                Location l = p.getLocation();
                plugin.getConfig().set("training.world", l.getWorld().getName());
                plugin.getConfig().set("training.x", l.getBlockX());
                plugin.getConfig().set("training.y", l.getBlockY());
                plugin.getConfig().set("training.z", l.getBlockZ());
                plugin.saveConfig();
                for (UUID id : new ArrayList<>(sessions.keySet())) {
                    Player t = Bukkit.getPlayer(id);
                    if (t != null) leave(t, "Талбай нүүлээ.", true);
                }
                yard.start(configured());
                services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "training.yard.set", l.getWorld().getName(),
                        l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()));
                sender.sendMessage(Messages.success("Дасгалын талбайн төв энд: " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()));
            }
            case "respawn" -> {
                Location c = yard.centreOrNull() == null ? configured() : yard.centre();
                yard.start(c);
                sender.sendMessage(Messages.success("Талбайн Warden, тавцангууд дахин байрлалаа."));
            }
            case "build", "undo" -> sender.sendMessage(Messages.info(YardBlocks.run(plugin, services, yard, sender, sub)));
            default -> {
                Location c = yard.centreOrNull();
                sender.sendMessage(Messages.info(c == null ? "Талбай байрлаагүй." : "Талбай: " + c.getWorld().getName() + " " + c.getBlockX() + " "
                        + c.getBlockY() + " " + c.getBlockZ() + " · дасгалжигч " + sessions.size() + " · Warden " + yard.dummies().size()));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("leave", "stats", "reset"));
            for (PlayerClass pc : PlayerClass.values()) out.add(pc.id());
            if (s.hasPermission("suld.admin.training")) out.add("yard");
            return out.stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("yard")) return List.of("info", "set", "respawn", "build", "undo");
        if (a.length == 2) return List.of("a", "b");
        return List.of();
    }

    // ------------------------------------------------------------------------------------------------ guards

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!sessions.containsKey(e.getPlayer().getUniqueId())) return;
        String m = e.getMessage().length() > 1 ? e.getMessage().substring(1) : "";
        String cmd = m.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);
        if (ALLOWED.contains(cmd)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(Messages.error("Дасгалын үеэр зөвхөн /dasgal (гарах: /dasgal leave) болон /mori."));
    }

    /** No damage reaches a trainee. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTraineeHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && sessions.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    /** Only trainees hit the Wardens (the numbers are theirs); everyone else is told how to start. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDummyHit(EntityDamageByEntityEvent e) {
        if (!TrainingYard.dummy(e.getEntity())) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) d = shooter;
        if (d instanceof Player p && sessions.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        if (d instanceof Player p) coach.hint(p);
    }

    /** A Warden is whole again a tick after every hit (and never burns for long). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDummyDamaged(EntityDamageEvent e) {
        if (!TrainingYard.dummy(e.getEntity()) || !(e.getEntity() instanceof LivingEntity le)) return;
        if (!(e instanceof EntityDamageByEntityEvent)) coach.unattributed(le, e);
        Bukkit.getScheduler().runTask(plugin, () -> TrainingYard.topUp(le));
    }

    /** A Warden without AI never darkens anyone; this catches any other way. */
    @EventHandler(ignoreCancelled = true)
    public void onDarkness(EntityPotionEffectEvent e) {
        if (e.getCause() == EntityPotionEffectEvent.Cause.WARDEN && e.getEntity() instanceof Player p && yard.inside(p.getLocation(), 16)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (TrainingKit.marked(e.getItemDrop().getItemStack())) e.getItemDrop().remove(); // a stray training item never lands
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && sessions.containsKey(p.getUniqueId())) e.setCancelled(true);
        if (TrainingKit.marked(e.getItem().getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    /** No chest, trade, crafting table, menu or other window while training (the dialogs are not inventories). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && sessions.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    /** The kit never wears. */
    @EventHandler(ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    /** A station (for anyone); a trainee touches nothing else. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityClick(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        PlayerClass c = t instanceof Interaction ? yard.stationOf(t) : null;
        if (c != null) {
            e.setCancelled(true);
            if (e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) station(e.getPlayer(), c);
            return;
        }
        if (sessions.containsKey(e.getPlayer().getUniqueId()) && !(t instanceof org.bukkit.entity.AbstractHorse)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStationAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        PlayerClass c = e.getAttacked() instanceof Interaction ? yard.stationOf(e.getAttacked()) : null;
        if (c == null) return;
        e.setCancelled(true);
        station(e.getPlayer(), c);
    }

    /** The two hotbar buttons of the kit. */
    @EventHandler(priority = EventPriority.LOW)
    public void onButton(PlayerInteractEvent e) {
        if (!sessions.containsKey(e.getPlayer().getUniqueId()) || e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!e.getAction().isRightClick()) return;
        ItemStack it = e.getItem();
        if (it == null || !TrainingKit.marked(it)) return;
        if (it.getType() == Material.BARRIER) {
            e.setCancelled(true);
            leave(e.getPlayer(), "Дасгал дууслаа.", true);
        } else if (it.getType() == Material.WRITABLE_BOOK) {
            e.setCancelled(true);
            menu(e.getPlayer());
        }
    }

    /** Stepping out of the yard ends training. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        if (!sessions.containsKey(e.getPlayer().getUniqueId())) return;
        if (!yard.inside(e.getTo(), 3)) {
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sessions.containsKey(p.getUniqueId()) && !yard.inside(p.getLocation(), 3)) leave(p, "Талбайгаас гарлаа — дасгал дууслаа.", true);
            });
        }
    }

    /** Anything that carries a trainee out of the yard (staff /tp, a portal) ends training there. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!sessions.containsKey(p.getUniqueId()) || yard.inside(e.getTo(), 3)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.containsKey(p.getUniqueId()) && !yard.inside(p.getLocation(), 3)) leave(p, "Талбайгаас гарлаа — дасгал дууслаа.", false);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        if (sessions.containsKey(e.getEntity().getUniqueId())) {
            e.getDrops().clear();
            e.setKeepInventory(true);
            e.setDroppedExp(0);
            leave(e.getEntity(), "Дасгал дууслаа.", false);
        }
    }

    /** The kit is not the player's: a vanilla advancement it earns (a blaze rod's «Into Fire») is taken back, unannounced. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent e) {
        if (!sessions.containsKey(e.getPlayer().getUniqueId())) return;
        e.message(null);
        var progress = e.getPlayer().getAdvancementProgress(e.getAdvancement());
        for (String c : new ArrayList<>(progress.getAwardedCriteria())) progress.revokeCriteria(c);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId())) leave(e.getPlayer(), "Дасгал тасарлаа.", true);
    }

    /**
     * A crash or a restart during training: the stand-in was never written (the profile loaded is the real one) and the
     * player's own things are in their data. They come back before anything else looks at the bag; the kit goes.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.getPersistentDataContainer().has(stashKey, PersistentDataType.BYTE_ARRAY)) {
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                if (TrainingKit.marked(p.getInventory().getItem(i))) p.getInventory().setItem(i, null); // a stray kit item
            }
            return;
        }
        Location back = storedReturn(p);
        unstash(p, false);
        for (PotionEffect pe : p.getActivePotionEffects()) p.removePotionEffect(pe.getType());
        PlayerDataSaves.now(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (back != null) p.teleport(back);
            p.sendMessage(Messages.info("Өмнөх дасгал дууссан: өөрийн дүр, эд зүйл тань хэвээрээ."));
        }, 2L);
    }
}
