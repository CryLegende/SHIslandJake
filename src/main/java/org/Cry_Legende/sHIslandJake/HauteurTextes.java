package org.Cry_Legende.sHIslandJake;

/**
 * Hauteur des textes au-dessus de Jake quand un bloc non traversable (le feuillage d'un arbre, un plafond) occupe leur
 * place : ils descendent pour tenir dessous, leur haut 0,1 bloc sous le bloc, sans passer sous une hauteur minimale ;
 * s'il n'y a pas la place, ils restent à leur hauteur normale. Ne touche pas au serveur : les blocs sont lus par
 * l'appelant.
 */
final class HauteurTextes {

    /** Écart entre le haut des textes et le dessous du bloc. */
    static final double MARGE = 0.1;

    /** Lit si le bloc à cette hauteur (y du bloc) est non traversable. */
    interface Blocs {
        boolean plein(int y);
    }

    private HauteurTextes() {
    }

    /**
     * Bas des textes au-dessus des pieds de Jake.
     *
     * @param pieds    y des pieds de Jake
     * @param normal   bas des textes au-dessus des pieds, sans obstacle
     * @param colonne  hauteur des textes (du bas du nom au haut de la parole)
     * @param minimum  bas des textes au-dessus des pieds, au plus bas
     */
    static double bas(double pieds, double normal, double colonne, double minimum, Blocs blocs) {
        double bas = pieds + normal;
        double haut = bas + colonne;
        // le bloc le plus bas qui touche la place des textes
        for (int y = (int) Math.floor(bas); y < haut; y++) {
            if (y + 1 > bas && blocs.plein(y)) {
                double nouveauBas = y - MARGE - colonne - pieds;
                return nouveauBas >= minimum ? Math.min(normal, nouveauBas) : normal;
            }
        }
        return normal;
    }
}
