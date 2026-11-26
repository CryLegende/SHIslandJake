package org.Cry_Legende.sHIslandJake;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * « Jake discret », réglage de chaque joueur gardé sur le joueur (shislandjake:discret) : ni bulle, ni geste, ni son ;
 * l'alerte reste. Lu au départ dans une liste en mémoire, que les placeholders lisent depuis n'importe quel fil.
 */
final class Discret {

    private final NamespacedKey cle;
    private final Set<UUID> discrets = ConcurrentHashMap.newKeySet();

    Discret(SHIslandJake plugin) {
        this.cle = new NamespacedKey(plugin, "discret");
    }

    /** Arrivée du joueur (fil principal) : son réglage est relu. */
    void charger(Player player) {
        Byte valeur = player.getPersistentDataContainer().get(cle, PersistentDataType.BYTE);
        if (valeur != null && valeur != 0) {
            discrets.add(player.getUniqueId());
        } else {
            discrets.remove(player.getUniqueId());
        }
    }

    void oublier(UUID player) {
        discrets.remove(player);
    }

    boolean est(UUID player) {
        return discrets.contains(player);
    }

    /** Change le réglage ; renvoie le nouvel état (vrai = discret). Fil principal. */
    boolean basculer(Player player) {
        boolean discret = !est(player.getUniqueId());
        if (discret) {
            player.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) 1);
            discrets.add(player.getUniqueId());
        } else {
            player.getPersistentDataContainer().remove(cle);
            discrets.remove(player.getUniqueId());
        }
        return discret;
    }
}
