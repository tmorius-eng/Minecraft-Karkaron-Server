package mn.suld.api.web;

import mn.suld.api.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The read-only armory's views (docs/ARMORY_API.md): what the web may know of a player, the clans and the week's
 * Хуралдай, as plain JSON. Only what the game shows to every other player is here: never an address, a location, a
 * balance, credits, a session or anything of the staff. Pure; the plugin fills the records from a main-thread snapshot.
 */
public final class Armory {

    /** Keys that must never appear in any view (ArmoryTest checks the views against them). */
    public static final List<String> NEVER = List.of("ip", "address", "host", "coins", "currency", "credits", "balance",
            "password", "token", "secret", "email", "x", "y", "z", "world", "location", "session");

    private Armory() {
    }

    /** One worn or held piece: its slot, its shown name, rarity and item level. */
    public record Gear(String slot, String name, String rarity, int level) {

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slot", slot);
            m.put("name", name);
            m.put("rarity", rarity);
            m.put("level", level);
            return m;
        }
    }

    /**
     * A player's public sheet. {@code clanTag}/{@code clanName} are null without a clan; {@code ascensionTitle} is the
     * rank's title (null below rank I). Mastery and craft maps hold levels only.
     */
    public record PlayerSheet(String uuid, String name, boolean online, String playerClass, int level, int ascension,
                              String ascensionTitle, String clanTag, String clanName, Map<String, Integer> weaponMastery,
                              Map<String, Integer> crafts, List<Gear> gear, long updatedAt) {

        public PlayerSheet {
            weaponMastery = Map.copyOf(weaponMastery);
            crafts = Map.copyOf(crafts);
            gear = List.copyOf(gear);
        }

        public Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", uuid);
            m.put("name", name);
            m.put("online", online);
            m.put("class", playerClass);
            m.put("level", level);
            Map<String, Object> asc = new LinkedHashMap<>();
            asc.put("rank", ascension);
            asc.put("title", ascensionTitle);
            m.put("ascension", asc);
            if (clanTag != null) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("tag", clanTag);
                c.put("name", clanName);
                m.put("clan", c);
            } else {
                m.put("clan", null);
            }
            m.put("weaponMastery", new java.util.TreeMap<>(weaponMastery));
            m.put("crafts", new java.util.TreeMap<>(crafts));
            List<Object> g = new ArrayList<>();
            for (Gear x : gear) g.add(x.json());
            m.put("gear", g);
            m.put("updatedAt", updatedAt);
            return m;
        }
    }

    /** A line of the clan board. */
    public record ClanLine(int rank, String tag, String name, int level, long exp, int members) {

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank);
            m.put("tag", tag);
            m.put("name", name);
            m.put("level", level);
            m.put("exp", exp);
            m.put("members", members);
            return m;
        }
    }

    /** The clan board: the top clans by EXP. */
    public static Map<String, Object> clans(List<ClanLine> lines, long updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> l = new ArrayList<>();
        for (ClanLine c : lines) l.add(c.json());
        m.put("clans", l);
        m.put("updatedAt", updatedAt);
        return m;
    }

    /** One campaign on the week's ballot, with its votes so far. */
    public record Option(String id, String name, int votes) {
    }

    /** The week's campaign: its progress toward its target, and its state. */
    public record Campaign(String id, String name, long total, long target, String state) {
    }

    /**
     * The week's Хуралдай: the cycle, its phase, the ballot with its tallies (empty outside the vote), the running
     * campaign (null when none) and the world's marks that campaigns left (flags of the world state).
     */
    public static Map<String, Object> khuraldai(String cycle, String phase, List<Option> ballot, Campaign campaign,
                                                List<String> worldMarks, long updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cycle", cycle);
        m.put("phase", phase);
        List<Object> b = new ArrayList<>();
        for (Option o : ballot) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", o.id());
            x.put("name", o.name());
            x.put("votes", o.votes());
            b.add(x);
        }
        m.put("ballot", b);
        if (campaign == null) {
            m.put("campaign", null);
        } else {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", campaign.id());
            c.put("name", campaign.name());
            c.put("total", campaign.total());
            c.put("target", campaign.target());
            c.put("state", campaign.state());
            m.put("campaign", c);
        }
        m.put("worldMarks", List.copyOf(worldMarks));
        m.put("updatedAt", updatedAt);
        return m;
    }

    /** The error body every endpoint answers with: {"error": "...", "status": n}. */
    public static String error(int status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        m.put("status", status);
        return Json.write(m);
    }

    /** Whether a path segment is a UUID in its canonical form (the only player key the armory takes). */
    public static boolean uuid(String s) {
        return s != null && s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
}
