package org.Cry_Legende.sHIslandJake;

/**
 * Où était un joueur au dernier passage complet du suivi, et ce qui oblige à en refaire un : il a bougé, changé de
 * monde, un Jake qu'il voit a changé (« sale »), un Jake à portée n'a pas pu être dessiné, ou le passage complet de
 * sécurité est dû. L'alerte et l'appartenance à l'île se relisent sans passage complet. Ne touche pas au serveur.
 */
final class Raccourci {

    private Object monde;
    private double x;
    private double y;
    private double z;
    private boolean sale = true;
    private int prochainComplet;
    /** Ticks où relire l'alerte, l'appartenance à l'île. */
    int alerteA = Integer.MAX_VALUE;
    int membreA = Integer.MAX_VALUE;

    /** Vrai si le passage de ce joueur peut être sauté (seule l'ambiance, l'alerte et l'appartenance sont regardées). */
    boolean saute(Object monde, double x, double y, double z, int tick, double seuil2) {
        if (sale || monde != this.monde || tick >= prochainComplet) {
            return false;
        }
        double dx = x - this.x;
        double dy = y - this.y;
        double dz = z - this.z;
        return dx * dx + dy * dy + dz * dz < seuil2;
    }

    /** Après un passage complet : place notée, prochain passage complet au tick donné. */
    void complet(Object monde, double x, double y, double z, int prochainComplet) {
        this.monde = monde;
        this.x = x;
        this.y = y;
        this.z = z;
        this.prochainComplet = prochainComplet;
        this.sale = false;
    }

    /** Un Jake que ce joueur voit a changé : le prochain passage est complet. */
    void salir() {
        sale = true;
    }
}
