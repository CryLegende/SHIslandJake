package org.Cry_Legende.sHIslandJake;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Choisit le corps de chaque Jake : le modèle de son apparence (BlockDisplayCreator) quand le moteur le connaît, sinon
 * le corps de secours (FancyNpcs, apparence « apparences.secours ») ; jamais d'île sans Jake. Un modèle que le moteur
 * connaît mais n'a pas encore lu est redemandé au tick suivant ({@link EssaisModele}) ; un Jake resté en secours
 * réessaie son modèle de temps en temps, et ne quitte le villageois que si le modèle s'est montré. Un avertissement par
 * apparence dont le modèle manque, et par scène absente d'un modèle, une fois. Fil principal.
 */
final class JakeDisplay {

    private final SHIslandJake plugin;
    private final JakeRegistry registry;
    private final CorpsMob mob;
    private final CorpsModele modele;
    private final Textes textes;
    /** Avertissements déjà écrits (apparence sans modèle, scène absente d'un modèle). */
    private final Set<String> signales = new HashSet<>();
    private JakeTracker tracker;

    JakeDisplay(SHIslandJake plugin, JakeRegistry registry, Textes textes) {
        this.plugin = plugin;
        this.registry = registry;
        this.textes = textes;
        this.mob = new CorpsMob(plugin, registry);
        this.modele = new CorpsModele(plugin);
        this.modele.revoir();
    }

    void setTracker(JakeTracker tracker) {
        this.tracker = tracker;
    }

    CorpsModele modeles() {
        return modele;
    }

    /**
     * Le Jake est dessiné (créé s'il ne l'était pas, redessiné si son modèle a été lâché) ; faux si son monde ou son
     * chunk n'est pas chargé, ou si le moteur lit encore son modèle (il est redemandé au tick suivant).
     *
     * @param arrivee un joueur arrive près de lui : le moteur est interrogé même si le dernier contrôle est récent
     */
    boolean montrer(Jake jake, boolean arrivee) {
        int tick = Bukkit.getCurrentTick();
        if (jake.verifie == tick) {
            return jake.corps() != null;
        }
        Settings s = plugin.settings();
        Corps corps = jake.corps();
        if (corps == modele) {
            if (!arrivee && tick < jake.prochainControle) {
                jake.verifie = tick;
                return true;
            }
            if (modele.vivant(jake)) {
                jake.prochainControle = tick + s.modelCheckTicks;
                jake.verifie = tick;
                if (jake.bornesProvisoires()) {
                    bornes(jake);
                }
                return true;
            }
            // lâché par le moteur (chunk déchargé, fichier retiré...) : tout est refait
            release(jake);
        } else if (corps != null) {
            if (corps.vivant(jake)) {
                jake.verifie = tick;
                if (jake.secours() && tick >= jake.prochainEssaiModele) {
                    essayerDepuisSecours(jake, tick);
                }
                return true;
            }
            release(jake);
        }
        jake.verifie = tick;
        Location location = jake.location();
        if (location == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return false;
        }
        numeroter(jake);
        Settings.Apparence a = s.apparence(jake.data().type());
        if (a.estModele()) {
            EssaisModele.Resultat r = modele.disponible() ? modele.essayer(jake) : EssaisModele.Resultat.INCONNU;
            switch (EssaisModele.suite(r, jake.essaisPlusTard)) {
                case MONTRE -> {
                    modeleMontre(jake, a, tick);
                    return true;
                }
                case RELANCER -> {
                    jake.essaisPlusTard++;
                    relancer(jake, tick);
                    return false;
                }
                default -> {
                    jake.essaisPlusTard = 0;
                    signaler(a, r);
                    Settings.Apparence secours = s.secours;
                    if (mob.montrer(jake, secours)) {
                        jake.dessine(mob, secours, Double.isNaN(secours.hauteur()) ? s.defaultHeight : secours.hauteur(), 0f, 0f);
                        blocs(jake, false);
                        jake.setSecours(true);
                        jake.prochainEssaiModele = tick + s.modelRetryTicks;
                        registry.markDrawn(jake, true);
                        return true;
                    }
                    return false;
                }
            }
        }
        if (mob.montrer(jake, a)) {
            jake.dessine(mob, a, Double.isNaN(a.hauteur()) ? s.defaultHeight : a.hauteur(), 0f, 0f);
            blocs(jake, false);
            registry.markDrawn(jake, true);
            return true;
        }
        return false;
    }

