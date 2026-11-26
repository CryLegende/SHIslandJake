package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HauteurTextesTest {

    /** Pieds de Jake à y = 100 ; textes de 2,35 à 3,45 au-dessus (colonne 1,1). */
    private static final double PIEDS = 100;
    private static final double NORMAL = 2.35;
    private static final double COLONNE = 1.1;
    private static final double MINIMUM = 1.9;

    private static double bas(Set<Integer> pleins) {
        return HauteurTextes.bas(PIEDS, NORMAL, COLONNE, MINIMUM, pleins::contains);
    }

    @Test
    void rienAuDessus() {
        assertEquals(NORMAL, bas(Set.of()), 1e-9);
        // un bloc plus haut que les textes ne gêne pas
        assertEquals(NORMAL, bas(Set.of(104)), 1e-9);
    }

    @Test
    void feuillageQuiTouche() {
        // feuille en y = 103 (de 3 à 4 au-dessus des pieds) : le haut des textes passe à 2,9, le bas à 1,8 -> sous le minimum
        assertEquals(NORMAL, bas(Set.of(103)), 1e-9, "pas la place au-dessus du minimum : hauteur normale");
        // avec un minimum plus bas, ils descendent : haut à 103 - 0,1, bas 1,1 en dessous
        assertEquals(103 - 0.1 - COLONNE - PIEDS, HauteurTextes.bas(PIEDS, NORMAL, COLONNE, 1.5, Set.of(103)::contains), 1e-9);
    }

    @Test
    void plafondHaut() {
        // colonne de 0,8 : textes de 2,35 à 3,15, le bloc en y = 103 les touche ; haut à 2,9, bas à 2,1 (au-dessus du minimum)
        assertEquals(2.1, HauteurTextes.bas(PIEDS, NORMAL, 0.8, MINIMUM, Set.of(103)::contains), 1e-9);
        // le bloc le plus bas compte (103 et 104 pleins)
        assertEquals(2.1, HauteurTextes.bas(PIEDS, NORMAL, 0.8, MINIMUM, Set.of(103, 104)::contains), 1e-9);
        // colonne de 0,6 : textes de 2,35 à 2,95, le bloc en y = 103 ne les touche pas
        assertEquals(NORMAL, HauteurTextes.bas(PIEDS, NORMAL, 0.6, MINIMUM, Set.of(103)::contains), 1e-9);
    }

    @Test
    void blocAuNiveauDuBasDesTextes() {
        // un bloc en y = 102 (de 2 à 3) recouvre le bas des textes : ils devraient passer sous 1,9 -> normale
        assertEquals(NORMAL, bas(Set.of(102)), 1e-9);
    }
}
