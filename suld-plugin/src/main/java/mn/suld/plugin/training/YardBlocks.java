package mn.suld.plugin.training;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.plugin.SuldServices;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The yard's few blocks, placed only when staff ask ({@code /dasgal yard build}, docs/TRAINING_GROUNDS.md): Хархорум is
 * built by hand, so the plugin never changes it on its own. Only open ground is touched (air, grass, flowers, dirt): any
 * block someone built stays, and is reported. Every changed block is written to an undo log first
 * ({@code plugins/SULD/training-yard-undo.txt}); {@code /dasgal yard undo} puts every one back and removes the log.
 * What is built: the big Warden's octagonal pedestal with four lights, a pad in the class colour under each station of
 * the arc on its north side, the walking Warden's track, and the 10 m and 15 m marks south of the Warden.
 */
final class YardBlocks {

    private static final Set<Material> OPEN = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.SHORT_GRASS, Material.TALL_GRASS,
            Material.FERN, Material.LARGE_FERN, Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.DANDELION, Material.POPPY,
            Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.ALLIUM, Material.RED_TULIP, Material.WHITE_TULIP,
            Material.PINK_TULIP, Material.ORANGE_TULIP, Material.LILY_OF_THE_VALLEY, Material.DIRT_PATH, Material.MOSS_CARPET);

    private static final Map<PlayerClass, Material> PAD = Map.of(
            PlayerClass.BAATAR, Material.RED_CONCRETE,
            PlayerClass.MERGEN, Material.LIME_CONCRETE,
            PlayerClass.BOO, Material.PURPLE_CONCRETE,
            PlayerClass.DARKHAN, Material.ORANGE_CONCRETE,
            PlayerClass.KHULEGCHIN, Material.LIGHT_BLUE_CONCRETE);

    private YardBlocks() {
    }

    static String run(Plugin plugin, SuldServices services, TrainingYard yard, CommandSender sender, String sub) {
        Location c = yard.centreOrNull();
        if (c == null) return "Талбай байрлаагүй: эхлээд /dasgal yard set.";
        Path log = plugin.getDataFolder().toPath().resolve("training-yard-undo.txt");
        String actor = sender instanceof Player p ? p.getUniqueId().toString() : "console";
        try {
            if (sub.equals("undo")) {
                if (!Files.exists(log)) return "Буцаах бүртгэл алга: талбайн блок тавигдаагүй.";
                int n = 0;
                for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
                    String[] f = line.split(";", 5);
                    if (f.length != 5) continue;
                    World w = Bukkit.getWorld(f[0]);
                    if (w == null) continue;
                    w.getBlockAt(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])).setBlockData(Bukkit.createBlockData(f[4]), false);
                    n++;
                }
                Files.delete(log);
                yard.start(c);
                services.audit().record(AuditEvent.of(actor, "training.yard.undo", c.getWorld().getName(), "blocks=" + n));
                return "Талбайн " + n + " блок буцаагдлаа.";
            }
            if (Files.exists(log)) return "Талбайн блок аль хэдийн тавигдсан: эхлээд /dasgal yard undo.";
            Map<Block, Material> plan = plan(c);
            List<String> undo = new ArrayList<>();
            int skipped = 0;
            for (Map.Entry<Block, Material> e : plan.entrySet()) {
                Block b = e.getKey();
                if (!OPEN.contains(b.getType())) {
                    skipped++;
                    continue;
                }
                undo.add(b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ() + ";" + b.getBlockData().getAsString());
            }
            Files.createDirectories(log.getParent());
            Files.write(log, undo, StandardCharsets.UTF_8); // the log is on disk before the first block changes
            int placed = 0;
            for (Map.Entry<Block, Material> e : plan.entrySet()) {
                if (!OPEN.contains(e.getKey().getType())) continue;
                e.getKey().setType(e.getValue(), false);
                placed++;
            }
            yard.start(c); // the Wardens stand on the pedestal now
            services.audit().record(AuditEvent.of(actor, "training.yard.build", c.getWorld().getName(), "blocks=" + placed + " kept=" + skipped));
            return "Талбай: " + placed + " блок тавигдлаа" + (skipped > 0 ? ", барьсан " + skipped + " блокт хүрсэнгүй" : "") + ". Буцаах: /dasgal yard undo";
        } catch (IOException | RuntimeException ex) {
            return "Талбайн блок: алдаа — " + ex.getMessage();
        }
    }

    /** Every block of the yard and what it becomes (the ground level is the centre's y − 1). */
    private static Map<Block, Material> plan(Location c) {
        World w = c.getWorld();
        int cx = c.getBlockX(), cz = c.getBlockZ(), gy = c.getBlockY() - 1;
        Map<Block, Material> out = new LinkedHashMap<>();
        // the pedestal: an octagon of radius 3, one block high, deepslate tiles on polished deepslate, a light on each corner
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) + Math.abs(z) > 4) continue;
                boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3 || Math.abs(x) + Math.abs(z) == 4;
                out.put(w.getBlockAt(cx + x, gy, cz + z), edge ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE);
                out.put(w.getBlockAt(cx + x, gy + 1, cz + z), edge ? Material.DEEPSLATE_TILE_SLAB : Material.POLISHED_DEEPSLATE);
            }
        }
        for (int[] d : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) out.put(w.getBlockAt(cx + d[0], gy + 1, cz + d[1]), Material.SEA_LANTERN);
        // the class stations: a 3×3 pad in the class colour under each one of the arc on the north side
        PlayerClass[] classes = PlayerClass.values();
        for (int i = 0; i < classes.length; i++) {
            double a = Math.toRadians(-90 + 180.0 * i / (classes.length - 1));
            int sx = cx + (int) Math.floor(0.5 + Math.sin(a) * TrainingYard.STATION_RADIUS);
            int sz = cz + (int) Math.floor(0.5 - Math.cos(a) * TrainingYard.STATION_RADIUS);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    out.put(w.getBlockAt(sx + x, gy, sz + z), x == 0 && z == 0 ? Material.POLISHED_BLACKSTONE : PAD.get(classes[i]));
                }
            }
        }
        // the walking Warden's track: a ring of polished andesite flush with the ground, one block wide, on its circle
        int r = (int) Math.ceil(TrainingYard.WALK_RADIUS) + 1;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.hypot(x, z);
                if (Math.abs(d - TrainingYard.WALK_RADIUS) <= 0.5) out.putIfAbsent(w.getBlockAt(cx + x, gy, cz + z), Material.POLISHED_ANDESITE);
            }
        }
        // Мэргэн's range marks: 10 m and 15 m south of the Warden, five blocks wide
        for (int m : new int[]{10, 15}) {
            for (int x = -2; x <= 2; x++) out.put(w.getBlockAt(cx + x, gy, cz + m), Material.WHITE_CONCRETE);
        }
        return out;
    }
}
