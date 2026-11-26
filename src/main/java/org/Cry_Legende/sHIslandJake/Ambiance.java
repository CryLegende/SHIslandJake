package org.Cry_Legende.sHIslandJake;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * La vie de Jake pour un joueur proche qui l'a devant lui, en ticks du serveur. Ne touche pas au serveur.
 * <ul>
 * <li>Scènes d'ambiance : courtes animations au hasard (jamais deux fois de suite la même), avec une pause tirée entre
 * deux bornes après chaque scène ou réaction. Une réaction passe devant une scène ; une scène n'interrompt rien.</li>
 * <li>Animations de vie (respirer, petits mouvements) : enchaînées sans trou tant que le joueur regarde Jake ; une
 * scène ou le geste d'une réaction les remplace, la vie reprend à leur fin ; quand le joueur se détourne, celle en
 * cours finit et rien n'est relancé.</li>
 * </ul>
 */
final class Ambiance {

    /** Ce qu'il faut faire de la vie de Jake pour un joueur, à la fin d'une animation de vie ou quand elle peut partir. */
    enum Vie {
        /** Une animation de vie part maintenant. */
        JOUER,
        /** Une scène ou le geste d'une réaction joue : la vie reprend à sa fin ({@link #reprise}). */
        ATTENDRE,
        /** Le joueur ne regarde plus Jake (ou la vie ne joue pas pour lui) : rien n'est relancé. */
        ARRETER
    }

    /** Ce qu'un joueur vit : occupé (scène, réaction), pause, animation en cours, dernière scène et dernière vie. */
    private static final class Etat {
        int occupeJusqua;
        int libreA;
        int sceneJusqua = -1;
        /** Fin de l'animation (scène ou geste d'une réaction) qui a la place de la vie. */
        int animJusqua = -1;
        /** Fin de l'animation de vie en cours ; -1 = aucune. */
        int vieJusqua = -1;
        String derniere;
        String derniereVie;
    }

    private final Map<UUID, Etat> etats = new HashMap<>();
    private final Random hasard;

    Ambiance(Random hasard) {
        this.hasard = hasard;
    }