    /**
     * Textes sous le bloc qui occupe leur place (feuillage, plafond) : quelques lectures de blocs dans la colonne de
     * Jake, chunk chargé, fil principal. Au dessin, puis toutes les « verification-secondes » pour un Jake qui a un
     * spectateur (un arbre pousse, un joueur construit).
     *
     * @param renvoyer vrai si les textes déjà envoyés doivent l'être à la nouvelle hauteur
     */
    void blocs(Jake jake, boolean renvoyer) {
        Settings s = plugin.settings();
        jake.prochainBlocs = Bukkit.getCurrentTick() + s.blocksCheckTicks;
        double bas = jake.hauteurNormale();
        Location location = jake.location();
        if (s.textsUnderBlocks && location != null
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            org.bukkit.World world = location.getWorld();
            int bx = location.getBlockX();
            int bz = location.getBlockZ();
            bas = HauteurTextes.bas(location.getY(), jake.hauteurNormale(), s.alertOffset + s.speechSpace, s.textsMinHeight,
                    y -> !world.getBlockAt(bx, y, bz).isPassable());
        }
        if (Math.abs(bas - jake.hauteurTexte()) > 1e-3) {
            jake.setHauteurTexte(bas);
            if (renvoyer && tracker != null) {
                tracker.forgetJake(jake);
                textes.oublierJake(jake);
            }
        }
    }

    /** Le modèle est montré : hauteur et zone par défaut, corrigées dès que le moteur donne son dessin. */
    private void modeleMontre(Jake jake, Settings.Apparence a, int tick) {
        Settings s = plugin.settings();
        jake.essaisPlusTard = 0;
        jake.dessine(modele, a, s.defaultHeight, (float) Math.min(s.maxZoneWidth, s.defaultZoneWidth), (float) s.defaultZoneHeight);
        jake.setSecours(false);
        jake.setBornesProvisoires(true);
        bornes(jake);
        blocs(jake, false);
        jake.prochainControle = tick + s.modelCheckTicks;
        registry.markDrawn(jake, true);
    }

    /**
     * Resté en secours : le modèle est-il revenu ? Il est demandé pendant que le villageois est encore là ; le
     * villageois ne part que si le modèle s'est montré (Jake ne disparaît jamais le temps d'un essai).
     */
    private void essayerDepuisSecours(Jake jake, int tick) {
        Settings s = plugin.settings();
        Settings.Apparence voulue = s.apparence(jake.data().type());
        if (!voulue.estModele() || !modele.disponible()) {
            jake.prochainEssaiModele = tick + s.modelRetryTicks;
            return;
        }
        EssaisModele.Resultat r = modele.essayer(jake);
        switch (EssaisModele.suite(r, jake.essaisPlusTard)) {
            case MONTRE -> {
                // le villageois part, les textes et la zone sont renvoyés à la hauteur du modèle
                mob.retirer(jake);
                if (tracker != null) {
                    tracker.forgetJake(jake);
                }
                textes.oublierJake(jake);
                modeleMontre(jake, voulue, tick);
            }
            case RELANCER -> {
                jake.essaisPlusTard++;
                jake.prochainEssaiModele = tick + 1;
                relancer(jake, tick);
            }
            default -> {
                jake.essaisPlusTard = 0;
                jake.prochainEssaiModele = tick + s.modelRetryTicks;
            }
        }
    }

