package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulleEtatTest {

    /** Ce que la bulle fait, tick par tick, jusqu'à sa fin. */
    private static List<String> deroule(BulleEtat e, int debut) {
        List<String> faits = new ArrayList<>();
        for (int t = debut; t < debut + 1000; t++) {
            BulleEtat.Pas p = e.avancer(t);
            if (p.grandir()) {
                faits.add(t + ":grandir");
            }
            if (p.mots() >= 0) {
                faits.add(t + ":mots" + p.mots());
            }
            if (p.retrecir()) {
                faits.add(t + ":retrecir");
            }
            if (p.fin()) {
                faits.add(t + ":fin");
                return faits;
            }
        }
        return faits;
    }

    @Test
    void motParMot() {
        // 5 mots, un tous les 2 ticks, apparition 3 ticks, 40 ticks de lecture
        BulleEtat e = new BulleEtat(5, 100, 2, 3, 40);
        assertEquals(1, e.premiers());
        assertEquals(0f, e.echelleDepart());
        assertEquals(8 + 40 + 3, e.dureeTicks());
        List<String> faits = deroule(e, 100);
        assertEquals(List.of("101:grandir", "102:mots2", "104:mots3", "106:mots4", "108:mots5",
                "148:retrecir", "151:fin"), faits);
    }

    @Test
    void dunCoupSansAnimation() {
        BulleEtat e = new BulleEtat(4, 0, 0, 0, 20);
        assertEquals(4, e.premiers());
        assertEquals(1f, e.echelleDepart());
        // rien à grossir ni à écrire : elle finit d'un coup après la lecture
        assertEquals(List.of("20:fin"), deroule(e, 0));
    }

    @Test
    void ticksEnRetard() {
        // un serveur en retard saute des ticks : les mots dus partent ensemble, rien ne se perd
        BulleEtat e = new BulleEtat(6, 0, 1, 2, 10);
        BulleEtat.Pas p = e.avancer(4);
        assertTrue(p.grandir());
        assertEquals(5, p.mots());
        p = e.avancer(5);
        assertEquals(6, p.mots());
        assertFalse(e.avancer(6).fin());
        assertTrue(e.avancer(15).retrecir());
        assertTrue(e.avancer(17).fin());
    }
}
