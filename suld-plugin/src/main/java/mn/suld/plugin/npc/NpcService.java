package mn.suld.plugin.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
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

    private static Role roleFor(WorldPoint p) {
        String id = p.id();
        if (id.equals("class_selection")) return new Role("shaman", "Ангийн Бөө", "Анги сонгох", NamedTextColor.LIGHT_PURPLE);
        if (id.equals("tutorial")) return new Role("guide", "Хөтөч", "Заавар · Тусламж", NamedTextColor.AQUA);
        if (id.equals("quest.first_hunt")) return new Role("hunter", "Анчдын Ахлагч", "Эрэл: Анхны Ан", NamedTextColor.GOLD);
        if (id.startsWith("merchant.")) return new Role("merchant", "Худалдаачин", "Хангамж · Олз зарах", NamedTextColor.GREEN);
        if (id.equals("blacksmith")) return new Role("blacksmith", "Дархан", "Засвар · Сайжруулалт", NamedTextColor.RED);
        if (id.startsWith("fast_travel.")) return new Role("rider", "Өртөөчин", "Хурдан аялал", NamedTextColor.YELLOW);
        if (id.equals("shrine.sky")) return new Role("lama", "Тэнгэрийн Тахилч", "Тэнгэрийн ивээл", NamedTextColor.WHITE);
        return null;
    }

    private static final Map<String, String> RELAY_NAMES = new LinkedHashMap<>();

    static {
        RELAY_NAMES.put("fast_travel.plaza", "Төв талбай");
        RELAY_NAMES.put("fast_travel.gate_south", "Өмнөд хаалга");
        RELAY_NAMES.put("fast_travel.gate_west", "Баруун хаалга");
        RELAY_NAMES.put("fast_travel.gate_east", "Зүүн хаалга");
        RELAY_NAMES.put("fast_travel.gate_north", "Хойд хаалга");
    }

    private static final long TRAVEL_COST = 10, BLESSING_COST = 30;
    private static final long BLESSING_COOLDOWN_MS = 10 * 60_000L;

    private final Plugin plugin;
    private final SuldServices services;
    private final WorldBuildService city;
    private final Menus menus;
    private final NamespacedKey key;
    private final Map<String, UUID> spawned = new ConcurrentHashMap<>();
    private final Map<UUID, Long> blessed = new ConcurrentHashMap<>();
    private final mn.suld.plugin.gui.SmithMenu smith;

    public NpcService(Plugin plugin, SuldServices services, WorldBuildService city, Menus menus) {
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
            WorldPoint p = point(e.getKey());
            Role r = p == null ? null : roleFor(p);
            if (r != null) out.put(e.getValue().toString(), r.glow());
        }
        return out;
    }

    private WorldPoint point(String id) {
        for (WorldPoint p : city.slicePoints()) if (p.id().equals(id)) return p;
        return null;
    }

    private void ensureAll() {
        if (!city.isBuilt()) return;
        for (WorldPoint p : city.slicePoints()) {
            Role r = roleFor(p);
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
            if (roleFor(p) == null) continue;
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
        act(p, id);
    }

    private void act(Player p, String id) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return;
        if (id.equals("class_selection")) {
            if (!pr.hasSelectedClass()) services.classSelectionGui().open(p);
            else p.performCommand("class");
        } else if (id.equals("tutorial")) {
            menus.tutorial(p);
        } else if (id.equals("quest.first_hunt")) {
            if (!pr.hasSelectedClass()) {
                p.sendMessage(Messages.info("Анчин: «Эхлээд ангиа сонго — Бөө чамайг хүлээж байна.»"));
                return;
            }
            services.quests().ensure(p, pr);
            menus.quests(p);
        } else if (id.startsWith("merchant.")) {
            menus.shop(p);
        } else if (id.equals("blacksmith")) {
            repair(p);
        } else if (id.startsWith("fast_travel.")) {
            travel(p, id);
        } else if (id.equals("shrine.sky")) {
            bless(p, pr);
        }
    }

    // ------------------------------------------------------------------ blacksmith

    /** The smith: repair worn gear (coins) and upgrade SÜLD items one level (coins + region materials). */
    private void repair(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        Menu m = new Menu(3, "Дархан · Засвар ба сайжруулалт", null);
        boolean damaged = !it.getType().isAir() && it.getItemMeta() instanceof Damageable d && d.hasDamage();
        mn.suld.api.item.ItemInstance inst = it.getType().isAir() ? null : services.items().read(it).orElse(null);
        boolean upgradable = inst != null && !inst.definitionId().startsWith("weapon.class.") && !inst.definitionId().startsWith("armor.class.") && !services.relics().items().isRelic(it)
                && mn.suld.plugin.content.SuldContent.definitionFor(inst.definitionId()) != null;
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
            long cost = 5 + (long) Math.ceil(damage / 8.0);
            m.set(upgradable ? 14 : 15, Menu.item(Material.ANVIL, Menu.title("Засах · " + cost + " ₮", NamedTextColor.GREEN),
                    List.of(Menu.kv("Гэмтэл:", String.valueOf(damage), NamedTextColor.RED), Menu.line("Бүрэн шинэ болгоно."))), (pl, c) -> {
                PlayerProfile pr = services.profiles().cached(pl.getUniqueId()).orElse(null);
                ItemStack hand = pl.getInventory().getItemInMainHand();
                if (pr == null || !hand.isSimilar(it)) {
                    pl.closeInventory();
                    return;
                }
                if (pr.currency() < cost) {
                    pl.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + cost + " ₮)."));
                    return;
                }
                if (hand.getItemMeta() instanceof Damageable hd) {
                    pr.addCurrency(-cost);
                    hd.setDamage(0);
                    hand.setItemMeta(hd);
                    pl.playSound(pl.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
                    pl.sendMessage(Messages.success("Дархан засаж өглөө (-" + cost + " ₮)."));
                    if (services.classArmor != null) services.classArmor.smithWork(pl);
                }
                pl.closeInventory();
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
                            Menu.kv("Үнэ:", cost.coins() + " ₮", NamedTextColor.GOLD),
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
                    pl.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + cost.coins() + " ₮)."));
                    return;
                }
                if (countItem(pl, cost.materialId()) < cost.materialCount()) {
                    pl.sendMessage(Messages.error(matName + " ×" + cost.materialCount() + " хэрэгтэй."));
                    return;
                }
                mn.suld.api.item.ItemDefinition def = mn.suld.plugin.content.SuldContent.definitionFor(now.definitionId());
                mn.suld.api.item.ItemInstance up = services.itemService().generator().upgrade(now, def);
                takeItem(pl, cost.materialId(), cost.materialCount());
                pr.addCurrency(-cost.coins());
                ItemStack upgraded = hand.clone();
                services.items().rewrite(upgraded, up, services.itemService().viewer(pl)); // keeps durability and identity
                pl.getInventory().setItemInMainHand(upgraded);
                pl.playSound(pl.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
                pl.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, pl.getLocation().add(0, 1.2, 0), 40, 0.4, 0.6, 0.4, 0.4);
                pl.sendMessage(Messages.success(def.displayName() + " → Зэрэг " + up.itemLevel() + " (+" + up.upgradeLevel() + ")"));
                services.profiles().save(pr);
                if (services.classArmor != null) services.classArmor.smithWork(pl);
                pl.closeInventory();
            });
        }
        m.open(p);
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

    private void travel(Player p, String here) {
        Menu m = new Menu(3, "Өртөө · Хурдан аялал", null);
        int slot = 11;
        for (Map.Entry<String, String> r : RELAY_NAMES.entrySet()) {
            boolean current = r.getKey().equals(here);
            Location to = city.pointLocation(r.getKey());
            Material icon = current ? Material.LIME_BANNER : Material.WHITE_BANNER;
            List<Component> lore = new ArrayList<>();
            lore.add(current ? Menu.line("Та энд байна.") : Menu.kv("Үнэ:", TRAVEL_COST + " ₮", NamedTextColor.GOLD));
            m.set(slot++, Menu.item(icon, Menu.title(r.getValue(), current ? NamedTextColor.GREEN : NamedTextColor.YELLOW), lore),
                    current || to == null ? null : (pl, c) -> {
                        PlayerProfile pr = services.profiles().cached(pl.getUniqueId()).orElse(null);
                        if (pr == null) return;
                        if (pr.currency() < TRAVEL_COST) {
                            pl.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + TRAVEL_COST + " ₮)."));
                            return;
                        }
                        pl.closeInventory();
                        if (!pl.teleport(to.clone().add(1.5, 0, 1.5))) {
                            pl.sendMessage(Messages.error("Аялал боломжгүй боллоо — зоос хасагдсангүй."));
                            return;
                        }
                        pr.addCurrency(-TRAVEL_COST);
                        services.profiles().save(pr);
                        pl.playSound(pl.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 1f);
                        pl.sendMessage(Messages.success("Өртөөгөөр " + r.getValue() + " хүрлээ (-" + TRAVEL_COST + " ₮)."));
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
            p.sendMessage(Messages.error("Тахилд " + BLESSING_COST + " ₮ хэрэгтэй."));
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
