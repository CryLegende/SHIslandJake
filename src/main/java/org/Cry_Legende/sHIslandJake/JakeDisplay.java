package org.Cry_Legende.sHIslandJake;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Choisit le corps de chaque Jake : le modèle de son apparence (BlockDisplayCreator) quand il existe, sinon le corps de
 * secours (FancyNpcs, apparence « apparences.secours ») ; jamais d'île sans Jake. Un avertissement par apparence
 * dont le modèle manque, une fois. Fil principal.
 */
final class JakeDisplay {

    private final SHIslandJake plugin;
    private final JakeRegistry registry;
    private final CorpsMob mob;
    private final CorpsModele modele;
    private final Textes textes;
    /** Apparences déjà signalées en console (modèle introuvable). */
    private final Set<String> signalees = new HashSet<>();
    /** Dernier tick où chaque Jake a été vérifié : plusieurs joueurs autour d'un Jake ne le vérifient qu'une fois. */
    private final java.util.Map<Jake, Integer> verifies = new java.util.HashMap<>();
    private JakeTracker tracker;

    JakeDisplay(SHIslandJake plugin, JakeRegistry registry, Textes textes) {
        this.plugin = plugin;
        this.registry = registry;
        this.textes = textes;
        this.mob = new CorpsMob(plugin, registry);
        this.modele = new CorpsModele(plugin);
    }

    void setTracker(JakeTracker tracker) {
        this.tracker = tracker;
    }

    CorpsModele modeles() {
        return modele;
    }

    /**
     * Le Jake est dessiné (créé s'il ne l'était pas, redessiné si son modèle a été lâché) ; faux si son monde ou son
     * chunk n'est pas chargé.
     */
    boolean montrer(Jake jake) {
        int tick = Bukkit.getCurrentTick();
        Integer vu = verifies.get(jake);
        if (vu != null && vu == tick) {
            return jake.corps() != null;
        }
        Corps corps = jake.corps();
        if (corps != null) {
            if (corps.vivant(jake)) {
                verifies.put(jake, tick);
                return true;
            }
            // lâché par le moteur (chunk déchargé, modèle rechargé...) : tout est refait
            release(jake);
        }
        Location location = jake.location();
        if (location == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return false;
        }
        numeroter(jake);
        Settings s = plugin.settings();
        Settings.Apparence a = s.apparence(jake.data().type());
        if (a.estModele()) {
            if (modele.montrer(jake)) {
                double[] b = modele.bornes(a.modele());
                double hauteur = !Double.isNaN(a.hauteur()) ? a.hauteur()
                        : b != null ? b[4] * a.taille() + 0.25 : s.defaultHeight;
                double largeur = !Double.isNaN(a.zoneLargeur()) ? a.zoneLargeur()
                        : b != null ? 2 * Math.max(Math.max(Math.abs(b[0]), Math.abs(b[3])), Math.max(Math.abs(b[2]), Math.abs(b[5]))) * a.taille()
                        : s.defaultZoneWidth;
                double zoneHauteur = !Double.isNaN(a.zoneHauteur()) ? a.zoneHauteur()
                        : b != null ? b[4] * a.taille() : s.defaultZoneHeight;
                jake.dessine(modele, a, hauteur, (float) Math.max(0.1, largeur), (float) Math.max(0.1, zoneHauteur));
                registry.markDrawn(jake, true);
                verifies.put(jake, tick);
                return true;
            }
            signaler(a);
            a = s.secours;
        }
        if (mob.montrer(jake, a)) {
            jake.dessine(mob, a, Double.isNaN(a.hauteur()) ? s.defaultHeight : a.hauteur(), 0f, 0f);
            registry.markDrawn(jake, true);
            verifies.put(jake, tick);
            return true;
        }
        return false;
    }

    /** Numéros des textes et de la zone, tirés au premier dessin ; la zone mène au Jake quand on clique dessus. */
    private void numeroter(Jake jake) {
        if (!jake.numerote()) {
            jake.numeros(Paquets.nouveauNumero(), Paquets.nouveauNumero(), Paquets.nouveauNumero());
            registry.link(jake.zoneId(), jake);
        }
    }

    private void signaler(Settings.Apparence a) {
        if (!signalees.add(a.id())) {
            return;
        }
        String raison = !modele.disponible() ? "BlockDisplayCreator absent ou sans modèles pour les autres plugins"
                : !modele.connait(a.modele()) ? "modèle « " + a.modele() + " » inconnu de BlockDisplayCreator"
                : "le modèle « " + a.modele() + " » ne dessine rien";
        plugin.getLogger().warning("Apparence " + a.id() + " : " + raison + ". Jake est dessiné en "
                + plugin.settings().secours.id() + " à la place.");
    }

    /** Ce joueur le voit ou plus (corps de secours ; le moteur des modèles gère seul ses spectateurs). */
    void voir(Jake jake, Player player, boolean visible) {
        Corps corps = jake.corps();
        if (corps != null) {
            corps.voir(jake, player, visible);
        }
    }

    /** Animation du corps pour ce joueur seul ; faux si le corps ne l'a pas. */
    boolean jouer(Jake jake, String animation, Player player) {
        Corps corps = jake.corps();
        return corps != null && animation != null && !animation.isEmpty() && corps.jouer(jake, animation, player);
    }

    /**
     * Retire le corps, les textes et la zone à tous ceux qui les voient : ils seront refaits avec les nouvelles
     * données au prochain passage d'un joueur.
     */
    void release(Jake jake) {
        if (tracker != null) {
            tracker.forgetJake(jake);
        }
        textes.oublierJake(jake);
        Corps corps = jake.corps();
        if (corps != null) {
            corps.retirer(jake);
        }
        // un corps de secours ou un modèle resté d'un ancien dessin part aussi
        mob.retirer(jake);
        modele.retirer(jake);
        jake.efface();
        registry.markDrawn(jake, false);
        verifies.remove(jake);
    }

    /** Arrêt ou rechargement : plus rien n'est dessiné. */
    void releaseAll() {
        for (Jake jake : new ArrayList<>(registry.drawn())) {
            release(jake);
        }
        signalees.clear();
        verifies.clear();
    }

    /** Joueur parti : les PNJ l'oublient. */
    void forget(Player player) {
        UUID id = player.getUniqueId();
        for (Jake jake : registry.drawn()) {
            jake.forget(id);
        }
    }
}
