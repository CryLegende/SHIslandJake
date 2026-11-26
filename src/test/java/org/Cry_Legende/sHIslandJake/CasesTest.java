package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CasesTest {

    private static long cle(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Test
    void ouvrirLireRetirer() {
        Cases c = new Cases();
        assertNull(c.get(cle(0, 0)));
        List<Jake> l = c.ouvrir(cle(3, -7));
        assertSame(l, c.ouvrir(cle(3, -7)));
        assertSame(l, c.get(cle(3, -7)));
        assertNull(c.get(cle(-7, 3)));
        c.retirer(cle(3, -7));
        assertNull(c.get(cle(3, -7)));
        assertEquals(0, c.taille());
    }

    @Test
    void commeUneTable() {
        // mêmes résultats qu'une HashMap sur beaucoup de clés, avec des retraits au milieu des chaînes
        Cases c = new Cases();
        Map<Long, List<Jake>> ref = new HashMap<>();
        Random r = new Random(9);
        for (int i = 0; i < 5000; i++) {
            long k = cle(r.nextInt(200) - 100, r.nextInt(200) - 100);
            if (r.nextInt(4) == 0) {
                c.retirer(k);
                ref.remove(k);
            } else {
                List<Jake> l = c.ouvrir(k);
                ref.putIfAbsent(k, l);
                assertSame(ref.get(k), l);
            }
        }
        assertEquals(ref.size(), c.taille());
        for (int x = -100; x < 100; x++) {
            for (int z = -100; z < 100; z++) {
                assertSame(ref.get(cle(x, z)), c.get(cle(x, z)));
            }
        }
    }
}
