package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.island.Island;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ce qui arrive aux Jake : création, déplacement, type, visibilité, retrait. Fil principal.
 */
final class JakeManager {

    private final SHIslandJake plugin;
    private final JakeRegistry registry;
    private final JakeStore store;
    private final JakeDisplay display;
    private final Islands islands;
    private JakeTracker tracker;

    JakeManager(SHIslandJake plugin, JakeRegistry registry, JakeStore store, JakeDisplay display, Islands islands) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.display = display;
        this.islands = islands;
    }

    void setTracker(JakeTracker tracker) {
        this.tracker = tracker;
    }

    Jake get(UUID island) {
        return registry.get(island);
    }

    /** Place de départ du Jake de l'île ; null si SSB2 ne connaît pas son centre. */
    JakeData defaultData(Island island) {
        Location center = islands.center(island);
        if (center == null || center.getWorld() == null) {
            return null;
        }
        Settings s = plugin.settings();
        return new JakeData(island.getUniqueId(), center.getWorld().getName(), center.getX() + s.offset.getX(),
                center.getY() + s.offset.getY(), center.getZ() + s.offset.getZ(), s.yaw, s.pitch, s.defaultType,
                s.defaultVisible);
    }

    /** Pose le Jake d'une île à sa place de départ (création d'île, Jake disparu). */
    Jake create(Island island) {
        JakeData data = defaultData(island);
        if (data == null) {
            return null;
        }
        Jake jake = new Jake(data);
        Jake previous = registry.add(jake);
        if (previous != null) {
            display.release(previous);
        }
        store.markDirty();
        if (tracker != null) {
            tracker.refreshAround(jake);
        }
        return jake;
    }

    /** Jake de l'île, posé s'il n'existait pas ; « created » reçoit vrai dans ce cas. */
    Jake getOrCreate(Island island, boolean[] created) {
        Jake jake = registry.get(island.getUniqueId());
        if (jake != null) {
            return jake;
        }
        jake = create(island);
        if (jake != null && created != null) {
            created[0] = true;
        }
        return jake;
    }

    void add(JakeData data) {
        Jake previous = registry.add(new Jake(data));
        if (previous != null) {
            display.release(previous);
        }
        store.markDirty();
    }

    void move(Jake jake, Location target, float yaw) {
        display.release(jake);
        registry.update(jake, jake.data().withPosition(target.getWorld().getName(), target.getX(), target.getY(),
                target.getZ(), yaw, 0f));
        store.markDirty();
        tracker.refreshAround(jake);
    }

    void setType(Jake jake, EntityType type) {
        display.release(jake);
        jake.setData(jake.data().withType(type));
        store.markDirty();
        tracker.refreshAround(jake);
    }

    /** Renvoie le nouvel état : vrai si Jake est visible. */
    boolean toggleVisible(Jake jake) {
        boolean visible = !jake.data().visible();
        display.release(jake);
        jake.setData(jake.data().withVisible(visible));
        store.markDirty();
        tracker.refreshAround(jake);
        return visible;
    }

    void delete(UUID island) {
        Jake jake = registry.get(island);
        if (jake == null) {
            return;
        }
        display.release(jake);
        registry.remove(jake);
        store.markDirty();
    }

    /**
     * Jake des îles que SSB2 ne connaît plus (île supprimée pendant que le plugin était arrêté). Rien n'est retiré
     * si SSB2 ne connaît aucune île ou si plus de la moitié des Jake seraient concernés : SSB2 n'a alors sans
     * doute pas fini de charger.
     */
    int pruneOrphans() {
        int known = islands.count();
        if (known == 0) {
            return 0;
        }
        List<Jake> orphans = new ArrayList<>();
        for (Jake jake : registry.all()) {
            if (islands.byId(jake.island()) == null) {
                orphans.add(jake);
            }
        }
        if (orphans.isEmpty()) {
            return 0;
        }
        if (orphans.size() * 2 > registry.size()) {
            plugin.getLogger().warning(orphans.size() + " Jake sur " + registry.size()
                    + " ont une île inconnue de SuperiorSkyblock2 : rien n'est retiré.");
            return 0;
        }
        for (Jake jake : orphans) {
            delete(jake.island());
        }
        return orphans.size();
    }

    /** Le joueur peut-il choisir ce type ? */
    boolean allowed(Player player, EntityType type) {
        Settings.TypeSettings settings = plugin.settings().types.get(type);
        return settings != null && settings.allowed(player);
    }
}
