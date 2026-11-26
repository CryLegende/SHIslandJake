package org.Cry_Legende.sHIslandJake;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tous les Jake, retrouvables sans parcourir la liste : par île (lisible depuis tout fil, pour les
 * placeholders), par numéro d'entité (clic d'un joueur sur le PNJ ou sur la zone d'un modèle) et par position, dans
 * une grille de cases de 32 blocs. Hors lecture par île, tout se fait sur le fil principal.
 */
final class JakeRegistry {

    private static final int CELL_SHIFT = 5;

    private final Map<UUID, Jake> islands = new ConcurrentHashMap<>();
    private final Map<Integer, Jake> entities = new HashMap<>();
    private final Map<String, Map<Long, List<Jake>>> grid = new HashMap<>();
    private final Set<Jake> drawn = new HashSet<>();

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
            if (previous.numerote()) {
                entities.remove(previous.zoneId(), previous);
            }
        }
        index(jake);
        return previous;
    }

    void remove(Jake jake) {
        islands.remove(jake.island(), jake);
        unindex(jake);
        if (jake.numerote()) {
            entities.remove(jake.zoneId(), jake);
        }
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

    /** Jake dessinés en ce moment (par FancyNpcs ou par BlockDisplayCreator). */
    Collection<Jake> drawn() {
        return drawn;
    }

    void markDrawn(Jake jake, boolean drawnNow) {
        if (drawnNow) {
            drawn.add(jake);
        } else {
            drawn.remove(jake);
        }
    }

    /**
     * Jake des cases autour d'une position, un peu au-delà de la distance demandée, ajoutés à « out » (vidée d'abord).
     * Un Jake n'est que dans une case : pas de doublon.
     */
    void near(String world, int blockX, int blockZ, int distance, List<Jake> out) {
        out.clear();
        Map<Long, List<Jake>> cells = grid.get(world);
        if (cells == null) {
            return;
        }
        int minX = (blockX - distance) >> CELL_SHIFT;
        int maxX = (blockX + distance) >> CELL_SHIFT;
        int minZ = (blockZ - distance) >> CELL_SHIFT;
        int maxZ = (blockZ + distance) >> CELL_SHIFT;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                List<Jake> cell = cells.get(key(x, z));
                if (cell != null) {
                    out.addAll(cell);
                }
            }
        }
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
