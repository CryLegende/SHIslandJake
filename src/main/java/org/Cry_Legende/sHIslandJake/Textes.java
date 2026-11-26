package org.Cry_Legende.sHIslandJake;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les textes au-dessus de Jake, envoyés en paquets à chaque joueur qui le voit : le nom (lignes « hologramme ») et,
 * au-dessus, la parole : la ligne d'alerte de ce joueur, ou une bulle quand Jake lui parle. Une bulle apparaît en
 * grossissant, s'écrit mot par mot, reste le temps de la lire puis disparaît ({@link BulleEtat}) ; l'alerte revient
 * ensuite. Une seule bulle par joueur : une nouvelle remplace l'ancienne. Aussi la zone cliquable d'un modèle. Une
 * entité n'est notée comme envoyée que si son paquet est parti. Fil principal.
 */
final class Textes {

    /** Ce qu'un joueur a reçu d'un Jake (tenu par le suivi). */
    static final class Vue {
        boolean nom;
        boolean zone;
        /** Alerte voulue pour ce joueur ; "" = aucune. */
        String alerte = "";
        /** Ce que montre l'entité de parole hors bulle ; "" = rien envoyé. */
        String parole = "";
        /** L'entité de parole montre une bulle : l'alerte sera renvoyée à la fin. */
        boolean bulle;
        /** Membre de l'île de ce Jake, et tick où il sera relu (un joueur peut être exclu ou invité). */
        boolean membre;
        int membreRelu;
        /** Distance au carré au dernier passage complet. */
        double distance2;
        /** Numéro du dernier passage du suivi qui l'a vu. */
        int passe;
    }

    /** La bulle d'un joueur : où elle en est. */
    private record Bulle(Player joueur, Jake jake, Vue vue, List<String> mots, BulleEtat etat) {
    }

    private final SHIslandJake plugin;
    private final Map<UUID, Bulle> bulles = new HashMap<>();
    /** Données du nom de chaque Jake, préparées une fois (vidées quand il est redessiné). */
    private final Map<Jake, List<EntityData<?>>> noms = new HashMap<>();
    private BukkitTask tache;

