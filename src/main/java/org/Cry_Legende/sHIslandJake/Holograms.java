package org.Cry_Legende.sHIslandJake;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Hologrammes FancyHolograms au-dessus des Jake. Ils sont créés sans être déclarés à FancyHolograms : il ne les
 * sauvegarde pas et ne les parcourt pas, c'est le suivi des joueurs de SHIslandJake qui les montre et les cache.
 * Seule classe qui touche à FancyHolograms.
 */
final class Holograms {

    private static final String PREFIX = "shislandjake_";

    private final Logger log;

    Holograms(Logger log) {
        this.log = log;
    }

    Object create(UUID island, Location location, List<String> lines, Settings settings) {
        TextHologramData data = new TextHologramData(PREFIX + island, location);
        data.setText(lines);
        data.setPersistent(false);
        data.setVisibilityDistance(settings.viewDistance);
        data.setBillboard(settings.billboard);
        data.setTextShadow(settings.textShadow);
        Color background = background(settings.background);
        if (background != null) {
            data.setBackground(background);
        }
        try {
            return FancyHologramsPlugin.get().getHologramManager().create(data);
        } catch (RuntimeException e) {
            log.warning("FancyHolograms n'a pas créé l'hologramme de l'île " + island + " : " + e.getMessage());
            return null;
        }
    }

    void show(Object hologram, Player player) {
        Hologram h = (Hologram) hologram;
        if (!h.isViewer(player)) {
            h.forceShowHologram(player);
        }
    }

    void hide(Object hologram, Player player) {
        Hologram h = (Hologram) hologram;
        if (h.isViewer(player)) {
            h.forceHideHologram(player);
        }
    }

    /** Joueur parti : il n'a plus rien à recevoir, on l'oublie seulement. */
    void forget(Object hologram, Player player) {
        ((Hologram) hologram).hideHologram(player);
    }

    /** Retire l'hologramme à tous ceux qui le voient ; il n'est plus utilisé ensuite. */
    void hideAll(Object hologram) {
        Hologram h = (Hologram) hologram;
        for (UUID viewer : h.getViewers()) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                h.forceHideHologram(player);
            }
        }
        h.deleteHologram();
    }

    private Color background(String value) {
        if (value == null || value.equalsIgnoreCase("default")) {
            return null;
        }
        if (value.equalsIgnoreCase("transparent")) {
            return Hologram.TRANSPARENT;
        }
        try {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            return Color.fromARGB((int) Long.parseLong(hex, 16));
        } catch (NumberFormatException e) {
            log.warning("hologramme.fond « " + value + " » illisible : fond par défaut.");
            return null;
        }
    }
}
