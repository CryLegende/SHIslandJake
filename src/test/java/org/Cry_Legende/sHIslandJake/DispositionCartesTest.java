package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispositionCartesTest {

    @Test
    void uneLigneCentreeCommeLesSelecteursDesCosmetiques() {
        assertEquals(List.of(22), DispositionCartes.cases(1, 5, 0));
        assertEquals(List.of(21, 23), DispositionCartes.cases(2, 5, 0));
        assertEquals(List.of(20, 22, 24), DispositionCartes.cases(3, 5, 0));
        assertEquals(List.of(19, 21, 23, 25), DispositionCartes.cases(4, 5, 0));
        assertEquals(List.of(20, 21, 22, 23, 24), DispositionCartes.cases(5, 5, 0));
    }

    @Test
    void plusieursLignesRepartiesDansLaHauteur() {
        // l'Aventurier et les six personnages à venir : 4 en haut, 3 en bas, ligne du milieu vide
        assertEquals(List.of(10, 12, 14, 16, 29, 31, 33), DispositionCartes.cases(7, 5, 0));
        assertEquals(List.of(11, 13, 15, 29, 31, 33), DispositionCartes.cases(6, 5, 0));
        List<Integer> onze = DispositionCartes.cases(11, 5, 0);
        assertEquals(List.of(10, 12, 14, 16, 19, 21, 23, 25, 29, 31, 33), onze);
        assertEquals(15, DispositionCartes.cases(15, 5, 0).size());
    }

    @Test
    void jamaisSurLeBordNiDeuxFoisLaMemeCase() {
        for (int lignes = 3; lignes <= 6; lignes++) {
            for (int n = 1; n <= DispositionCartes.parPage(lignes); n++) {
                List<Integer> cases = DispositionCartes.cases(n, lignes, 0);
                Set<Integer> vues = new HashSet<>(cases);
                assertEquals(n, vues.size(), n + " cartes sur " + lignes + " lignes");
                for (int c : cases) {
                    int ligne = c / 9;
                    int colonne = c % 9;
                    assertTrue(ligne > 0 && ligne < lignes - 1 && colonne > 0 && colonne < 8, "case " + c);
                }
            }
        }
    }

    @Test
    void unePageDePlusSeulementSiCaNeTientPas() {
        assertEquals(1, DispositionCartes.pages(0, 5));
        assertEquals(1, DispositionCartes.pages(15, 5));
        assertEquals(2, DispositionCartes.pages(16, 5));
        assertEquals(15, DispositionCartes.cases(17, 5, 0).size());
        assertEquals(List.of(21, 23), DispositionCartes.cases(17, 5, 1));
        assertTrue(DispositionCartes.cases(17, 5, 2).isEmpty());
    }
}
