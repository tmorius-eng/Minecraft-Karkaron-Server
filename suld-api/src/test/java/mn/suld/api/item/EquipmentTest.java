package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.loot.Rng;
import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.ModKey;
import mn.suld.api.skill.tree.ProcKind;
import mn.suld.api.skill.tree.StatKey;
import mn.suld.api.skill.tree.TriggerEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentTest {

    static final UUID ME = UUID.randomUUID();
    static final Equipment.Wearer BAATAR_30 = new Equipment.Wearer(ME, PlayerClass.BAATAR, 30);

    static ItemInstance item(String def, ItemRarity r, int lvl, Map<ItemStat, Double> stats, RolledAffix... affixes) {
        return new ItemInstance(def, UUID.randomUUID(), r, lvl, stats, List.of(affixes), false, null, 0, "t", ItemInstance.SCHEMA_VERSION);
    }

    static ItemCatalog catalog() {
        ItemDefinition sword = Fixtures.def("w.sword").stat(ItemStat.DAMAGE, 10, 10).stat(ItemStat.CRIT_CHANCE, 5, 5).build();
        ItemDefinition helm = Fixtures.def("a.helm").type(ItemType.HELMET, "minecraft:iron_helmet").stat(ItemStat.ARMOR, 2, 2).set("set.s").build();
        ItemDefinition chest = Fixtures.def("a.chest").type(ItemType.CHESTPLATE, "minecraft:iron_chestplate").stat(ItemStat.MAX_HEALTH, 10, 10).set("set.s").build();
        ItemDefinition boots = Fixtures.def("a.boots").type(ItemType.BOOTS, "minecraft:iron_boots").stat(ItemStat.MOVE_SPEED, 5, 5).set("set.s").build();
        ItemDefinition ring = Fixtures.def("j.ring").type(ItemType.RING, "minecraft:gold_nugget").stat(ItemStat.XP_GAIN, 4, 4).set("set.s")
                .effect(new Effect.Proc(TriggerEvent.KILL, 100, ProcKind.HEAL, 2, 0, 0)).build();
        ItemDefinition mageOnly = Fixtures.def("w.staff").type(ItemType.STAFF, "minecraft:stick").classes(PlayerClass.BOO).stat(ItemStat.SPELL_DAMAGE, 8, 8).build();
        ItemDefinition high = Fixtures.def("w.high").level(40).stat(ItemStat.DAMAGE, 50, 50).build();
        ItemDefinition baatarBlade = Fixtures.def("w.bb").classes(PlayerClass.BAATAR).stat(ItemStat.DAMAGE, 5, 5)
                .effect(new Effect.SpellMod(Spell.TENGER_TSAVCHILT, ModKey.DAMAGE_PCT, 10)).build();
        Affix sharp = Fixtures.statAffix("sharp", ItemStat.DAMAGE, 1, 5, ItemRarity.UNCOMMON, ItemType.Category.WEAPON);
        Affix spell = new Affix("spell", "s", false, null, Spell.TENGER_TSAVCHILT, ModKey.DAMAGE_PCT, 5, 15, 0, ItemRarity.RARE, Set.of(ItemType.Category.WEAPON), 1);
        TreeMap<Integer, ItemSet.Bonus> b = new TreeMap<>();
        b.put(2, new ItemSet.Bonus(Map.of(ItemStat.MAX_HEALTH, 20.0), List.of(), ""));
        b.put(3, new ItemSet.Bonus(Map.of(ItemStat.CRIT_CHANCE, 5.0), List.of(), ""));
        b.put(4, new ItemSet.Bonus(Map.of(), List.of(new Effect.Stat(StatKey.THORNS, 15), new Effect.Proc(TriggerEvent.HIT, 10, ProcKind.SMITE, 1.5, 0, 6)), ""));
        ItemSet set = new ItemSet("set.s", "S", List.of("a.helm", "a.chest", "a.boots", "j.ring"), b);
        return Fixtures.catalog(List.of(sword, helm, chest, boots, ring, mageOnly, high, baatarBlade), List.of(sharp, spell), List.of(set), List.of());
    }

    static final ItemCatalog CAT = catalog();

    @Test
    void statsFromEverySlotAddUpAndFlatDamageIsSeparate() {
        Map<EquipSlot, ItemInstance> worn = new EnumMap<>(EquipSlot.class);
        worn.put(EquipSlot.MAIN_HAND, item("w.sword", ItemRarity.RARE, 5, Map.of(ItemStat.DAMAGE, 10.0, ItemStat.CRIT_CHANCE, 5.0), new RolledAffix("sharp", 3)));
        worn.put(EquipSlot.HEAD, item("a.helm", ItemRarity.COMMON, 5, Map.of(ItemStat.ARMOR, 2.0)));
        Equipment.Bonus bonus = Equipment.compute(CAT, worn, BAATAR_30, Set.of());
        assertEquals(13.0, bonus.flatDamage(), 1e-9, "base 10 + affix 3");
        assertEquals(5.0, bonus.statKeys().get(StatKey.CRIT_CHANCE), 1e-9);
        assertEquals(2.0, bonus.statKeys().get(StatKey.ARMOR), 1e-9);
        assertNull(bonus.statKeys().get(StatKey.ATTACK_PCT), "flat damage is not a percent stat");
        assertTrue(bonus.inactive().isEmpty());
    }

    @Test
    void wrongSlotClassLevelBindingOrBrokenGiveNothing() {
        Map<EquipSlot, ItemInstance> worn = new EnumMap<>(EquipSlot.class);
        worn.put(EquipSlot.HEAD, item("w.sword", ItemRarity.COMMON, 1, Map.of(ItemStat.DAMAGE, 10.0)));              // a sword on the head
        worn.put(EquipSlot.MAIN_HAND, item("w.staff", ItemRarity.COMMON, 1, Map.of(ItemStat.SPELL_DAMAGE, 8.0)));    // a boo staff on a baatar
        worn.put(EquipSlot.OFF_HAND, item("w.high", ItemRarity.COMMON, 40, Map.of(ItemStat.DAMAGE, 50.0)));          // level 40 at level 30
        worn.put(EquipSlot.CHEST, new ItemInstance("a.chest", UUID.randomUUID(), ItemRarity.COMMON, 1, Map.of(ItemStat.MAX_HEALTH, 10.0), List.of(),
                false, UUID.randomUUID(), 0, "t", 2));                                                                       // bound to someone else
        worn.put(EquipSlot.FEET, item("a.boots", ItemRarity.COMMON, 1, Map.of(ItemStat.MOVE_SPEED, 5.0)));           // broken
        Equipment.Bonus bonus = Equipment.compute(CAT, worn, BAATAR_30, Set.of(EquipSlot.FEET));
        assertEquals(Equipment.Inactive.WRONG_SLOT, bonus.inactive().get(EquipSlot.HEAD));
        assertEquals(Equipment.Inactive.CLASS, bonus.inactive().get(EquipSlot.MAIN_HAND));
        assertEquals(Equipment.Inactive.WRONG_SLOT, bonus.inactive().get(EquipSlot.OFF_HAND), "a sword is not an off-hand");
        assertEquals(Equipment.Inactive.BOUND_TO_OTHER, bonus.inactive().get(EquipSlot.CHEST));
        assertEquals(Equipment.Inactive.BROKEN, bonus.inactive().get(EquipSlot.FEET));
        assertTrue(bonus.itemStats().isEmpty());
        Map<EquipSlot, ItemInstance> high = Map.of(EquipSlot.MAIN_HAND, item("w.high", ItemRarity.COMMON, 40, Map.of(ItemStat.DAMAGE, 50.0)));
        assertEquals(Equipment.Inactive.LEVEL, Equipment.compute(CAT, high, BAATAR_30, Set.of()).inactive().get(EquipSlot.MAIN_HAND));
        assertTrue(Equipment.compute(CAT, high, new Equipment.Wearer(ME, PlayerClass.BAATAR, 40), Set.of()).inactive().isEmpty());
    }

    @Test
    void itemLevelAboveTheRequirementAlsoGates() {
        ItemInstance lvl35 = item("w.sword", ItemRarity.COMMON, 35, Map.of(ItemStat.DAMAGE, 10.0));
        assertEquals(35, Equipment.requiredLevel(CAT.require("w.sword"), lvl35));
        assertEquals(Equipment.Inactive.LEVEL, Equipment.check(CAT, EquipSlot.MAIN_HAND, lvl35, BAATAR_30, false).orElseThrow());
    }

    @Test
    void setBonusesActivateOnlyAtTheirPieceCounts() {
        Map<EquipSlot, ItemInstance> worn = new EnumMap<>(EquipSlot.class);
        worn.put(EquipSlot.HEAD, item("a.helm", ItemRarity.COMMON, 1, Map.of(ItemStat.ARMOR, 2.0)));
        Equipment.Bonus one = Equipment.compute(CAT, worn, BAATAR_30, Set.of());
        assertEquals(1, one.sets().get(0).worn());
        assertTrue(one.sets().get(0).active().isEmpty());
        assertEquals(0.0, one.stat(ItemStat.MAX_HEALTH));

        worn.put(EquipSlot.CHEST, item("a.chest", ItemRarity.COMMON, 1, Map.of(ItemStat.MAX_HEALTH, 10.0)));
        Equipment.Bonus two = Equipment.compute(CAT, worn, BAATAR_30, Set.of());
        assertEquals(30.0, two.stat(ItemStat.MAX_HEALTH), 1e-9, "10 from the chest + the 2-piece 20");
        assertEquals(0.0, two.stat(ItemStat.CRIT_CHANCE));

        worn.put(EquipSlot.FEET, item("a.boots", ItemRarity.COMMON, 1, Map.of(ItemStat.MOVE_SPEED, 5.0)));
        Equipment.Bonus three = Equipment.compute(CAT, worn, BAATAR_30, Set.of());
        assertEquals(5.0, three.stat(ItemStat.CRIT_CHANCE), 1e-9);
        assertNull(three.statKeys().get(StatKey.THORNS));

        worn.put(EquipSlot.ACCESSORY_1, item("j.ring", ItemRarity.COMMON, 1, Map.of(ItemStat.XP_GAIN, 4.0)));
        Equipment.Bonus four = Equipment.compute(CAT, worn, BAATAR_30, Set.of());
        assertEquals(15.0, four.statKeys().get(StatKey.THORNS), 1e-9, "the 4-piece stat effect");
        assertEquals(2, four.procs().size(), "the ring's own passive and the 4-piece passive");
        assertEquals(3, four.sets().get(0).active().size());
    }

    @Test
    void theSamePieceTwiceCountsOnce() {
        Map<EquipSlot, ItemInstance> worn = new EnumMap<>(EquipSlot.class);
        worn.put(EquipSlot.ACCESSORY_1, item("j.ring", ItemRarity.COMMON, 1, Map.of()));
        worn.put(EquipSlot.ACCESSORY_2, item("j.ring", ItemRarity.COMMON, 1, Map.of()));
        assertEquals(1, Equipment.compute(CAT, worn, BAATAR_30, Set.of()).sets().get(0).worn());
    }

    @Test
    void spellAffixesAndItemEffectsBecomeSpellModifiers() {
        Map<EquipSlot, ItemInstance> worn = Map.of(EquipSlot.MAIN_HAND, item("w.bb", ItemRarity.RARE, 5, Map.of(ItemStat.DAMAGE, 5.0), new RolledAffix("spell", 12)));
        Equipment.Bonus bonus = Equipment.compute(CAT, worn, BAATAR_30, Set.of());
        assertEquals(22.0, bonus.mods().get(Spell.TENGER_TSAVCHILT).get(ModKey.DAMAGE_PCT), 1e-9, "affix 12 + unique effect 10");
    }

    @Test
    void gearScoreCountsOnlyActiveItems() {
        Map<EquipSlot, ItemInstance> worn = new EnumMap<>(EquipSlot.class);
        worn.put(EquipSlot.MAIN_HAND, item("w.sword", ItemRarity.EPIC, 20, Map.of()));
        worn.put(EquipSlot.HEAD, item("a.helm", ItemRarity.COMMON, 10, Map.of()));
        assertEquals(Math.round(20 * 1.75 + 10), Equipment.gearScore(worn, Map.of()));
        assertEquals(10, Equipment.gearScore(worn, Map.of(EquipSlot.MAIN_HAND, Equipment.Inactive.BROKEN)));
    }

    @Test
    void bundledSetsGiveTheirBonusesWithRealItems() {
        ItemCatalog cat = ItemCatalogLoaderTest.BUNDLED;
        ItemGenerator gen = new ItemGenerator(cat);
        Map<EquipSlot, ItemInstance> worn = new EnumMap<>(EquipSlot.class);
        Rng rng = Rng.seeded(5);
        worn.put(EquipSlot.HEAD, gen.generate(cat.require("armor.chingis.helmet"), ItemRarity.LEGENDARY, 45, rng, "t", ME));
        worn.put(EquipSlot.CHEST, gen.generate(cat.require("armor.chingis.chestplate"), ItemRarity.LEGENDARY, 45, rng, "t", ME));
        worn.put(EquipSlot.LEGS, gen.generate(cat.require("armor.chingis.leggings"), ItemRarity.LEGENDARY, 45, rng, "t", ME));
        worn.put(EquipSlot.FEET, gen.generate(cat.require("armor.chingis.boots"), ItemRarity.LEGENDARY, 45, rng, "t", ME));
        worn.put(EquipSlot.ACCESSORY_1, gen.generate(cat.require("jewel.chingis_tamga"), ItemRarity.LEGENDARY, 45, rng, "t", ME));
        Equipment.Bonus b = Equipment.compute(cat, worn, new Equipment.Wearer(ME, PlayerClass.BAATAR, 50), Set.of());
        Equipment.SetProgress p = b.sets().get(0);
        assertEquals(5, p.worn());
        assertEquals(4, p.active().size(), "2, 3, 4 and 5-piece bonuses");
        assertEquals(10.0, b.statKeys().get(StatKey.ATTACK_PCT), 1e-9);
        assertTrue(b.procs().stream().anyMatch(pr -> pr.kind() == ProcKind.SMITE));
    }

    @Test
    void woundScalesOnlyTheWearersSoulboundGear() {
        ItemCatalog cat = catalog();
        ItemInstance own = new ItemInstance("w.sword", UUID.randomUUID(), ItemRarity.COMMON, 30, Map.of(ItemStat.DAMAGE, 10.0, ItemStat.CRIT_CHANCE, 5.0),
                List.of(), true, ME, 0, "t", ItemInstance.SCHEMA_VERSION);
        Map<EquipSlot, ItemInstance> worn = new EnumMap<>(EquipSlot.class);
        worn.put(EquipSlot.MAIN_HAND, own);
        Equipment.Bonus healthy = Equipment.compute(cat, worn, BAATAR_30, Set.of());
        Equipment.Bonus wounded = Equipment.compute(cat, worn, new Equipment.Wearer(ME, PlayerClass.BAATAR, 30, 0.85), Set.of());
        assertEquals(10.0, healthy.flatDamage(), 1e-9);
        assertEquals(8.5, wounded.flatDamage(), 1e-9, "−15 % on the class gear");
        assertEquals(5.0 * 0.85, wounded.stat(ItemStat.CRIT_CHANCE), 1e-9);
        // gear that is not soulbound to this wearer is untouched
        worn.put(EquipSlot.MAIN_HAND, item("w.sword", ItemRarity.COMMON, 30, Map.of(ItemStat.DAMAGE, 10.0)));
        assertEquals(10.0, Equipment.compute(cat, worn, new Equipment.Wearer(ME, PlayerClass.BAATAR, 30, 0.85), Set.of()).flatDamage(), 1e-9);
        assertEquals(1.0, new Equipment.Wearer(ME, PlayerClass.BAATAR, 30, 7).boundFactor(), "clamped");
    }
}
