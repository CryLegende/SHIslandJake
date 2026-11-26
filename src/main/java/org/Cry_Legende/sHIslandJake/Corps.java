package org.Cry_Legende.sHIslandJake;

import org.bukkit.entity.Player;

/**
 * Ce qui dessine Jake : le PNJ de FancyNpcs (corps de secours) ou un modèle de BlockDisplayCreator. Fil principal.
 */
interface Corps {

    /** Crée le dessin s'il manque ; faux si c'est impossible ici (modèle introuvable, moteur absent). */
    boolean montrer(Jake jake);

    /** Le retire à tous. */
    void retirer(Jake jake);

    /** Ce joueur le voit ou plus (le moteur des modèles gère seul ses spectateurs). */
    void voir(Jake jake, Player joueur, boolean visible);

    /** Animation pour ce joueur seul ; faux si elle n'existe pas. */
    boolean jouer(Jake jake, String animation, Player pour);

    /** Encore dessiné ; faux si le dessin a été lâché (chunk déchargé, moteur rechargé...). */
    boolean vivant(Jake jake);
}
