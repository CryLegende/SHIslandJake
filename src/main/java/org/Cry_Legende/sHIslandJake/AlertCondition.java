package org.Cry_Legende.sHIslandJake;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lit le placeholder de l'alerte (alerte.placeholder) pour un joueur, au plus une fois par alerte.verification-ticks.
 * Sans PlaceholderAPI, l'alerte ne s'affiche jamais. Fil principal.
 */
final class AlertCondition {

    private record Cached(boolean shows, int untilTick) {
    }

    private final SHIslandJake plugin;
    private final Map<UUID, Cached> cache = new HashMap<>();

    AlertCondition(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    boolean shows(Player player) {
        Settings s = plugin.settings();
        if (!s.alertEnabled || s.alertPlaceholder.isEmpty() || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return false;
        }
        int now = Bukkit.getCurrentTick();
        Cached c = cache.get(player.getUniqueId());
        if (c != null && now < c.untilTick()) {
            return c.shows();
        }
        boolean shows = PlaceholderAPI.setPlaceholders(player, s.alertPlaceholder).trim().equalsIgnoreCase(s.alertValue);
        cache.put(player.getUniqueId(), new Cached(shows, now + s.alertCheckTicks));
        return shows;
    }

    void forget(Player player) {
        cache.remove(player.getUniqueId());
    }
}
