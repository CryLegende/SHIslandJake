package org.Cry_Legende.sHIslandJake;

/**
 * Durée des tâches de Jake sur la dernière minute (/shislandjake stats). Une case par seconde et par tâche, réécrite
 * quand la minute tourne : deux lectures de l'horloge par passage, rien par joueur. Fil principal.
 */
final class Mesures {

    /** Tâches mesurées, dans l'ordre de l'affichage. */
    enum Tache {
        SUIVI("suivi"),
        REGARD("regard"),
        REACTIONS("reactions"),
        BULLES("bulles"),
        VIE("vie");

        final String cle;

        Tache(String cle) {
            this.cle = cle;
        }
    }

    /** Ce qu'une tâche a coûté sur la fenêtre : passages, nanosecondes en tout, passage le plus long. */
    record Bilan(long passages, long totalNanos, long maxNanos) {

        double moyenneMicros() {
            return passages == 0 ? 0 : totalNanos / 1000.0 / passages;
        }

        double maxMicros() {
            return maxNanos / 1000.0;
        }
    }

    static final int SECONDES = 60;

    private final long[][] passages = new long[Tache.values().length][SECONDES];
    private final long[][] totaux = new long[Tache.values().length][SECONDES];
    private final long[][] maxima = new long[Tache.values().length][SECONDES];
    private final long[][] secondes = new long[Tache.values().length][SECONDES];
    private final long depart = System.nanoTime();

    long debut() {
        return System.nanoTime();
    }

    void fin(Tache tache, long debut) {
        long now = System.nanoTime();
        ajouter(tache, now / 1_000_000_000L, now - debut);
    }

    /** Ajoute un passage d'une durée donnée à la seconde donnée (secondes de System.nanoTime). */
    void ajouter(Tache tache, long seconde, long nanos) {
        int t = tache.ordinal();
        int i = (int) Math.floorMod(seconde, (long) SECONDES);
        if (secondes[t][i] != seconde) {
            secondes[t][i] = seconde;
            passages[t][i] = 0;
            totaux[t][i] = 0;
            maxima[t][i] = 0;
        }
        passages[t][i]++;
        totaux[t][i] += nanos;
        if (nanos > maxima[t][i]) {
            maxima[t][i] = nanos;
        }
    }

    Bilan bilan(Tache tache) {
        return bilan(tache, System.nanoTime() / 1_000_000_000L);
    }

    /** Les SECONDES dernières secondes, celle en cours comprise. */
    Bilan bilan(Tache tache, long maintenant) {
        int t = tache.ordinal();
        long n = 0;
        long total = 0;
        long max = 0;
        for (int i = 0; i < SECONDES; i++) {
            long s = secondes[t][i];
            if (s > maintenant - SECONDES && s <= maintenant && passages[t][i] > 0) {
                n += passages[t][i];
                total += totaux[t][i];
                max = Math.max(max, maxima[t][i]);
            }
        }
        return new Bilan(n, total, max);
    }

    /** Secondes réellement couvertes : moins d'une minute juste après le démarrage. */
    double fenetreSecondes() {
        double ecoule = (System.nanoTime() - depart) / 1e9;
        return Math.max(1, Math.min(SECONDES, ecoule));
    }
}
