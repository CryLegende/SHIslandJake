package org.Cry_Legende.sHIslandJake;

import java.util.regex.Pattern;

/**
 * Typographie française des textes au-dessus de Jake : l'espace devant « ! ? : ; » et derrière « devient insécable au
 * moment de l'affichage, pour qu'un retour à la ligne ne laisse pas un « ! » seul. Les fichiers gardent des espaces
 * normales. Ne touche pas au serveur.
 */
final class Typo {

    private static final char INSECABLE = ' ';
    private static final Pattern AVANT = Pattern.compile(" +(?=[!?:;»])");
    private static final Pattern APRES = Pattern.compile("(?<=«) +");

    private Typo() {
    }

    static String insecables(String texte) {
        if (texte == null || texte.isEmpty()) {
            return texte;
        }
        String out = AVANT.matcher(texte).replaceAll(String.valueOf(INSECABLE));
        return APRES.matcher(out).replaceAll(String.valueOf(INSECABLE));
    }
}
