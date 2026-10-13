package mn.suld.api.mastery;

import mn.suld.api.region.Area;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatherPlanTest {

    // three areas of the bundled world's shape: an inner one by the capital, a middle one and an outer one
    private static final Area TUUL = new Area(16, "area.tuul", "Туулын Хөндий", "region.kherlen", 51, 700, 45, 90, 1, 3, 60, "", "VERIFIED");
    private static final Area SELENGE = new Area(30, "area.selenge", "Сэлэнгэ Мөрөн", "region.khangai", 700, 1500, 0, 45, 13, 17, 0, "", "VERIFIED");
    private static final Area KHARKHIRAA = new Area(38, "area.kharkhiraa", "Хархираа Уул", "region.altai", 1500, 2600, 270, 315, 26, 30, 0, "", "VERIFIED");
    private static final Area ULZ = new Area(42, "area.ulz", "Улз Гол", "region.khentii", 3800, 5000, 45, 90, 44, 60, 0, "", "VERIFIED");
    private static final Area KHUVSGUL = new Area(33, "area.khuvsgul", "Хөвсгөл Нуур", "region.khangai", 1500, 2600, 315, 360, 17, 20, 0, "", "VERIFIED");
    private static final List<Area> AREAS = List.of(TUUL, SELENGE, KHARKHIRAA, ULZ, KHUVSGUL);

    @Test
    void anAreasTierIsItsBand() {
        assertEquals(1, GatherPlan.tier(TUUL));
        assertEquals(2, GatherPlan.tier(SELENGE), "13 opens tier 2");
        assertEquals(3, GatherPlan.tier(KHARKHIRAA), "26: tier 3 (25)");
        assertEquals(4, GatherPlan.tier(ULZ));
        assertEquals(2, GatherPlan.tier(KHUVSGUL));
    }

    @Test
    void everyAreaHoldsItsVeinsHerbsAndOnePenInsideItself() {
        List<GatherPlan.Node> plan = GatherPlan.plan(AREAS, 0, 0, 5000, List.of());
        for (Area a : AREAS) {
            List<GatherPlan.Node> mine = plan.stream().filter(n -> n.areaId().equals(a.id())).toList();
            assertEquals(GatherPlan.VEINS, mine.stream().filter(n -> n.kind() == GatherPlan.Kind.VEIN).count(), a.id());
            assertEquals(GatherPlan.HERBS, mine.stream().filter(n -> n.kind() == GatherPlan.Kind.HERB).count(), a.id());
            assertEquals(1, mine.stream().filter(n -> n.kind() == GatherPlan.Kind.PEN && n.alt() == 0).count(), a.id());
            assertEquals(GatherPlan.PEN_ALTS, mine.stream().filter(n -> n.kind() == GatherPlan.Kind.PEN && n.alt() > 0).count(), a.id() + ": its pen's other spots");
            for (GatherPlan.Node n : mine) if (n.kind() == GatherPlan.Kind.PEN) assertEquals("pen." + a.id().substring(5), n.base());
            for (GatherPlan.Node n : mine) {
                assertTrue(a.contains(n.x(), n.z()), n + " stands outside " + a.id());
                assertEquals(GatherPlan.tier(a), n.tier());
            }
        }
    }

    @Test
    void theSpotsAreTheSameOnEveryStartAndMoveWithTheSpawn() {
        List<GatherPlan.Node> a = GatherPlan.plan(AREAS, 0, 0, 5000, List.of());
        assertEquals(a, GatherPlan.plan(AREAS, 0, 0, 5000, List.of()));
        List<GatherPlan.Node> moved = GatherPlan.plan(AREAS, 100, -40, 5000, List.of());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).id(), moved.get(i).id());
            assertEquals(a.get(i).x() + 100, moved.get(i).x(), "a spot is fixed relative to the spawn");
            assertEquals(a.get(i).z() - 40, moved.get(i).z());
        }
    }

    @Test
    void nodesKeepClearOfEachOtherAndOfTheKeepAwayPoints() {
        // an ovoo at each area's heart, as OvooService places them
        List<int[]> ovoos = new java.util.ArrayList<>();
        for (Area a : AREAS) {
            double span = ((a.toDeg() - a.fromDeg()) % 360 + 360) % 360;
            double mid = Math.toRadians(a.fromDeg() + span / 2);
            double r = (a.minRadius() + a.maxRadius()) / 2;
            ovoos.add(new int[]{(int) Math.round(Math.sin(mid) * r), (int) Math.round(-Math.cos(mid) * r)});
        }
        List<GatherPlan.Node> plan = GatherPlan.plan(AREAS, 0, 0, 5000, ovoos);
        Set<String> ids = new HashSet<>();
        for (GatherPlan.Node n : plan) {
            assertTrue(ids.add(n.id()), "one id once: " + n.id());
            int keep = n.kind() == GatherPlan.Kind.PEN ? GatherPlan.PEN_KEEP : GatherPlan.KEEP;
            for (int[] o : ovoos) assertTrue(n.distanceTo(o[0], o[1]) >= keep, n + " is " + n.distanceTo(o[0], o[1]) + " from an ovoo");
            for (GatherPlan.Node m : plan) {
                if (m == n) continue;
                assertTrue(n.distanceTo(m.x(), m.z()) >= GatherPlan.SPACING, n.id() + " and " + m.id() + " are " + n.distanceTo(m.x(), m.z()) + " apart");
            }
        }
    }

    @Test
    void nothingIsPlannedInsideTheCitysWalls() {
        // Kharkhorum's bounds on the dev world, relative to the spawn: its outer wall reaches 190 blocks out, deep into
        // the inner areas that begin at 51 (Туул)
        int[] city = {-190, -192, 189, 118};
        List<GatherPlan.Node> plan = GatherPlan.plan(AREAS, 0, 0, 5000, List.of(), List.<int[]>of(city));
        for (GatherPlan.Node n : plan) {
            int keep = n.kind() == GatherPlan.Kind.PEN ? GatherPlan.PEN_KEEP + GatherPlan.PEN_HALF : GatherPlan.KEEP;
            assertTrue(!GatherPlan.within(List.<int[]>of(city), n.x(), n.z(), keep), n + " stands in or within " + keep + " of the city");
        }
        List<GatherPlan.Node> tuul = plan.stream().filter(n -> n.areaId().equals(TUUL.id())).toList();
        assertEquals(GatherPlan.VEINS + GatherPlan.HERBS + 1 + GatherPlan.PEN_ALTS, tuul.size(), "the inner area keeps every node, outside the walls");
        assertTrue(GatherPlan.plan(AREAS, 0, 0, 5000, List.of()).stream().anyMatch(n -> GatherPlan.within(List.<int[]>of(city), n.x(), n.z(), 0)),
                "without the city some nodes would stand inside it (the case this guards)");
    }

    @Test
    void nothingStandsPastTheWorldBorder() {
        // a world border 3 850 blocks out: Улз (3 800–5 000) has no room left inside it, the others keep theirs
        List<GatherPlan.Node> plan = GatherPlan.plan(AREAS, 0, 0, 3850, List.of());
        for (GatherPlan.Node n : plan) assertTrue(Math.hypot(n.x(), n.z()) <= 3850 - 64, n + " is past the border");
        assertTrue(plan.stream().noneMatch(n -> n.areaId().equals(ULZ.id())), "Улз lies wholly past the border");
        assertTrue(plan.stream().anyMatch(n -> n.areaId().equals(TUUL.id())));
    }

    @Test
    void theCraftOfANodeGathersItsMaterial() {
        assertEquals(Profession.TOMRIIN_DARKHAN, GatherPlan.Kind.VEIN.craft());
        assertEquals(Profession.EMIIN_BARIACH, GatherPlan.Kind.HERB.craft());
        assertEquals(ProfessionMaterials.TUMRIIN_KHUDAR, ProfessionMaterials.gathered(Profession.TOMRIIN_DARKHAN, 2));
        assertEquals(ProfessionMaterials.ALTAN_KHUDAR, ProfessionMaterials.gathered(Profession.TOMRIIN_DARKHAN, 3));
        assertEquals(ProfessionMaterials.KHYALGAS, ProfessionMaterials.gathered(Profession.ADUUCHIN, 1));
        assertEquals(ProfessionMaterials.NARIIN_NOOS, ProfessionMaterials.gathered(Profession.ESGIICH, 4));
    }
}
