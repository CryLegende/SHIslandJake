package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ce qui permet de sauter le passage d'un joueur, et ce qui l'annule. */
class RaccourciTest {

    private static final double SEUIL = 0.25 * 0.25;
    private final Object monde = new Object();

    @Test
    void sauteQuandRienNeChange() {
        Raccourci r = new Raccourci();
        assertFalse(r.saute(monde, 0, 0, 0, 0, SEUIL), "jamais vu : passage complet");
        r.complet(monde, 10, 64, 10, 100);
        assertTrue(r.saute(monde, 10, 64, 10, 10, SEUIL));
        assertTrue(r.saute(monde, 10.2, 64, 10, 10, SEUIL), "0,2 bloc : immobile");
    }

    @Test
    void cequiAnnuleLeRaccourci() {
        Raccourci r = new Raccourci();
        r.complet(monde, 10, 64, 10, 100);
        assertFalse(r.saute(monde, 10.3, 64, 10, 10, SEUIL), "a bougé de plus de 0,25 bloc");
        assertFalse(r.saute(new Object(), 10, 64, 10, 10, SEUIL), "autre monde");
        assertFalse(r.saute(monde, 10, 64, 10, 100, SEUIL), "passage complet de sécurité dû");
        r.salir();
        assertFalse(r.saute(monde, 10, 64, 10, 10, SEUIL), "un Jake qu'il voit a changé");
        r.complet(monde, 10, 64, 10, 100);
        assertTrue(r.saute(monde, 10, 64, 10, 10, SEUIL), "après un passage complet, le raccourci revient");
        // un Jake pas encore dessiné : le suivant est prévu tout de suite
        r.complet(monde, 10, 64, 10, 20);
        assertFalse(r.saute(monde, 10, 64, 10, 20, SEUIL));
    }
}
