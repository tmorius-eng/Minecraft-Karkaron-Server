package mn.suld.api.web;

import mn.suld.api.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmoryTest {

    private static final Armory.PlayerSheet SHEET = new Armory.PlayerSheet("0f8fad5b-d9cb-469f-a165-70867728950e", "Temujin", true,
            "Баатар", 42, 3, "Тэнгэрийн Зэрэг III", "KHN", "Хан Овог", Map.of("SCIMITAR", 12), Map.of("TOMRIIN_DARKHAN", 7),
            List.of(new Armory.Gear("HEAD", "Ган Дуулга", "rare", 40)), 1_700_000_000_000L);

    @Test
    void aSheetCarriesWhatOthersSeeInTheGame() {
        Map<String, Object> m = Json.object(Json.parse(Json.write(SHEET.json())));
        assertEquals("Temujin", m.get("name"));
        assertEquals("Баатар", m.get("class"));
        assertEquals(42, Json.integer(m.get("level")));
        assertEquals(3, Json.integer(Json.object(m.get("ascension")).get("rank")));
        assertEquals("KHN", Json.object(m.get("clan")).get("tag"));
        assertEquals(7, Json.integer(Json.object(m.get("crafts")).get("TOMRIIN_DARKHAN")));
        assertEquals("Ган Дуулга", Json.object(Json.array(m.get("gear")).get(0)).get("name"));
    }

    @Test
    void noViewCarriesAForbiddenKey() {
        List<Object> views = List.of(
                SHEET.json(),
                Armory.clans(List.of(new Armory.ClanLine(1, "KHN", "Хан Овог", 5, 12_000, 8)), 1),
                Armory.khuraldai("2026-W42", "VOTING", List.of(new Armory.Option("campaign.chonyn_ayan", "Чонын Их Аян", 4)),
                        new Armory.Campaign("campaign.chonyn_ayan", "Чонын Их Аян", 120, 3000, "ACTIVE"), List.of("campaign.prop.caravan"), 1));
        for (Object v : views) {
            String json = Json.write(v);
            Object parsed = Json.parse(json);
            assertNoForbidden(parsed, json);
        }
    }

    private static void assertNoForbidden(Object node, String json) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                assertFalse(Armory.NEVER.contains(String.valueOf(e.getKey()).toLowerCase(java.util.Locale.ROOT)), "key " + e.getKey() + " in " + json);
                assertNoForbidden(e.getValue(), json);
            }
        } else if (node instanceof List<?> l) {
            for (Object o : l) assertNoForbidden(o, json);
        }
    }

    @Test
    void aClanlessPlayerHasANullClan() {
        Armory.PlayerSheet lone = new Armory.PlayerSheet("0f8fad5b-d9cb-469f-a165-70867728950e", "Lone", false, null, 1, 0, null,
                null, null, Map.of(), Map.of(), List.of(), 0);
        Map<String, Object> m = Json.object(Json.parse(Json.write(lone.json())));
        assertTrue(m.containsKey("clan"));
        assertNull(m.get("clan"));
    }

    @Test
    void onlyACanonicalUuidIsAPlayerKey() {
        assertTrue(Armory.uuid("0f8fad5b-d9cb-469f-a165-70867728950e"));
        assertFalse(Armory.uuid("Temujin"));
        assertFalse(Armory.uuid("0f8fad5b-d9cb-469f-a165-70867728950e/../x"));
        assertFalse(Armory.uuid(null));
    }

    @Test
    void anErrorIsJsonWithItsStatus() {
        Map<String, Object> m = Json.object(Json.parse(Armory.error(429, "too many requests")));
        assertEquals(429, Json.integer(m.get("status")));
        assertEquals("too many requests", m.get("error"));
    }
}
