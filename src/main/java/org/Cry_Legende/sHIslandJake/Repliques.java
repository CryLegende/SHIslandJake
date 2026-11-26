package org.Cry_Legende.sHIslandJake;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Tirage d'une réplique dans une liste : au hasard, jamais deux fois de suite la même pour un même joueur et une même
 * liste. En mémoire seulement. Fil principal.
 */
final class Repliques {

    private final Map<UUID, Map<String, Integer>> dernieres = new HashMap<>();
    private final Random hasard;

    Repliques(Random hasard) {
        this.hasard = hasard;
    }

    /** Null si la liste est vide. */
    String tirer(UUID joueur, String liste, List<String> lignes) {
        if (lignes.isEmpty()) {
            return null;
        }
        Map<String, Integer> mine = dernieres.computeIfAbsent(joueur, k -> new HashMap<>());
        Integer derniere = mine.get(liste);
        int index;
        if (lignes.size() == 1) {
            index = 0;
        } else if (derniere == null || derniere >= lignes.size()) {
            index = hasard.nextInt(lignes.size());
        } else {
            // un tirage parmi les autres lignes : jamais la même que la dernière
            index = hasard.nextInt(lignes.size() - 1);
            if (index >= derniere) {
                index++;
            }
        }
        mine.put(liste, index);
        return lignes.get(index);
    }

    void oublier(UUID joueur) {
        dernieres.remove(joueur);
    }
}
