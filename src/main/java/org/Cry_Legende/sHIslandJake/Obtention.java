package org.Cry_Legende.sHIslandJake;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Obtention et usage d'une apparence (types.&lt;ID&gt;.permission, types.&lt;ID&gt;.obtention, apparences.permission-usage),
 * sans toucher au serveur. Une apparence sans obtention (ni droit ni condition) est à tout le monde ; une apparence
 * avec obtention s'obtient en jouant (droit et conditions vrais) et ne s'utilise qu'avec le droit d'usage.
 */
final class Obtention {

    private Obtention() {
    }

    /**
     * @param permission droit d'obtention, vide = aucun
     * @param conditions conditions d'obtention (écriture des « si » des réactions), toutes vraies
     * @param droit      le joueur a-t-il ce droit ?
     * @param lecteur    placeholders du joueur
     * @param usage      droit d'usage (apparences.permission-usage), vide = l'obtention suffit
     */
    static Settings.Etat etat(String permission, List<String> conditions, Predicate<String> droit,
                              UnaryOperator<String> lecteur, String usage) {
        if (permission.isEmpty() && conditions.isEmpty()) {
            return Settings.Etat.DISPONIBLE;
        }
        boolean obtenue = (permission.isEmpty() || droit.test(permission)) && Condition.toutes(conditions, lecteur);
        if (!obtenue) {
            return Settings.Etat.NON_OBTENUE;
        }
        return usage.isEmpty() || droit.test(usage) ? Settings.Etat.DISPONIBLE : Settings.Etat.RESERVE;
    }

    /**
     * Le nombre à atteindre : côté droit de la première condition « valeur >= N », « valeur > N » ou « valeur == N »
     * (valeur vide = la première condition de cette forme dont le côté gauche a un placeholder). Vide sinon.
     */
    static OptionalDouble objectif(List<String> conditions, String valeur) {
        for (String c : conditions) {
            String[] p = Condition.parties(c);
            if (p == null || !(p[1].equals(">=") || p[1].equals(">") || p[1].equals("=="))) {
                continue;
            }
            boolean gauche = valeur.isEmpty() ? p[0].indexOf('%') >= 0 : p[0].equals(valeur.trim());
            OptionalDouble n = Num.parse(p[2]);
            if (gauche && n.isPresent()) {
                return n;
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Ligne {reserve} du bouton Apparence des réglages, selon l'état des autres apparences proposées : aucune (pas
     * d'autre apparence, ou une autre est utilisable), « reserve » (une autre est obtenue mais réservée aux grades),
     * sinon « a-obtenir » (les autres restent à obtenir en jouant).
     */
    static String ligneAutres(List<Settings.Etat> autres) {
        if (autres.isEmpty() || autres.contains(Settings.Etat.DISPONIBLE)) {
            return null;
        }
        return autres.contains(Settings.Etat.RESERVE) ? "reserve" : "a-obtenir";
    }

    /** Un nombre comme on l'écrit : « 10 », « 2,5 ». */
    static String nombre(double n) {
        if (n == Math.rint(n) && Math.abs(n) < 1e15) {
            return Long.toString((long) n);
        }
        return Double.toString(n).replace('.', ',');
    }
}
