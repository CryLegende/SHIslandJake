package org.Cry_Legende.sHIslandJake;

import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * Ce qui est enregistré d'un Jake : son île, sa place, son type et s'il est visible. Immuable, donc lisible
 * depuis n'importe quel fil (placeholders, écriture du fichier).
 */
public record JakeData(UUID island, String world, double x, double y, double z, float yaw, float pitch,
                       EntityType type, boolean visible) {

    public JakeData withPosition(String world, double x, double y, double z, float yaw, float pitch) {
        return new JakeData(island, world, x, y, z, yaw, pitch, type, visible);
    }

    public JakeData withType(EntityType type) {
        return new JakeData(island, world, x, y, z, yaw, pitch, type, visible);
    }

    public JakeData withVisible(boolean visible) {
        return new JakeData(island, world, x, y, z, yaw, pitch, type, visible);
    }
}
