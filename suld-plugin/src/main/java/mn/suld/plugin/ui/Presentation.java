package mn.suld.plugin.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Shared title/sound presentations so combat, quests and dungeons feel consistent. */
public final class Presentation {

    private Presentation() {
    }

    /** Told of every level-up shown (the achievements record the level; SuldPlugin sets it). */
    public static volatile java.util.function.BiConsumer<Player, Integer> onLevel = (p, level) -> { };

    public static void levelUp(Player player, int from, int to) {
        onLevel.accept(player, to);
        player.showTitle(Title.title(
                Component.text("ТҮВШИН ДЭЭШЛЭВ", Messages.BRAND, TextDecoration.BOLD),
                Component.text("Түвшин " + from + " → " + to, NamedTextColor.WHITE, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage(Messages.success("Түвшин " + to + "-д хүрлээ!"));
    }

    public static void banner(Player player, String title, String subtitle, NamedTextColor color) {
        player.showTitle(Title.title(
                Component.text(title, color, TextDecoration.BOLD),
                Component.text(subtitle, NamedTextColor.WHITE, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
    }
}
