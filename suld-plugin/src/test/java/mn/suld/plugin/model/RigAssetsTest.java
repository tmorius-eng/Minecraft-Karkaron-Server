package mn.suld.plugin.model;

import mn.suld.api.json.Json;
import mn.suld.api.model.Rig;
import mn.suld.api.model.RigLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every shipped rig loads, fits its budget, and has every bone model, item definition and texture in the pack. */
class RigAssetsTest {

    static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();
    static final Path RES = ROOT.resolve("suld-plugin/src/main/resources/models");
    static final Path PACK = ROOT.resolve("resourcepack/assets/suld");

    @Test
    void everyRigLoadsAndItsAssetsExist() throws Exception {
        List<Object> ids = Json.array(Json.object(Json.parse(Files.readString(RES.resolve("index.json")))).get("models"));
        assertTrue(ids.contains("khasar"));
        for (Object o : ids) {
            String id = String.valueOf(o);
            RigLoader.Result r = RigLoader.load(Files.readString(RES.resolve(id + "/rig.json")), Files.readString(RES.resolve(id + "/clips.json")));
            assertTrue(r.ok(), id + ": " + r.issues());
            Rig rig = r.model().rig();
            assertTrue(rig.displayCount() <= 30, id + " displays " + rig.displayCount() + " (ASSET_BUDGET: boss ≤ 30)");
            int cuboids = 0;
            for (Rig.Bone b : rig.bones()) {
                if (!b.model()) continue;
                Path model = PACK.resolve("models/entity/" + id + "/" + b.id() + ".json");
                Path item = PACK.resolve("items/entity/" + id + "/" + b.id() + ".json");
                assertTrue(Files.exists(model), model.toString());
                assertTrue(Files.exists(item), item.toString());
                Map<String, Object> m = Json.object(Json.parse(Files.readString(model)));
                for (Object el : Json.array(m.get("elements"))) {
                    cuboids++;
                    Map<String, Object> e = Json.object(el);
                    for (String k : List.of("from", "to")) for (Object v : Json.array(e.get(k))) {
                        double d = ((Number) v).doubleValue();
                        assertTrue(d >= -16 && d <= 32, id + "/" + b.id() + " element outside −16..32: " + d);
                    }
                }
                for (Object tex : Json.object(m.getOrDefault("textures", Map.of())).values()) {
                    String t = String.valueOf(tex).replace("suld:", "");
                    assertTrue(Files.exists(PACK.resolve("textures/" + t + ".png")), id + "/" + b.id() + " texture " + t);
                }
                String itemJson = Files.readString(item);
                assertTrue(itemJson.contains("custom_model_data"), id + "/" + b.id() + " has no hit-flash tint");
            }
            assertTrue(cuboids <= 260, id + " cuboids " + cuboids + " (ASSET_BUDGET: ≤ 260)");
            for (String clip : List.of("idle", "walk", "death")) assertTrue(r.model().clips().containsKey(clip), id + " clip " + clip);
        }
        // Хасар's ability clips carry the events KhasarBrain listens for
        RigLoader.Model k = RigLoader.load(Files.readString(RES.resolve("khasar/rig.json")), Files.readString(RES.resolve("khasar/clips.json"))).model();
        for (String[] ce : new String[][] {{"bite", "bite_hit"}, {"pounce", "pounce_land"}, {"roar", "roar_wave"}, {"howl", "howl"}}) {
            assertEquals(1, k.clip(ce[0]).events().stream().filter(e -> e.name().equals(ce[1])).count(), ce[0]);
        }
        assertTrue(Files.exists(PACK.resolve("models/entity/khasar/head_rage.json")), "phase look");
    }
}
