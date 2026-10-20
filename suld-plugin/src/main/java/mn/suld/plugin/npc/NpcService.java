package mn.suld.plugin.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import mn.suld.api.item.ClassWeaponRule;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.worldbuild.WorldPoint;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.gui.Menu;
import mn.suld.plugin.gui.Menus;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.worldbuild.WorldBuildService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kharkhorum's NPCs: Mannequins (player-shaped, 1.21.9+) wearing Mongolian skins from the resource pack, with a
 * name and a role line, a glowing outline in their role colour, and their head turned to the nearest player.
 * They stand on the city plan's points and are not saved with the world: spawned when their chunk loads,
 * removed with it. Right-click: Бөө → classes, Хөтөч → tutorial, Анчдын ахлагч → the first hunt, Худалдаачин →
 * shop, Дархан → repair, Өртөөчин → fast travel between the gates, Тэнгэрийн тахилч → a blessing.
 */
public final class NpcService implements Listener {

    public record Role(String skin, String name, String line, NamedTextColor glow) {
    }

    /** The person at a city point (content/npcs.json), or null for a point nobody serves. */
    private static Role roleFor(String pointId) {
        mn.suld.api.npc.NpcDefinition d = mn.suld.plugin.content.SuldContent.npcFor(pointId);
        return d == null ? null : roleOf(d);
    }

    private static Role roleOf(mn.suld.api.npc.NpcDefinition d) {
        NamedTextColor glow = NamedTextColor.NAMES.value(d.glow().toLowerCase(java.util.Locale.ROOT));
        return new Role(d.skin(), d.name(), d.line(), glow == null ? NamedTextColor.WHITE : glow);
    }

    /** The entry behind an NPC the player clicked: a city point, or a campaign's dynamic NPC by its role. */
    private mn.suld.api.npc.NpcDefinition defFor(String npcId) {
        Dynamic dyn = dynamics.get(npcId);
        return mn.suld.plugin.content.SuldContent.npcFor(dyn != null ? dyn.roleId() : npcId);
    }

    /** Where an NPC stands (its entity, else its point), or null before the city is built; the side quests measure from it. */
    public Location where(String npcId) {
        UUID id = spawned.get(npcId);
        Entity e = id == null ? null : Bukkit.getEntity(id);
        if (e != null && e.isValid()) return e.getLocation();
        Dynamic dyn = dynamics.get(npcId);
        return dyn != null ? dyn.at() : city.pointLocation(npcId);
    }

    private static final Map<String, String> RELAY_NAMES = new LinkedHashMap<>();

    static {
        RELAY_NAMES.put("fast_travel.plaza", "Төв талбай");
        RELAY_NAMES.put("fast_travel.gate_south", "Өмнөд хаалга");
        RELAY_NAMES.put("fast_travel.gate_west", "Баруун хаалга");
        RELAY_NAMES.put("fast_travel.gate_east", "Зүүн хаалга");
        RELAY_NAMES.put("fast_travel.gate_north", "Хойд хаалга");
    }

    public static final long TRAVEL_COST = 10;
    private static final long BLESSING_COST = 30;

    /** The relay points (id → name), in the order the travel page shows them. */
    public static Map<String, String> relays() {
        return java.util.Collections.unmodifiableMap(RELAY_NAMES);
    }

    /** How close to a relay post a player must stand to travel from it (blocks). */
    public static final double RELAY_RANGE = 8;

    /** The relay the player stands at (within {@link #RELAY_RANGE}), or null: trips start only at a relay. */
    public String relayHere(Player p) {
        String best = null;
        double bestD = RELAY_RANGE * RELAY_RANGE;
        for (String key : RELAY_NAMES.keySet()) {
            Location at = city.pointLocation(key);
            if (at == null || at.getWorld() != p.getWorld()) continue;
            double d = at.clone().add(1.5, 0, 1.5).distanceSquared(p.getLocation());
            if (d < bestD) {
                bestD = d;
                best = key;
            }
        }
        return best;
    }

