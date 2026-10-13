package mn.suld.plugin.clazz;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.clazz.ClassChangeRules;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.event.ClassChangedEvent;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.item.PlayerDataSaves;
import mn.suld.plugin.skill.SkillService;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class change (docs/CLASS_CHANGE.md). {@link ClassChangeRules} decides; this gathers the facts and performs the
 * change in one main-thread step:
 * <ol>
 *   <li>the old class's soulbound weapon and armour leave the player (bag, armour, off hand, cursor, ender chest) —
 *   their progress stays in the profile under that class and they are rebuilt with the same identity on return;</li>
 *   <li>the player file is written (the item side reaches disk first, PlayerDataSaves); a seal, if used, goes with
 *   it;</li>
 *   <li>the profile switches class (coins paid), the new class's tree, attributes and class gear come back (a
 *   first-time class gets its starter set), every potion effect is purged, the resource pool starts empty and every
 *   spell waits; the ultimate's cooldown carries over;</li>
 *   <li>health keeps its share of the maximum (no free heal); the profile is saved and the change audited.</li>
 * </ol>
 */
public final class ClassChangeService implements Listener {

    /** Every spell waits this long after a change. */
    static final long SPELL_LOCK_MS = 10_000;

    private final Plugin plugin;
    private final SuldServices services;
    private final SkillService skills;
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();

    public ClassChangeService(Plugin plugin, SuldServices services, SkillService skills) {
        this.plugin = plugin;
        this.services = services;
        this.skills = skills;
    }

