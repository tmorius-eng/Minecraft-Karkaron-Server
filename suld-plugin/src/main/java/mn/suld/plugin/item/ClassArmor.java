package mn.suld.plugin.item;

import mn.suld.api.activity.ActivityTracker;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.classgear.ArmorPiece;
import mn.suld.api.classgear.ArmorRules;
import mn.suld.api.classgear.ArmorTier;
import mn.suld.api.classgear.ClassGear;
import mn.suld.api.classgear.MasteryPerks;
import mn.suld.api.classgear.MasteryRules;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.event.ExpGainedEvent;
import mn.suld.api.event.LevelUpEvent;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.loot.Rng;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpSource;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.event.SuldDomainBukkitEvent;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The class armour (docs/CLASS_ARMOR_SYSTEM.md, docs/ARMOR_PROGRESSION.md): one soulbound set per character whose
 * truth is the profile's {@link ClassGear} record. The stacks are renderings of it: regenerated in place with the same
 * UUIDs when the armour level or tier changes, rebuilt by {@code /classgear recover} when lost. Armour XP comes from
 * validated active minutes (ActivePlaytime), kills, quests, discoveries and dungeon clears; the armour level is capped
 * at the player level. Tiers are bought at their gates; enhancement scales the set through {@code Equipment.Wearer}.
 * Classes without armour definitions yet (everything but Баатар in the vertical slice) are left alone.
 */
public final class ClassArmor implements Listener {

    private final Plugin plugin;
    private final SuldServices services;

