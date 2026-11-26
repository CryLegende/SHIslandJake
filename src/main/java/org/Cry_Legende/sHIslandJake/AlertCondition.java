package org.Cry_Legende.sHIslandJake;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que l'alerte au-dessus de Jake dit à un joueur : les lignes « alerte » de messages.yml, quand alerte.placeholder vaut
 * alerte.valeur pour lui. Lu au plus une fois par alerte.verification-ticks. Sans PlaceholderAPI, il n'y a jamais d'alerte.
 * Fil principal.
 */
final class AlertCondition {

    private record Cached(String text, int untilTick) {
    }

    private final SHIslandJake plugin;
    private final Map<UUID, Cached> cache = new HashMap<>();

    AlertCondition(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    /** Lignes séparées par des retours à la ligne ; vide = pas d'alerte. */
    String text(Player player) {
        Settings s = plugin.settings();
        if (!s.alertEnabled || s.alertPlaceholder.isEmpty()) {
            return "";
        }
        int now = Bukkit.getCurrentTick();
        Cached c = cache.get(player.getUniqueId());
        if (c != null && now < c.untilTick()) {
            return c.text();
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return "";
        }
        String text = PlaceholderAPI.setPlaceholders(player, s.alertPlaceholder).trim().equalsIgnoreCase(s.alertValue)
                ? lines(player) : "";
        cache.put(player.getUniqueId(), new Cached(text, now + s.alertCheckTicks));
        return text;
    }

    private String lines(Player player) {
        StringBuilder out = new StringBuilder();
        for (String raw : plugin.messages().lines("alerte")) {
            String line = raw.indexOf('%') >= 0 ? PlaceholderAPI.setPlaceholders(player, raw) : raw;
            if (!line.isBlank()) {
                out.append(out.isEmpty() ? "" : "\n").append(line);
            }
        }
        return out.toString();
    }

    /** Tick où l'alerte de ce joueur doit être relue (maintenant si elle ne l'a jamais été) ; jamais si elle est éteinte. */
    int prochaineLecture(Player player) {
        Settings s = plugin.settings();
        if (!s.alertEnabled || s.alertPlaceholder.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        Cached c = cache.get(player.getUniqueId());
        return c == null ? Bukkit.getCurrentTick() : c.untilTick();
    }

    void forget(Player player) {
        cache.remove(player.getUniqueId());
    }
}
