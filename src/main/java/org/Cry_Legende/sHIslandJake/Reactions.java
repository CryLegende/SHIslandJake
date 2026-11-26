package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

/**
 * Les réactions de Jake, jouées pour le seul joueur concerné : à son arrivée près de Jake (réactions « approche ») ou
 * demandées par commande (/shislandjake react, say). Une réaction : un geste du modèle, une réplique dans une bulle,
 * un son et des particules. Les conditions (placeholders) ne sont lues qu'à l'arrivée et pour une commande. Un joueur
 * qui a un menu ouvert attend sa fermeture ; un joueur « discret » ne reçoit rien. Aussi les scènes d'ambiance : une
 * animation jouée pour un joueur proche qui a Jake devant lui, quand rien d'autre ne se passe pour lui. Fil principal.
 */
final class Reactions {

    /** Ce qui attend la fermeture d'un menu : une réaction (null = bulle seule) et son texte imposé. */
    private record Demande(String reaction, String texte) {
    }

    /** Le Jake le plus proche que ce joueur voit, et ce qu'il en a reçu. */
    private record Proche(Jake jake, Textes.Vue vue) {
    }

    private final SHIslandJake plugin;
    private final Pauses pauses = new Pauses();
    private final Repliques repliques = new Repliques(new Random());
    private final Attentes<Demande> attentes = new Attentes<>();
    private final Ambiance ambiance = new Ambiance(new Random());
    private final Map<String, Particle> particules = new HashMap<>();
    private final Signalements erreurs = new Signalements();
    /** Jake que chaque joueur regarde (près, de face), lu au dernier passage lent du suivi. */
    private final Map<UUID, Jake> regarde = new HashMap<>();
    /** Animation de vie suivante de chaque joueur : une tâche à la fin de l'animation en cours, rien par tick. */
    private final Map<UUID, BukkitTask> chaines = new HashMap<>();

