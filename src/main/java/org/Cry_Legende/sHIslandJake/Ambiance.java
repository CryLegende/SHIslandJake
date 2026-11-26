package org.Cry_Legende.sHIslandJake;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Scènes d'ambiance : courtes animations d'un modèle jouées pour un joueur proche qui a Jake devant lui, au hasard
 * (jamais deux fois de suite la même), avec une pause tirée entre deux bornes après chaque scène ou réaction. Une
 * réaction passe devant une scène ; une scène n'interrompt rien. En ticks du serveur. Ne touche pas au serveur.
 */
final class Ambiance {

    /** Ce qu'un joueur vit : jusqu'à quand il est occupé (scène ou réaction), dernière scène. */
    private static final class Etat {
        int occupeJusqua;
        int libreA;
        int sceneJusqua = -1;
        String derniere;
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
        if (scenes.isEmpty()) {
            return null;
        }
        Etat e = etats.get(joueur);
        String derniere = e == null ? null : e.derniere;
        if (scenes.size() == 1) {
            return scenes.getFirst();
        }
        int i = hasard.nextInt(scenes.size());
        if (scenes.get(i).equals(derniere)) {
            i = (i + 1 + hasard.nextInt(scenes.size() - 1)) % scenes.size();
        }
        return scenes.get(i);
    }

    /** Pause tirée entre les deux bornes, en ticks. */
    int pause(int minTicks, int maxTicks) {
        int min = Math.max(0, Math.min(minTicks, maxTicks));
        int max = Math.max(minTicks, maxTicks);
        return max <= min ? min : min + hasard.nextInt(max - min + 1);
    }

    /** Une scène commence pour ce joueur : il est occupé « duree » ticks, puis en pause « pause » ticks. */
    void scene(UUID joueur, String scene, int maintenant, int duree, int pause) {
        Etat e = etats.computeIfAbsent(joueur, k -> new Etat());
        e.derniere = scene;
        e.sceneJusqua = maintenant + duree;
        e.occupeJusqua = maintenant + duree;
        e.libreA = maintenant + duree + pause;
    }

    /** Une réaction (ou une bulle) commence pour ce joueur : elle remplace la scène en cours. */
    void reaction(UUID joueur, int maintenant, int duree, int pause) {
        Etat e = etats.computeIfAbsent(joueur, k -> new Etat());
        e.sceneJusqua = -1;
        e.occupeJusqua = Math.max(e.occupeJusqua, maintenant + duree);
        e.libreA = Math.max(e.libreA, maintenant + duree + pause);
    }

    void oublier(UUID joueur) {
        etats.remove(joueur);
    }
}
