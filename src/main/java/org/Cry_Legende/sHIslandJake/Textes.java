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
 * grossissant, s'écrit mot par mot, reste le temps de la lire puis disparaît ; l'alerte revient ensuite. Une seule
 * bulle par joueur : une nouvelle remplace l'ancienne. Aussi la zone cliquable d'un modèle. Fil principal.
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
        /** Membre de l'île de ce Jake (lu quand le joueur arrive près de lui). */
        boolean membre;
        /** Dans la zone des réactions (arrivée = passage de dehors à dedans). */
        boolean pres;
        /** Numéro du dernier passage du suivi qui l'a vu. */
        int passe;
    }

    /** La bulle d'un joueur : sa réplique découpée et où elle en est (en ticks du serveur). */
    private static final class Bulle {
        final Player joueur;
        final Jake jake;
        final Vue vue;
        final List<String> mots;
        final int debut;
        final int reste;
        int montres;
        boolean grandie;
        int finEcriture = -1;
        int finRetrecie = -1;

        Bulle(Player joueur, Jake jake, Vue vue, List<String> mots, int debut, int reste) {
            this.joueur = joueur;
            this.jake = jake;
            this.vue = vue;
            this.mots = mots;
            this.debut = debut;
            this.reste = reste;
        }
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
            Paquets.texte(envoi, jake.nomId(), jake.nomUuid(), d.x(), d.y() + jake.hauteurTexte(), d.z(), donnees);
            vue.nom = true;
        }
        if (!vue.zone && jake.aUneZone()) {
            Paquets.zone(envoi, jake.zoneId(), jake.zoneUuid(), d.x(), d.y(), d.z(), jake.zoneLargeur(), jake.zoneHauteur());
            vue.zone = true;
        }
    }

    /** L'alerte de ce joueur au-dessus du nom ("" = aucune). Pendant une bulle, elle attend la fin de la réplique. */
    void alerte(Paquets.Envoi envoi, Player joueur, Jake jake, Vue vue, String texte) {
        vue.alerte = texte;
        Bulle b = bulles.get(joueur.getUniqueId());
        if (b != null && b.jake == jake) {
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
        } else {
            JakeData d = jake.data();
            Paquets.texte(envoi, jake.paroleId(), jake.paroleUuid(), d.x(), hauteurParole(jake), d.z(),
                    Paquets.donneesTexte(lignes(vue.alerte), style(), 1f));
        }
        vue.parole = vue.alerte;
        vue.bulle = false;
    }

    /** Retire à ce joueur tout ce qu'il a reçu de ce Jake (sa bulle aussi). */
    void cacher(Paquets.Envoi envoi, Player joueur, Jake jake, Vue vue) {
        Bulle b = bulles.get(joueur.getUniqueId());
        if (b != null && b.jake == jake) {
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
     * Jake dit ce texte à ce joueur seul. Faux si le texte est vide. Remplace la bulle qu'il avait, sur ce Jake ou un
     * autre (l'alerte de l'autre revient tout de suite).
     */
    boolean bulle(Player joueur, Jake jake, Vue vue, String texte) {
        List<String> mots = BulleTexte.mots(texte);
        if (mots.isEmpty()) {
            return false;
        }
        Settings s = plugin.settings();
        Bulle ancienne = bulles.remove(joueur.getUniqueId());
        if (ancienne != null && ancienne.jake != jake) {
            Paquets.Envoi e = Paquets.envoi(joueur);
            appliquerAlerte(e, ancienne.jake, ancienne.vue);
            e.envoyer();
        }
        int maintenant = Bukkit.getCurrentTick();
        int premiers = s.ticksPerWord <= 0 ? mots.size() : 1;
        Bulle b = new Bulle(joueur, jake, vue, mots, maintenant,
                BulleTexte.resteTicks(texte, s.bubbleBaseSeconds, s.bubblePerCharacter));
        b.montres = premiers;
        b.grandie = s.bubbleGrowTicks <= 0;
        if (premiers == mots.size()) {
            b.finEcriture = maintenant;
        }
        JakeData d = jake.data();
        Paquets.Envoi envoi = Paquets.envoi(joueur);
        Paquets.texte(envoi, jake.paroleId(), jake.paroleUuid(), d.x(), hauteurParole(jake), d.z(),
                Paquets.donneesTexte(texteBulle(BulleTexte.debut(mots, premiers)), styleBulle(), b.grandie ? 1f : 0f));
        envoi.envoyer();
        vue.bulle = true;
        bulles.put(joueur.getUniqueId(), b);
        if (tache == null) {
            tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
        return true;
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
            if (!b.joueur.isOnline() || avancer(b, s, maintenant)) {
                it.remove();
            }
        }
        plugin.mesures().fin(Mesures.Tache.BULLES, start);
    }

    /** Fait avancer la bulle ; vrai quand elle est finie. Un paquet par mot écrit. */
    private boolean avancer(Bulle b, Settings s, int maintenant) {
        Paquets.Envoi envoi = null;
        int id = b.jake.paroleId();
        int age = maintenant - b.debut;
        if (!b.grandie && age >= 1) {
            envoi = Paquets.envoi(b.joueur);
            Paquets.echelle(envoi, id, 1f, s.bubbleGrowTicks);
            b.grandie = true;
        }
        if (b.montres < b.mots.size()) {
            int dus = Math.min(b.mots.size(), 1 + age / Math.max(1, s.ticksPerWord));
            if (dus > b.montres) {
                if (envoi == null) {
                    envoi = Paquets.envoi(b.joueur);
                }
                Paquets.changerTexte(envoi, id, texteBulle(BulleTexte.debut(b.mots, dus)));
                b.montres = dus;
                if (dus == b.mots.size()) {
                    b.finEcriture = maintenant;
                }
            }
        }
        if (b.finEcriture >= 0 && b.finRetrecie < 0 && maintenant >= b.finEcriture + b.reste) {
            if (s.bubbleGrowTicks > 0) {
                if (envoi == null) {
                    envoi = Paquets.envoi(b.joueur);
                }
                Paquets.echelle(envoi, id, 0f, s.bubbleGrowTicks);
            }
            b.finRetrecie = maintenant + s.bubbleGrowTicks;
        }
        boolean finie = b.finRetrecie >= 0 && maintenant >= b.finRetrecie;
        if (finie) {
            if (envoi == null) {
                envoi = Paquets.envoi(b.joueur);
            }
            // l'alerte (ou rien) reprend la place de la bulle
            appliquerAlerte(envoi, b.jake, b.vue);
        }
        if (envoi != null) {
            envoi.envoyer();
        }
        return finie;
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

    /** Chaque ligne est lue à part : ses couleurs ne débordent pas sur la suivante. */
    static Component lignes(String texte) {
        String[] lignes = texte.split("\n", -1);
        List<Component> parts = new ArrayList<>(lignes.length);
        for (String ligne : lignes) {
            parts.add(Messages.chat(ligne));
        }
        return Component.join(JoinConfiguration.newlines(), parts);
    }
}
