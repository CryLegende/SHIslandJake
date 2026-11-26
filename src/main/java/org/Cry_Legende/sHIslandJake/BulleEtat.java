package org.Cry_Legende.sHIslandJake;

/**
 * Où en est une bulle, en ticks du serveur : elle apparaît à l'échelle 0 avec son premier mot (tous ses mots si
 * « ticks-par-mot » vaut 0), grossit au tick suivant, s'écrit d'un mot tous les « ticks-par-mot », reste entière
 * « reste » ticks, rétrécit pendant « apparition » ticks, puis finit. Ne touche pas au serveur.
 */
final class BulleEtat {

    /** Ce qui est dû à un tick : grossir, montrer « mots » mots (-1 = rien de nouveau), rétrécir, finir. */
    record Pas(boolean grandir, int mots, boolean retrecir, boolean fin) {

        static final Pas RIEN = new Pas(false, -1, false, false);

        boolean rien() {
            return !grandir && mots < 0 && !retrecir && !fin;
        }
    }

    private final int total;
    private final int debut;
    private final int ticksParMot;
    private final int apparition;
    private final int reste;
    private int montres;
    private boolean grandie;
    private int finEcriture = -1;
    private int finRetrecie = -1;

    BulleEtat(int mots, int debut, int ticksParMot, int apparition, int reste) {
        this.total = Math.max(1, mots);
        this.debut = debut;
        this.ticksParMot = Math.max(0, ticksParMot);
        this.apparition = Math.max(0, apparition);
        this.reste = Math.max(0, reste);
        this.montres = this.ticksParMot == 0 ? total : 1;
        this.grandie = this.apparition == 0;
        if (montres == total) {
            finEcriture = debut;
        }
    }

    /** Mots montrés à l'apparition. */
    int premiers() {
        return ticksParMot == 0 ? total : 1;
    }

    /** Échelle à l'apparition : 0 si elle doit grossir, 1 sinon. */
    float echelleDepart() {
        return apparition == 0 ? 1f : 0f;
    }

    int montres() {
        return montres;
    }

    /** Durée totale prévue, en ticks : écriture, lecture et disparition. */
    int dureeTicks() {
        return BulleTexte.ecritureTicks(total, ticksParMot) + reste + apparition;
    }

    Pas avancer(int maintenant) {
        int age = maintenant - debut;
        boolean grandir = false;
        if (!grandie && age >= 1) {
            grandie = true;
            grandir = true;
        }
        int mots = -1;
        if (montres < total) {
            int dus = Math.min(total, 1 + age / Math.max(1, ticksParMot));
            if (dus > montres) {
                montres = dus;
                mots = dus;
                if (dus == total) {
                    finEcriture = maintenant;
                }
            }
        }
        boolean retrecir = false;
        if (finEcriture >= 0 && finRetrecie < 0 && maintenant >= finEcriture + reste) {
            retrecir = apparition > 0;
            finRetrecie = maintenant + apparition;
        }
        boolean fin = finRetrecie >= 0 && maintenant >= finRetrecie;
        return grandir || mots >= 0 || retrecir || fin ? new Pas(grandir, mots, retrecir, fin) : Pas.RIEN;
    }
}