    /** A paid trip to the relay {@code key}: the coins go first and come back when the teleport fails. */
    public void travelTo(Player pl, String key) {
        if (services.isSoul.test(pl.getUniqueId())) {
            pl.sendMessage(Messages.error("Сүнс байхдаа өртөөгөөр аялах боломжгүй."));
            return;
        }
        Location to = city.pointLocation(key);
        String name = RELAY_NAMES.get(key);
        if (to == null || name == null) return;
        PlayerProfile pr = services.profiles().cached(pl.getUniqueId()).orElse(null);
        if (pr == null) return;
        if (pr.currency() < TRAVEL_COST) {
            pl.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + TRAVEL_COST + " зоос)."));
            return;
        }
        if (!travelling.add(pl.getUniqueId())) return; // a second click while the first trip loads
        pr.addCurrency(-TRAVEL_COST); // paid up front, refunded if the trip fails
        mn.suld.plugin.perf.SafeTeleport.to(plugin, pl, to.clone().add(1.5, 0, 1.5),
                org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN, ok -> {
                    travelling.remove(pl.getUniqueId());
                    if (!ok) {
                        pr.addCurrency(TRAVEL_COST);
                        services.profiles().save(pr);
                        pl.sendMessage(Messages.error("Аялал боломжгүй боллоо — зоос буцаагдлаа."));
                        return;
                    }
                    services.profiles().save(pr);
                    pl.playSound(pl.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 1f);
                    pl.sendMessage(Messages.success("Өртөөгөөр " + name + " хүрлээ (-" + TRAVEL_COST + " зоос)."));
                });
    }
    private static final long BLESSING_COOLDOWN_MS = 10 * 60_000L;

    private final Plugin plugin;
    /** Players whose relay trip is loading (teleportAsync), so a double click cannot pay or travel twice. */
    private final java.util.Set<java.util.UUID> travelling = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final SuldServices services;
    private final WorldBuildService city;
    private final Menus menus;
    private final NamespacedKey key;
    private final Map<String, UUID> spawned = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blessed = new ConcurrentHashMap<>();
    private final mn.suld.plugin.gui.SmithMenu smith;

    public NpcService(Plugin plugin, SuldServices services, WorldBuildService city, Menus menus) {
        this.dialogue = new NpcDialogue(plugin);
        this.plugin = plugin;
        this.services = services;
        this.city = city;
        this.menus = menus;
        this.key = new NamespacedKey(plugin, "npc");
        this.smith = new mn.suld.plugin.gui.SmithMenu(plugin, services);
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::ensureAll, 60L, 200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::lookAtPlayers, 40L, 5L);
    }

    /** Entity UUIDs and glow colours, for the per-player scoreboard teams that colour the glow. */
    public Map<String, NamedTextColor> glowEntries() {
        Map<String, NamedTextColor> out = new HashMap<>();
        for (Map.Entry<String, UUID> e : spawned.entrySet()) {
            Dynamic d = dynamics.get(e.getKey());
            WorldPoint p = d == null ? point(e.getKey()) : null;
            Role r = d != null ? d.role() : p == null ? null : roleFor(p.id());
            if (r != null) out.put(e.getValue().toString(), r.glow());
        }
        return out;
    }

    private WorldPoint point(String id) {
        for (WorldPoint p : city.slicePoints()) if (p.id().equals(id)) return p;
        return null;
    }

    // ------------------------------------------------------------- dynamic NPCs (campaign world effects)

    /** A dynamic NPC: a role at a fixed spot, under a world-state flag key (docs/KHURALDAI.md). */
    private record Dynamic(String key, String roleId, Role role, Location at, float yaw) {
    }

    private final Map<String, Dynamic> dynamics = new java.util.LinkedHashMap<>();

    /** The roles a campaign may bring to the city, by role id (content/npcs.json; the loader checks every campaign names one). */
    private static Role dynamicRole(String roleId) {
        mn.suld.api.npc.NpcDefinition d = mn.suld.plugin.content.SuldContent.npcFor(roleId);
        return d == null ? null : roleOf(d);
    }

    /**
     * Put the NPC {@code roleId} at {@code anchor}: a city point id, or {@code "city:x,z"} (an offset from the city
     * origin), shifted by {@code dx}/{@code dz}. Idempotent per key.
     */
    public void addDynamic(String key, String roleId, String anchor, int dx, int dz, float yaw) {
        Role role = dynamicRole(roleId);
        if (role == null) {
            plugin.getLogger().warning("[npc] unknown dynamic role " + roleId + " for " + key);
            return;
        }
        if (dynamics.containsKey(key)) return;
        Location at = null;
        if (anchor.startsWith("city:")) {
            String[] xz = anchor.substring(5).split(",");
            World w = Bukkit.getWorlds().get(0);
            int[] o = services.city().origin(w.getName()).orElse(null);
            if (o != null && xz.length == 2) {
                try {
                    int x = o[0] + Integer.parseInt(xz[0].trim()) + dx, z = o[1] + Integer.parseInt(xz[1].trim()) + dz;
                    at = new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
                } catch (NumberFormatException ignored) {
                    at = null;
                }
            }
        } else {
            Location p = city.pointLocation(anchor);
            if (p != null) at = p.clone().add(dx, 0, dz);
        }
        if (at == null) {
            plugin.getLogger().warning("[npc] anchor " + anchor + " of " + key + " cannot be placed yet (city not built?)");
            return;
        }
        dynamics.put(key, new Dynamic(key, roleId, role, at, yaw));
        plugin.getLogger().info("[npc] dynamic " + roleId + " at " + at.getBlockX() + " " + at.getBlockZ() + " for " + key);
        ensureAll();
    }

    public void removeDynamic(String key) {
        Dynamic d = dynamics.remove(key);
        if (d == null) return;
        UUID id = spawned.remove(key);
        Entity e = id == null ? null : Bukkit.getEntity(id);
        if (e != null) e.remove();
        services.hud().refreshGlow();
    }

    private void ensureDynamics() {
        for (Dynamic d : dynamics.values()) {
            UUID id = spawned.get(d.key());
            Entity e = id == null ? null : Bukkit.getEntity(id);
            if (e != null && e.isValid()) continue;
            if (!d.at().isChunkLoaded()) continue;
            spawn(new WorldPoint(d.key(), "npc", "dynamic", d.at().getBlockX(), d.at().getBlockY(), d.at().getBlockZ(), d.yaw(), false, "dynamic"), d.role(), d.at());
        }
    }

    private void ensureAll() {
        ensureDynamics();
        if (!city.isBuilt()) return;
        for (WorldPoint p : city.slicePoints()) {
            Role r = roleFor(p.id());
            if (r == null) continue;
            UUID id = spawned.get(p.id());
            Entity e = id == null ? null : Bukkit.getEntity(id);
            if (e != null && e.isValid()) continue;
            Location at = city.pointLocation(p.id());
            if (at == null || !at.isChunkLoaded()) continue;
            spawn(p, r, at);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        // only a chunk that holds an NPC point matters (exploring or pre-generating loads hundreds a second), and
        // several such loads in one tick share one ensureAll
        if (ensurePending || !city.isBuilt() || !hasNpcPoint(e.getChunk())) return;
        ensurePending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ensurePending = false;
            ensureAll();
        });
    }

    private boolean ensurePending;

    private boolean hasNpcPoint(org.bukkit.Chunk c) {
        for (WorldPoint p : city.slicePoints()) {
            if (roleFor(p.id()) == null) continue;
            Location at = city.pointLocation(p.id());
            if (at != null && at.getWorld() == c.getWorld() && (at.getBlockX() >> 4) == c.getX() && (at.getBlockZ() >> 4) == c.getZ()) return true;
        }
        return false;
    }

    private void spawn(WorldPoint p, Role r, Location at) {
        // remove leftovers of this NPC (e.g. a reload) before spawning a fresh one
        for (Entity old : at.getWorld().getNearbyEntities(at, 2, 2, 2)) {
            if (p.id().equals(old.getPersistentDataContainer().get(key, PersistentDataType.STRING))) old.remove();
        }
        Location loc = at.clone();
        loc.setYaw(p.yaw());
        Mannequin m = at.getWorld().spawn(loc, Mannequin.class, mq -> {
            mq.setPersistent(false);
            mq.setInvulnerable(true);
            mq.setImmovable(true);
            mq.setGlowing(true);
            mq.setSilent(true);
            mq.customName(Component.text(r.name(), TextColor.fromHexString("#FFD24A"), TextDecoration.BOLD));
            mq.setCustomNameVisible(true);
            mq.setDescription(Component.text(r.line(), NamedTextColor.WHITE, TextDecoration.BOLD)
                    .append(Component.text("  ▶ дарж ярилц", NamedTextColor.GRAY)));
            mq.setProfile(ResolvableProfile.resolvableProfile()
                    .skinPatch(sp -> sp.body(Key.key("suld", "entity/npc/" + r.skin()))).build());
            mq.getPersistentDataContainer().set(key, PersistentDataType.STRING, p.id());
            mq.addScoreboardTag("city_npc");
        });
        spawned.put(p.id(), m.getUniqueId());
        services.hud().refreshGlow();
    }

    /** Turn each NPC's head to the nearest player within 8 blocks. */
    private void lookAtPlayers() {
        for (UUID id : spawned.values()) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Mannequin m) || !m.isValid()) continue;
            Player best = null;
            double bestD = 64;
            for (Player p : m.getWorld().getPlayers()) {
                double d = p.getLocation().distanceSquared(m.getLocation());
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            if (best == null) continue;
            Location from = m.getEyeLocation(), to = best.getEyeLocation();
            double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            m.setRotation(yaw, pitch);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(key, PersistentDataType.STRING)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        String id = e.getRightClicked().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        p.playSound(e.getRightClicked().getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.8f, 1.1f);
        e.getRightClicked().getWorld().spawnParticle(Particle.HAPPY_VILLAGER, e.getRightClicked().getLocation().add(0, 2.1, 0), 5, 0.3, 0.2, 0.3);
        // a conversation first (docs/NPC_DIALOGUE.md); Shift + right-click goes straight to the action
        mn.suld.api.npc.NpcDefinition def = defFor(id);
        if (!p.isSneaking() && plugin.getConfig().getBoolean("npc.dialogue", true) && dialogue.has(def)) {
            org.bukkit.Location npcAt = e.getRightClicked().getLocation();
            // the button can be pressed minutes later: only while still beside the NPC, alive (not a soul)
            dialogue.open(p, def, id, services.profiles().cached(p.getUniqueId()).orElse(null), actionLabel(def), () -> {
                if (p.isOnline() && p.getWorld().equals(npcAt.getWorld()) && p.getLocation().distanceSquared(npcAt) <= 8 * 8
                        && !services.isSoul.test(p.getUniqueId())) {
                    act(p, id);
                } else {
                    p.sendMessage(Messages.error("Ярилцагчаасаа хэт холдсон байна — дахин ойртоод ярь."));
                }
            });
        } else {
            act(p, id);
        }
    }

    private final NpcDialogue dialogue;

    private String actionLabel(mn.suld.api.npc.NpcDefinition def) {
        if (def == null) return "Үргэлжлүүлэх";
        return switch (def.action()) {
            case CLASS -> "Анги сонгох";
            case TUTORIAL -> "Заавар нээх";
            case QUESTS -> "Аяны зам";
            case SHOP -> "Дэлгүүр";
            case SMITH -> "Засвар · Хуяг";
            case TRAVEL -> "Өртөөгөөр явах";
            case SHRINE -> "Ивээл · Тэнгэрийн Зэрэг (60+)";
            case SIDE_QUESTS -> sideQuestOpener != null ? "Нэмэлт эрэл" : "Аяны зам"; // until the side-quest book is wired, it opens the story
            case KHURALDAI -> "Их Хуралдай";
            case NONE -> "Үргэлжлүүлэх";
        };
    }

    /** What a SIDE_QUESTS NPC opens (the side-quest service sets it); until then its quest book. */
    private java.util.function.BiConsumer<Player, String> sideQuestOpener;

    public void sideQuestOpener(java.util.function.BiConsumer<Player, String> opener) {
        this.sideQuestOpener = opener;
    }

    private void act(Player p, String id) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        mn.suld.api.npc.NpcDefinition def = defFor(id);
        if (def == null) return;
        mn.suld.api.npc.NpcAction action = def.action();
        if (action == mn.suld.api.npc.NpcAction.CLASS) {
            if (!pr.hasSelectedClass()) services.classSelectionGui().open(p);
            else menus.academy(p); // «Ангийн Академи»: look at every class, change here (docs/CLASS_CHANGE.md)
        } else if (action == mn.suld.api.npc.NpcAction.TUTORIAL) {
            menus.tutorial(p);
        } else if (action == mn.suld.api.npc.NpcAction.QUESTS || action == mn.suld.api.npc.NpcAction.SIDE_QUESTS) {
            if (!pr.hasSelectedClass()) {
                p.sendMessage(Messages.info(def.name() + ": «Эхлээд ангиа сонго — Бөө чамайг хүлээж байна.»"));
                return;
            }
            if (action == mn.suld.api.npc.NpcAction.SIDE_QUESTS && sideQuestOpener != null) {
                sideQuestOpener.accept(p, def.id());
                return;
            }
            services.quests().ensure(p, pr);
            menus.quests(p);
        } else if (action == mn.suld.api.npc.NpcAction.SHOP) {
            menus.shop(p, def.shopId(), 0);
        } else if (action == mn.suld.api.npc.NpcAction.SMITH) {
            repair(p);
        } else if (action == mn.suld.api.npc.NpcAction.TRAVEL) {
            menus.travel(p);        // the Аялал page of the hub; the player stands at this relay
        } else if (action == mn.suld.api.npc.NpcAction.KHURALDAI) {
            p.performCommand("khuraldai");
        } else if (action == mn.suld.api.npc.NpcAction.SHRINE) {
            // at the level cap the priest leads the Тэнгэрийн Зэрэг rite; below it, the blessing
            if ((pr.progression().level() >= mn.suld.api.balance.Balance.MAX_LEVEL || pr.ascension() > 0) && services.ascension != null) {
                if (p.isSneaking()) bless(p, pr);
                else services.ascension.open(p);
            } else {
                bless(p, pr);
            }
        }
    }

    // ------------------------------------------------------------------ blacksmith

    /** Only equippable, non-stacking gear levels up (a stack of materials at a higher level would sell for more). */
    private static boolean upgradableDef(mn.suld.api.item.ItemDefinition def) {
        return def != null && def.equippable() && !def.stackable();
    }

    /** The smith: repair worn gear (coins) and upgrade SÜLD items one level (coins + region materials). */
    private void repair(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        Menu m = new Menu(3, "Дархан · Засвар ба сайжруулалт", null);
        boolean damaged = !it.getType().isAir() && it.getItemMeta() instanceof Damageable d && d.hasDamage();
        mn.suld.api.item.ItemInstance inst = it.getType().isAir() ? null : services.items().read(it).orElse(null);
        boolean upgradable = inst != null && !ClassWeaponRule.isClassWeapon(inst.definitionId()) && !inst.definitionId().startsWith("armor.class.") && !services.relics().items().isRelic(it)
                && upgradableDef(mn.suld.plugin.content.SuldContent.definitionFor(inst.definitionId())) && it.getAmount() == 1;
        // the class armour: inspect, next tier, mastery, upgrade with confirmation (SmithMenu)
        m.set(22, Menu.item(Material.NETHERITE_CHESTPLATE, Menu.title("⚔ Ангийн хуяг", NamedTextColor.GOLD),
                List.of(Menu.line("Хуягаа үзэх, зэрэг ахиулах, сайжруулах"))), (pl, c) -> smith.open(pl, this::smithLocation));
        if (!damaged && !upgradable) {
            m.set(13, Menu.item(Material.ANVIL, Menu.title("Засах, сайжруулах зүйл алга", NamedTextColor.RED),
                    List.of(Menu.line("Гэмтсэн зэвсэг/хуяг эсвэл SÜLD зэвсгээ гартаа барь."),
                            Menu.line("Ангийн зэвсэг түвшинтэйгээ хамт өөрөө өсдөг."))), null);
            m.open(p);
            return;
        }
        m.set(11, it.clone(), null);
        if (damaged) {
            int damage = ((Damageable) it.getItemMeta()).getDamage();
            long cost = inst != null ? mn.suld.api.item.ItemEconomy.repairCost(inst, damage) : 5 + (long) Math.ceil(damage / 8.0);
            m.set(upgradable ? 14 : 15, Menu.item(Material.ANVIL, Menu.title("Засах · " + cost + " зоос", NamedTextColor.GREEN),
                    List.of(Menu.kv("Гэмтэл:", String.valueOf(damage), NamedTextColor.RED), Menu.line("Бүрэн шинэ болгоно."))), (pl, c) -> {
                PlayerProfile pr = services.profiles().cached(pl.getUniqueId()).orElse(null);
                ItemStack hand = pl.getInventory().getItemInMainHand();
                if (pr == null || !hand.isSimilar(it)) {
                    pl.closeInventory();
                    return;
                }
                if (pr.currency() < cost) {
                    pl.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + cost + " зоос)."));
                    return;
                }
                if (!(hand.getItemMeta() instanceof Damageable)) return;
                pl.closeInventory();
                // COINS -> ITEMS (PlayerDataSaves.coinsForItem): the charge is committed before the mended item is written
                pr.addCurrency(-cost);
                mn.suld.plugin.item.PlayerDataSaves.coinsForItem(plugin, services, pl, pr, cost, "smith repair", p2 -> {
                    ItemStack now = p2.getInventory().getItemInMainHand();
                    if (!now.isSimilar(it) || !(now.getItemMeta() instanceof Damageable nd)) {
                        refund(p2, pr, cost);
                        return;
                    }
                    nd.setDamage(0);
                    now.setItemMeta(nd);
                    p2.playSound(p2.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
                    p2.sendMessage(Messages.success("Дархан засаж өглөө (-" + cost + " зоос)."));
                    if (services.classArmor != null) services.classArmor.smithWork(p2);
                });
            });
        }
        if (upgradable) {
            PlayerProfile pr0 = services.profiles().cached(p.getUniqueId()).orElse(null);
            int playerLevel = pr0 == null ? 1 : pr0.progression().level();
            String why = mn.suld.api.item.Reforge.blocked(inst.itemLevel(), playerLevel);
            mn.suld.api.item.Reforge.Cost cost = mn.suld.api.item.Reforge.cost(inst.itemLevel());
            mn.suld.api.item.ItemDefinition matDef = mn.suld.plugin.content.SuldContent.definitionFor(cost.materialId());
            String matName = matDef == null ? cost.materialId() : matDef.displayName();
            m.set(damaged ? 16 : 15, Menu.item(why == null ? Material.SMITHING_TABLE : Material.BARRIER,
                    Menu.title("Сайжруулах → Зэрэг " + (inst.itemLevel() + 1), why == null ? NamedTextColor.GOLD : NamedTextColor.RED), List.of(
                            Menu.kv("Үнэ:", cost.coins() + " зоос", NamedTextColor.GOLD),
                            Menu.kv("Материал:", matName + " ×" + cost.materialCount(), NamedTextColor.AQUA),
                            Menu.line(why == null ? "Зэвсгийн хүч нэмэгдэнэ." : why))), why != null ? null : (pl, c) -> {
                PlayerProfile pr = services.profiles().cached(pl.getUniqueId()).orElse(null);
                ItemStack hand = pl.getInventory().getItemInMainHand();
                mn.suld.api.item.ItemInstance now = services.items().read(hand).orElse(null);
                if (pr == null || now == null || !now.uuid().equals(inst.uuid()) || now.itemLevel() != inst.itemLevel()) {
                    pl.closeInventory();
                    return;
                }
                if (pr.currency() < cost.coins()) {
                    pl.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + cost.coins() + " зоос)."));
                    return;
                }
                if (countItem(pl, cost.materialId()) < cost.materialCount()) {
                    pl.sendMessage(Messages.error(matName + " ×" + cost.materialCount() + " хэрэгтэй."));
                    return;
                }
                mn.suld.api.item.ItemDefinition def = mn.suld.plugin.content.SuldContent.definitionFor(now.definitionId());
                if (!upgradableDef(def) || hand.getAmount() != 1) return; // only single equippable gear, never materials
                pl.closeInventory();
                // COINS -> ITEMS (PlayerDataSaves.coinsForItem): the charge is committed before the materials are taken and
                // the upgraded item is written, so a crash between them costs the coins, never gives a free upgrade
                pr.addCurrency(-cost.coins());
                mn.suld.plugin.item.PlayerDataSaves.coinsForItem(plugin, services, pl, pr, cost.coins(), "smith upgrade", p2 -> {
                    ItemStack h2 = p2.getInventory().getItemInMainHand();
                    mn.suld.api.item.ItemInstance n2 = services.items().read(h2).orElse(null);
                    // the very item, still at the level the price was for, and the materials still in the bag
                    if (n2 == null || !n2.uuid().equals(inst.uuid()) || n2.itemLevel() != inst.itemLevel() || h2.getAmount() != 1
                            || countItem(p2, cost.materialId()) < cost.materialCount()) {
                        refund(p2, pr, cost.coins());
                        return;
                    }
                    mn.suld.api.item.ItemInstance up = services.itemService().generator().upgrade(n2, def);
                    takeItem(p2, cost.materialId(), cost.materialCount());
                    ItemStack upgraded = h2.clone();
                    services.items().rewrite(upgraded, up, services.itemService().viewer(p2)); // keeps durability and identity
                    p2.getInventory().setItemInMainHand(upgraded);
                    p2.playSound(p2.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
                    p2.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, p2.getLocation().add(0, 1.2, 0), 40, 0.4, 0.6, 0.4, 0.4);
                    p2.sendMessage(Messages.success(def.displayName() + " → Зэрэг " + up.itemLevel() + " (+" + up.upgradeLevel() + ")"));
                    if (services.classArmor != null) services.classArmor.smithWork(p2);
                });
            });
            reroll(m, p, inst);
        }
        m.open(p);
    }

    /**
     * «Эртний Хас Чулуу» (docs/HEROIC_DUNGEONS.md): an endgame item's affixes rolled again for jade by rarity and the
     * reforge coins of its level ({@link mn.suld.api.item.AffixReroll}). The same item, rarity, level and base stats.
     */
    private void reroll(Menu m, Player p, mn.suld.api.item.ItemInstance inst) {
        mn.suld.api.item.ItemDefinition def0 = mn.suld.plugin.content.SuldContent.definitionFor(inst.definitionId());
        String why = mn.suld.api.item.AffixReroll.blocked(inst, def0);
        int jade = mn.suld.api.item.AffixReroll.jade(inst.rarity());
        long coins = mn.suld.api.item.AffixReroll.coins(inst.itemLevel());
        int have = countItem(p, mn.suld.api.item.AffixReroll.JADE);
        List<Component> lore = new ArrayList<>();
        if (why == null) {
            lore.add(Menu.kv("Үнэ:", jade + " Эртний Хас Чулуу · " + coins + " зоос", NamedTextColor.GOLD));
            lore.add(Menu.kv("Танд:", have + " хас чулуу", have >= jade ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Menu.line("Зэрэглэл, түвшин, үндсэн хүч хэвээр; шинжүүд шинээр сугалагдана."));
            lore.add(Menu.line("Хас чулууг баатарлаг агуйг хугацаанд нь давж олно."));
        } else {
            lore.add(Menu.line(why));
        }
        m.set(17, Menu.item(why == null ? Material.EMERALD : Material.BARRIER,
                Menu.title("Шинж дахин сугалах", why == null ? NamedTextColor.GREEN : NamedTextColor.RED), lore), why != null ? null : (pl, c) -> {
            PlayerProfile pr = services.profiles().cached(pl.getUniqueId()).orElse(null);
            ItemStack hand = pl.getInventory().getItemInMainHand();
            mn.suld.api.item.ItemInstance now = services.items().read(hand).orElse(null);
            // the very item the window was opened for, unchanged since (a second click finds new affixes and stops)
            if (pr == null || now == null || !now.uuid().equals(inst.uuid()) || !now.affixes().equals(inst.affixes()) || hand.getAmount() != 1) {
                pl.closeInventory();
                return;
            }
            mn.suld.api.item.ItemDefinition def = mn.suld.plugin.content.SuldContent.definitionFor(now.definitionId());
            if (mn.suld.api.item.AffixReroll.blocked(now, def) != null) {
                pl.closeInventory();
                return;
            }
            if (pr.currency() < coins) {
                pl.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + coins + " зоос)."));
                return;
            }
            if (countItem(pl, mn.suld.api.item.AffixReroll.JADE) < jade) {
                pl.sendMessage(Messages.error("Эртний Хас Чулуу ×" + jade + " хэрэгтэй: баатарлаг агуйг хугацаанд нь дав (/dungeon heroic)."));
                return;
            }
            pl.closeInventory();
            // COINS -> ITEMS (PlayerDataSaves.coinsForItem): the coins are committed first, then the jade is taken and the
            // new affixes written in one player-file save (IT-08: jade and affixes always reach disk together)
            pr.addCurrency(-coins);
            mn.suld.plugin.item.PlayerDataSaves.coinsForItem(plugin, services, pl, pr, coins, "affix reroll", p2 -> {
                ItemStack h2 = p2.getInventory().getItemInMainHand();
                mn.suld.api.item.ItemInstance n2 = services.items().read(h2).orElse(null);
                if (n2 == null || !n2.uuid().equals(inst.uuid()) || !n2.affixes().equals(inst.affixes()) || h2.getAmount() != 1
                        || mn.suld.api.item.AffixReroll.blocked(n2, def) != null || countItem(p2, mn.suld.api.item.AffixReroll.JADE) < jade) {
                    refund(p2, pr, coins);
                    return;
                }
                mn.suld.api.item.ItemInstance rolled = services.itemService().rerollAffixes(n2, def);
                takeItem(p2, mn.suld.api.item.AffixReroll.JADE, jade);
                ItemStack out = h2.clone();
                services.items().rewrite(out, rolled, services.itemService().viewer(p2)); // keeps durability and identity
                p2.getInventory().setItemInMainHand(out);
                p2.playSound(p2.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
                p2.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, p2.getLocation().add(0, 1.2, 0), 24, 0.4, 0.5, 0.4, 0);
                p2.sendMessage(Messages.success(def.displayName() + ": шинжүүд шинэчлэгдлээ (-" + jade + " хас чулуу, -" + coins + " зоос)."));
                services.audit().record(mn.suld.api.audit.AuditEvent.of(p2.getUniqueId().toString(), "item.affix_reroll",
                        n2.definitionId() + "-" + n2.uuid(), n2.rarity().name() + " L" + n2.itemLevel()));
            });
        });
    }

    /** A smith job whose item changed while its charge was being saved: the coins go back (and are saved again). */
    private void refund(Player p, PlayerProfile pr, long coins) {
        pr.addCurrency(coins);
        services.profiles().save(pr);
        p.sendMessage(Messages.error("Эд зүйл өөрчлөгдсөн тул ажил цуцлагдлаа — зоос буцаалаа."));
    }

    /** Where the smith stands (the purchase confirmation checks the player is still there). */
    private Location smithLocation() {
        UUID id = spawned.get("blacksmith");
        Entity e = id == null ? null : Bukkit.getEntity(id);
        return e != null && e.isValid() ? e.getLocation() : city.pointLocation("blacksmith");
    }

    private int countItem(Player p, String definitionId) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && services.items().read(it).map(i -> i.definitionId().equals(definitionId)).orElse(false)) n += it.getAmount();
        }
        return n;
    }

    private void takeItem(Player p, String definitionId, int amount) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && amount > 0; i++) {
            if (inv[i] == null || !services.items().read(inv[i]).map(x -> x.definitionId().equals(definitionId)).orElse(false)) continue;
            int use = Math.min(amount, inv[i].getAmount());
            amount -= use;
            if (use == inv[i].getAmount()) inv[i] = null;
            else inv[i].setAmount(inv[i].getAmount() - use);
        }
        p.getInventory().setStorageContents(inv);
    }

    // ------------------------------------------------------------------ relays

    @SuppressWarnings("unused")
    private void travelClassic(Player p, String here) {
        Menu m = new Menu(3, "Өртөө · Хурдан аялал", null);
        int slot = 11;
        for (Map.Entry<String, String> r : RELAY_NAMES.entrySet()) {
            boolean current = r.getKey().equals(here);
            Location to = city.pointLocation(r.getKey());
            Material icon = current ? Material.LIME_BANNER : Material.WHITE_BANNER;
            List<Component> lore = new ArrayList<>();
            lore.add(current ? Menu.line("Та энд байна.") : Menu.kv("Үнэ:", TRAVEL_COST + " зоос", NamedTextColor.GOLD));
            m.set(slot++, Menu.item(icon, Menu.title(r.getValue(), current ? NamedTextColor.GREEN : NamedTextColor.YELLOW), lore),
                    current || to == null ? null : (pl, c) -> {
                        PlayerProfile pr = services.profiles().cached(pl.getUniqueId()).orElse(null);
                        if (pr == null) return;
                        if (pr.currency() < TRAVEL_COST) {
                            pl.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + TRAVEL_COST + " зоос)."));
                            return;
                        }
                        pl.closeInventory();
                        if (!travelling.add(pl.getUniqueId())) return; // a second click while the first trip loads
                        pr.addCurrency(-TRAVEL_COST); // paid up front, refunded if the trip fails
                        mn.suld.plugin.perf.SafeTeleport.to(plugin, pl, to.clone().add(1.5, 0, 1.5),
                                org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN, ok -> {
                                    travelling.remove(pl.getUniqueId());
                                    if (!ok) {
                                        pr.addCurrency(TRAVEL_COST);
                                        services.profiles().save(pr);
                                        pl.sendMessage(Messages.error("Аялал боломжгүй боллоо — зоос буцаагдлаа."));
                                        return;
                                    }
                                    long pay = TRAVEL_COST;
                                    services.profiles().save(pr);
                                    pl.playSound(pl.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 1f);
                                    pl.sendMessage(Messages.success("Өртөөгөөр " + r.getValue() + " хүрлээ (-" + pay + " зоос)."));
                                });
                    });
        }
        m.open(p);
    }

    // ------------------------------------------------------------------ shrine

    private void bless(Player p, PlayerProfile pr) {
        long now = System.currentTimeMillis();
        long last = blessed.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < BLESSING_COOLDOWN_MS) {
            p.sendMessage(Messages.info("Тахилч: «Тэнгэр чамайг аль хэдийн ивээсэн. " + ((BLESSING_COOLDOWN_MS - (now - last)) / 60_000 + 1) + " минутын дараа ир.»"));
            return;
        }
        if (pr.currency() < BLESSING_COST) {
            p.sendMessage(Messages.error("Тахилд " + BLESSING_COST + " зоос хэрэгтэй."));
            return;
        }
        pr.addCurrency(-BLESSING_COST);
        blessed.put(p.getUniqueId(), now);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 60, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 300, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20 * 300, 0));
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.02);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.3f);
        p.sendMessage(Messages.success("Мөнх Тэнгэр ивээлээ: эдгэрэл, хурд, аз (5 мин)."));
    }
}
