package org.Cry_Legende.sHIslandJake;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Corps dessiné par BlockDisplayCreator : un « modèle montré pour un autre plugin », rien n'est écrit dans le monde.
 * Le regard est dans le fichier du modèle. Seule classe qui touche à BlockDisplayCreator, par réflexion : le plugin se
 * charge et tourne sans lui (softdepend). L'API est cherchée une fois par instance du moteur et de son afficheur (à
 * l'activation, à l'arrêt, puis de temps en temps) ; si elle manque, un avertissement (une fois) et Jake garde le
 * corps de secours. Fil principal.
 */
final class CorpsModele implements Corps {

    private static final String MOTEUR = "BlockDisplayCreator";
    private static final String RENDERER = "me.general_breddok.blockdisplaycreator.render.DisplayRenderer";
    private static final String SHOWN = RENDERER + "$ShownModel";

    /** Les méthodes de l'afficheur d'une instance du moteur ; « duration » et « animations » sont facultatives. */
    record Api(Object moteur, Object renderer, Method show, Method hide, Method shows, Method play, Method stop,
               Method knows, Method bounds, Method duration, Method animations) {

        /** Cherche l'API sur ce plugin moteur ; lève une exception si une méthode obligatoire manque. */
        static Api lire(Object moteur) throws ReflectiveOperationException {
            ClassLoader loader = moteur.getClass().getClassLoader();
            Class<?> r = Class.forName(RENDERER, true, loader);
            Class<?> shown = Class.forName(SHOWN, true, loader);
            Object renderer = moteur.getClass().getMethod("getDisplayRenderer").invoke(moteur);
            if (renderer == null) {
                throw new IllegalStateException("moteur d'affichage inactif");
            }
            return new Api(moteur, renderer,
                    r.getMethod("show", Plugin.class, String.class, Location.class, float.class),
                    r.getMethod("hide", shown), r.getMethod("shows", shown),
                    r.getMethod("play", shown, String.class, Collection.class),
                    r.getMethod("stop", shown, Collection.class),
                    r.getMethod("knows", String.class), r.getMethod("bounds", String.class),
                    facultative(r, "duration", String.class, String.class), facultative(r, "animations", String.class));
        }

        private static Method facultative(Class<?> r, String name, Class<?>... types) {
            try {
                return r.getMethod(name, types);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    }

    private final SHIslandJake plugin;
    private Api api;
    /** Instance du moteur pour laquelle l'API a été cherchée sans succès (pas de nouvel essai pour elle). */
    private Object echecPour;
    private boolean echecSignale;
    /** Animations de chaque modèle, lues une fois (vidées quand le moteur change ou au rechargement). */
    private final Map<String, Set<String>> animations = new HashMap<>();
    private final Signalements signalements = new Signalements();

    CorpsModele(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    /** Vrai si BlockDisplayCreator est là, actif, et expose l'API des modèles montrés. Lu dans un cache. */
    boolean disponible() {
        return api != null;
    }

    /**
     * Relit l'état du moteur : à l'activation ou à l'arrêt d'un plugin, et de temps en temps (un « /bdc reload » peut
     * refaire son afficheur). Renvoie vrai si quelque chose a changé : disponibilité, instance du moteur ou afficheur
     * (les modèles montrés par l'ancienne ne valent plus rien).
     */
    boolean revoir() {
        Plugin bdc = Bukkit.getPluginManager().getPlugin(MOTEUR);
        Object avant = api == null ? null : api.renderer();
        if (bdc == null || !bdc.isEnabled()) {
            api = null;
            echecPour = null;
        } else if (api == null || api.moteur() != bdc || afficheur(bdc) != api.renderer()) {
            if (api == null && bdc == echecPour) {
                return false;
            }
            resoudre(bdc);
        }
        Object apres = api == null ? null : api.renderer();
        if (avant != apres) {
            animations.clear();
            return true;
        }
        return false;
    }

    private static Object afficheur(Object bdc) {
        try {
            return bdc.getClass().getMethod("getDisplayRenderer").invoke(bdc);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private void resoudre(Object bdc) {
        try {
            api = Api.lire(bdc);
            echecPour = null;
            if (echecSignale) {
                plugin.getLogger().info("BlockDisplayCreator : modèles de Jake disponibles.");
            }
            echecSignale = false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            api = null;
            echecPour = bdc;
            if (!echecSignale) {
                echecSignale = true;
                plugin.getLogger().warning("BlockDisplayCreator ne permet pas de montrer un modèle pour un autre plugin ("
                        + e + ") : Jake garde le corps de secours.");
            }
        }
    }

    /** Le modèle est-il connu du moteur ? */
    boolean connait(String modele) {
        return api != null && Boolean.TRUE.equals(appel(api.knows(), modele));
    }

    /**
     * {minX, minY, minZ, maxX, maxY, maxZ} du modèle au repos, dans ses propres axes et à sa taille d'origine ; null si
     * inconnu ou pas encore lu par le moteur.
     */
    double[] bornes(String modele) {
        if (api == null) {
            return null;
        }
        Object b = appel(api.bounds(), modele);
        return b instanceof double[] d && d.length == 6 ? d : null;
    }

    /**
     * Le modèle a-t-il cette animation ? Vrai quand le moteur ne sait pas le dire (méthode absente) : le moteur
     * répondra lui-même au moment de la jouer.
     */
    boolean aAnimation(String modele, String animation) {
        if (api == null || api.animations() == null) {
            return true;
        }
        Set<String> noms = animations.get(modele);
        if (noms == null) {
            Object lu = appel(api.animations(), modele);
            noms = new java.util.HashSet<>();
            if (lu instanceof Collection<?> c) {
                for (Object o : c) {
                    // le moteur lit les noms d'animation sans tenir compte des majuscules
                    noms.add(String.valueOf(o).toLowerCase(java.util.Locale.ROOT));
                }
            }
            // un modèle pas encore lu par le moteur n'est pas gardé : il sera relu
            if (!noms.isEmpty()) {
                animations.put(modele, noms);
            }
        }
        return noms.isEmpty() || noms.contains(animation.toLowerCase(java.util.Locale.ROOT));
    }

    void oublierAnimations() {
        animations.clear();
    }

    /**
     * Moteur qui ne donne pas la durée des animations : chaque animation d'une apparence (scènes, vie, gestes des
     * réactions, clic) sans ambiance.durees est signalée une fois, avec la durée prise à sa place. Au démarrage, au
     * rechargement et quand le moteur change.
     */
    void signalerDurees(java.util.Set<String> dejaSignalees) {
        if (api == null || api.duration() != null) {
            return;
        }
        Settings s = plugin.settings();
        java.util.Set<String> gestes = new java.util.LinkedHashSet<>();
        for (ReglagesReactions.Reaction r : s.reactions.toutes().values()) {
            if (r.animation() != null && !r.animation().isEmpty()) {
                gestes.add(r.animation());
            }
        }
        if (!s.clickAnimation.isEmpty()) {
            gestes.add(s.clickAnimation);
        }
        for (Settings.Apparence a : s.types.values()) {
            if (!a.estModele()) {
                continue;
            }
            java.util.Set<String> noms = new java.util.LinkedHashSet<>(a.scenes());
            noms.addAll(a.vie());
            noms.addAll(gestes);
            for (String n : noms) {
                if (!s.dureeEcrite(a.modele(), n) && aAnimation(a.modele(), n) && dejaSignalees.add(a.modele() + "/" + n)) {
                    plugin.getLogger().warning("BlockDisplayCreator ne donne pas la durée des animations : « " + n
                            + " » du modèle « " + a.modele() + " » compte pour " + s.animationTicks(a.modele(), n) / 20.0
                            + " s (ambiance.durees." + a.modele() + "." + n + ").");
                }
            }
        }
    }

    /** Montre le modèle de l'apparence du Jake. */
    EssaisModele.Resultat essayer(Jake jake) {
        if (jake.modele() != null) {
            return EssaisModele.Resultat.MONTRE;
        }
        Settings.Apparence a = plugin.settings().apparence(jake.data().type());
        Location where = jake.location();
        if (!a.estModele() || where == null || api == null) {
            return EssaisModele.Resultat.INCONNU;
        }
        Object shown = appel(api.show(), plugin, a.modele(), where, a.taille());
        if (shown != null) {
            jake.setModele(shown);
            return EssaisModele.Resultat.MONTRE;
        }
        return connait(a.modele()) ? EssaisModele.Resultat.PLUS_TARD : EssaisModele.Resultat.INCONNU;
    }

    @Override
    public boolean montrer(Jake jake) {
        return essayer(jake) == EssaisModele.Resultat.MONTRE;
    }

    @Override
    public void retirer(Jake jake) {
        Object shown = jake.modele();
        jake.setModele(null);
        if (shown != null && api != null) {
            appel(api.hide(), shown);
        }
    }

    /** Le moteur gère seul ceux qui voient le modèle. */
    @Override
    public void voir(Jake jake, Player joueur, boolean visible) {
    }

    @Override
    public boolean jouer(Jake jake, String animation, Player pour) {
        Object shown = jake.modele();
        if (shown == null || animation == null || animation.isEmpty() || api == null) {
            return false;
        }
        Object result = appel(api.play(), shown, animation, List.of(pour));
        return result instanceof Enum<?> e && e.name().equals("PLAYED");
    }

    @Override
    public void arreter(Jake jake, Player pour) {
        Object shown = jake.modele();
        if (shown != null && api != null) {
            appel(api.stop(), shown, List.of(pour));
        }
    }

    /** Durée donnée par le moteur pour le modèle réellement dessiné ; -1 s'il ne sait pas la dire. */
    @Override
    public int duree(Jake jake, String animation) {
        Settings.Apparence a = jake.dessinee();
        if (jake.modele() == null || a == null || !a.estModele() || api == null || api.duration() == null) {
            return -1;
        }
        Object d = appel(api.duration(), a.modele(), animation);
        return d instanceof Number n && n.intValue() > 0 ? n.intValue() : -1;
    }

    @Override
    public boolean vivant(Jake jake) {
        Object shown = jake.modele();
        return shown != null && api != null && Boolean.TRUE.equals(appel(api.shows(), shown));
    }

    private Object appel(Method method, Object... args) {
        try {
            return method.invoke(api.renderer(), args);
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
            if (signalements.premiere("moteur/" + method.getName(), cause)) {
                plugin.getLogger().log(Level.WARNING, "BlockDisplayCreator : " + method.getName() + " a échoué", cause);
            }
            return null;
        }
    }
}