    public ClassArmor(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    private ItemService items() {
        return services.itemService();
    }

    private Optional<PlayerProfile> profile(Player p) {
        return services.profiles().cached(p.getUniqueId());
    }

    /** True when the class has armour definitions (the Баатар slice first). */
    public boolean supports(PlayerClass c) {
        return c != null && items() != null && items().catalog().item(ArmorRules.definitionId(c, ArmorPiece.HELMET, ArmorTier.T1)).isPresent();
    }

    /** The piece's item at the record's armour level and tier, with the given identity. */
    public ItemInstance build(UUID owner, PlayerClass c, ArmorPiece piece, ClassGear g, UUID id) {
        ItemDefinition def = items().catalog().require(ArmorRules.definitionId(c, piece, g.tier()));
        return items().generator().generate(def, def.rarity(), g.armorLevel(), Rng.seeded(ArmorRules.seed(owner, piece, g.tier())),
                "class", owner, id);
    }

    // ------------------------------------------------------------------------------------------------ grant

    /**
     * Grant missing pieces (class pick, or an existing character meeting the class armour for the first time).
     * A piece goes into its armour slot if that is free, else into the bag; with no room it is recorded and comes
     * with {@code /classgear recover}. Never dropped on the ground.
     */
    public void ensure(Player p) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null) return;
        PlayerClass c = pr.playerClass().orElse(null);
        if (!supports(c)) return;
        ClassGear g = pr.classGear();
        UUID weapon = services.classWeapons().rememberedId(p);
        if (g.weapon() == null && weapon != null) g = g.withWeapon(weapon);
        boolean full = false;
        for (ArmorPiece piece : ArmorPiece.values()) {
            if (g.piece(piece).isPresent()) continue;
            UUID id = UUID.randomUUID();
            g = g.withPiece(piece, id);
            if (!give(p, piece, items().stack(build(p.getUniqueId(), c, piece, g, id), p, 1))) full = true;
            services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "classgear.grant", ArmorRules.definitionId(c, piece, g.tier()) + "-" + id, ""));
        }
        if (!g.equals(pr.classGear())) {
            pr.classGear(g);
            services.profiles().save(pr);
            services.equipment().dirty(p);
        }
        if (full) p.sendMessage(Messages.error("Цүнх дүүрэн: ангийн хуягийн зарим хэсгийг /classgear recover-оор аваарай."));
    }

    private boolean give(Player p, ArmorPiece piece, ItemStack stack) {
        PlayerInventory inv = p.getInventory();
        org.bukkit.inventory.EquipmentSlot slot = switch (piece) {
            case HELMET -> org.bukkit.inventory.EquipmentSlot.HEAD;
            case CHESTPLATE -> org.bukkit.inventory.EquipmentSlot.CHEST;
            case LEGGINGS -> org.bukkit.inventory.EquipmentSlot.LEGS;
            case BOOTS -> org.bukkit.inventory.EquipmentSlot.FEET;
        };
        ItemStack worn = inv.getItem(slot);
        if (worn == null || worn.getType().isAir()) {
            inv.setItem(slot, stack);
            return true;
        }
        if (inv.firstEmpty() < 0) return false;
        inv.addItem(stack);
        return true;
    }

    // ------------------------------------------------------------------------------------- find / refresh

    /** Where each recorded piece is in the player's own inventory (armour slots included): raw slot index. */
    private Map<ArmorPiece, List<Integer>> locate(Player p, ClassGear g) {
        Map<ArmorPiece, List<Integer>> out = new EnumMap<>(ArmorPiece.class);
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemInstance ii = contents[i] == null ? null : services.items().read(contents[i]).orElse(null);
            if (ii == null) continue;
            for (Map.Entry<ArmorPiece, UUID> e : g.pieces().entrySet()) {
                if (e.getValue().equals(ii.uuid())) out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(i);
            }
        }
        return out;
    }

    /** Regenerate every piece the player carries at the record's level and tier (same UUID, same slot). */
    public void refresh(Player p) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null) return;
        PlayerClass c = pr.playerClass().orElse(null);
        if (!supports(c)) return;
        ClassGear g = pr.classGear();
        for (Map.Entry<ArmorPiece, List<Integer>> e : locate(p, g).entrySet()) {
            UUID id = g.pieces().get(e.getKey());
            for (int slot : e.getValue()) p.getInventory().setItem(slot, items().stack(build(p.getUniqueId(), c, e.getKey(), g, id), p, 1));
        }
        services.equipment().dirty(p);
    }

    /** {@code /classgear recover} for the armour. */
    public record Recovered(int restored, int duplicatesRemoved, boolean noRoom) {
    }

    /**
     * Rebuild missing pieces (not in the bag, armour slots, cursor or ender chest) with their recorded identity;
     * extra copies of a recorded identity in the player's own inventory are removed (only one may exist).
     */
    public Recovered recover(Player p) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null || !supports(pr.playerClass().orElse(null))) return new Recovered(0, 0, false);
        ensure(p); // pieces never granted are granted (and recorded) first
        ClassGear g = pr.classGear();
        PlayerClass c = pr.playerClass().orElseThrow();
        Map<ArmorPiece, List<Integer>> where = locate(p, g);
        int removed = 0;
        for (Map.Entry<ArmorPiece, List<Integer>> e : where.entrySet()) {
            for (int i = 1; i < e.getValue().size(); i++) {
                p.getInventory().setItem(e.getValue().get(i), null);
                removed++;
            }
        }
        List<UUID> elsewhere = new ArrayList<>();
        for (ItemStack it : p.getEnderChest().getContents()) services.items().read(it).ifPresent(ii -> elsewhere.add(ii.uuid()));
        services.items().read(p.getItemOnCursor()).ifPresent(ii -> elsewhere.add(ii.uuid()));
        int restored = 0;
        boolean noRoom = false;
        for (ArmorPiece piece : ArmorPiece.values()) {
            UUID id = g.pieces().get(piece);
            if (id == null || where.containsKey(piece) || elsewhere.contains(id)) continue;
            if (give(p, piece, items().stack(build(p.getUniqueId(), c, piece, g, id), p, 1))) restored++;
            else noRoom = true;
        }
        if (restored > 0 || removed > 0) {
            services.equipment().dirty(p);
            services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "classgear.recover.armor", p.getUniqueId().toString(),
                    "restored=" + restored + " duplicates=" + removed));
        }
        return new Recovered(restored, removed, noRoom);
    }

    // --------------------------------------------------------------------------------------------- armour XP

    /** Add armour XP (already the source's amount); levels regenerate the set and are announced. */
    public void addXp(Player p, double xp) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null || !supports(pr.playerClass().orElse(null)) || services.isSoul.test(p.getUniqueId())) return;
        ArmorRules.Gain gain = ArmorRules.gain(pr.classGear(), xp, pr.progression().level());
        if (gain.added() <= 0) return;
        pr.classGear(gain.after());
        services.session(p.getUniqueId()).armorXp += gain.added();
        if (gain.levels() > 0) levelled(p, gain.after());
    }

    private void levelled(Player p, ClassGear g) {
        refresh(p);
        p.sendMessage(Messages.success("Ангийн хуяг " + g.armorLevel() + "-р түвшинд хүрлээ (" + g.tier().displayName() + ")."));
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 1.2f);
    }

    /** ActivePlaytime: one validated active minute (no armour XP while area-fatigued). */
    public void activeMinute(Player p, ActivityTracker.Verdict v) {
        if (!v.active()) return;
        addXp(p, ArmorRules.XP_ACTIVE_MINUTE * (services.activity == null ? 1 : services.activity.areaFactor(p.getUniqueId())));
    }

    /** A dungeon clear: armour XP with repeat fatigue, and the tier gate. */
    public void dungeonCleared(Player p, String dungeonId) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null || !supports(pr.playerClass().orElse(null))) return;
        ClassGear before = pr.classGear();
        double xp = ArmorRules.dungeonXp(before, dungeonId);
        double mastery = MasteryRules.XP_DUNGEON * ArmorRules.fatigue(before, dungeonId)
                + (before.cleared().contains(dungeonId) ? 0 : MasteryRules.XP_FIRST_BOSS);
        pr.classGear(before.withCleared(dungeonId));
        addXp(p, xp);
        addMastery(p, mastery);
    }

    // ------------------------------------------------------------------------------------------------ mastery

    private final Map<UUID, MasteryRules.MinuteCap> castCaps = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, MasteryRules.MinuteCap> objectiveCaps = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Double> damageTaken = new java.util.concurrent.ConcurrentHashMap<>();

    /** Add armour mastery XP; new ranks refresh the stat pipeline (perks, +0.25 % power per rank). */
    public void addMastery(Player p, double xp) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null || !(xp > 0) || !supports(pr.playerClass().orElse(null)) || services.isSoul.test(p.getUniqueId())) return;
        MasteryRules.Gain gain = MasteryRules.gain(pr.classGear(), xp, pr.progression().level());
        pr.classGear(gain.after());
        services.session(p.getUniqueId()).masteryXp += xp;
        if (gain.ranks() > 0) masteryRanked(p, pr, gain.after());
    }

    private void masteryRanked(Player p, PlayerProfile pr, ClassGear g) {
        services.equipment().dirty(p);
        StringBuilder sb = new StringBuilder("Хуягийн ур чадвар " + g.mastery() + "-р зэрэгт хүрлээ!");
        for (MasteryPerks.Perk perk : MasteryPerks.of(pr.playerClass().orElse(null))) {
            if (perk.rank() == g.mastery()) sb.append(" Шинэ чадвар: ").append(perk.text());
        }
        p.sendMessage(Messages.success(sb.toString()));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "classgear.mastery", p.getUniqueId().toString(), "rank=" + g.mastery()));
    }

    /** A class objective (capped per minute): the meaningful class-specific act of each class. */
    public void objective(Player p) {
        if (objectiveCaps.computeIfAbsent(p.getUniqueId(), k -> new MasteryRules.MinuteCap(MasteryRules.OBJECTIVES_PER_MINUTE))
                .take(System.currentTimeMillis())) addMastery(p, MasteryRules.XP_OBJECTIVE);
    }

    /** A cast of one of the player's own class spells (SkillService, real casts only). */
    public void spellCast(Player p, mn.suld.api.skill.Spell spell) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null || pr.playerClass().orElse(null) != spell.clazz()) return;
        if (castCaps.computeIfAbsent(p.getUniqueId(), k -> new MasteryRules.MinuteCap(MasteryRules.CASTS_PER_MINUTE))
                .take(System.currentTimeMillis())) addMastery(p, MasteryRules.XP_CAST);
        // Бөө: a healing prayer that actually heals someone hurt (self or a player within 8 blocks below 80 %)
        if (spell == mn.suld.api.skill.Spell.SUNSNII_ZALBIRAL || spell == mn.suld.api.skill.Spell.TENGERIIN_KHAALGA) {
            for (Player o : p.getWorld().getPlayers()) {
                if (o.getLocation().distanceSquared(p.getLocation()) <= 64 && o.getHealth() < 0.8 * mn.suld.plugin.mob.MobService.maxHealth(o)) {
                    objective(p);
                    break;
                }
            }
        }
    }

    /**
     * A SÜLD mob kill in the open world or a dungeon ({@code farm} = the farming factor, 1 inside dungeons): armour XP
     * by tier, mastery by tier and level gap, and the kill-based class objectives.
     */
    public void mobKilled(Player p, org.bukkit.entity.LivingEntity mob, mn.suld.api.mob.MobDefinition def, double farm) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null) return;
        PlayerClass c = pr.playerClass().orElse(null);
        if (!supports(c)) return;
        double armorXp = switch (def.tier()) {
            case NORMAL -> ArmorRules.XP_KILL;
            case ELITE -> ArmorRules.XP_ELITE;
            default -> ArmorRules.XP_CHAMPION;
        };
        addXp(p, armorXp * farm);
        addMastery(p, MasteryRules.killXp(def.tier(), def.level(), pr.progression().level()) * farm);
        if (c == PlayerClass.MERGEN && mob.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by
                && by.getDamager() instanceof org.bukkit.entity.Projectile && p.getLocation().distance(mob.getLocation()) >= 16) objective(p);
        if (c == PlayerClass.KHULEGCHIN && p.isInsideVehicle()) objective(p);
    }

    /** Баатар's objective: every 40 damage taken from mobs while fighting. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getDamager() instanceof Player) return;
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null || pr.playerClass().orElse(null) != PlayerClass.BAATAR) return;
        double total = damageTaken.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
        while (total >= 40) {
            total -= 40;
            objective(p);
        }
        damageTaken.put(p.getUniqueId(), total);
    }

    /** Дархан's objective: crafting at the bench (and smith work, called by the smith). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(org.bukkit.event.inventory.CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (profile(p).flatMap(PlayerProfile::playerClass).orElse(null) == PlayerClass.DARKHAN) objective(p);
    }

    /** Smith work (repair / item upgrade at the Дархан NPC): Дархан's objective. */
    public void smithWork(Player p) {
        if (profile(p).flatMap(PlayerProfile::playerClass).orElse(null) == PlayerClass.DARKHAN) objective(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDomain(SuldDomainBukkitEvent e) {
        if (e.payload() instanceof ExpGainedEvent g) {
            Player p = Bukkit.getPlayer(g.player());
            if (p == null) return;
            // kills are credited by mobKilled (with tier and the farming factor); quests and discoveries here
            if (g.source() == ExpSource.QUEST || g.source() == ExpSource.DISCOVERY) addXp(p, ArmorRules.xpFor(g.source()));
            if (g.source() == ExpSource.DISCOVERY) addMastery(p, MasteryRules.XP_DISCOVERY);
        } else if (e.payload() instanceof LevelUpEvent lu) {
            Player p = Bukkit.getPlayer(lu.player());
            PlayerProfile pr = p == null ? null : profile(p).orElse(null);
            if (pr == null || !supports(pr.playerClass().orElse(null))) return;
            ArmorRules.Gain gain = ArmorRules.settle(pr.classGear(), lu.toLevel());
            if (gain.levels() > 0) {
                pr.classGear(gain.after());
                levelled(p, gain.after());
            }
            MasteryRules.Gain m = MasteryRules.settle(pr.classGear(), lu.toLevel());
            if (m.ranks() > 0) {
                pr.classGear(m.after());
                masteryRanked(p, pr, m.after());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        // the per-minute mastery caps stay (a relog must not refill them; they hold one minute's counts at most)
        damageTaken.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // after the class weapon's own join work; the profile is loaded at pre-login
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            ensure(p);
            refresh(p);
        }, 5L);
    }

    // ------------------------------------------------------------------------------------- tier / enhancement

    private int held(Player p, String materialId) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            ItemInstance ii = it == null ? null : services.items().read(it).orElse(null);
            if (ii != null && ii.definitionId().equals(materialId)) n += it.getAmount();
        }
        return n;
    }

    private void take(Player p, String materialId, int amount) {
        ItemStack[] st = p.getInventory().getStorageContents();
        for (int i = 0; i < st.length && amount > 0; i++) {
            ItemInstance ii = st[i] == null ? null : services.items().read(st[i]).orElse(null);
            if (ii == null || !ii.definitionId().equals(materialId)) continue;
            int use = Math.min(amount, st[i].getAmount());
            amount -= use;
            if (use == st[i].getAmount()) p.getInventory().setItem(i, null);
            else st[i].setAmount(st[i].getAmount() - use);
        }
    }

    /** Display name of a material (catalog) or a dungeon (the ones in the game); unknown dungeons say so. */
    public String displayName(String id) {
        if (id == null) return "";
        if (id.startsWith("dungeon.")) {
            var d = mn.suld.plugin.content.SuldContent.dungeonFor(id);
            return d != null ? d.displayName() : id.substring("dungeon.".length()) + " (тоглоомд хараахан нэмэгдээгүй)";
        }
        return items().catalog().item(id).map(ItemDefinition::displayName).orElse(id);
    }

    /** What the player holds towards the next tier. */
    public ArmorRules.Holdings holdings(Player p, PlayerProfile pr) {
        ArmorTier next = pr.classGear().tier().next().orElse(null);
        int mats = next == null || next.material() == null ? 0 : held(p, next.material());
        return new ArmorRules.Holdings(pr.currency(), mats, 0); // Тэнгэрийн Зэрэг (Ascension) is not in the game yet
    }

    public enum Upgrade { DONE, MAX, GATES, NO_CLASS, SOUL }

    /** Buy the next tier when every gate is met: coins and materials are taken, the set regenerates in its new look. */
    public Upgrade upgrade(Player p) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null || !supports(pr.playerClass().orElse(null))) return Upgrade.NO_CLASS;
        if (services.isSoul.test(p.getUniqueId())) return Upgrade.SOUL;
        ClassGear g = pr.classGear();
        ArmorTier next = g.tier().next().orElse(null);
        if (next == null) return Upgrade.MAX;
        if (!ArmorRules.canUpgrade(g, holdings(p, pr))) return Upgrade.GATES;
        pr.addCurrency(-next.coins());
        if (next.material() != null) take(p, next.material(), next.materials());
        pr.classGear(g.withTier(next));
        services.profiles().save(pr);
        refresh(p);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "classgear.tier", p.getUniqueId().toString(),
                g.tier() + "->" + next + " coins=" + next.coins()));
        return Upgrade.DONE;
    }

    public enum Enhance { DONE, MAX, COINS, NO_CLASS, SOUL }

    public Enhance enhance(Player p) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null || !supports(pr.playerClass().orElse(null))) return Enhance.NO_CLASS;
        if (services.isSoul.test(p.getUniqueId())) return Enhance.SOUL;
        ClassGear g = pr.classGear();
        if (g.enhance() >= ArmorRules.MAX_ENHANCE) return Enhance.MAX;
        long cost = ArmorRules.enhanceCost(g.armorLevel(), g.enhance() + 1, g.tier());
        if (pr.currency() < cost) return Enhance.COINS;
        pr.addCurrency(-cost);
        pr.classGear(g.withEnhance(g.enhance() + 1));
        services.profiles().save(pr);
        services.equipment().dirty(p);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "classgear.enhance", p.getUniqueId().toString(),
                "+" + (g.enhance() + 1) + " coins=" + cost));
        return Enhance.DONE;
    }

    /** Staff QA: a dungeon counted as cleared (tier gates), audited by the command. */
    public void markCleared(Player p, String dungeonId) {
        profile(p).ifPresent(pr -> {
            pr.classGear(pr.classGear().withCleared(dungeonId));
            services.profiles().save(pr);
        });
    }

    /** Staff QA: set the armour level / tier / enhancement / mastery directly (audited by the command). */
    public void set(Player p, String what, int value) {
        PlayerProfile pr = profile(p).orElse(null);
        if (pr == null) return;
        ClassGear g = pr.classGear();
        ClassGear after = switch (what) {
            case "level" -> g.withProgress(value, 0);
            case "tier" -> g.withTier(ArmorTier.of(value));
            case "enhance" -> g.withEnhance(value);
            case "mastery" -> g.withMastery(value, 0);
            default -> g;
        };
        pr.classGear(after);
        services.profiles().save(pr);
        refresh(p);
    }
}
