package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Les réactions de Jake, jouées pour le seul joueur concerné : à son arrivée près de Jake (réactions « approche ») ou
 * demandées par commande (/shislandjake react, say). Une réaction : un geste du modèle, une réplique dans une bulle,
 * un son et des particules. Les conditions (placeholders) ne sont lues qu'à l'arrivée et pour une commande. Un joueur
 * qui a un menu ouvert attend sa fermeture ; un joueur « discret » ne reçoit rien. Fil principal.
 */
final class Reactions {

    /** Réaction en attente de la fermeture d'un menu ; {@code reaction} null = bulle seule (say). */
    private record Attente(String reaction, String texte, long jeton) {
    }

    /** Le Jake le plus proche que ce joueur voit, et ce qu'il en a reçu. */
    private record Proche(Jake jake, Textes.Vue vue) {
    }

    private final SHIslandJake plugin;
    private final Pauses pauses = new Pauses();
    private final Repliques repliques = new Repliques(new Random());
    private final Map<UUID, Attente> attentes = new HashMap<>();
    private final Map<String, Particle> particules = new HashMap<>();
    private final Set<String> signales = new HashSet<>();
    private long jetons;

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
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        ReglagesReactions.Reaction choisie = r.choisir(vue.membre, x -> pauses.libre(id, x.nom(), now),
                x -> conditions(player, x));
        if (choisie != null) {
            if (menuOuvert(player)) {
                attendre(player, choisie.nom(), null);
            } else {
                jouer(player, jake, vue, choisie, null);
            }
        }
        plugin.mesures().fin(Mesures.Tache.REACTIONS, start);
    }

    /**
     * /shislandjake react : la réaction pour ce joueur, abandonnée sans un mot s'il n'est pas près d'un Jake qu'il voit,
     * si elle n'est pas pour lui, en pause ou si une condition est fausse. Faux seulement si elle n'existe pas.
     *
     * @param texte remplace la réplique ; null ou vide = une réplique de la liste de la réaction
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
                    || !conditions(player, r)) {
                return true;
            }
            if (menuOuvert(player)) {
                attendre(player, r.nom(), texte);
            } else {
                jouer(player, proche.jake(), proche.vue(), r, texte);
            }
            return true;
        } finally {
            plugin.mesures().fin(Mesures.Tache.REACTIONS, start);
        }
    }

    /** /shislandjake say : une bulle seule, avec les mêmes règles (discret, portée, menu ouvert). */
    void dire(Player player, String texte) {
        long start = plugin.mesures().debut();
        if (!plugin.discret().est(player.getUniqueId())) {
            Proche proche = proche(player);
            if (proche != null) {
                if (menuOuvert(player)) {
                    attendre(player, null, texte);
                } else {
                    plugin.textes().bulle(player, proche.jake(), proche.vue(), resoudre(player, proche.jake(), texte));
                }
            }
        }
        plugin.mesures().fin(Mesures.Tache.REACTIONS, start);
    }

    //MENU OUVERT----------

    /** Un menu est ouvert quand la vue du joueur n'est pas son propre inventaire. */
    static boolean menuOuvert(Player player) {
        InventoryType type = player.getOpenInventory().getTopInventory().getType();
        return type != InventoryType.CRAFTING && type != InventoryType.CREATIVE;
    }

    /** Une seule attente par joueur : la dernière demandée ; abandonnée après « attente-menu-secondes ». */
    private void attendre(Player player, String reaction, String texte) {
        UUID id = player.getUniqueId();
        long jeton = ++jetons;
        attentes.put(id, new Attente(reaction, texte, jeton));
        int ticks = plugin.settings().reactions.attenteMenuTicks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Attente a = attentes.get(id);
            if (a != null && a.jeton() == jeton) {
                attentes.remove(id);
            }
        }, Math.max(1, ticks));
    }

    /** Un menu de ce joueur vient de se fermer : sa réaction part si aucun menu ne s'est rouvert entre-temps. */
    void fermeture(Player player) {
        UUID id = player.getUniqueId();
        Attente attente = attentes.get(id);
        if (attente == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (attentes.get(id) != attente || !player.isOnline() || menuOuvert(player)) {
                return;
            }
            attentes.remove(id);
            long start = plugin.mesures().debut();
            rejouer(player, attente);
            plugin.mesures().fin(Mesures.Tache.REACTIONS, start);
        }, plugin.settings().reactions.menuFermeTicks);
    }

    private void rejouer(Player player, Attente attente) {
        if (plugin.discret().est(player.getUniqueId())) {
            return;
        }
        Proche proche = proche(player);
        if (proche == null) {
            return;
        }
        if (attente.reaction() == null) {
            plugin.textes().bulle(player, proche.jake(), proche.vue(), resoudre(player, proche.jake(), attente.texte()));
            return;
        }
        ReglagesReactions.Reaction r = plugin.settings().reactions.get(attente.reaction());
        if (r != null) {
            jouer(player, proche.jake(), proche.vue(), r, attente.texte());
        }
    }

    //JEU----------

    /** Geste, bulle, particules, puis le son s'il accompagne l'un d'eux ; la pause commence. */
    private void jouer(Player player, Jake jake, Textes.Vue vue, ReglagesReactions.Reaction r, String texte) {
        boolean geste = plugin.display().jouer(jake, r.animation(), player);
        String ligne = texte != null && !texte.isBlank() ? texte : replique(player, jake, r);
        boolean bulle = ligne != null && plugin.textes().bulle(player, jake, vue, resoudre(player, jake, ligne));
        boolean effet = particules(player, jake, r);
        if (geste || bulle || effet) {
            son(player, jake, r);
            pauses.poser(player.getUniqueId(), r.nom(), System.currentTimeMillis(), r.pauseMillis());
        }
    }

    /** Une ligne de repliques.<caractère>.<liste>, sinon de repliques.defaut.<liste> ; null s'il n'y en a pas. */
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
        return repliques.tirer(player.getUniqueId(), cle, lignes);
    }

    /** {joueur}, {nom} (nom de Jake), {chef} (chef de l'île), puis les placeholders lus pour le joueur. */
    String resoudre(Player player, Jake jake, String texte) {
        String chef = "";
        Island island = plugin.islands().byId(jake.island());
        SuperiorPlayer owner = island == null ? null : island.getOwner();
        if (owner != null && owner.getName() != null) {
            chef = owner.getName();
        }
        String out = Messages.replace(texte, "joueur", player.getName(), "nom",
                plugin.messages().jakeName(jake.data().type()), "chef", chef);
        if (out.indexOf('%') >= 0 && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            out = PlaceholderAPI.setPlaceholders(player, out);
        }
        return out;
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
        player.playSound(new org.bukkit.Location(player.getWorld(), d.x(), d.y() + 1, d.z()), son, r.volume(), r.pitch());
    }

    private boolean particules(Player player, Jake jake, ReglagesReactions.Reaction r) {
        if (r.particule().isEmpty()) {
            return false;
        }
        Particle particle = particules.computeIfAbsent(r.particule(), name -> {
            try {
                return Particle.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        });
        if (particle == null) {
            signaler("particule-" + r.particule(), "reactions." + r.nom() + ".particules : « " + r.particule() + " » inconnue.");
            return false;
        }
        JakeData d = jake.data();
        double milieu = Math.max(0.6, jake.hauteurTexte() * 0.55);
        try {
            player.spawnParticle(particle, d.x(), d.y() + milieu, d.z(), r.particules(), 0.4, 0.5, 0.4, 0.02);
            return true;
        } catch (IllegalArgumentException e) {
            signaler("particule-" + r.particule(), "reactions." + r.nom() + ".particules : « " + r.particule()
                    + " » demande des données (couleur...), elle ne peut pas servir ici.");
            return false;
        }
    }

    private void signaler(String cle, String message) {
        if (signales.add(cle)) {
            plugin.getLogger().warning(message);
        }
    }

    //OUTILS----------

    private boolean conditions(Player player, ReglagesReactions.Reaction r) {
        if (r.si().isEmpty()) {
            return true;
        }
        boolean papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        return Condition.toutes(r.si(), text -> papi && text.indexOf('%') >= 0 ? PlaceholderAPI.setPlaceholders(player, text) : text);
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

    /** Joueur parti : son attente et ses dernières répliques sont oubliées (ses pauses restent jusqu'au redémarrage). */
    void oublier(UUID player) {
        attentes.remove(player);
        repliques.oublier(player);
    }

    /** Ménage des pauses finies. */
    void nettoyer() {
        pauses.nettoyer(System.currentTimeMillis());
    }

    /** Rechargement : les messages déjà signalés peuvent l'être de nouveau. */
    void recharge() {
        particules.clear();
        signales.clear();
    }
}