    /** Le moteur lit le modèle : les joueurs autour du Jake sont repassés au tick suivant, une fois. */
    private void relancer(Jake jake, int tick) {
        if (jake.relance == tick + 1) {
            return;
        }
        jake.relance = tick + 1;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (tracker != null && registry.get(jake.island()) == jake) {
                tracker.refreshAround(jake);
            }
        });
    }

    /**
     * Hauteur du nom et zone cliquable d'après le dessin du modèle (bornes dans ses axes, à sa taille d'origine) ;
     * tant que le moteur ne le donne pas, les valeurs par défaut restent et seront corrigées plus tard. Les réglages
     * de l'apparence passent devant.
     */
    private void bornes(Jake jake) {
        Settings s = plugin.settings();
        Settings.Apparence a = jake.dessinee();
        if (a == null) {
            return;
        }
        double[] b = Double.isNaN(a.hauteur()) || Double.isNaN(a.zoneLargeur()) || Double.isNaN(a.zoneHauteur())
                ? modele.bornes(a.modele()) : null;
        boolean connues = b != null || !Double.isNaN(a.hauteur()) && !Double.isNaN(a.zoneLargeur()) && !Double.isNaN(a.zoneHauteur());
        if (!connues) {
            return;
        }
        double hauteur = !Double.isNaN(a.hauteur()) ? a.hauteur() : b[4] * a.taille() + s.textMargin;
        double largeur = !Double.isNaN(a.zoneLargeur()) ? a.zoneLargeur()
                : 2 * Math.max(Math.max(Math.abs(b[0]), Math.abs(b[3])), Math.max(Math.abs(b[2]), Math.abs(b[5]))) * a.taille();
        double zoneHauteur = !Double.isNaN(a.zoneHauteur()) ? a.zoneHauteur() : b[4] * a.taille();
        float l = (float) Math.min(s.maxZoneWidth, Math.max(0.1, largeur));
        float h = (float) Math.max(0.1, zoneHauteur);
        boolean change = Math.abs(hauteur - jake.hauteurNormale()) > 1e-3 || l != jake.zoneLargeur() || h != jake.zoneHauteur();
        jake.dessine(modele, a, hauteur, l, h);
        jake.setBornesProvisoires(false);
        blocs(jake, false);
        if (change && tracker != null) {
            // textes et zone renvoyés à la bonne hauteur ; le corps reste
            tracker.forgetJake(jake);
            textes.oublierJake(jake);
        }
    }

    /** Numéros des textes et de la zone, tirés au premier dessin ; la zone mène au Jake quand on clique dessus. */
    private void numeroter(Jake jake) {
        if (!jake.numerote()) {
            jake.numeros(Paquets.nouveauNumero(), Paquets.nouveauNumero(), Paquets.nouveauNumero());
            registry.link(jake.zoneId(), jake);
        }
    }

    private void signaler(Settings.Apparence a, EssaisModele.Resultat r) {
        if (!signales.add("apparence/" + a.id())) {
            return;
        }
        String raison = !modele.disponible() ? "BlockDisplayCreator absent ou sans modèles pour les autres plugins"
                : r == EssaisModele.Resultat.PLUS_TARD ? "le moteur ne montre toujours pas le modèle « " + a.modele() + " »"
                : "modèle « " + a.modele() + " » inconnu de BlockDisplayCreator";
        plugin.getLogger().warning("Apparence " + a.id() + " : " + raison + ". Jake est dessiné en "
                + plugin.settings().secours.id() + " à la place (nouvel essai toutes les "
                + plugin.settings().modelRetryTicks / 20 + " s).");
    }

    /**
     * Le modèle du Jake a-t-il cette animation ? Sinon un avertissement, une fois par modèle et par animation (une
     * scène écrite dans « types » mais absente du fichier du modèle).
     */
    boolean animationConnue(Jake jake, String animation) {
        Settings.Apparence a = jake.dessinee();
        if (jake.corps() != modele || a == null || modele.aAnimation(a.modele(), animation)) {
            return true;
        }
        if (signales.add("animation/" + a.modele() + "/" + animation)) {
            plugin.getLogger().warning("Apparence " + a.id() + " : l'animation « " + animation + " » n'existe pas dans le modèle « "
                    + a.modele() + " » (types." + a.id() + ".scenes).");
        }
        return false;
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

    void arreter(Jake jake, Player player) {
        Corps corps = jake.corps();
        if (corps != null) {
            corps.arreter(jake, player);
        }
    }

    /**
     * Durée d'une animation en ticks : celle que donne le moteur (s'il sait la dire), sinon ambiance.durees du modèle,
     * sinon ambiance.duree-defaut-secondes.
     */
    int duree(Jake jake, String animation) {
        Corps corps = jake.corps();
        int d = corps == null ? -1 : corps.duree(jake, animation);
        if (d > 0) {
            return d;
        }
        Settings.Apparence a = jake.dessinee();
        return plugin.settings().animationTicks(a == null ? "" : a.modele(), animation);
    }

    /** Le Jake est-il dessiné par un modèle ? */
    boolean modele(Jake jake) {
        return jake.corps() == modele;
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
    }

    /** Arrêt ou rechargement : plus rien n'est dessiné. */
    void releaseAll() {
        for (Jake jake : new ArrayList<>(registry.drawn())) {
            release(jake);
        }
        signales.clear();
        modele.oublierAnimations();
    }

    /** Le moteur est arrivé, reparti, ou son instance ou son afficheur a changé : tous les Jake sont redessinés. */
    void moteurChange() {
        if (modele.revoir()) {
            releaseAll();
        }
    }

    /** Joueur parti : les PNJ l'oublient. */
    void forget(Player player) {
        UUID id = player.getUniqueId();
        for (Jake jake : registry.drawn()) {
            jake.forget(id);
        }
    }
}
