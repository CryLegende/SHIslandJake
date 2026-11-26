package me.general_breddok.blockdisplaycreator.render;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Set;

/**
 * Faux afficheur pour les essais de CorpsModele : les signatures de l'API « modèle montré », sans « duration » ni
 * « animations » (un moteur plus ancien).
 */
public final class DisplayRenderer {

    public enum PlayResult {
        PLAYED,
        NO_BLOCK,
        NO_ANIMATION,
        LOOPING
    }

    public static final class ShownModel {
        boolean shown = true;
    }

    public int shows;

    public ShownModel show(Plugin owner, String model, Location where, float size) {
        return "connu".equals(model) ? new ShownModel() : null;
    }

    public void hide(ShownModel shown) {
        shown.shown = false;
    }

    public boolean shows(ShownModel shown) {
        shows++;
        return shown.shown;
    }

    public PlayResult play(ShownModel shown, String animation, Collection<? extends Player> players) {
        return "geste".equals(animation) ? PlayResult.PLAYED : PlayResult.NO_ANIMATION;
    }

    public int stop(ShownModel shown, Collection<? extends Player> players) {
        return 0;
    }

    public boolean knows(String model) {
        return "connu".equals(model) || "pas-lu".equals(model);
    }

    public double[] bounds(String model) {
        return "connu".equals(model) ? new double[] {-0.4, 0, -0.3, 0.4, 2.2, 0.3} : null;
    }

    /** Pas dans l'API : présent pour vérifier qu'une méthode en trop ne gêne pas. */
    public Set<String> autre() {
        return Set.of();
    }
}
