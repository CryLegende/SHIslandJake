package org.Cry_Legende.sHIslandJake;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pause de chaque réaction pour chaque joueur, en mémoire (remise à zéro au redémarrage). Fil principal. */
final class Pauses {

    private final Map<UUID, Map<String, Long>> jusqua = new HashMap<>();

    boolean libre(UUID joueur, String reaction, long maintenant) {
        Map<String, Long> mine = jusqua.get(joueur);
        Long fin = mine == null ? null : mine.get(reaction);
        return fin == null || maintenant >= fin;
    }

    void poser(UUID joueur, String reaction, long maintenant, long dureeMillis) {
        if (dureeMillis <= 0) {
            return;
        }
        jusqua.computeIfAbsent(joueur, k -> new HashMap<>()).put(reaction, maintenant + dureeMillis);
    }

    /** Retire les pauses finies (pour que la mémoire ne grossisse pas avec les joueurs passés). */
    void nettoyer(long maintenant) {
        jusqua.values().forEach(m -> m.values().removeIf(fin -> fin <= maintenant));
        jusqua.values().removeIf(Map::isEmpty);
    }
}
