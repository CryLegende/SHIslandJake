package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RepliquesTest {

    @Test
    void jamaisDeuxFoisDeSuite() {
        Repliques r = new Repliques(new Random(42));
        UUID joueur = UUID.randomUUID();
        List<String> lignes = List.of("a", "b", "c");
        Set<String> vues = new HashSet<>();
        String avant = null;
        for (int i = 0; i < 500; i++) {
            String l = r.tirer(joueur, "defaut.bonjour", lignes);
            assertNotEquals(avant, l);
            vues.add(l);
            avant = l;
        }
        assertEquals(Set.of("a", "b", "c"), vues);
    }

    @Test
    void deuxLignesAlternent() {
        Repliques r = new Repliques(new Random(7));
        UUID joueur = UUID.randomUUID();
        String premiere = r.tirer(joueur, "x", List.of("a", "b"));
        for (int i = 0; i < 10; i++) {
            String suivante = r.tirer(joueur, "x", List.of("a", "b"));
            assertNotEquals(premiere, suivante);
            premiere = suivante;
        }
    }

    @Test
    void parJoueurEtParListe() {
        Repliques r = new Repliques(new Random(1));
        UUID a = UUID.randomUUID();
        // une seule ligne : toujours elle ; une liste vide : rien
        assertEquals("seule", r.tirer(a, "un", List.of("seule")));
        assertEquals("seule", r.tirer(a, "un", List.of("seule")));
        assertNull(r.tirer(a, "vide", List.of()));
        // une liste raccourcie depuis le dernier tirage (rechargement) ne casse rien
        r.tirer(a, "court", List.of("a", "b", "c", "d"));
        for (int i = 0; i < 20; i++) {
            r.tirer(a, "court", List.of("a", "b"));
        }
    }
}
