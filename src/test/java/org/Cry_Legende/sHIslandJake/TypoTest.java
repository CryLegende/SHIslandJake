package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypoTest {

    private static final String NB = " ";

    @Test
    void espacesDevenantInsecables() {
        assertEquals("Superbe, continuez comme ça" + NB + "!", Typo.insecables("Superbe, continuez comme ça !"));
        assertEquals("Prêt" + NB + "? Oui" + NB + ": allons-y" + NB + ";", Typo.insecables("Prêt ? Oui : allons-y ;"));
        assertEquals("«" + NB + "Bonjour" + NB + "»", Typo.insecables("« Bonjour »"));
        // plusieurs espaces : une seule insécable
        assertEquals("Bravo" + NB + "!", Typo.insecables("Bravo   !"));
        // avec des codes couleur
        assertEquals("&aBravo &e{joueur}" + NB + "!", Typo.insecables("&aBravo &e{joueur} !"));
    }

    @Test
    void dejaInsecableOuRienAFaire() {
        String deja = "Bravo" + NB + "!";
        assertEquals(deja, Typo.insecables(deja));
        assertEquals("Bravo!", Typo.insecables("Bravo!"));
        assertEquals("Sans ponctuation", Typo.insecables("Sans ponctuation"));
        assertEquals("", Typo.insecables(""));
        assertEquals(null, Typo.insecables(null));
    }

    @Test
    void laBulleGardeLePointAvecSonMot() {
        // la bulle s'écrit mot par mot : « ça ! » reste un seul mot
        List<String> mots = BulleTexte.mots(Typo.insecables("Superbe, continuez comme ça !"));
        assertEquals(List.of("Superbe,", "continuez", "comme", "ça" + NB + "!"), mots);
    }
}
