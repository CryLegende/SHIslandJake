package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.island.Island;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * %shislandjake_type%, %shislandjake_type_nom%, %shislandjake_nom%, %shislandjake_visible% : le Jake de l'île
 * du joueur. Peut être appelé hors du fil principal : seules des données immuables sont lues.
 * Seule classe qui touche à PlaceholderAPI.
 */
final class JakePlaceholders extends PlaceholderExpansion {

    private final SHIslandJake plugin;

    JakePlaceholders(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "shislandjake";
    }

    @Override
    public String getAuthor() {
        return "Cry_Legende";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        Island island = plugin.islands().of(player.getUniqueId());
        Jake jake = island == null ? null : plugin.registry().get(island.getUniqueId());
        if (jake == null) {
            return "";
        }
        JakeData d = jake.data();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "type" -> d.type().name();
            case "type_nom" -> plugin.messages().typeName(d.type());
            case "nom" -> plugin.messages().jakeName(d.type());
            case "visible" -> plugin.messages().text(d.visible() ? "placeholders.visible" : "placeholders.cache");
            default -> null;
        };
    }
}
