package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbianceTest {

    private static final double COS70 = Math.cos(Math.toRadians(70));

    @Test
    void devantOuDerriere() {
        // joueur en 0 0 0 regardant vers le sud (+z, yaw 0), Jake 5 blocs au sud
        assertTrue(Ambiance.devant(0, 1.6, 0, 0f, 0f, 0, 1, 5, COS70));
        // dos tourné (yaw 180 : vers le nord)
        assertFalse(Ambiance.devant(0, 1.6, 0, 180f, 0f, 0, 1, 5, COS70));
        // de côté à 60° : dans l'angle ; à 80° : dehors
        assertTrue(Ambiance.devant(0, 1.6, 0, 60f, 0f, 0, 1.6, 5, COS70));
        assertFalse(Ambiance.devant(0, 1.6, 0, 80f, 0f, 0, 1.6, 5, COS70));
        // yaw 90 regarde vers l'ouest (-x)
        assertTrue(Ambiance.devant(0, 1.6, 0, 90f, 0f, -5, 1.6, 0, COS70));
        // regard vers le ciel (pitch -90) : Jake n'est pas devant
        assertFalse(Ambiance.devant(0, 1.6, 0, 0f, -90f, 0, 1, 5, COS70));
    }

    @Test
    void tirageEtPauses() {
        Ambiance a = new Ambiance(new Random(3));
        UUID j = UUID.randomUUID();
        List<String> scenes = List.of("carte", "sac", "somnole");
        assertTrue(a.libre(j, 0));
        String avant = null;
        int t = 0;
        for (int i = 0; i < 200; i++) {
            String s = a.tirer(j, scenes);
            assertNotEquals(avant, s);
            a.scene(j, s, t, 100, 300);
            assertTrue(a.enScene(j, t + 50));
            assertFalse(a.libre(j, t + 399));
            t += 400;
            assertTrue(a.libre(j, t));
            avant = s;
        }
        assertEquals("seule", a.tirer(j, List.of("seule")));
        int p = a.pause(300, 600);
        assertTrue(p >= 300 && p <= 600);
        assertEquals(300, a.pause(300, 300));
    }

    @Test
    void uneReactionPasseDevant() {
        Ambiance a = new Ambiance(new Random(1));
        UUID j = UUID.randomUUID();
        a.scene(j, "carte", 0, 160, 300);
        assertTrue(a.enScene(j, 10));
        a.reaction(j, 10, 60, 300);
        assertFalse(a.enScene(j, 11), "la réaction a remplacé la scène");
        assertFalse(a.libre(j, 400), "pause après la réaction");
        assertTrue(a.libre(j, 460));
    }
}