    Reactions(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    //DÉCLENCHEMENTS----------

    /** Le joueur vient d'arriver près de ce Jake (suivi) : la réaction d'approche possible de plus haute priorité. */
    void arrivee(Player player, Jake jake, Textes.Vue vue) {
        ReglagesReactions r = plugin.settings().reactions;
        if (r.approche().isEmpty() || plugin.discret().est(player.getUniqueId())) {
            return;
        }
        long start = plugin.mesures().debut();
        try {
            UUID id = player.getUniqueId();
            long now = System.currentTimeMillis();
            UnaryOperator<String> lu = lecteur(player);
            ReglagesReactions.Reaction choisie = r.choisir(vue.membre, x -> pauses.libre(id, x.nom(), now),
                    x -> Condition.toutes(x.si(), lu));
            if (choisie != null) {
                if (menuOuvert(player)) {
                    attendre(player, new Demande(choisie.nom(), null));
                } else {
                    jouer(player, jake, vue, choisie, null);
                }
            }
        } catch (RuntimeException e) {
            signaler("arrivee", "Réaction d'arrivée pour " + player.getName() + " impossible", e);
        } finally {
            plugin.mesures().fin(Mesures.Tache.REACTIONS, start);
        }
    }

    /**
     * /shislandjake react : la réaction pour ce joueur, abandonnée sans un mot s'il n'est pas près d'un Jake qu'il voit,
     * si elle n'est pas pour lui, en pause ou si une condition est fausse. Faux seulement si elle n'existe pas.
     *
     * @param texte remplace la réplique (pris tel quel : l'appelant a déjà lu ses placeholders) ; null ou vide = une
     *              réplique de la liste de la réaction
     */
    boolean commande(Player player, String nom, String texte) {
        ReglagesReactions.Reaction r = plugin.settings().reactions.get(nom);
        if (r == null) {
            return false;
        }
        long start = plugin.mesures().debut();
        try {
            if (plugin.discret().est(player.getUniqueId())) {
                return true;
            }
            Proche proche = proche(player);
            if (proche == null || !r.pour().accepte(proche.vue().membre)
                    || !pauses.libre(player.getUniqueId(), r.nom(), System.currentTimeMillis())
                    || !Condition.toutes(r.si(), lecteur(player))) {
                return true;
            }
            if (menuOuvert(player)) {
                attendre(player, new Demande(r.nom(), texte));
            } else {
                jouer(player, proche.jake(), proche.vue(), r, texte);
            }
            return true;
        } catch (RuntimeException e) {
            signaler("commande", "Réaction « " + nom + " » pour " + player.getName() + " impossible", e);
            return true;
        } finally {
            plugin.mesures().fin(Mesures.Tache.REACTIONS, start);
        }
    }

    /** /shislandjake say : une bulle seule, avec les mêmes règles (discret, portée, menu ouvert). Texte pris tel quel. */
    void dire(Player player, String texte) {
        long start = plugin.mesures().debut();
        try {
            if (!plugin.discret().est(player.getUniqueId())) {
                Proche proche = proche(player);
                if (proche != null) {
                    if (menuOuvert(player)) {
                        attendre(player, new Demande(null, texte));
                    } else {
                        bulle(player, proche.jake(), proche.vue(), jetons(player, proche.jake(), texte));
                    }
                }
            }
        } finally {
            plugin.mesures().fin(Mesures.Tache.REACTIONS, start);
        }
    }

    //MENU OUVERT----------

    /** Un menu est ouvert quand la vue du joueur n'est pas son propre inventaire. */
    static boolean menuOuvert(Player player) {
        InventoryType type = player.getOpenInventory().getTopInventory().getType();
        return type != InventoryType.CRAFTING && type != InventoryType.CREATIVE;
    }

    /** Une seule attente par joueur : la dernière demandée ; abandonnée après « attente-menu-secondes ». */
    private void attendre(Player player, Demande demande) {
        attentes.demander(player.getUniqueId(), demande, Bukkit.getCurrentTick(), plugin.settings().reactions.attenteMenuTicks);
    }

    /** Une réaction attend-elle la fermeture d'un menu de ce joueur ? Lisible depuis n'importe quel fil. */
    boolean attend(UUID player) {
        return attentes.contient(player);
    }

    /** Un menu de ce joueur vient de se fermer : sa réaction part si aucun menu ne s'est rouvert entre-temps. */
    void fermeture(Player player) {
        UUID id = player.getUniqueId();
        if (!attentes.contient(id)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                attentes.oublier(id);
                return;
            }
            Demande demande = attentes.verifier(id, Bukkit.getCurrentTick(), menuOuvert(player));
            if (demande != null) {
                long start = plugin.mesures().debut();
                try {
                    rejouer(player, demande);
                } catch (RuntimeException e) {
                    signaler("attente", "Réaction en attente pour " + player.getName() + " impossible", e);
                } finally {
                    plugin.mesures().fin(Mesures.Tache.REACTIONS, start);
                }
            }
        }, plugin.settings().reactions.menuFermeTicks);
    }

    private void rejouer(Player player, Demande demande) {
        if (plugin.discret().est(player.getUniqueId())) {
            return;
        }
        Proche proche = proche(player);
        if (proche == null) {
            return;
        }
        if (demande.reaction() == null) {
            bulle(player, proche.jake(), proche.vue(), jetons(player, proche.jake(), demande.texte()));
            return;
        }
        ReglagesReactions.Reaction r = plugin.settings().reactions.get(demande.reaction());
        // le joueur a pu changer de Jake pendant son menu : la réaction doit être pour lui sur celui-ci
        if (r != null && r.pour().accepte(proche.vue().membre)) {
            jouer(player, proche.jake(), proche.vue(), r, demande.texte());
        }
    }

    /**
     * Clic d'un membre sur Jake : le geste « clic » du modèle pour lui seul (s'il existe). C'est une réaction pour
     * l'ambiance : aucune scène ne le coupe, et il remplace la scène en cours.
     */
    void clic(Player player, Jake jake) {
        Settings s = plugin.settings();
        UUID id = player.getUniqueId();
        if (plugin.discret().est(id) || s.clickAnimation.isEmpty()) {
            return;
        }
        int tick = Bukkit.getCurrentTick();
        if (plugin.display().jouer(jake, s.clickAnimation, player)) {
            int duree = plugin.display().duree(jake, s.clickAnimation);
            ambiance.reaction(id, tick, duree, duree, ambiance.pause(s.ambiancePauseMinTicks, s.ambiancePauseMaxTicks));
        }
    }

    //JEU----------

    /** Geste, bulle, particules, puis le son s'il accompagne l'un d'eux ; la pause commence. */
    private void jouer(Player player, Jake jake, Textes.Vue vue, ReglagesReactions.Reaction r, String texte) {
        UUID id = player.getUniqueId();
        int tick = Bukkit.getCurrentTick();
        // une réaction passe devant une scène d'ambiance : son geste remplace la scène (le moteur ramène les pièces au
        // repos en douceur) ; sans geste, la scène est arrêtée
        boolean geste = plugin.display().jouer(jake, r.animation(), player);
        if (!geste && ambiance.enScene(id, tick)) {
            plugin.display().arreter(jake, player);
            // scène arrêtée sans geste à la place : la vie reprend tout de suite
            ambiance.sceneArretee(id, tick);
            if (regarde.get(id) == jake && plugin.settings().vieEnabled) {
                vivre(player, jake);
            }
        }
        String ligne = texte != null && !texte.isBlank() ? jetons(player, jake, texte) : replique(player, jake, r);
        int dureeBulle = ligne == null ? 0 : plugin.textes().bulle(player, jake, vue, ligne);
        boolean effet = particules(player, jake, r);
        if (geste || dureeBulle > 0 || effet) {
            son(player, jake, r);
            pauses.poser(id, r.nom(), System.currentTimeMillis(), r.pauseMillis());
            int dureeGeste = geste ? plugin.display().duree(jake, r.animation()) : 0;
            Settings s = plugin.settings();
            ambiance.reaction(id, tick, Math.max(dureeBulle, dureeGeste), dureeGeste,
                    ambiance.pause(s.ambiancePauseMinTicks, s.ambiancePauseMaxTicks));
        }
    }

    private void bulle(Player player, Jake jake, Textes.Vue vue, String texte) {
        int duree = plugin.textes().bulle(player, jake, vue, texte);
        if (duree > 0) {
            Settings s = plugin.settings();
            ambiance.reaction(player.getUniqueId(), Bukkit.getCurrentTick(), duree, 0,
                    ambiance.pause(s.ambiancePauseMinTicks, s.ambiancePauseMaxTicks));
        }
    }

    /**
     * Une ligne de repliques.<caractère>.<liste>, sinon de repliques.defaut.<liste> ; null s'il n'y en a pas. Ses
     * placeholders sont lus pour le joueur (une fois), puis {joueur}, {nom} et {chef} sont remplacés : ce qui vient d'un
     * joueur n'est jamais relu par PlaceholderAPI.
     */
    private String replique(Player player, Jake jake, ReglagesReactions.Reaction r) {
        if (r.repliques().isEmpty()) {
            return null;
        }
        String caractere = apparence(jake).caractere();
        List<String> lignes = plugin.messages().lines("repliques." + caractere + "." + r.repliques());
        String cle = caractere + "." + r.repliques();
        if (lignes.isEmpty() && !caractere.equals("defaut")) {
            lignes = plugin.messages().lines("repliques.defaut." + r.repliques());
            cle = "defaut." + r.repliques();
        }
        String ligne = repliques.tirer(player.getUniqueId(), cle, lignes);
        if (ligne == null) {
            return null;
        }
        if (ligne.indexOf('%') >= 0 && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            ligne = PlaceholderAPI.setPlaceholders(player, ligne);
        }
        return jetons(player, jake, ligne);
    }

    /** {joueur}, {nom} (nom de Jake), {chef} (chef de l'île). */
    private String jetons(Player player, Jake jake, String texte) {
        String chef = "";
        Island island = plugin.islands().byId(jake.island());
        SuperiorPlayer owner = island == null ? null : island.getOwner();
        if (owner != null && owner.getName() != null) {
            chef = owner.getName();
        }
        return Messages.replace(texte, "joueur", player.getName(), "nom",
                plugin.messages().jakeName(jake.data().type()), "chef", chef);
    }

    private void son(Player player, Jake jake, ReglagesReactions.Reaction r) {
        String son = r.son();
        if (son.isEmpty()) {
            son = apparence(jake).sons().getOrDefault(r.nom(), "");
        }
        if (son.isEmpty()) {
            return;
        }
        JakeData d = jake.data();
        try {
            player.playSound(new Location(player.getWorld(), d.x(), d.y() + plugin.settings().reactions.sonHauteur, d.z()),
                    son, r.volume(), r.pitch());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Son « " + son + " » de la réaction " + r.nom() + " impossible : " + e.getMessage());
        }
    }

    private boolean particules(Player player, Jake jake, ReglagesReactions.Reaction r) {
        if (r.particule().isEmpty()) {
            return false;
        }
        // nom vérifié à la lecture de la configuration
        Particle particle = particules.computeIfAbsent(r.particule(), name -> Particle.valueOf(name.toUpperCase(Locale.ROOT)));
        ReglagesReactions s = plugin.settings().reactions;
        JakeData d = jake.data();
        double hauteur = Math.max(0.3, jake.hauteurTexte() * s.particulesHauteur);
        player.spawnParticle(particle, d.x(), d.y() + hauteur, d.z(), r.particules(), s.particulesEcart,
                s.particulesEcartVertical, s.particulesEcart, s.particulesVitesse);
        return true;
    }

    //AMBIANCE----------

    /**
     * Passage du suivi : le Jake que ce joueur regarde (près, dessiné par un modèle, devant lui). Une scène d'ambiance
     * s'il n'est pas discret et que rien d'autre ne se passe pour lui (bulle, réaction en cours ou en attente, pause),
     * et la vie de Jake si elle ne joue pas déjà pour lui. S'il ne regarde plus Jake, l'animation de vie en cours finit
     * et rien n'est relancé.
     */
    void ambiance(Player player, Map<Jake, Textes.Vue> vues, Location ici) {
        Settings s = plugin.settings();
        UUID id = player.getUniqueId();
        int tick = Bukkit.getCurrentTick();
        if (!s.ambianceEnabled || vues == null) {
            regarde.remove(id);
            return;
        }
        double max = s.ambianceDistance * s.ambianceDistance;
        Jake choisi = null;
        double best = Double.MAX_VALUE;
        for (Map.Entry<Jake, Textes.Vue> entry : vues.entrySet()) {
            Jake jake = entry.getKey();
            double d2 = entry.getValue().distance2;
            Settings.Apparence a = apparence(jake);
            if (d2 <= max && d2 < best && plugin.display().modele(jake) && (!a.scenes().isEmpty() || !a.vie().isEmpty())) {
                best = d2;
                choisi = jake;
            }
        }
        if (choisi == null) {
            regarde.remove(id);
            return;
        }
        JakeData d = choisi.data();
        double yeux = ici.getY() + player.getEyeHeight();
        if (!Ambiance.devant(ici.getX(), yeux, ici.getZ(), ici.getYaw(), ici.getPitch(),
                d.x(), d.y() + choisi.hauteurTexte() * 0.5, d.z(), s.ambianceCosine)) {
            regarde.remove(id);
            return;
        }
        regarde.put(id, choisi);
        scene(player, choisi, tick);
        // la vie : elle part si rien ne la fait déjà vivre (animation en cours ou suite prévue)
        if (s.vieEnabled && !apparence(choisi).vie().isEmpty() && !ambiance.enVie(id, tick) && !chaines.containsKey(id)) {
            long start = plugin.mesures().debut();
            vivre(player, choisi);
            plugin.mesures().fin(Mesures.Tache.VIE, start);
        }
    }

    /** Une scène d'ambiance pour ce joueur, si elle peut partir. */
    private void scene(Player player, Jake jake, int tick) {
        Settings s = plugin.settings();
        UUID id = player.getUniqueId();
        if (!ambiance.libre(id, tick) || plugin.discret().est(id) || plugin.textes().parle(id) || attentes.contient(id)) {
            return;
        }
        List<String> scenes = apparence(jake).scenes().stream().filter(sc -> plugin.display().animationConnue(jake, sc)).toList();
        if (scenes.isEmpty()) {
            return;
        }
        String scene = ambiance.tirer(id, scenes);
        int pause = ambiance.pause(s.ambiancePauseMinTicks, s.ambiancePauseMaxTicks);
        if (plugin.display().jouer(jake, scene, player)) {
            ambiance.scene(id, scene, tick, plugin.display().duree(jake, scene), pause);
        } else {
            // scène refusée par le moteur : pas de nouvel essai avant la pause
            ambiance.scene(id, scene, tick, 0, pause);
        }
    }

    /**
     * La vie de Jake pour ce joueur : une animation de vie tout de suite, ou à la fin de la scène ou du geste qui joue,
     * ou plus rien s'il ne regarde plus ce Jake. La suivante est prévue à la fin exacte de celle-ci (sans trou).
     */
    private void vivre(Player player, Jake jake) {
        Settings s = plugin.settings();
        UUID id = player.getUniqueId();
        BukkitTask prevue = chaines.remove(id);
        if (prevue != null) {
            prevue.cancel();
        }
        int tick = Bukkit.getCurrentTick();
        boolean vu = player.isOnline() && regarde.get(id) == jake && plugin.display().modele(jake);
        boolean permise = s.vieEnabled && (s.vieSiDiscret || !plugin.discret().est(id));
        switch (ambiance.decider(id, tick, vu, permise)) {
            case ARRETER -> ambiance.finVie(id);
            case ATTENDRE -> planifier(player, jake, ambiance.reprise(id) - tick);
            case JOUER -> {
                List<String> vie = apparence(jake).vie().stream().filter(v -> plugin.display().animationConnue(jake, v)).toList();
                String animation = ambiance.tirerVie(id, vie);
                if (animation != null && plugin.display().jouer(jake, animation, player)) {
                    int duree = plugin.display().duree(jake, animation);
                    ambiance.vie(id, animation, tick, duree);
                    planifier(player, jake, duree);
                } else {
                    ambiance.finVie(id);
                }
            }
        }
    }

    private void planifier(Player player, Jake jake, int ticks) {
        UUID id = player.getUniqueId();
        BukkitTask avant = chaines.remove(id);
        if (avant != null) {
            avant.cancel();
        }
        chaines.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long start = plugin.mesures().debut();
            try {
                vivre(player, jake);
            } catch (RuntimeException e) {
                chaines.remove(id);
                signaler("vie", "Vie de Jake pour " + player.getName() + " impossible", e);
            } finally {
                plugin.mesures().fin(Mesures.Tache.VIE, start);
            }
        }, Math.max(1, ticks)));
    }

    //OUTILS----------

    /** Une même erreur n'est écrite qu'une fois en console. */
    private void signaler(String contexte, String message, RuntimeException e) {
        if (erreurs.premiere(contexte, e)) {
            plugin.getLogger().log(Level.WARNING, message, e);
        }
    }

    /** Lit les placeholders d'un texte pour ce joueur ; un même texte n'est lu qu'une fois par évaluation. */
    static UnaryOperator<String> lecteur(Player player) {
        boolean papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        Map<String, String> lus = new HashMap<>(4);
        return text -> !papi || text.indexOf('%') < 0 ? text
                : lus.computeIfAbsent(text, t -> PlaceholderAPI.setPlaceholders(player, t));
    }

    private Settings.Apparence apparence(Jake jake) {
        Settings.Apparence a = jake.dessinee();
        return a != null ? a : plugin.settings().apparence(jake.data().type());
    }

    /** Le Jake le plus proche que ce joueur voit, à moins de « portee-commande » blocs ; null sinon. */
    private Proche proche(Player player) {
        Map<Jake, Textes.Vue> vues = plugin.tracker().vues(player.getUniqueId());
        if (vues == null) {
            return null;
        }
        double max = plugin.settings().reactions.porteeCommande;
        double best = max * max;
        Proche proche = null;
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        for (Map.Entry<Jake, Textes.Vue> entry : vues.entrySet()) {
            JakeData d = entry.getKey().data();
            if (entry.getKey().corps() == null || !d.world().equals(player.getWorld().getName())) {
                continue;
            }
            double dist = (px - d.x()) * (px - d.x()) + (py - d.y()) * (py - d.y()) + (pz - d.z()) * (pz - d.z());
            if (dist <= best) {
                best = dist;
                proche = new Proche(entry.getKey(), entry.getValue());
            }
        }
        return proche;
    }

    /** Joueur parti : son attente, ses dernières répliques et son ambiance sont oubliées (ses pauses restent). */
    void oublier(UUID player) {
        attentes.oublier(player);
        repliques.oublier(player);
        ambiance.oublier(player);
        regarde.remove(player);
        BukkitTask chaine = chaines.remove(player);
        if (chaine != null) {
            chaine.cancel();
        }
    }

    /** Ménage des pauses finies et des attentes dépassées. */
    void nettoyer() {
        pauses.nettoyer(System.currentTimeMillis());
        attentes.expirer(Bukkit.getCurrentTick());
    }

    /** Rechargement : les particules sont relues. */
    void recharge() {
        particules.clear();
    }
}
