package org.Cry_Legende.sHIslandJake;

import java.util.ArrayList;
import java.util.List;

/**
 * Cases des cartes d'un menu à la charte des menus de SkyHand : bord en haut, en bas et aux colonnes 0 et 8, cartes
 * dans les lignes du milieu, jamais plus de cinq par ligne. Une ligne de n cartes est centrée comme les sélecteurs
 * des cosmétiques : 1 → colonne 4 ; 2 → 3, 5 ; 3 → 2, 4, 6 ; 4 → 1, 3, 5, 7 ; 5 → 2 à 6. Les lignes prises sont
 * réparties dans la hauteur (une seule : celle du milieu). Une page de plus seulement si les cartes ne tiennent pas.
 */
final class DispositionCartes {

    static final int PAR_LIGNE = 5;
    private static final int[][] COLONNES = {{}, {4}, {3, 5}, {2, 4, 6}, {1, 3, 5, 7}, {2, 3, 4, 5, 6}};

    private DispositionCartes() {
    }

    /** Lignes du milieu d'un menu de « lignes » lignes (au moins une). */
    static int lignesDuMilieu(int lignes) {
        return Math.max(1, lignes - 2);
    }

    /** Cartes par page. */
    static int parPage(int lignes) {
        return lignesDuMilieu(lignes) * PAR_LIGNE;
    }

    static int pages(int cartes, int lignes) {
        return Math.max(1, (cartes + parPage(lignes) - 1) / parPage(lignes));
    }

    /** Cases des cartes de la page « page » (à partir de 0), dans l'ordre des cartes. */
    static List<Integer> cases(int cartes, int lignes, int page) {
        int parPage = parPage(lignes);
        int debut = page * parPage;
        int n = Math.max(0, Math.min(parPage, cartes - debut));
        List<Integer> out = new ArrayList<>(n);
        if (n == 0) {
            return out;
        }
        int milieu = lignesDuMilieu(lignes);
        int prises = (n + PAR_LIGNE - 1) / PAR_LIGNE;
        int premiere = lignes >= 3 ? 1 : 0;
        int reste = n;
        for (int i = 0; i < prises; i++) {
            int ligne = premiere + (prises == 1 ? (milieu - 1) / 2 : Math.round(i * (milieu - 1) / (float) (prises - 1)));
            // les premières lignes prennent une carte de plus quand le partage ne tombe pas juste
            int ici = (reste + (prises - i) - 1) / (prises - i);
            for (int colonne : COLONNES[ici]) {
                out.add(ligne * 9 + colonne);
            }
            reste -= ici;
        }
        return out;
    }
}
