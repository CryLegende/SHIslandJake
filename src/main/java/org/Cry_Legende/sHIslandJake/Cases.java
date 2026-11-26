package org.Cry_Legende.sHIslandJake;

import java.util.ArrayList;
import java.util.List;

/**
 * Cases de la grille d'un monde : clé (x, z) de case → Jake de la case. Table à adressage ouvert sur des long, pour
 * qu'une recherche ne crée aucun objet (le suivi en fait plusieurs par joueur et par passage). Fil principal.
 */
final class Cases {

    private static final long VIDE = Long.MIN_VALUE;

    private long[] cles = new long[64];
    private Object[] valeurs = new Object[64];
    private int taille;

    Cases() {
        java.util.Arrays.fill(cles, VIDE);
    }

    @SuppressWarnings("unchecked")
    List<Jake> get(long cle) {
        int masque = cles.length - 1;
        for (int i = melange(cle) & masque; ; i = (i + 1) & masque) {
            long k = cles[i];
            if (k == VIDE) {
                return null;
            }
            if (k == cle) {
                return (List<Jake>) valeurs[i];
            }
        }
    }

    /** La liste de la case, créée si besoin. */
    @SuppressWarnings("unchecked")
    List<Jake> ouvrir(long cle) {
        List<Jake> existante = get(cle);
        if (existante != null) {
            return existante;
        }
        if ((taille + 1) * 2 > cles.length) {
            agrandir();
        }
        List<Jake> liste = new ArrayList<>(2);
        placer(cle, liste);
        taille++;
        return liste;
    }

    /** Retire la case (quand elle est vide). */
    void retirer(long cle) {
        int masque = cles.length - 1;
        int i = melange(cle) & masque;
        while (cles[i] != cle) {
            if (cles[i] == VIDE) {
                return;
            }
            i = (i + 1) & masque;
        }
        cles[i] = VIDE;
        valeurs[i] = null;
        taille--;
        // les suivantes de la même chaîne sont replacées
        for (int j = (i + 1) & masque; cles[j] != VIDE; j = (j + 1) & masque) {
            long k = cles[j];
            Object v = valeurs[j];
            cles[j] = VIDE;
            valeurs[j] = null;
            placer(k, v);
        }
    }

    int taille() {
        return taille;
    }

    private void placer(long cle, Object valeur) {
        int masque = cles.length - 1;
        int i = melange(cle) & masque;
        while (cles[i] != VIDE) {
            i = (i + 1) & masque;
        }
        cles[i] = cle;
        valeurs[i] = valeur;
    }

    private void agrandir() {
        long[] anciennesCles = cles;
        Object[] anciennesValeurs = valeurs;
        cles = new long[anciennesCles.length * 2];
        valeurs = new Object[anciennesCles.length * 2];
        java.util.Arrays.fill(cles, VIDE);
        for (int i = 0; i < anciennesCles.length; i++) {
            if (anciennesCles[i] != VIDE) {
                placer(anciennesCles[i], anciennesValeurs[i]);
            }
        }
    }

    private static int melange(long cle) {
        long h = cle * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }
}
