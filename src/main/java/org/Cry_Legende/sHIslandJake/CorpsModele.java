package org.Cry_Legende.sHIslandJake;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Corps dessiné par BlockDisplayCreator : un « modèle montré pour un autre plugin », rien n'est écrit dans le monde.
 * Le regard est dans le fichier du modèle. Seule classe qui touche à BlockDisplayCreator, par réflexion : le plugin se
 * charge et tourne sans lui (softdepend). L'API est cherchée une fois par instance du moteur (à son activation, puis
 * de temps en temps) ; si elle manque, un avertissement (une fois) et Jake garde le corps de secours. Fil principal.
 */
final class CorpsModele implements Corps {

    /** Ce qu'a donné une demande de dessin. */
    enum Resultat {
        MONTRE,
        /** Le moteur connaît le modèle mais ne l'a pas encore lu (demande faite dans une commande) : réessayer. */
        PLUS_TARD,
        /** Modèle inconnu du moteur, ou moteur absent : corps de secours. */
        INCONNU
    }

    private static final String MOTEUR = "BlockDisplayCreator";
    private static final String RENDERER = "me.general_breddok.blockdisplaycreator.render.DisplayRenderer";
    private static final String SHOWN = RENDERER + "$ShownModel";

    private final SHIslandJake plugin;
    /** Instance du moteur pour laquelle l'API a été cherchée (trouvée ou non). */
    private Plugin moteur;
    private Object renderer;
    private boolean disponible;
    private Method show;
    private Method hide;
    private Method shows;
    private Method play;
    private Method stop;
    private Method knows;
    private Method bounds;
    /** Facultative : durée d'une animation en ticks (modèle, animation). */
    private Method duration;
    private boolean echecSignale;
    /** Erreurs déjà écrites en console : une par méthode et par sorte d'erreur. */
    private final Set<String> signalees = new HashSet<>();

    CorpsModele(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    /** Vrai si BlockDisplayCreator est là, actif, et expose l'API des modèles montrés. Lu dans un cache. */
    boolean disponible() {
        return disponible;
    }

    /**
     * Relit l'état du moteur : à l'activation ou à l'arrêt d'un plugin, et de temps en temps (un « /bdc reload » peut
     * rallumer son rendu). Renvoie vrai si la disponibilité a changé.
     */
    boolean revoir() {
        boolean avant = disponible;
        Plugin bdc = Bukkit.getPluginManager().getPlugin(MOTEUR);
        if (bdc == null || !bdc.isEnabled()) {
            moteur = null;
            renderer = null;
            disponible = false;
        } else {
            if (bdc != moteur || renderer == null) {
                resoudre(bdc);
            } else {
                // même instance : son rendu a pu être refait (/bdc reload)
                try {
                    Object actuel = bdc.getClass().getMethod("getDisplayRenderer").invoke(bdc);
                    if (actuel != renderer) {
                        resoudre(bdc);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    resoudre(bdc);
                }
            }
            disponible = renderer != null;
        }
        return avant != disponible;
    }

    private void resoudre(Plugin bdc) {
        moteur = bdc;
        renderer = null;
        try {
            ClassLoader loader = bdc.getClass().getClassLoader();
            Class<?> r = Class.forName(RENDERER, true, loader);
            Class<?> shown = Class.forName(SHOWN, true, loader);
            Object instance = bdc.getClass().getMethod("getDisplayRenderer").invoke(bdc);
            if (instance == null) {
                throw new IllegalStateException("moteur d'affichage inactif");
            }
            show = r.getMethod("show", Plugin.class, String.class, Location.class, float.class);
            hide = r.getMethod("hide", shown);
            shows = r.getMethod("shows", shown);
            play = r.getMethod("play", shown, String.class, Collection.class);
            stop = r.getMethod("stop", shown, Collection.class);
            knows = r.getMethod("knows", String.class);
            bounds = r.getMethod("bounds", String.class);
            try {
                duration = r.getMethod("duration", String.class, String.class);
            } catch (NoSuchMethodException e) {
                duration = null;
            }
            renderer = instance;
            if (echecSignale) {
                plugin.getLogger().info("BlockDisplayCreator : modèles de Jake disponibles.");
            }
            echecSignale = false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            renderer = null;
            if (!echecSignale) {
                echecSignale = true;
                plugin.getLogger().warning("BlockDisplayCreator ne permet pas de montrer un modèle pour un autre plugin ("
                        + e + ") : Jake garde le corps de secours.");
            }
        }
    }

    /** Le modèle est-il connu du moteur ? */
    boolean connait(String modele) {
        return disponible && Boolean.TRUE.equals(appel(knows, modele));
    }

    /**
     * {minX, minY, minZ, maxX, maxY, maxZ} du modèle au repos, dans ses propres axes et à sa taille d'origine ; null si
     * inconnu ou pas encore lu par le moteur.
     */
    double[] bornes(String modele) {
        if (!disponible) {
            return null;
        }
        Object b = appel(bounds, modele);
        return b instanceof double[] d && d.length == 6 ? d : null;
    }

    /** Montre le modèle de l'apparence du Jake. */
    Resultat essayer(Jake jake) {
        if (jake.modele() != null) {
            return Resultat.MONTRE;
        }
        Settings.Apparence a = plugin.settings().apparence(jake.data().type());
        Location where = jake.location();
        if (!a.estModele() || where == null || !disponible) {
            return Resultat.INCONNU;
        }
        Object shown = appel(show, plugin, a.modele(), where, a.taille());
        if (shown != null) {
            jake.setModele(shown);
            return Resultat.MONTRE;
        }
        return connait(a.modele()) ? Resultat.PLUS_TARD : Resultat.INCONNU;
    }

    @Override
    public boolean montrer(Jake jake) {
        return essayer(jake) == Resultat.MONTRE;
    }

    @Override
    public void retirer(Jake jake) {
        Object shown = jake.modele();
        jake.setModele(null);
        if (shown != null && disponible) {
            appel(hide, shown);
        }
    }

    /** Le moteur gère seul ceux qui voient le modèle. */
    @Override
    public void voir(Jake jake, Player joueur, boolean visible) {
    }

    @Override
    public boolean jouer(Jake jake, String animation, Player pour) {
        Object shown = jake.modele();
        if (shown == null || animation == null || animation.isEmpty() || !disponible) {
            return false;
        }
        Object result = appel(play, shown, animation, List.of(pour));
        return result instanceof Enum<?> e && e.name().equals("PLAYED");
    }

    @Override
    public void arreter(Jake jake, Player pour) {
        Object shown = jake.modele();
        if (shown != null && disponible) {
            appel(stop, shown, List.of(pour));
        }
    }

    @Override
    public int duree(Jake jake, String animation) {
        Object shown = jake.modele();
        if (shown == null || duration == null || !disponible) {
            return -1;
        }
        Object d = appel(duration, plugin.settings().apparence(jake.data().type()).modele(), animation);
        return d instanceof Number n && n.intValue() > 0 ? n.intValue() : -1;
    }

    @Override
    public boolean vivant(Jake jake) {
        Object shown = jake.modele();
        return shown != null && disponible && Boolean.TRUE.equals(appel(shows, shown));
    }

    private Object appel(Method method, Object... args) {
        try {
            return method.invoke(renderer, args);
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
            if (signalees.add(method.getName() + "/" + cause.getClass().getName())) {
                plugin.getLogger().log(Level.WARNING, "BlockDisplayCreator : " + method.getName() + " a échoué", cause);
            }
            return null;
        }
    }
}
