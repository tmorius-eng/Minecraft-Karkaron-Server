package mn.suld.plugin.training;

import mn.suld.api.classgear.ArmorPiece;
import mn.suld.api.classgear.ArmorRules;
import mn.suld.api.classgear.ArmorTier;
import mn.suld.api.classgear.ClassGear;
import mn.suld.api.classgear.MasteryRules;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.Affix;
import mn.suld.api.item.EquipSlot;
import mn.suld.api.item.EquipmentState;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemStat;
import mn.suld.api.item.RolledAffix;
import mn.suld.api.loot.Rng;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.skill.tree.SkillPoints;
import mn.suld.api.skill.tree.SkillTree;
import mn.suld.api.training.TrainingBuilds;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.item.ClassWeapons;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The max character of the training grounds (docs/TRAINING_GROUNDS.md): a stand-in copy of the player's profile
 * ({@link PlayerProfile#ephemeralCopy()}, which nothing writes) at level 60 and Тэнгэрийн Зэрэг III, with build A or B
 * ({@link TrainingBuilds}), the class's top armour (T6, enhancement 5, mastery 10, armour level 60), its T6 weapon and
 * the two best accessories. Every stat is at the top of its range and the affixes are the combat ones, so every trainee
 * sees the same numbers. The items are bound to the player and marked as training items: they exist only in the yard.
 */
final class TrainingKit {

    /** On every item of the kit (and nothing else). */
    static final NamespacedKey ITEM = new NamespacedKey("suld", "training_item");
    static final String RING = "jewel.altan_bugj", AMULET = "jewel.chingis_tamga";

    private static final Set<ItemStat> COMBAT = EnumSet.of(ItemStat.DAMAGE, ItemStat.CRIT_CHANCE, ItemStat.CRIT_DAMAGE, ItemStat.ATTACK_SPEED,
            ItemStat.SPELL_DAMAGE, ItemStat.COOLDOWN_REDUCTION);

    private final SuldServices services;

    TrainingKit(SuldServices services) {
        this.services = services;
    }

    /** What a trainee wears and holds: the weapon first (hotbar slot 1), then the four armour pieces. */
    record Items(ItemStack weapon, Map<ArmorPiece, ItemStack> armour, ClassGear gear) {
    }

    /**
     * An item rolled at the top: the count of affixes the most its rarity allows, every value at the top of its range,
     * and of {@code tries} seeded draws of which affixes, the one with the most combat affixes (damage, crits, attack
     * speed, spell damage, cooldowns, spell modifiers). Deterministic: the same definition always gives the same item.
     */
    ItemInstance top(ItemDefinition def, UUID owner, int level, long seed) {
        ItemInstance best = null;
        int bestScore = -1;
        for (int t = 0; t < 24; t++) {
            ItemInstance i = services.itemService().generator().generate(def, def.maxRarity(), level, rolls(seed * 31 + t), "training", owner, UUID.randomUUID());
            int score = 0;
            for (RolledAffix a : i.affixes()) {
                Affix x = services.itemService().catalog().affix(a.affixId()).orElse(null);
                if (x != null && (x.stat() == null || COMBAT.contains(x.stat()))) score++;
            }
            if (score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best.bound() ? best : best.boundTo(owner);
    }

    /** First call (the affix count) the most, quality always the top, the affix draws from a seed. */
    private static Rng rolls(long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        return new Rng() {
            boolean counted;

            @Override
            public double nextDouble() {
                return 0.999_999;
            }

            @Override
            public int nextInt(int bound) {
                if (!counted) {
                    counted = true;
                    return bound - 1;
                }
                return r.nextInt(bound);
            }
        };
    }

    /** The weapon and armour of {@code c}, bound to {@code p}, marked as training items. */
    Items items(Player p, PlayerClass c) {
        UUID owner = p.getUniqueId();
        ItemDefinition wd = ClassWeapons.definition(c, ClassWeapons.tierFor(TrainingBuilds.LEVEL));
        ItemInstance weapon = top(wd, owner, TrainingBuilds.LEVEL, c.ordinal() * 1000L + 7);
        ClassGear g = ClassGear.NONE.withProgress(ArmorRules.MAX_ARMOR_LEVEL, 0).withTier(ArmorTier.T6).withEnhance(ArmorRules.MAX_ENHANCE)
                .withMastery(MasteryRules.MAX_RANK, 0).withWeapon(weapon.uuid());
        Map<ArmorPiece, ItemStack> armour = new EnumMap<>(ArmorPiece.class);
        for (ArmorPiece piece : ArmorPiece.values()) {
            ItemDefinition d = services.itemService().catalog().item(ArmorRules.definitionId(c, piece, ArmorTier.T6)).orElse(null);
            if (d == null) continue;
            ItemInstance a = top(d, owner, ArmorRules.MAX_ARMOR_LEVEL, c.ordinal() * 1000L + 100 + piece.ordinal());
            g = g.withPiece(piece, a.uuid());
            armour.put(piece, mark(services.itemService().stack(a, p, 1)));
        }
        return new Items(mark(services.itemService().stack(weapon, p, 1)), armour, g);
    }

    /** A class's T6 weapon to look at (a station's floating icon): nobody's, never in a bag. */
    ItemStack icon(PlayerClass c) {
        ItemDefinition wd = ClassWeapons.definition(c, ClassWeapons.tierFor(TrainingBuilds.LEVEL));
        return services.itemService().stack(services.itemService().generate(wd, wd.rarity(), TrainingBuilds.LEVEL, null, "training-icon"), null, 1);
    }

    /** The two best accessories (a ring and an amulet), kept with the stand-in like a player's own. */
    EquipmentState accessories(Player p) {
        EquipmentState s = EquipmentState.NONE;
        ItemDefinition ring = services.itemService().catalog().item(RING).orElse(null);
        ItemDefinition amulet = services.itemService().catalog().item(AMULET).orElse(null);
        if (ring != null) s = s.with(EquipSlot.ACCESSORY_1, top(ring, p.getUniqueId(), TrainingBuilds.LEVEL, 9001));
        if (amulet != null) s = s.with(EquipSlot.ACCESSORY_2, top(amulet, p.getUniqueId(), TrainingBuilds.LEVEL, 9002));
        return s;
    }

    /**
     * The stand-in: the real profile's copy as {@code c} at level 60 with Тэнгэрийн Зэрэг III, build {@code variant}
     * learned (the points a finished character has granted), the kit's class gear, the accessories, and no coins (the
     * yard sells nothing; a stand-in's coins could only ever be spent on something real).
     */
    PlayerProfile standIn(PlayerProfile real, Player p, PlayerClass c, SkillTree tree, int variant, ClassGear gear, EquipmentState accessories,
                          mn.suld.api.skill.tree.SkillEngine.Context realContext) {
        PlayerProfile s = real.ephemeralCopy();
        s.forceClass(c);
        s.progression(new Progression(TrainingBuilds.LEVEL, 0));
        s.endgame(s.endgame().withAscension(mn.suld.api.balance.Ascension.MAX_RANK));
        s.classGear(gear);
        s.equipment(accessories);
        s.currency(0);
        TrainingBuilds.Build b = TrainingBuilds.of(tree, variant);
        int base = SkillPoints.total(TrainingBuilds.LEVEL, realContext.finishedChapters(), realContext.discoveredRegions(), 0)
                + Math.min(mn.suld.api.balance.Ascension.MAX_RANK, s.ascension()); // the honours IV–X give no point
        s.skillState(mn.suld.api.skill.tree.SkillState.NONE.withRanks(b.allocation().encode())
                .withGranted(Math.max(0, TrainingBuilds.maxPoints() - base)));
        return s;
    }

    static ItemStack mark(ItemStack s) {
        if (s == null) return null;
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(ITEM, PersistentDataType.BYTE, (byte) 1);
            s.setItemMeta(m);
        }
        return s;
    }

    static boolean marked(ItemStack s) {
        return s != null && s.hasItemMeta() && s.getItemMeta().getPersistentDataContainer().has(ITEM, PersistentDataType.BYTE);
    }
}
