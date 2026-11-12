package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Ce que SuperiorSkyblock2 dit des îles et de leurs membres.
 */
final class Islands {

    private final Dimension normal = normalDimension();

    /** Île du joueur, null s'il n'en a pas. */
    Island of(Player player) {
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
        return superiorPlayer == null ? null : superiorPlayer.getIsland();
    }

    /** Île du joueur hors ligne ou en ligne, par son identifiant. */
    Island of(UUID player) {
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
        return superiorPlayer == null ? null : superiorPlayer.getIsland();
    }

    Island byId(UUID island) {
        return SuperiorSkyblockAPI.getIslandByUUID(island);
    }

    Island at(Location location) {
        return SuperiorSkyblockAPI.getIslandAt(location);
    }

    int count() {
        return SuperiorSkyblockAPI.getGrid().getIslands().size();
    }

    /** Centre de l'île dans le monde normal. */
    Location center(Island island) {
        return island.getCenter(normal);
    }

    /** Chef de l'île, ou membre si « membres » le permet. */
    boolean canUse(Player player, Island island, boolean members) {
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
        if (superiorPlayer == null) {
            return false;
        }
        SuperiorPlayer owner = island.getOwner();
        if (owner != null && owner.getUniqueId().equals(player.getUniqueId())) {
            return true;
        }
        return members && island.isMember(superiorPlayer);
    }

    /** SSB2 2026.3 attend une Dimension pour trouver le centre d'une île. */
    private static Dimension normalDimension() {
        for (Dimension dimension : Dimension.values()) {
            if (dimension.getEnvironment() == World.Environment.NORMAL) {
                return dimension;
            }
        }
        return Dimension.getByName("NORMAL");
    }
}
