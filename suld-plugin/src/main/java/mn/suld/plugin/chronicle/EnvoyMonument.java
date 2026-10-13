package mn.suld.plugin.chronicle;

import mn.suld.api.chronicle.ChronicleEntry;
import mn.suld.api.chronicle.ChronicleKind;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.worldbuild.WorldBuildService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Kharkhorum's monument to «Мөнх Тэнгэрийн Элч» (Тэнгэрийн Зэрэг X; the shrine promises it): the names of every player
 * who reached rank X, the first at the top, on a plaque before the Khan's monument in the middle of the plaza, read from
 * the Chronicle (ChronicleKind.HEAVENLY_ENVOY). A text display, not saved with the chunk: placed again whenever its
 * chunk is loaded and it is missing, and redrawn when a name is added. Until the first envoy it says that the first
 * name will stand there.
 */
public final class EnvoyMonument {

    static final String TAG = "suld_envoy_monument";
    /** Before the pedestal (it reaches 10 blocks south of the city's middle), toward the spawn. */
    static final int SOUTH = 13;
    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A"), CREAM = TextColor.fromHexString("#F4E8C8");

    private final Plugin plugin;
    private final SuldServices services;
    private final WorldBuildService city;
    private volatile List<String> names = List.of();
    private UUID plaque;

    public EnvoyMonument(Plugin plugin, SuldServices services, WorldBuildService city) {
        this.plugin = plugin;
        this.services = services;
        this.city = city;
    }

    public void start() {
        refresh();
        Bukkit.getScheduler().runTaskTimer(plugin, this::keep, 100L, 100L);
    }

    /** Reads the envoys from the Chronicle again and redraws the plaque (main thread). */
    public void refresh() {
        ChronicleService c = services.chronicle();
        if (c == null) return;
        c.byKind(ChronicleKind.HEAVENLY_ENVOY, 24).whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().warning("[monument] the envoys could not be read: " + err);
                return;
            }
            List<ChronicleEntry> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparingLong(ChronicleEntry::at));
            List<String> n = new ArrayList<>();
            for (ChronicleEntry e : sorted) n.add(e.playerName());
            names = List.copyOf(n);
            redraw();
        }));
    }

    /** The names on the plaque now, the first envoy first. */
    public List<String> names() {
        return names;
    }

    private Location at() {
        World w = Bukkit.getWorlds().get(0);
        int[] o = city.origin(w.getName()).orElse(null);
        if (o == null) return null;
        int x = o[0], z = o[1] + SOUTH;
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        return new Location(w, x + 0.5, y + 1.8, z + 0.5);
    }

    private void keep() {
        Entity e = plaque == null ? null : Bukkit.getEntity(plaque);
        if (e == null || !e.isValid()) redraw();
    }

    private void redraw() {
        Location l = at();
        if (l == null) return;
        Entity old = plaque == null ? null : Bukkit.getEntity(plaque);
        if (old instanceof TextDisplay t && t.isValid()) {
            t.text(text());
            return;
        }
        for (Entity e : l.getWorld().getNearbyEntities(l, 3, 4, 3)) if (e.getScoreboardTags().contains(TAG)) e.remove();
        TextDisplay t = l.getWorld().spawn(l, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x99000000));
            d.setLineWidth(240);
            d.text(text());
        });
        plaque = t.getUniqueId();
    }

    private Component text() {
        Component c = Component.text("✧ МӨНХ ТЭНГЭРИЙН ЭЛЧ ✧", GOLD, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("Тэнгэрийн Зэрэг X-д хүрсэн баатрууд", NamedTextColor.GRAY));
        if (names.isEmpty()) {
            return c.append(Component.newline()).append(Component.text("Анхны баатрын нэр энд бичигдэнэ", CREAM, TextDecoration.ITALIC));
        }
        for (String n : names) c = c.append(Component.newline()).append(Component.text(n, NamedTextColor.YELLOW, TextDecoration.BOLD));
        return c;
    }
}
