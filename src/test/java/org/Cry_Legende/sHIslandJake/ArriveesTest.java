package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArriveesTest {

    private static final double ENTREE = 6 * 6;
    private static final double SORTIE = 7 * 7;
    private final UUID joueur = UUID.randomUUID();
    private final UUID ile = UUID.randomUUID();

    @Test
    void hysteresis() {
        Arrivees a = new Arrivees();
        assertFalse(a.passe(joueur, ile, 10 * 10, ENTREE, SORTIE), "loin : rien");
        assertTrue(a.passe(joueur, ile, 5 * 5, ENTREE, SORTIE), "arrivée");
        assertFalse(a.passe(joueur, ile, 4 * 4, ENTREE, SORTIE), "reste près : rien");
        // entre 6 et 7 blocs : toujours près, puis de retour à 5 blocs, pas de nouvelle arrivée
        assertFalse(a.passe(joueur, ile, 6.5 * 6.5, ENTREE, SORTIE));
        assertFalse(a.passe(joueur, ile, 5 * 5, ENTREE, SORTIE));
        // au-delà de 7 blocs : sorti ; le retour est une arrivée
        assertFalse(a.passe(joueur, ile, 7.5 * 7.5, ENTREE, SORTIE));
        assertTrue(a.passe(joueur, ile, 5.9 * 5.9, ENTREE, SORTIE));
    }

    @Test
    void vueRefaiteSansMouvement() {
        Arrivees a = new Arrivees();
        assertTrue(a.passe(joueur, ile, 3 * 3, ENTREE, SORTIE));
        // Jake redessiné, rechargement, réapparition : la vue est refaite mais l'état « près » reste
        assertFalse(a.passe(joueur, ile, 3 * 3, ENTREE, SORTIE));
        assertTrue(a.estPres(joueur, ile));
        // le joueur s'éloigne hors de vue puis revient : arrivée
        a.loin(joueur, ile);
        assertTrue(a.passe(joueur, ile, 3 * 3, ENTREE, SORTIE));
        // changement de monde : son retour est une arrivée
        a.oublier(joueur);
        assertTrue(a.passe(joueur, ile, 3 * 3, ENTREE, SORTIE));
    }

    @Test
    void chargementAChaud() {
        Arrivees a = new Arrivees();
        a.amorcer(joueur);
        UUID autre = UUID.randomUUID();
        // premier passage : place notée, rien déclenché (deux Jake vus dans le même passage)
        assertFalse(a.passe(joueur, ile, 3 * 3, ENTREE, SORTIE));
        assertFalse(a.passe(joueur, autre, 4 * 4, ENTREE, SORTIE));
        a.finPassage(joueur);
        assertFalse(a.passe(joueur, ile, 3 * 3, ENTREE, SORTIE));
        assertFalse(a.passe(joueur, ile, 8 * 8, ENTREE, SORTIE));
        assertTrue(a.passe(joueur, ile, 3 * 3, ENTREE, SORTIE), "après l'amorce, une vraie arrivée déclenche");
    }
}
