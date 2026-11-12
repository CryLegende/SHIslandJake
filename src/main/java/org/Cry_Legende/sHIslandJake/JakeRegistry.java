package org.Cry_Legende.sHIslandJake;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tous les Jake, retrouvables sans parcourir la liste : par île (lisible depuis tout fil, pour les
 * placeholders), par numéro d'entité (clic d'un joueur) et par position, dans une grille de cases de 32 blocs.
 * Hors lecture par île, tout se fait sur le fil principal.
 */
final class JakeRegistry {

    private static final int CELL_SHIFT = 5;

    private final Map<UUID, Jake> islands = new ConcurrentHashMap<>();
    private final Map<Integer, Jake> entities = new HashMap<>();
    private final Map<String, Map<Long, List<Jake>>> grid = new HashMap<>();

    Jake get(UUID island) {
        return island == null ? null : islands.get(island);
    }

    Collection<Jake> all() {
        return islands.values();
    }

    int size() {
        return islands.size();
    }

    /** Remplace le Jake que l'île avait déjà ; renvoie l'ancien. */
    Jake add(Jake jake) {
        Jake previous = islands.put(jake.island(), jake);
        if (previous != null) {
            unindex(previous);
        }
        index(jake);
        return previous;
    }

    void remove(Jake jake) {
        islands.remove(jake.island(), jake);
        unindex(jake);
    }

    /** Nouvelles données du Jake, la grille suit sa position. */
    void update(Jake jake, JakeData data) {
        unindex(jake);
        jake.setData(data);
        index(jake);
    }

    void link(int entityId, Jake jake) {
        entities.put(entityId, jake);
    }

    void unlink(int entityId) {
        entities.remove(entityId);
    }

    Jake byEntity(int entityId) {
        return entities.get(entityId);
    }

    /** Jake que FancyNpcs dessine en ce moment (handle créé). */
    Collection<Jake> drawn() {
        return entities.values();
    }

    void clear() {
        islands.clear();
        entities.clear();
        grid.clear();
    }

    /** Jake des cases autour d'une position, un peu au-delà de la distance demandée. */
    Set<Jake> near(String world, int blockX, int blockZ, int distance) {
        Map<Long, List<Jake>> cells = grid.get(world);
        if (cells == null) {
            return Set.of();
        }
        int minX = (blockX - distance) >> CELL_SHIFT;
        int maxX = (blockX + distance) >> CELL_SHIFT;
        int minZ = (blockZ - distance) >> CELL_SHIFT;
        int maxZ = (blockZ + distance) >> CELL_SHIFT;
        Set<Jake> near = null;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                List<Jake> cell = cells.get(key(x, z));
                if (cell != null) {
                    if (near == null) {
                        near = new LinkedHashSet<>();
                    }
                    near.addAll(cell);
                }
            }
        }
        return near == null ? Set.of() : near;
    }

    List<JakeData> snapshot() {
        List<JakeData> list = new ArrayList<>(islands.size());
        for (Jake jake : islands.values()) {
            list.add(jake.data());
        }
        return list;
    }

    private void index(Jake jake) {
        JakeData d = jake.data();
        grid.computeIfAbsent(d.world(), w -> new HashMap<>())
                .computeIfAbsent(cell(d), k -> new ArrayList<>(2))
                .add(jake);
    }

    private void unindex(Jake jake) {
        JakeData d = jake.data();
        Map<Long, List<Jake>> cells = grid.get(d.world());
        if (cells == null) {
            return;
        }
        long key = cell(d);
        List<Jake> cell = cells.get(key);
        if (cell != null) {
            cell.remove(jake);
            if (cell.isEmpty()) {
                cells.remove(key);
            }
        }
    }

    private static long cell(JakeData d) {
        return key((int) Math.floor(d.x()) >> CELL_SHIFT, (int) Math.floor(d.z()) >> CELL_SHIFT);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
