package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttentesTest {

    private final UUID joueur = UUID.randomUUID();

    @Test
    void fermeturePuisDepart() {
        Attentes<String> a = new Attentes<>();
        a.demander(joueur, "joie", 100, 600);
        assertTrue(a.contient(joueur));
        // fermeture à 150, vérification 3 ticks plus tard, aucun menu : elle part
        assertEquals("joie", a.verifier(joueur, 153, false));
        assertFalse(a.contient(joueur));
        assertNull(a.verifier(joueur, 154, false));
    }

    @Test
    void menuRouvertDansLesTroisTicks() {
        Attentes<String> a = new Attentes<>();
        a.demander(joueur, "joie", 100, 600);
        // le joueur passe d'un menu à un autre : elle attend la fermeture suivante
        assertNull(a.verifier(joueur, 203, true));
        assertTrue(a.contient(joueur));
        assertEquals("joie", a.verifier(joueur, 303, false));
    }

    @Test
    void laDerniereDemandeeEtLeDelai() {
        Attentes<String> a = new Attentes<>();
        a.demander(joueur, "joie", 100, 600);
        a.demander(joueur, "tache", 110, 600);
        assertEquals("tache", a.verifier(joueur, 120, false));
        // délai dépassé : abandonnée
        a.demander(joueur, "joie", 1000, 600);
        assertNull(a.verifier(joueur, 1601, false));
        assertFalse(a.contient(joueur));
        a.demander(joueur, "joie", 2000, 600);
        a.expirer(2601);
        assertFalse(a.contient(joueur));
    }

    @Test
    void departDuJoueur() {
        Attentes<String> a = new Attentes<>();
        a.demander(joueur, "joie", 100, 600);
        a.oublier(joueur);
        assertFalse(a.contient(joueur));
        assertNull(a.verifier(joueur, 103, false));
    }
}
