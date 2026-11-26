package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        a.reaction(j, 10, 60, 60, 300);
        assertFalse(a.enScene(j, 11), "la réaction a remplacé la scène");
        assertFalse(a.libre(j, 400), "pause après la réaction");
        assertTrue(a.libre(j, 460));
    }

    @Test
    void leGesteDuClicPasseDevantLaScene() {
        // le clic est déclaré comme une réaction : aucune scène ne le coupe au passage suivant
        Ambiance a = new Ambiance(new Random(2));
        UUID j = UUID.randomUUID();
        a.scene(j, "carte", 0, 160, 300);
        a.reaction(j, 20, 34, 34, 300);
        assertFalse(a.enScene(j, 21), "une réaction suivante ne croit plus à une scène en cours");
        assertFalse(a.libre(j, 30), "pas de scène pendant le geste du clic");
        // la pause la plus longue des deux (scène coupée, geste) est gardée
        assertFalse(a.libre(j, 459));
        assertTrue(a.libre(j, 460));
    }

    //VIE----------

    @Test
    void laVieSEnchaineSansTrou() {
        Ambiance a = new Ambiance(new Random(4));
        UUID j = UUID.randomUUID();
        List<String> vie = List.of("respire", "respire2");
        int t = 0;
        String avant = null;
        for (int i = 0; i < 50; i++) {
            assertEquals(Ambiance.Vie.JOUER, a.decider(j, t, true, true));
            String v = a.tirerVie(j, vie);
            assertNotEquals(avant, v, "jamais deux fois de suite la même");
            a.vie(j, v, t, 240);
            assertTrue(a.enVie(j, t + 239));
            // la suivante part au tick même où celle-ci finit
            assertFalse(a.enVie(j, t + 240));
            t += 240;
            avant = v;
        }
    }

    @Test
    void uneSeuleAnimationDeVieSeRepete() {
        Ambiance a = new Ambiance(new Random(5));
        UUID j = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            String v = a.tirerVie(j, List.of("respire"));
            assertEquals("respire", v);
            a.vie(j, v, i * 200, 200);
        }
        assertNull(a.tirerVie(j, List.of()));
    }

    @Test
    void sceneEtReactionPassentDevantLaVie() {
        Ambiance a = new Ambiance(new Random(6));
        UUID j = UUID.randomUUID();
        a.vie(j, "respire", 0, 240);
        // une scène à 100 remplace la vie ; à la fin prévue de la vie (240), on attend la fin de la scène (100 + 160)
        a.scene(j, "carte", 100, 160, 300);
        assertFalse(a.enVie(j, 101), "la scène a remplacé la vie");
        assertEquals(Ambiance.Vie.ATTENDRE, a.decider(j, 240, true, true));
        assertEquals(260, a.reprise(j));
        assertEquals(Ambiance.Vie.JOUER, a.decider(j, 260, true, true));
        a.vie(j, "respire2", 260, 240);
        // le geste d'une réaction (34 ticks) remplace la vie ; elle reprend à sa fin
        a.reaction(j, 300, 34, 34, 300);
        assertFalse(a.enVie(j, 301));
        assertEquals(Ambiance.Vie.ATTENDRE, a.decider(j, 320, true, true));
        assertEquals(334, a.reprise(j));
        assertEquals(Ambiance.Vie.JOUER, a.decider(j, 334, true, true));
        // une réaction sans geste (bulle seule) laisse la vie jouer
        a.vie(j, "respire", 334, 240);
        a.reaction(j, 400, 80, 0, 300);
        assertTrue(a.enVie(j, 401));
        assertEquals(Ambiance.Vie.JOUER, a.decider(j, 574, true, true));
    }

    @Test
    void uneVieLongueInterrompueTotReprendALaFinDuGeste() {
        // respire (252 ticks) coupé à 10 par le clic (12 ticks) : la vie reprend à 22, pas à 252
        Ambiance a = new Ambiance(new Random(11));
        UUID j = UUID.randomUUID();
        a.vie(j, "respire", 0, 252);
        a.reaction(j, 10, 12, 12, 300);
        assertEquals(22, a.reprise(j));
        assertEquals(Ambiance.Vie.ATTENDRE, a.decider(j, 21, true, true));
        assertEquals(Ambiance.Vie.JOUER, a.decider(j, 22, true, true));
        // puis une scène de 160 ticks coupe la vie suivante à 40 : reprise à 200
        a.vie(j, "respire2", 22, 280);
        a.scene(j, "carte", 40, 160, 300);
        assertEquals(200, a.reprise(j));
        assertEquals(Ambiance.Vie.JOUER, a.decider(j, 200, true, true));
    }

    @Test
    void unGesteQuiRemplaceUneSceneRendLaVieASaFin() {
        Ambiance a = new Ambiance(new Random(7));
        UUID j = UUID.randomUUID();
        a.scene(j, "carte", 0, 160, 300);
        a.reaction(j, 10, 60, 20, 300);
        assertEquals(30, a.reprise(j), "la scène coupée ne retient plus la vie");
        assertEquals(Ambiance.Vie.JOUER, a.decider(j, 30, true, true));
    }

    @Test
    void sceneArreteeLaVieRepartTouteDeSuite() {
        Ambiance a = new Ambiance(new Random(8));
        UUID j = UUID.randomUUID();
        a.scene(j, "sac", 0, 160, 300);
        assertEquals(Ambiance.Vie.ATTENDRE, a.decider(j, 50, true, true));
        a.sceneArretee(j, 50);
        assertFalse(a.enScene(j, 51));
        assertEquals(Ambiance.Vie.JOUER, a.decider(j, 50, true, true));
        // la pause des scènes continue de compter
        assertFalse(a.libre(j, 200));
    }

    @Test
    void detourneOuDiscretRienNEstRelance() {
        Ambiance a = new Ambiance(new Random(9));
        UUID j = UUID.randomUUID();
        a.vie(j, "respire", 0, 240);
        // à la fin de l'animation, le joueur ne regarde plus Jake : rien
        assertEquals(Ambiance.Vie.ARRETER, a.decider(j, 240, false, true));
        a.finVie(j);
        assertFalse(a.enVie(j, 241));
        // même pendant une scène : il s'est détourné, rien n'attend sa fin
        a.scene(j, "carte", 300, 160, 300);
        assertEquals(Ambiance.Vie.ARRETER, a.decider(j, 310, false, true));
        // discret sans vie-si-discret : la vie ne joue pas
        assertEquals(Ambiance.Vie.ARRETER, a.decider(j, 500, true, false));
        // il regarde de nouveau : ça repart
        assertEquals(Ambiance.Vie.JOUER, a.decider(j, 500, true, true));
    }

    @Test
    void lAnimationDeVieNeTouchePasALaPauseDesScenes() {
        Ambiance a = new Ambiance(new Random(10));
        UUID j = UUID.randomUUID();
        a.scene(j, "carte", 0, 100, 300);
        a.vie(j, "respire", 100, 240);
        a.vie(j, "respire2", 340, 240);
        assertTrue(a.libre(j, 400), "la pause s'est écoulée pendant la vie");
        assertTrue(a.enVie(j, 400));
    }
}
