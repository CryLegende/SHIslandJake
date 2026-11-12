package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Déplacement de Jake : après /jake deplacer, le prochain clic du joueur sur un bloc de son île y pose Jake,
 * tourné vers lui. Les sessions sont lues depuis le fil du monde du joueur (événement de clic), modifiées
 * sur le fil principal.
 */
final class MoveSessions {

    private record Session(UUID island, long token) {
    }

    private final SHIslandJake plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private long nextToken;

    MoveSessions(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    boolean isMoving(UUID player) {
        return sessions.containsKey(player);
    }

    void start(Player player, Jake jake) {
        String name = plugin.messages().jakeName(jake.data().type());
        if (sessions.containsKey(player.getUniqueId())) {
            plugin.messages().send(player, "deplacement-deja", "nom", name);
            return;
        }
        long token = ++nextToken;
        sessions.put(player.getUniqueId(), new Session(jake.island(), token));
        int seconds = plugin.settings().moveSeconds;
        plugin.messages().send(player, "deplacement-debut", "nom", name, "secondes", String.valueOf(seconds));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session session = sessions.get(player.getUniqueId());
            if (session != null && session.token() == token) {
                sessions.remove(player.getUniqueId());
                if (player.isOnline()) {
                    plugin.messages().send(player, "deplacement-temps", "nom", name);
                }
            }
        }, seconds * 20L);
    }

    /** Changement de monde : le déplacement s'arrête, avec un message. */
    void cancel(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            Jake jake = plugin.registry().get(session.island());
            String name = plugin.messages().jakeName(jake == null ? plugin.settings().defaultType : jake.data().type());
            plugin.messages().send(player, "deplacement-annule", "nom", name);
        }
    }

    void forget(UUID player) {
        sessions.remove(player);
    }

    void clear() {
        sessions.clear();
    }

    /** Clic sur un bloc pendant le déplacement (fil principal). */
    void click(Player player, Block block) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !player.isOnline()) {
            return;
        }
        Jake jake = plugin.registry().get(session.island());
        Island island = plugin.islands().byId(session.island());
        if (jake == null || island == null || !plugin.islands().canUse(player, island, true)) {
            sessions.remove(player.getUniqueId());
            return;
        }
        Block feet = block.getRelative(BlockFace.UP);
        Location target = feet.getLocation().add(0.5, 0, 0.5);
        if (!island.isInsideRange(target)) {
            plugin.sound(player, plugin.settings().soundRefusal);
            plugin.messages().send(player, "deplacement-hors-ile");
            return;
        }
        if (!feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
            plugin.sound(player, plugin.settings().soundRefusal);
            plugin.messages().send(player, "deplacement-place");
            return;
        }
        sessions.remove(player.getUniqueId());
        plugin.manager().move(jake, target, facing(target, player.getLocation()));
        plugin.sound(player, plugin.settings().soundSuccess);
        plugin.messages().send(player, "deplace", "nom", plugin.messages().jakeName(jake.data().type()));
    }

    /** Orientation de Jake posé en « from » pour regarder vers « to ». */
    private static float facing(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        if (direction.getX() == 0 && direction.getZ() == 0) {
            return to.getYaw() + 180f;
        }
        Location look = from.clone();
        look.setDirection(direction);
        return look.getYaw();
    }
}
