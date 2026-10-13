package mn.suld.plugin.hud;

import mn.suld.api.balance.AscensionHonor;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

/**
 * «Нурууны туяа»: the back aura of Тэнгэрийн Зэрэг VII–IX (AscensionHonor; the shrine promises it). Twice a second a few
 * motes of light gather behind an honoured player's shoulders, for everyone near to see: gold at VII, gold and the
 * steppe's blue at VIII, and a white glow among them from IX (X keeps IX's). Not while invisible, dead, spectating or
 * standing in for themselves in the training yard.
 */
public final class HonorAura {

    private static final Color GOLD = Color.fromRGB(0xF2C14E), BLUE = Color.fromRGB(0x3F7FD8);
    private static final Particle.DustOptions GOLD_DUST = new Particle.DustOptions(GOLD, 0.9f), BLUE_DUST = new Particle.DustOptions(BLUE, 0.9f);

    private final Plugin plugin;
    private final SuldServices services;

    public HonorAura(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int tier = AscensionHonor.auraTier(services.profiles().cached(p.getUniqueId()).map(PlayerProfile::ascension).orElse(0));
            if (tier == 0 || p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.isInvisible()
                    || p.hasPotionEffect(PotionEffectType.INVISIBILITY)) continue;
            if (services.training != null && services.training.training(p.getUniqueId())) continue;
            Location l = p.getLocation();
            // behind the back at the shoulders: Minecraft looks along (-sin yaw, cos yaw)
            double yaw = Math.toRadians(l.getYaw());
            Location back = l.clone().add(Math.sin(yaw) * 0.35, 1.25, -Math.cos(yaw) * 0.35);
            World w = p.getWorld();
            w.spawnParticle(Particle.DUST, back, 3, 0.22, 0.25, 0.22, 0, GOLD_DUST);
            if (tier >= 2) w.spawnParticle(Particle.DUST, back, 2, 0.25, 0.3, 0.25, 0, BLUE_DUST);
            if (tier >= 3) w.spawnParticle(Particle.END_ROD, back.clone().add(0, 0.2, 0), 1, 0.15, 0.2, 0.15, 0.005);
        }
    }
}
