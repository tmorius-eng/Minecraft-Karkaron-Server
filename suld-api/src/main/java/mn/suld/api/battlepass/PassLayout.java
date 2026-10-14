package mn.suld.api.battlepass;

import mn.suld.api.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The pass's rewards ({@code /achievements/pass.json} in the jar): for each track, tier → cosmetic id. The same layout
 * serves every season until a season has its own. The plugin checks that every cosmetic exists.
 */
public final class PassLayout {

    public static final String RESOURCE = "/achievements/pass.json";

    private PassLayout() {
    }

    /** The bundled layout; a broken file throws (the build's tests read it). */
    public static CampaignPass.Layout bundled() {
        try (InputStream in = PassLayout.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " missing from the jar");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + ": " + e.getMessage(), e);
        }
    }

    public static CampaignPass.Layout parse(String text) {
        Map<String, Object> doc = Json.object(Json.parse(text));
        List<CampaignPass.Reward> out = new ArrayList<>();
        for (CampaignPass.Track t : CampaignPass.Track.values()) {
            Object track = doc.get(t.name().toLowerCase(java.util.Locale.ROOT));
            if (!(track instanceof Map<?, ?> m)) throw new IllegalArgumentException(RESOURCE + ": no " + t.name().toLowerCase(java.util.Locale.ROOT) + " track");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                int tier;
                try {
                    tier = Integer.parseInt(String.valueOf(e.getKey()));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(RESOURCE + ": tier " + e.getKey() + " is not a number");
                }
                out.add(new CampaignPass.Reward(tier, t, String.valueOf(e.getValue())));
            }
        }
        return new CampaignPass.Layout(out);
    }
}
