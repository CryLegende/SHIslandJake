package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import static org.Cry_Legende.sHIslandJake.EssaisModele.Resultat.INCONNU;
import static org.Cry_Legende.sHIslandJake.EssaisModele.Resultat.MONTRE;
import static org.Cry_Legende.sHIslandJake.EssaisModele.Resultat.PLUS_TARD;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** « Plus tard », vingt essais, puis le secours et un nouvel essai plus tard. */
class EssaisModeleTest {

    @Test
    void plusTardPuisSecours() {
        int essais = 0;
        // le moteur lit le fichier : on redemande au tick suivant, vingt fois
        for (int i = 0; i < EssaisModele.MAX; i++) {
            assertEquals(EssaisModele.Suite.RELANCER, EssaisModele.suite(PLUS_TARD, essais));
            essais++;
        }
        // toujours rien : le corps de secours, le modèle sera réessayé plus tard
        assertEquals(EssaisModele.Suite.SECOURS, EssaisModele.suite(PLUS_TARD, essais));
        // nouvel essai (compteur remis à zéro) : le modèle se montre
        assertEquals(EssaisModele.Suite.MONTRE, EssaisModele.suite(MONTRE, 0));
    }

    @Test
    void inconnuOuMontre() {
        assertEquals(EssaisModele.Suite.SECOURS, EssaisModele.suite(INCONNU, 0));
        assertEquals(EssaisModele.Suite.MONTRE, EssaisModele.suite(MONTRE, 19));
        assertEquals(EssaisModele.Suite.RELANCER, EssaisModele.suite(PLUS_TARD, 0));
    }
}
