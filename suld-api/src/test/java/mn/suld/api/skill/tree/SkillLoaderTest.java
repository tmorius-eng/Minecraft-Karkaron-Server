package mn.suld.api.skill.tree;

import mn.suld.api.clazz.PlayerClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLoaderTest {

    private static final SkillTreeLoader.Source BUNDLED = SkillTreeLoader.classpath();

    private static String read(String name) {
        try {
            return BUNDLED.read(name);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The bundled files with one file replaced by an edited version. */
    private static SkillTreeLoader.Result with(String file, java.util.function.UnaryOperator<String> edit) {
        Map<String, String> files = new HashMap<>();
        for (String n : new String[]{"baatar.json", "mergen.json", "boo.json", "darkhan.json", "khulegchin.json", "universal.json"}) files.put(n, read(n));
        files.put(file, edit.apply(files.get(file)));
        return SkillTreeLoader.loadAll(name -> {
            String t = files.get(name);
            if (t == null) throw new NoSuchFileException(name);
            return t;
        });
    }

    private static boolean mentions(SkillTreeLoader.Result r, String file, String path) {
        return r.issues().stream().anyMatch(i -> i.file().equals(file) && i.path().contains(path));
    }

    @Test
    void validDataHasNoIssues() {
        assertTrue(with("baatar.json", s -> s).ok());
    }

    @Test
    void reportsTheExactFileAndField_forAnUnknownEnumValue() {
        var r = with("boo.json", s -> s.replace("\"key\": \"HEALTH\"", "\"key\": \"HEALT\""));
        assertFalse(r.ok());
        assertTrue(mentions(r, "boo.json", "effects[0].key"), r.issues().toString());
        assertTrue(r.issues().get(0).message().contains("HEALTH"), "lists the valid values");
        assertFalse(r.trees().containsKey(PlayerClass.BOO), "a broken class gets no tree");
        assertNotNull(r.trees().get(PlayerClass.BAATAR), "the other classes still load");
    }

    @Test
    void reportsMissingFields() {
        var r = with("darkhan.json", s -> s.replace("\"icon\": \"FURNACE\", ", ""));
        assertTrue(mentions(r, "darkhan.json", ".icon"), r.issues().toString());
    }

    @Test
    void reportsBadNumbers() {
        var r = with("mergen.json", s -> s.replaceFirst("\"cost\": 1", "\"cost\": 0"));
        assertTrue(mentions(r, "mergen.json", ".cost"), r.issues().toString());
        var r2 = with("mergen.json", s -> s.replaceFirst("\"maxRank\": 3", "\"maxRank\": 9"));
        assertTrue(mentions(r2, "mergen.json", ".maxRank"));
        var r3 = with("mergen.json", s -> s.replaceFirst("\"chance\": 20", "\"chance\": 250"));
        assertTrue(mentions(r3, "mergen.json", ".chance"));
    }

    @Test
    void reportsASpellOfAnotherClassAndUnsupportedModifiers() {
        var r = with("baatar.json", s -> s.replace("TENGER_TSAVCHILT", "CHONYN_NUD"));
        assertTrue(r.issues().stream().anyMatch(i -> i.message().contains("belongs to mergen")), r.issues().toString());
        var r2 = with("darkhan.json", s -> s.replace("\"spell\": \"GAN_BAMBAI\", \"key\": \"SHIELD\"", "\"spell\": \"GAN_BAMBAI\", \"key\": \"BURN\""));
        assertTrue(r2.issues().stream().anyMatch(i -> i.message().contains("does not support BURN")), r2.issues().toString());
    }

    @Test
    void reportsDuplicateIdsAndBrokenGraphs() {
        var dup = with("baatar.json", s -> s.replace("\"id\": \"m1\"", "\"id\": \"l1\""));
        assertTrue(dup.issues().stream().anyMatch(i -> i.message().contains("duplicate id")), dup.issues().toString());
        var edge = with("boo.json", s -> s.replace("[\"root\", \"l1\"]", "[\"root\", \"nowhere\"]"));
        assertTrue(edge.issues().stream().anyMatch(i -> i.path().equals("tree") && i.message().contains("nowhere")), edge.issues().toString());
        var far = with("boo.json", s -> s.replace("[\"root\", \"l1\"]", "[\"root\", \"u3\"]"));
        assertTrue(far.issues().stream().anyMatch(i -> i.message().contains("not between neighbours")), far.issues().toString());
        var island = with("boo.json", s -> s.replace("[\"l7\", \"u1\"]", "[\"l7\", \"l6\"]").replace("[\"l7\", \"u2\"], ", ""));
        assertFalse(island.ok());
    }

    @Test
    void reportsBadRequirementsAndKeystoneMismatch() {
        var req = with("baatar.json", s -> s.replace("\"node\": \"l6\"", "\"node\": \"zzz\""));
        assertTrue(req.issues().stream().anyMatch(i -> i.message().contains("unknown node zzz")), req.issues().toString());
        var ks = with("baatar.json", s -> s.replace(", \"keystone\": true", ""));
        assertTrue(mentions(ks, "baatar.json", ".keystone"), ks.issues().toString());
    }

    @Test
    void reportsBrokenJsonAndMissingFiles() {
        var bad = with("khulegchin.json", s -> s.substring(0, s.length() / 2));
        assertTrue(mentions(bad, "khulegchin.json", "$"), bad.issues().toString());
        var gone = SkillTreeLoader.loadAll(name -> {
            if (name.equals("boo.json")) throw new NoSuchFileException(name);
            return read(name);
        });
        assertTrue(mentions(gone, "boo.json", "file"));
        assertEquals(4, gone.trees().size());
    }

    @Test
    void universalProblemsBlockEveryClass() {
        var r = with("universal.json", s -> s.replace("\"EXP_PCT\"", "\"XP_PCTT\""));
        assertTrue(mentions(r, "universal.json", "effects[0].key"));
        assertEquals(0, r.trees().size());
    }

    @Test
    void rankedNodesCannotCarryProcs() {
        var r = with("baatar.json", s -> s.replace("{\"id\": \"r3\", \"name\": \"Уур Хилэн\", \"category\": \"OFFENSE\", \"icon\": \"FIRE_CHARGE\", \"x\": 3, \"y\": 3, \"cost\": 2,",
                "{\"id\": \"r3\", \"name\": \"Уур Хилэн\", \"category\": \"OFFENSE\", \"icon\": \"FIRE_CHARGE\", \"x\": 3, \"y\": 3, \"cost\": 2, \"maxRank\": 2,"));
        assertTrue(r.issues().stream().anyMatch(i -> i.message().contains("several ranks")), r.issues().toString());
    }
}
