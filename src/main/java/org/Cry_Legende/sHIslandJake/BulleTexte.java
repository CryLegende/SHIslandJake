package org.Cry_Legende.sHIslandJake;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Découpage d'une réplique en mots (écriture mot par mot) et durée de la bulle. Les codes couleur (&a, &#RRGGBB)
 * restent collés à leur mot : le début d'une phrase garde ses couleurs.
 */
final class BulleTexte {

    private static final Pattern CODES = Pattern.compile("(?i)[&§](#[0-9a-f]{6}|[0-9a-fk-or])");

    private BulleTexte() {
    }

    /** Les mots de la réplique, retours à la ligne gardés ; vide si elle ne contient rien. */
    static List<String> mots(String texte) {
        List<String> mots = new ArrayList<>();
        if (texte == null) {
            return mots;
        }
        StringBuilder mot = new StringBuilder();
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (c == ' ' || c == '\n') {
                if (c == '\n') {
                    mot.append('\n');
                }
                if (!visible(mot.toString()).isBlank()) {
                    mots.add(mot.toString());
                    mot.setLength(0);
                } else if (c == ' ' && !mot.isEmpty()) {
                    // codes couleur seuls : ils passent au mot suivant
                    continue;
                }
            } else {
                mot.append(c);
            }
        }
        if (!visible(mot.toString()).isBlank()) {
            mots.add(mot.toString());
        }
        return mots;
    }

    /** Les n premiers mots, tels qu'ils s'affichent. */
    static String debut(List<String> mots, int n) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(n, mots.size()); i++) {
            String mot = mots.get(i);
            out.append(mot);
            if (i + 1 < Math.min(n, mots.size()) && !mot.endsWith("\n")) {
                out.append(' ');
            }
        }
        return out.toString();
    }

    /** Texte sans les codes couleur. */
    static String visible(String texte) {
        return CODES.matcher(texte == null ? "" : texte).replaceAll("");
    }

    /**
     * Ticks pendant lesquels la bulle reste entière une fois écrite : base + par caractère × longueur visible
     * (espaces compris, retours à la ligne non).
     */
    static int resteTicks(String texte, double baseSecondes, double parCaractere) {
        int longueur = visible(texte).replace("\n", "").length();
        double secondes = Math.max(0, baseSecondes) + Math.max(0, parCaractere) * longueur;
        return (int) Math.round(secondes * 20);
    }

    /** Ticks pour écrire tous les mots : le premier arrive avec la bulle, puis un mot tous les « ticksParMot ». */
    static int ecritureTicks(int mots, int ticksParMot) {
        return ticksParMot <= 0 || mots <= 1 ? 0 : (mots - 1) * ticksParMot;
    }
}
