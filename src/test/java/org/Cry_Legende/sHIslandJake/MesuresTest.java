package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MesuresTest {

    @Test
    void derniereMinute() {
        Mesures m = new Mesures();
        m.ajouter(Mesures.Tache.SUIVI, 1000, 100_000);
        m.ajouter(Mesures.Tache.SUIVI, 1000, 300_000);
        m.ajouter(Mesures.Tache.SUIVI, 1030, 50_000);
        m.ajouter(Mesures.Tache.REGARD, 1030, 7_000);
        Mesures.Bilan b = m.bilan(Mesures.Tache.SUIVI, 1030);
        assertEquals(3, b.passages());
        assertEquals(450_000, b.totalNanos());
        assertEquals(300.0, b.maxMicros(), 1e-9);
        assertEquals(150.0, b.moyenneMicros(), 1e-9);
        // une minute plus tard, la seconde 1000 est sortie de la fenêtre
        b = m.bilan(Mesures.Tache.SUIVI, 1060);
        assertEquals(1, b.passages());
        // la case de la seconde 1000 est réécrite par la seconde 1060
        m.ajouter(Mesures.Tache.SUIVI, 1060, 10_000);
        assertEquals(2, m.bilan(Mesures.Tache.SUIVI, 1060).passages());
        assertEquals(1, m.bilan(Mesures.Tache.REGARD, 1060).passages());
        assertEquals(0, m.bilan(Mesures.Tache.BULLES, 1060).passages());
    }
}
