package org.Cry_Legende.sHIslandJake;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Qui est « près » de quel Jake (zone des réactions d'approche), gardé à part de ce que le joueur a reçu : un Jake
 * redessiné, un rechargement ou une vue refaite ne font pas croire à une arrivée. Une arrivée est un passage de dehors
 * à dedans, avec une marge pour sortir. Le premier passage d'un joueur « amorcé » (plugin chargé à chaud) note où il
 * est sans rien déclencher. Fil principal.
 */
final class Arrivees {

    /** Joueur → îles dont il est près. */
    private final Map<UUID, Set<UUID>> pres = new HashMap<>();
    private final Set<UUID> amorces = new HashSet<>();

    /**
     * Où en est ce joueur par rapport à ce Jake ; vrai s'il vient d'arriver.
     *
     * @param distance2 distance au carré entre le joueur et Jake
     * @param entree2   carré de la distance d'arrivée
     * @param sortie2   carré de la distance de sortie (un peu plus grande)
     */
    boolean passe(UUID joueur, UUID ile, double distance2, double entree2, double sortie2) {
        Set<UUID> mine = pres.get(joueur);
        boolean etait = mine != null && mine.contains(ile);
        if (!etait && distance2 <= entree2) {
            pres.computeIfAbsent(joueur, k -> new HashSet<>(2)).add(ile);
            return !amorces.contains(joueur);
        }
        if (etait && distance2 > sortie2) {
            mine.remove(ile);
            if (mine.isEmpty()) {
                pres.remove(joueur);
            }
        }
        return false;
    }

    /** Fin du passage de ce joueur : ses passages suivants déclenchent de nouveau. */
    void finPassage(UUID joueur) {
        amorces.remove(joueur);
    }

    /** Le prochain passage de ce joueur note sa place sans déclencher (chargement à chaud). */
    void amorcer(UUID joueur) {
        amorces.add(joueur);
    }

    /** Le joueur ne voit plus ce Jake (il s'est éloigné) : il n'est plus près. */
    void loin(UUID joueur, UUID ile) {
        Set<UUID> mine = pres.get(joueur);
        if (mine != null && mine.remove(ile) && mine.isEmpty()) {
            pres.remove(joueur);
        }
    }

    boolean estPres(UUID joueur, UUID ile) {
        Set<UUID> mine = pres.get(joueur);
        return mine != null && mine.contains(ile);
    }

    /** Jake déplacé ou île supprimée : plus personne n'est près de lui (un joueur près de sa nouvelle place y arrive). */
    void oublierIle(UUID ile) {
        pres.values().removeIf(mine -> mine.remove(ile) && mine.isEmpty());
    }

    /** Départ ou changement de monde : son retour sera une arrivée. */
    void oublier(UUID joueur) {
        pres.remove(joueur);
        amorces.remove(joueur);
    }
}
