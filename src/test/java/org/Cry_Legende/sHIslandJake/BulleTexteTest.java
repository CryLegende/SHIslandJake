package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulleTexteTest {

    @Test
    void decoupage() {
        assertEquals(List.of("Bonjour", "Cry", "!"), BulleTexte.mots("Bonjour  Cry !"));
        // les codes couleur restent collés au mot qui les suit
        List<String> mots = BulleTexte.mots("&aBravo &e&lCry&r, vous avez fini.");
        assertEquals(List.of("&aBravo", "&e&lCry&r,", "vous", "avez", "fini."), mots);
        assertEquals(List.of("&eBravo"), BulleTexte.mots("&e Bravo"));
        assertEquals(List.of("&#55ffaaVert"), BulleTexte.mots("&#55ffaaVert"));
        assertTrue(BulleTexte.mots("  &a ").isEmpty());
        assertTrue(BulleTexte.mots(null).isEmpty());
    }

    @Test
    void debutDePhrase() {
        List<String> mots = BulleTexte.mots("&aBravo &e&lCry&r, vous avez fini.");
        assertEquals("&aBravo", BulleTexte.debut(mots, 1));
        assertEquals("&aBravo &e&lCry&r,", BulleTexte.debut(mots, 2));
        assertEquals("&aBravo &e&lCry&r, vous avez fini.", BulleTexte.debut(mots, 99));
        // un retour à la ligne est gardé, sans espace en plus
        List<String> lignes = BulleTexte.mots("Bonjour\nCry");
        assertEquals(List.of("Bonjour\n", "Cry"), lignes);
        assertEquals("Bonjour\nCry", BulleTexte.debut(lignes, 2));
    }

    @Test
    void duree() {
        // 2,5 s + 0,06 s par caractère visible : « Bravo Cry ! » = 11 caractères → 3,16 s = 63 ticks
        assertEquals(63, BulleTexte.resteTicks("&aBravo &eCry !", 2.5, 0.06));
        assertEquals(50, BulleTexte.resteTicks("", 2.5, 0.06));
        assertEquals(0, BulleTexte.resteTicks("abc", -1, -1));
        assertEquals("Bravo Cry", BulleTexte.visible("&a&lBravo &#00ff00Cry"));
        // écriture : le premier mot arrive avec la bulle, puis un mot tous les 2 ticks
        assertEquals(8, BulleTexte.ecritureTicks(5, 2));
        assertEquals(0, BulleTexte.ecritureTicks(5, 0));
        assertEquals(0, BulleTexte.ecritureTicks(1, 2));
    }
}
