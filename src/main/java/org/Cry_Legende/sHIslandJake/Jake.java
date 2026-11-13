package org.Cry_Legende.sHIslandJake;

import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Le Jake d'une île. Ses données changent par remplacement de {@link JakeData} ; le PNJ FancyNpcs (handle) et
 * l'hologramme ne sont créés qu'au passage d'un joueur, et ne sont touchés que depuis le fil principal.
 */
final class Jake {

    private volatile JakeData data;
    private Npc handle;
    /** Hologramme FancyHolograms, gardé en Object pour que le plugin tourne sans FancyHolograms. */
    private Object hologram;
    private long lastSeen;

    Jake(JakeData data) {
        this.data = data;
    }

    JakeData data() {
        return data;
    }

    void setData(JakeData data) {
        this.data = data;
    }

    UUID island() {
        return data.island();
    }

    /** Null tant que le monde n'est pas chargé. */
    Location location() {
        JakeData d = data;
        World world = Bukkit.getWorld(d.world());
        return world == null ? null : new Location(world, d.x(), d.y(), d.z(), d.yaw(), d.pitch());
    }

    Npc handle() {
        return handle;
    }

    void setHandle(Npc handle) {
        this.handle = handle;
    }

    Object hologram() {
        return hologram;
    }

    void setHologram(Object hologram) {
        this.hologram = hologram;
    }

    long lastSeen() {
        return lastSeen;
    }

    void seen(long now) {
        lastSeen = now;
    }

    void forget(UUID player) {
        if (handle != null) {
            handle.getIsVisibleForPlayer().remove(player);
            handle.getIsLookingAtPlayer().remove(player);
            handle.getIsTeamCreated().remove(player);
            handle.getLastPlayerInteraction().remove(player);
        }
    }
}
