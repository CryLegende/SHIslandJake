package org.Cry_Legende.sHIslandJake;

import java.util.HashSet;
import java.util.Set;

/**
 * Erreurs déjà écrites en console : une même erreur (même endroit, même sorte) ne l'est qu'une fois. Une trace vide
 * (la JVM vide celle d'une erreur qui se répète) compte comme une sorte à part. Fil principal.
 */
final class Signalements {

    private final Set<String> vues = new HashSet<>();

    /** Vrai la première fois que cette erreur arrive dans ce contexte. */
    boolean premiere(String contexte, Throwable e) {
        StackTraceElement[] trace = e.getStackTrace();
        return vues.add(contexte + "/" + e.getClass().getName() + (trace.length > 0 ? "@" + trace[0] : ""));
    }
}
