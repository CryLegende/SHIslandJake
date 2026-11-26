package org.Cry_Legende.sHIslandJake;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Réactions en attente de la fermeture d'un menu : une seule par joueur (la dernière demandée), abandonnée après un
 * délai. Après une fermeture, elle part si aucun menu n'est rouvert quelques ticks plus tard. Les écritures se font sur
 * le fil principal ; {@link #contient} se lit depuis n'importe quel fil (tri avant de repasser sur le fil principal).
 *
 * @param <T> ce qui attend
 */
final class Attentes<T> {

    private record Attente<T>(T quoi, int jusqua) {
    }

    private final Map<UUID, Attente<T>> attentes = new ConcurrentHashMap<>();

    /** Remplace l'attente de ce joueur ; elle est abandonnée après « delai » ticks. */
    void demander(UUID joueur, T quoi, int maintenant, int delai) {
        attentes.put(joueur, new Attente<>(quoi, maintenant + Math.max(1, delai)));
    }

    boolean contient(UUID joueur) {
        return attentes.containsKey(joueur);
    }

    /**
     * Vérifie l'attente de ce joueur après une fermeture de menu : ce qui doit partir maintenant (et n'attend plus), ou
     * null (rien en attente, délai dépassé, ou un menu est de nouveau ouvert : elle attend la prochaine fermeture).
     */
    T verifier(UUID joueur, int maintenant, boolean menuOuvert) {
        Attente<T> a = attentes.get(joueur);
        if (a == null) {
            return null;
        }
        if (maintenant > a.jusqua()) {
            attentes.remove(joueur, a);
            return null;
        }
        if (menuOuvert) {
            return null;
        }
        attentes.remove(joueur, a);
        return a.quoi();
    }

    /** Retire les attentes dont le délai est dépassé. */
    void expirer(int maintenant) {
        attentes.values().removeIf(a -> maintenant > a.jusqua());
    }

    void oublier(UUID joueur) {
        attentes.remove(joueur);
    }
}