    /** What would happen if {@code p} asked for {@code to} now (the academy shows it before the click). */
    public ClassChangeRules.Verdict verdict(Player p, PlayerClass to) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return new ClassChangeRules.Verdict(ClassChangeRules.Refusal.NO_CLASS, null, 0, 0);
        return ClassChangeRules.check(request(p, pr, to));
    }

    ClassChangeRules.Request request(Player p, PlayerProfile pr, PlayerClass to) {
        UUID id = p.getUniqueId();
        boolean inEvent = services.worldEvents() != null
                && services.worldEvents().active().map(r -> r.contributions().containsKey(id)).orElse(false);
        int free = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it == null || it.getType().isAir()) free++;
        return new ClassChangeRules.Request(pr.playerClass().orElse(null), to, pr.progression().level(),
                pr.activeMinutes().total(), pr.currency(), ClassSeal.count(p), System.currentTimeMillis(), pr.lastClassChange(),
                lastCombat.getOrDefault(id, 0L), services.inCity(p.getLocation()),
                services.dungeons() != null && services.dungeons().isInAnyRun(id),
                services.duels() != null && services.duels().duelOf(id).isPresent(), inEvent,
                services.isSoul != null && services.isSoul.test(id), p.isDead(), free);
    }

    /** Change class; the verdict says why not, or how it was paid. Main thread. */
    public ClassChangeRules.Verdict change(Player p, PlayerClass to) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return new ClassChangeRules.Verdict(ClassChangeRules.Refusal.NO_CLASS, null, 0, 0);
        if (pr.classTrial() != null) return new ClassChangeRules.Verdict(ClassChangeRules.Refusal.IN_TRIAL, null, 0, 0);
        ClassChangeRules.Verdict v = ClassChangeRules.check(request(p, pr, to));
        if (!v.allowed()) return v;
        PlayerClass from = pr.playerClass().orElseThrow();
        var maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double oldMax = maxAttr == null ? 20 : maxAttr.getValue();
        double share = Math.max(0.05, Math.min(1.0, p.getHealth() / Math.max(1, oldMax)));

        // 1-2: the old class's gear leaves; the seal is taken; the player file reaches disk before the profile
        if (v.payment() == ClassChangeRules.Payment.SEAL && !ClassSeal.consumeOne(p)) {
            return new ClassChangeRules.Verdict(ClassChangeRules.Refusal.NO_COINS, null, 0, 0);
        }
        int stored = stripClassGear(p, from);
        PlayerDataSaves.now(p);

        // 3: the profile changes class and pays
        long now = System.currentTimeMillis();
        if (v.payment() == ClassChangeRules.Payment.COINS) pr.addCurrency(-v.price());
        if (!pr.switchClass(to, now)) return new ClassChangeRules.Verdict(ClassChangeRules.Refusal.SAME_CLASS, null, 0, 0);
        // a free change while new starts no cooldown: the 6 hours count from the first paid (or sealed) change
        if (v.payment() == ClassChangeRules.Payment.GRACE) pr.clearClassChangeCooldown();
        if (services.hud() != null) services.hud().teamChanged(p); // the new class badge next to the name, now
        // from here the profile is the new class: anything below that fails is logged and the change still stands
        // (saved below); a missing piece of gear is what /classgear recover rebuilds, so nothing is left half-way
        try {
            services.classWeapons().syncRemembered(p);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            if (services.skillTree() != null) services.skillTree().attach(p);
            skills.afterClassChange(p, SPELL_LOCK_MS);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("[class] " + p.getName() + ": applying " + to + " failed: " + ex);
        }
        try {
            // the new class's gear: rebuilt with its identity, or the starter set the first time
            if (services.classArmor != null && services.classArmor.supports(to)) services.classArmor.recover(p);
            if (pr.classGear().weapon() == null) {
                ItemStack w = services.classWeapons().starter(to, pr.progression().level(), p);
                if (w != null) p.getInventory().addItem(w).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            } else {
                services.classWeapons().recover(p);
            }
            mn.suld.plugin.item.BowQuiver.give(p);
            services.equipment().dirty(p);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("[class] " + p.getName() + ": the " + to + " gear failed (/classgear recover rebuilds it): " + ex);
            p.sendMessage(Messages.error("Ангийн зэвсэг/хуяг бүрэн ирсэнгүй: /classgear recover"));
        }
        PlayerDataSaves.now(p);

        // 4: health keeps its share of the new maximum
        double newMax = maxAttr == null ? 20 : maxAttr.getValue();
        p.setHealth(Math.max(1, Math.min(newMax, share * newMax)));

        services.profiles().save(pr).exceptionally(ex -> {
            plugin.getLogger().severe("[class] " + p.getName() + " changed " + from + " -> " + to + " but the profile save failed: " + ex);
            return null;
        });
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "class.change", p.getUniqueId().toString(),
                from.id() + " -> " + to.id() + " paid=" + v.payment() + " price=" + v.price() + " gearStored=" + stored));
        services.events().dispatch(new ClassChangedEvent(p.getUniqueId(), from, to));
        if (services.hud() != null) services.hud().update(p, pr);
        p.showTitle(Title.title(Component.text(to.displayName(), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Шинэ анги · " + from.displayName() + "-ийн ахиц хадгалагдлаа", NamedTextColor.WHITE)));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.1f);
        p.sendMessage(Messages.success("Та " + to.displayName() + " анги боллоо. " + switch (v.payment()) {
            case GRACE -> "Шинэ тоглогчийн хугацаанд үнэгүй.";
            case SEAL -> "Хувь Төөргийн Тамга зарцууллаа.";
            case COINS -> v.price() + " зоос төллөө. Дараагийн солилт 6 цагийн дараа.";
        }));
        return v;
    }

    /** The old class's own soulbound weapon and armour, out of every place the player keeps items. */
    public int stripClassGear(Player p, PlayerClass from) {
        String w = "weapon.class." + from.id() + ".", a = "armor.class." + from.id() + ".";
        int n = 0;
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (own(p, inv[i], w, a)) {
                inv[i] = null;
                n++;
            }
        }
        p.getInventory().setContents(inv);
        ItemStack[] ender = p.getEnderChest().getContents();
        for (int i = 0; i < ender.length; i++) {
            if (own(p, ender[i], w, a)) {
                ender[i] = null;
                n++;
            }
        }
        p.getEnderChest().setContents(ender);
        mn.suld.plugin.item.BowQuiver.strip(p);
        if (own(p, p.getItemOnCursor(), w, a)) {
            p.setItemOnCursor(null);
            n++;
        }
        return n;
    }

    private boolean own(Player p, ItemStack it, String weaponPrefix, String armorPrefix) {
        if (it == null || it.getType().isAir()) return false;
        ItemInstance ii = services.items().read(it).orElse(null);
        if (ii == null) return false;
        String d = ii.definitionId();
        return (d.startsWith(weaponPrefix) || d.startsWith(armorPrefix)) && (ii.boundTo() == null || p.getUniqueId().equals(ii.boundTo()));
    }

    /** When {@code id} last hit or was hit by anything (0 = not this run). */
    public long lastCombat(UUID id) {
        return lastCombat.getOrDefault(id, 0L);
    }

    /**
     * Put the class the profile now names on the player: tree, attributes, empty pool, waiting spells, its own gear
     * (rebuilt with its identity, or the starter set the first time), and health at {@code share} of the new maximum.
     * Shared by the change and the trial.
     */
    public void applyClass(Player p, PlayerProfile pr, double share) {
        PlayerClass to = pr.playerClass().orElse(null);
        if (to == null) return;
        if (services.hud() != null) services.hud().teamChanged(p); // the class badge (and ✦) next to the name, now
        try {
            services.classWeapons().syncRemembered(p);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            if (services.skillTree() != null) services.skillTree().attach(p);
            skills.afterClassChange(p, SPELL_LOCK_MS);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("[class] " + p.getName() + ": applying " + to + " failed: " + ex);
        }
        try {
            if (services.classArmor != null && services.classArmor.supports(to)) services.classArmor.recover(p);
            if (pr.classGear().weapon() == null) {
                ItemStack w = services.classWeapons().starter(to, pr.progression().level(), p);
                if (w != null) p.getInventory().addItem(w).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            } else {
                services.classWeapons().recover(p);
            }
            mn.suld.plugin.item.BowQuiver.give(p);
            services.equipment().dirty(p);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("[class] " + p.getName() + ": the " + to + " gear failed (/classgear recover rebuilds it): " + ex);
        }
        var maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? 20 : maxAttr.getValue();
        if (!p.isDead()) p.setHealth(Math.max(1, Math.min(max, share * max)));
        if (services.hud() != null) services.hud().update(p, pr);
    }

    /** The share of the maximum health the player has now. */
    public static double healthShare(Player p) {
        var maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? 20 : maxAttr.getValue();
        return Math.max(0.05, Math.min(1.0, p.getHealth() / Math.max(1, max)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        // hits on a class-trial practice dummy are not a fight
        if (e.getEntity().getScoreboardTags().contains(ClassTrialService.TAG) || e.getEntity().getScoreboardTags().contains("suld_training_dummy")) return;
        long now = System.currentTimeMillis();
        if (e.getEntity() instanceof Player p) lastCombat.put(p.getUniqueId(), now);
        if (e.getDamager() instanceof Player p) lastCombat.put(p.getUniqueId(), now);
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) lastCombat.put(p.getUniqueId(), now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // a relog does not clear the combat tag: logging out to dodge the 60 s wait would be a way around it
        long now = System.currentTimeMillis();
        lastCombat.values().removeIf(t -> now - t > ClassChangeRules.COMBAT_MS);
    }
}