    /**
     * Jake est-il devant le joueur ? Angle entre la direction du regard (yaw, pitch en degrés, comme Minecraft) et la
     * direction de ses yeux vers Jake, comparé par son cosinus (aucune racine pour le regard, une pour la distance).
     */
    static boolean devant(double yeuxX, double yeuxY, double yeuxZ, float yaw, float pitch,
                          double jakeX, double jakeY, double jakeZ, double cosinusMax) {
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);
        double cp = Math.cos(rp);
        double vx = -Math.sin(ry) * cp;
        double vy = -Math.sin(rp);
        double vz = Math.cos(ry) * cp;
        double dx = jakeX - yeuxX;
        double dy = jakeY - yeuxY;
        double dz = jakeZ - yeuxZ;
        double longueur = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (longueur < 1e-6) {
            return true;
        }
        return (vx * dx + vy * dy + vz * dz) / longueur >= cosinusMax;
    }

    //SCÈNES----------

    /** Une scène peut-elle commencer pour ce joueur (pause écoulée, rien en cours) ? */
    boolean libre(UUID joueur, int maintenant) {
        Etat e = etats.get(joueur);
        return e == null || maintenant >= e.occupeJusqua && maintenant >= e.libreA;
    }

    /** Une scène est-elle en cours pour ce joueur ? */
    boolean enScene(UUID joueur, int maintenant) {
        Etat e = etats.get(joueur);
        return e != null && e.sceneJusqua >= 0 && maintenant < e.sceneJusqua;
    }

    /** Une scène au hasard, jamais la même que la dernière (sauf s'il n'y en a qu'une) ; null si la liste est vide. */
    String tirer(UUID joueur, List<String> scenes) {
        Etat e = etats.get(joueur);
        return tirage(scenes, e == null ? null : e.derniere);
    }

    /** Pause tirée entre les deux bornes, en ticks. */
    int pause(int minTicks, int maxTicks) {
        int min = Math.max(0, Math.min(minTicks, maxTicks));
        int max = Math.max(minTicks, maxTicks);
        return max <= min ? min : min + hasard.nextInt(max - min + 1);
    }

    /** Une scène commence pour ce joueur : il est occupé « duree » ticks, puis en pause ; elle remplace la vie. */
    void scene(UUID joueur, String scene, int maintenant, int duree, int pause) {
        Etat e = etat(joueur);
        e.derniere = scene;
        e.sceneJusqua = maintenant + duree;
        e.occupeJusqua = maintenant + duree;
        e.libreA = maintenant + duree + pause;
        if (duree > 0) {
            e.animJusqua = maintenant + duree;
            e.vieJusqua = -1;
        }
    }

    /**
     * Une réaction commence pour ce joueur (bulle, geste) : elle remplace la scène en cours ; son geste (« geste »
     * ticks, 0 = aucun) remplace aussi l'animation de vie, qui reprend à sa fin.
     */
    void reaction(UUID joueur, int maintenant, int duree, int geste, int pause) {
        Etat e = etat(joueur);
        e.sceneJusqua = -1;
        e.occupeJusqua = Math.max(e.occupeJusqua, maintenant + duree);
        e.libreA = Math.max(e.libreA, maintenant + duree + pause);
        if (geste > 0) {
            // le geste remplace ce qui jouait (scène, autre geste, vie) : la vie reprend à sa fin à lui
            e.animJusqua = maintenant + geste;
            e.vieJusqua = -1;
        }
    }

    /** La scène en cours a été arrêtée (réaction sans geste) : la vie peut reprendre tout de suite. */
    void sceneArretee(UUID joueur, int maintenant) {
        Etat e = etats.get(joueur);
        if (e != null && e.sceneJusqua > maintenant) {
            e.animJusqua = Math.min(e.animJusqua, maintenant);
            e.sceneJusqua = -1;
        }
    }

    //VIE----------

    /**
     * Que faire de la vie de Jake pour ce joueur maintenant ?
     *
     * @param regarde le joueur est près de Jake et l'a devant lui (lu au dernier passage lent du suivi)
     * @param permise la vie joue pour lui (« Jake discret » et ambiance.vie-si-discret)
     */
    Vie decider(UUID joueur, int maintenant, boolean regarde, boolean permise) {
        if (!regarde || !permise) {
            return Vie.ARRETER;
        }
        Etat e = etats.get(joueur);
        if (e != null && e.animJusqua > maintenant) {
            return Vie.ATTENDRE;
        }
        return Vie.JOUER;
    }

    /** Tick où la vie peut reprendre après la scène ou le geste en cours. */
    int reprise(UUID joueur) {
        Etat e = etats.get(joueur);
        return e == null ? 0 : e.animJusqua;
    }

    /** Une animation de vie est-elle en cours pour ce joueur ? */
    boolean enVie(UUID joueur, int maintenant) {
        Etat e = etats.get(joueur);
        return e != null && e.vieJusqua > maintenant;
    }

    /** Une animation de vie au hasard ; avec deux ou plus, jamais deux fois de suite la même ; seule, elle se répète. */
    String tirerVie(UUID joueur, List<String> vie) {
        Etat e = etats.get(joueur);
        return tirage(vie, e == null ? null : e.derniereVie);
    }

    /** Une animation de vie commence : elle finit dans « duree » ticks, où la suivante partira. */
    void vie(UUID joueur, String animation, int maintenant, int duree) {
        Etat e = etat(joueur);
        e.derniereVie = animation;
        e.vieJusqua = maintenant + Math.max(1, duree);
    }

    /** La vie ne joue plus pour ce joueur (il s'est détourné, Jake a disparu). */
    void finVie(UUID joueur) {
        Etat e = etats.get(joueur);
        if (e != null) {
            e.vieJusqua = -1;
        }
    }

    void oublier(UUID joueur) {
        etats.remove(joueur);
    }

    private Etat etat(UUID joueur) {
        return etats.computeIfAbsent(joueur, k -> new Etat());
    }

    private String tirage(List<String> liste, String derniere) {
        if (liste.isEmpty()) {
            return null;
        }
        if (liste.size() == 1) {
            return liste.getFirst();
        }
        int i = hasard.nextInt(liste.size());
        if (liste.get(i).equals(derniere)) {
            i = (i + 1 + hasard.nextInt(liste.size() - 1)) % liste.size();
        }
        return liste.get(i);
    }
}