    Textes(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    //NOM, ALERTE, ZONE----------

    /** Nom et zone cliquable, s'il ne les a pas encore. */
    void montrer(Paquets.Envoi envoi, Jake jake, Vue vue) {
        JakeData d = jake.data();
        if (!vue.nom && plugin.settings().hologram) {
            List<EntityData<?>> donnees = noms.computeIfAbsent(jake, j -> Paquets.donneesTexte(nom(j), style(), 1f));
            vue.nom = Paquets.texte(envoi, jake.nomId(), jake.nomUuid(), d.x(), d.y() + jake.hauteurTexte(), d.z(), donnees);
        }
        if (!vue.zone && jake.aUneZone()) {
            vue.zone = Paquets.zone(envoi, jake.zoneId(), jake.zoneUuid(), d.x(), d.y(), d.z(), jake.zoneLargeur(), jake.zoneHauteur());
        }
    }

    /** L'alerte de ce joueur au-dessus du nom ("" = aucune). Pendant une bulle, elle attend la fin de la réplique. */
    void alerte(Paquets.Envoi envoi, Player joueur, Jake jake, Vue vue, String texte) {
        vue.alerte = texte;
        Bulle b = bulles.get(joueur.getUniqueId());
        if (b != null && b.jake() == jake) {
            return;
        }
        appliquerAlerte(envoi, jake, vue);
    }

    private void appliquerAlerte(Paquets.Envoi envoi, Jake jake, Vue vue) {
        if (!vue.bulle && vue.alerte.equals(vue.parole)) {
            return;
        }
        if (vue.alerte.isEmpty()) {
            Paquets.detruire(envoi, jake.paroleId());
            vue.parole = "";
        } else {
            JakeData d = jake.data();
            boolean parti = Paquets.texte(envoi, jake.paroleId(), jake.paroleUuid(), d.x(), hauteurParole(jake), d.z(),
                    Paquets.donneesTexte(lignes(vue.alerte), style(), 1f));
            vue.parole = parti ? vue.alerte : "";
        }
        vue.bulle = false;
    }

    /** Retire à ce joueur tout ce qu'il a reçu de ce Jake (sa bulle aussi). */
    void cacher(Paquets.Envoi envoi, Player joueur, Jake jake, Vue vue) {
        Bulle b = bulles.get(joueur.getUniqueId());
        if (b != null && b.jake() == jake) {
            bulles.remove(joueur.getUniqueId());
        }
        // détruire un numéro inconnu du client ne coûte rien : tout part, même ce qu'on croit absent
        Paquets.detruire(envoi, jake.nomId(), jake.paroleId(), jake.zoneId());
        vue.nom = false;
        vue.zone = false;
        vue.parole = "";
        vue.bulle = false;
    }

    /** Le Jake est redessiné (apparence, place) : son nom sera reconstruit. */
    void oublierJake(Jake jake) {
        noms.remove(jake);
    }

    /** Joueur parti : rien à lui envoyer. */
    void oublier(UUID joueur) {
        bulles.remove(joueur);
    }

    //BULLES----------

    /**
     * Jake dit ce texte à ce joueur seul. Renvoie la durée prévue de la bulle en ticks, 0 si rien n'est parti (texte
     * vide, joueur sans connexion). Remplace la bulle qu'il avait, sur ce Jake ou un autre (l'alerte de l'autre revient
     * tout de suite).
     */
    int bulle(Player joueur, Jake jake, Vue vue, String texte) {
        // « ! », « ? »... ne restent jamais seuls en début de ligne : l'espace devant devient insécable
        List<String> mots = BulleTexte.mots(Typo.insecables(texte));
        if (mots.isEmpty()) {
            return 0;
        }
        Settings s = plugin.settings();
        Bulle ancienne = bulles.remove(joueur.getUniqueId());
        if (ancienne != null && ancienne.jake() != jake) {
            Paquets.Envoi e = Paquets.envoi(joueur);
            appliquerAlerte(e, ancienne.jake(), ancienne.vue());
            e.envoyer();
        }
        BulleEtat etat = new BulleEtat(mots.size(), Bukkit.getCurrentTick(), s.ticksPerWord, s.bubbleGrowTicks,
                BulleTexte.resteTicks(texte, s.bubbleBaseSeconds, s.bubblePerCharacter));
        JakeData d = jake.data();
        Paquets.Envoi envoi = Paquets.envoi(joueur);
        boolean parti = Paquets.texte(envoi, jake.paroleId(), jake.paroleUuid(), d.x(), hauteurParole(jake), d.z(),
                Paquets.donneesTexte(texteBulle(BulleTexte.debut(mots, etat.premiers())), styleBulle(), etat.echelleDepart()));
        envoi.envoyer();
        if (!parti) {
            return 0;
        }
        vue.bulle = true;
        bulles.put(joueur.getUniqueId(), new Bulle(joueur, jake, vue, mots, etat));
        if (tache == null) {
            tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
        return etat.dureeTicks();
    }

    void arreter() {
        if (tache != null) {
            tache.cancel();
            tache = null;
        }
        bulles.clear();
        noms.clear();
    }

    /** Un tick : ne parcourt que les bulles en cours ; la tâche s'arrête quand il n'y en a plus. */
    private void tick() {
        if (bulles.isEmpty()) {
            tache.cancel();
            tache = null;
            return;
        }
        long start = plugin.mesures().debut();
        Settings s = plugin.settings();
        int maintenant = Bukkit.getCurrentTick();
        Iterator<Bulle> it = bulles.values().iterator();
        while (it.hasNext()) {
            Bulle b = it.next();
            if (!b.joueur().isOnline() || avancer(b, s, maintenant)) {
                it.remove();
            }
        }
        plugin.mesures().fin(Mesures.Tache.BULLES, start);
    }

    /** Fait avancer la bulle ; vrai quand elle est finie. Un paquet par mot écrit. */
    private boolean avancer(Bulle b, Settings s, int maintenant) {
        BulleEtat.Pas pas = b.etat().avancer(maintenant);
        if (pas.rien()) {
            return false;
        }
        Paquets.Envoi envoi = Paquets.envoi(b.joueur());
        int id = b.jake().paroleId();
        if (pas.grandir()) {
            Paquets.echelle(envoi, id, 1f, s.bubbleGrowTicks);
        }
        if (pas.mots() >= 0) {
            Paquets.changerTexte(envoi, id, texteBulle(BulleTexte.debut(b.mots(), pas.mots())));
        }
        if (pas.retrecir()) {
            Paquets.echelle(envoi, id, 0f, s.bubbleGrowTicks);
        }
        if (pas.fin()) {
            // l'alerte (ou rien) reprend la place de la bulle
            appliquerAlerte(envoi, b.jake(), b.vue());
        }
        envoi.envoyer();
        return pas.fin();
    }

    /** Ce joueur a-t-il une bulle en cours ? */
    boolean parle(UUID joueur) {
        return bulles.containsKey(joueur);
    }

    //TEXTES----------

    private double hauteurParole(Jake jake) {
        return jake.data().y() + jake.hauteurTexte() + plugin.settings().alertOffset;
    }

    private Paquets.Style style() {
        Settings s = plugin.settings();
        return new Paquets.Style(s.billboard, s.background, s.textShadow, s.lineWidth);
    }

    private Paquets.Style styleBulle() {
        Settings s = plugin.settings();
        return new Paquets.Style(s.billboard, s.bubbleBackground, s.textShadow, s.bubbleLineWidth);
    }

    private Component nom(Jake jake) {
        Messages m = plugin.messages();
        return lignes(String.join("\n", m.lines("hologramme", "nom", m.jakeName(jake.data().type()))));
    }

    private Component texteBulle(String partie) {
        return lignes(plugin.messages().text("bulle.format", "texte", partie));
    }

    /**
     * Chaque ligne est lue à part : ses couleurs ne débordent pas sur la suivante. Les espaces devant « ! ? : ; » et
     * derrière « deviennent insécables.
     */
    static Component lignes(String texte) {
        String[] lignes = Typo.insecables(texte).split("\n", -1);
        List<Component> parts = new ArrayList<>(lignes.length);
        for (String ligne : lignes) {
            parts.add(Messages.chat(ligne));
        }
        return Component.join(JoinConfiguration.newlines(), parts);
    }
}
