package org.Cry_Legende.sHIslandJake;

/**
 * Ce que l'on fait d'une demande de dessin d'un modèle : le moteur peut répondre plus tard (fichier pas encore lu), et
 * l'on redemande alors au tick suivant, au plus {@link #MAX} fois avant de prendre le corps de secours (qui réessaiera
 * le modèle plus tard). Ne touche pas au serveur.
 */
final class EssaisModele {

    /** Réponse du moteur à une demande de dessin. */
    enum Resultat {
        MONTRE,
        /** Le moteur connaît le modèle mais ne l'a pas encore lu : réessayer. */
        PLUS_TARD,
        /** Modèle inconnu du moteur, ou moteur absent. */
        INCONNU
    }

    /** Suite à donner. */
    enum Suite {
        MONTRE,
        /** Redemander au tick suivant. */
        RELANCER,
        /** Corps de secours, nouvel essai du modèle plus tard. */
        SECOURS
    }

    /** Demandes sans réponse avant de prendre le corps de secours. */
    static final int MAX = 20;

    private EssaisModele() {
    }

    /**
     * @param essais demandes restées sans réponse avant celle-ci
     */
    static Suite suite(Resultat r, int essais) {
        return switch (r) {
            case MONTRE -> Suite.MONTRE;
            case PLUS_TARD -> essais < MAX ? Suite.RELANCER : Suite.SECOURS;
            case INCONNU -> Suite.SECOURS;
        };
    }
}
