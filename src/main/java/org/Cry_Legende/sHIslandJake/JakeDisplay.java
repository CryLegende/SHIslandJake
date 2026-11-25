package org.Cry_Legende.sHIslandJake;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Dessin des Jake : un PNJ FancyNpcs créé par l'adaptateur sans passer par son NpcManager (ni sauvegarde ni
 * boucle de visibilité de FancyNpcs) et, s'il est installé, un hologramme FancyHolograms. Fil principal.
 */
final class JakeDisplay {

    private final SHIslandJake plugin;
    private final JakeRegistry registry;
    private final Holograms holograms;

    JakeDisplay(SHIslandJake plugin, JakeRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        // La classe Holograms ne se charge pas sans FancyHolograms : sa présence est vérifiée avant
        this.holograms = Bukkit.getPluginManager().isPluginEnabled("FancyHolograms") ? new Holograms(plugin.getLogger()) : null;
        if (holograms == null) {
            plugin.getLogger().info("FancyHolograms absent : la première ligne de l'hologramme devient le nom de Jake.");
        }
    }

    private boolean useHolograms() {
        return holograms != null && plugin.settings().hologram;
    }

    /** Le PNJ du Jake, créé au premier passage d'un joueur. Null si son monde n'est pas chargé. */
    Npc handle(Jake jake) {
        if (jake.handle() != null) {
            return jake.handle();
        }
        Location location = jake.location();
        if (location == null) {
            return null;
        }
        Settings settings = plugin.settings();
        JakeData d = jake.data();
        NpcData data = new NpcData("shislandjake-" + d.island(), d.island(), location);
        data.setType(d.type());
        data.setVisibilityDistance(settings.viewDistance);
        data.setCollidable(false);
        data.setShowInTab(false);
        data.setGlowing(settings.glowing);
        data.setGlowingColor(settings.glowColor);
        data.setDisplayName(nameTag(d));

        Npc handle = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
        handle.setSaveToFile(false);
        handle.create();
        jake.setHandle(handle);
        registry.link(handle.getEntityId(), jake);
        return handle;
    }

    /** Sans hologramme, la première ligne sert de nom au-dessus du PNJ. */
    private String nameTag(JakeData d) {
        if (useHolograms() || !plugin.settings().hologram) {
            return "<empty>";
        }
        List<String> lines = hologramLines(d);
        return lines.isEmpty() ? "<empty>" : lines.getFirst();
    }

    private List<String> hologramLines(JakeData d) {
        List<String> lines = new ArrayList<>();
        for (String line : plugin.messages().lines("hologramme", "nom", plugin.messages().jakeName(d.type()))) {
            lines.add(Messages.miniMessage(line));
        }
        return lines;
    }

    /** Montre ou cache l'hologramme du Jake à ce joueur. */
    void syncHologram(Jake jake, Player player, boolean shown) {
        if (!useHolograms()) {
            return;
        }
        Object hologram = jake.hologram();
        if (hologram == null) {
            if (!shown) {
                return;
            }
            Location base = jake.location();
            if (base == null) {
                return;
            }
            JakeData d = jake.data();
            base.add(0, plugin.settings().height(d.type()), 0);
            base.setYaw(0);
            base.setPitch(0);
            hologram = holograms.create("shislandjake_" + d.island(), base, hologramLines(d), plugin.settings());
            if (hologram == null) {
                return;
            }
            jake.setHologram(hologram);
        }
        if (shown) {
            holograms.show(hologram, player);
        } else {
            holograms.hide(hologram, player);
        }
    }

    /** Montre à ce joueur sa ligne d'alerte au-dessus de l'hologramme, la change ou l'enlève (texte vide). */
    void syncAlert(Jake jake, Player player, String text) {
        if (!useHolograms()) {
            return;
        }
        UUID id = player.getUniqueId();
        Jake.Alert current = jake.alerts().get(id);
        if (current != null && current.text().equals(text)) {
            holograms.show(current.hologram(), player);
            return;
        }
        if (current != null) {
            jake.alerts().remove(id);
            holograms.hideAll(current.hologram());
        }
        Location base = text.isEmpty() ? null : jake.location();
        if (base == null) {
            return;
        }
        JakeData d = jake.data();
        base.add(0, plugin.settings().height(d.type()) + plugin.settings().alertOffset, 0);
        base.setYaw(0);
        base.setPitch(0);
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            lines.add(Messages.miniMessage(line));
        }
        Object hologram = holograms.create("shislandjake_alerte_" + d.island() + "_" + id, base, lines, plugin.settings());
        if (hologram != null) {
            jake.alerts().put(id, new Jake.Alert(hologram, text));
            holograms.show(hologram, player);
        }
    }

    /**
     * Retire le PNJ et l'hologramme à tous ceux qui les voient et les oublie : ils seront recréés avec les
     * nouvelles données au prochain passage d'un joueur.
     */
    void release(Jake jake) {
        Npc handle = jake.handle();
        if (handle != null) {
            registry.unlink(handle.getEntityId());
            jake.setHandle(null);
            forViewers(handle, Npc::remove);
        }
        Object hologram = jake.hologram();
        if (hologram != null) {
            jake.setHologram(null);
            holograms.hideAll(hologram);
        }
        for (Jake.Alert alert : jake.alerts().values()) {
            holograms.hideAll(alert.hologram());
        }
        jake.alerts().clear();
    }

    /** Arrêt ou rechargement : plus rien n'est dessiné. Un hologramme n'existe qu'avec son PNJ. */
    void releaseAll() {
        for (Jake jake : new ArrayList<>(registry.drawn())) {
            release(jake);
        }
    }

    /** Joueur parti : les PNJ et hologrammes l'oublient. */
    void forget(Player player) {
        UUID id = player.getUniqueId();
        for (Jake jake : registry.drawn()) {
            jake.forget(id);
            if (jake.hologram() != null) {
                holograms.forget(jake.hologram(), player);
            }
            Jake.Alert alert = jake.alerts().remove(id);
            if (alert != null) {
                holograms.forget(alert.hologram(), player);
            }
        }
    }

    /** Sur le fil de FancyNpcs, pour chaque joueur qui voit le PNJ (l'ordre des tâches de ce fil est gardé). */
    private void forViewers(Npc handle, BiConsumer<Npc, Player> action) {
        FancyNpcsPlugin.get().getNpcThread().submit(() -> {
            for (Map.Entry<UUID, Boolean> viewer : handle.getIsVisibleForPlayer().entrySet()) {
                Player player = viewer.getValue() ? Bukkit.getPlayer(viewer.getKey()) : null;
                if (player != null) {
                    action.accept(handle, player);
                }
            }
        });
    }
}
