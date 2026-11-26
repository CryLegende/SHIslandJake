package org.Cry_Legende.sHIslandJake;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Corps dessiné par BlockDisplayCreator : un « modèle montré pour un autre plugin », rien n'est écrit dans le monde.
 * Le regard, le sommeil et le réveil sont dans le fichier du modèle. Seule classe qui touche à BlockDisplayCreator,
 * par réflexion : le plugin se charge et tourne sans lui (softdepend). L'API est cherchée une fois par instance du
 * moteur ; si elle manque, un avertissement (une fois) et Jake garde le corps de secours. Fil principal.
 */
final class CorpsModele implements Corps {

    private static final String MOTEUR = "BlockDisplayCreator";
    private static final String RENDERER = "me.general_breddok.blockdisplaycreator.render.DisplayRenderer";
    private static final String SHOWN = RENDERER + "$ShownModel";

    private final SHIslandJake plugin;
    /** Instance du moteur pour laquelle l'API a été cherchée (trouvée ou non). */
    private Plugin moteur;
    private Object renderer;
    private Method show;
    private Method hide;
    private Method shows;
    private Method play;
    private Method knows;
    private Method bounds;
    private boolean echecSignale;
    private boolean appelSignale;

    CorpsModele(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    /** Vrai si BlockDisplayCreator est là, actif, et expose l'API des modèles montrés. */
    boolean disponible() {
        Plugin bdc = Bukkit.getPluginManager().getPlugin(MOTEUR);
        if (bdc == null || !bdc.isEnabled()) {
            return false;
        }
        if (bdc != moteur) {
            resoudre(bdc);
        }
        return renderer != null;
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
            knows = r.getMethod("knows", String.class);
            bounds = r.getMethod("bounds", String.class);
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
        return disponible() && Boolean.TRUE.equals(appel(knows, modele));
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} du modèle au repos, en blocs ; null si inconnu. */
    double[] bornes(String modele) {
        if (!disponible()) {
            return null;
        }
        Object b = appel(bounds, modele);
        return b instanceof double[] d && d.length == 6 ? d : null;
    }

    @Override
    public boolean montrer(Jake jake) {
        if (jake.modele() != null) {
            return true;
        }
        Settings.Apparence a = plugin.settings().apparence(jake.data().type());
        Location where = jake.location();
        if (!a.estModele() || where == null || !disponible()) {
            return false;
        }
        Object shown = appel(show, plugin, a.modele(), where, a.taille());
        if (shown == null) {
            return false;
        }
        jake.setModele(shown);
        return true;
    }

    @Override
    public void retirer(Jake jake) {
        Object shown = jake.modele();
        jake.setModele(null);
        if (shown != null && disponible()) {
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
        if (shown == null || animation == null || animation.isEmpty() || !disponible()) {
            return false;
        }
        Object result = appel(play, shown, animation, List.of(pour));
        return result instanceof Enum<?> e && e.name().equals("PLAYED");
    }

    @Override
    public boolean vivant(Jake jake) {
        Object shown = jake.modele();
        return shown != null && disponible() && Boolean.TRUE.equals(appel(shows, shown));
    }

    private Object appel(Method method, Object... args) {
        try {
            return method.invoke(renderer, args);
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException e) {
            Throwable cause = e instanceof InvocationTargetException ite ? ite.getCause() : e;
            if (!appelSignale) {
                appelSignale = true;
                plugin.getLogger().log(Level.WARNING, "BlockDisplayCreator : " + method.getName() + " a échoué", cause);
            }
            return null;
        }
    }
}
