package org.Cry_Legende.sHIslandJake;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Corps de secours : un PNJ FancyNpcs créé par l'adaptateur sans passer par son NpcManager (ni sauvegarde ni boucle de
 * visibilité de FancyNpcs). Le regard par joueur est fait par le suivi ({@link JakeTracker}). Seule classe qui crée et
 * retire les PNJ de FancyNpcs. Fil principal.
 */
final class CorpsMob implements Corps {

    private final SHIslandJake plugin;
    private final JakeRegistry registry;

    CorpsMob(SHIslandJake plugin, JakeRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /** Dessine le Jake sous cette apparence (une apparence « mob »). */
    boolean montrer(Jake jake, Settings.Apparence apparence) {
        if (jake.handle() != null) {
            return true;
        }
        Location location = jake.location();
        if (location == null || apparence.mob() == null) {
            return false;
        }
        Settings settings = plugin.settings();
        JakeData d = jake.data();
        NpcData data = new NpcData("shislandjake-" + d.island(), d.island(), location);
        data.setType(apparence.mob());
        data.setVisibilityDistance(settings.viewDistance);
        data.setCollidable(false);
        data.setShowInTab(false);
        data.setGlowing(settings.glowing);
        data.setGlowingColor(settings.glowColor);
        data.setDisplayName("<empty>");

        Npc handle = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
        handle.setSaveToFile(false);
        handle.create();
        jake.setHandle(handle);
        registry.link(handle.getEntityId(), jake);
        return true;
    }

    /** L'apparence du Jake si c'est un mob, sinon celle de secours. */
    @Override
    public boolean montrer(Jake jake) {
        Settings.Apparence a = plugin.settings().apparence(jake.data().type());
        return montrer(jake, a.estModele() ? plugin.settings().secours : a);
    }

    @Override
    public void retirer(Jake jake) {
        Npc handle = jake.handle();
        if (handle == null) {
            return;
        }
        registry.unlink(handle.getEntityId());
        jake.setHandle(null);
        // Sur le fil de FancyNpcs, pour chaque joueur qui voit le PNJ (l'ordre des tâches de ce fil est gardé)
        FancyNpcsPlugin.get().getNpcThread().submit(() -> {
            for (Map.Entry<UUID, Boolean> viewer : handle.getIsVisibleForPlayer().entrySet()) {
                Player player = viewer.getValue() ? Bukkit.getPlayer(viewer.getKey()) : null;
                if (player != null) {
                    handle.remove(player);
                }
            }
        });
    }

    /**
     * FancyNpcs décide lui-même d'après la distance et le monde du joueur. Tant que le PNJ est montré à ce joueur, rien
     * n'est demandé : une tâche sur le fil de FancyNpcs par passage et par joueur coûterait pour rien.
     */
    @Override
    public void voir(Jake jake, Player joueur, boolean visible) {
        Npc handle = jake.handle();
        if (handle != null && (!visible || !handle.isShownFor(joueur))) {
            handle.checkAndUpdateVisibility(joueur);
        }
    }

    @Override
    public boolean jouer(Jake jake, String animation, Player pour) {
        return false;
    }

    @Override
    public void arreter(Jake jake, Player pour) {
    }

    @Override
    public int duree(Jake jake, String animation) {
        return -1;
    }

    @Override
    public boolean vivant(Jake jake) {
        return jake.handle() != null;
    }
}
