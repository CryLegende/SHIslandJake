package org.Cry_Legende.sHIslandJake;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Section « reactions » de config.yml : les réglages communs (clés simples) et une réaction par sous-section.
 * Ne touche pas au serveur : sons et particules restent des noms, vérifiés au moment de jouer.
 */
final class ReglagesReactions {

    /** À qui une réaction peut se jouer. */
    enum Pour {
        MEMBRES,
        VISITEURS,
        TOUS;

        boolean accepte(boolean membre) {
            return this == TOUS || (this == MEMBRES) == membre;
        }
    }

    /**
     * Une réaction. {@code repliques} = nom de la liste de messages.yml (vide = pas de bulle), {@code son} vide = le son
     * de l'apparence pour cette réaction, {@code particule} vide = aucune.
     */
    record Reaction(String nom, boolean approche, List<String> si, Pour pour, String animation, String repliques,
                    String son, float volume, float pitch, String particule, int particules, long pauseMillis,
                    int priorite) {
    }

    final double distance;
    final double porteeCommande;
    final int attenteMenuTicks;
    final int menuFermeTicks;
    /** Particules : écart horizontal et vertical autour de Jake, vitesse, hauteur (part de la hauteur du nom). */
    final double particulesEcart;
    final double particulesEcartVertical;
    final double particulesVitesse;
    final double particulesHauteur;
    /** Hauteur du son au-dessus des pieds de Jake, en blocs. */
    final double sonHauteur;
    private final Map<String, Reaction> parNom;
    /** Réactions d'approche, de la plus haute priorité à la plus basse (ordre du fichier à priorité égale). */
    private final List<Reaction> approche;

    private ReglagesReactions(ConfigurationSection s, Map<String, Reaction> parNom) {
        this.distance = s == null ? 6 : Math.max(1, s.getDouble("distance", 6));
        this.porteeCommande = s == null ? 16 : Math.max(1, s.getDouble("portee-commande", 16));
        this.attenteMenuTicks = s == null ? 600 : (int) Math.round(Math.max(0, s.getDouble("attente-menu-secondes", 30)) * 20);
        this.menuFermeTicks = s == null ? 3 : Math.max(1, s.getInt("menu-ferme-ticks", 3));
        this.particulesEcart = s == null ? 0.4 : Math.max(0, s.getDouble("particules.ecart", 0.4));
        this.particulesEcartVertical = s == null ? 0.5 : Math.max(0, s.getDouble("particules.ecart-vertical", 0.5));
        this.particulesVitesse = s == null ? 0.02 : Math.max(0, s.getDouble("particules.vitesse", 0.02));
        this.particulesHauteur = s == null ? 0.55 : Math.max(0, s.getDouble("particules.hauteur", 0.55));
        this.sonHauteur = s == null ? 1.0 : s.getDouble("son-hauteur", 1.0);
        this.parNom = Collections.unmodifiableMap(parNom);
        List<Reaction> list = new ArrayList<>();
        for (Reaction r : parNom.values()) {
            if (r.approche()) {
                list.add(r);
            }
        }
        list.sort(Comparator.comparingInt(Reaction::priorite).reversed());
        this.approche = List.copyOf(list);
    }

    /** Lecture sans vérifier les noms de particules (essais). */
    static ReglagesReactions lire(ConfigurationSection section, Consumer<String> avertir) {
        return lire(section, avertir, p -> true);
    }

    /**
     * @param avertir         reçoit un message par réaction mal écrite (elle est ignorée, ou la clé fautive retirée)
     * @param particuleValide vrai pour un nom de particule utilisable sans données (vérifié par le serveur)
     */
    static ReglagesReactions lire(ConfigurationSection section, Consumer<String> avertir, Predicate<String> particuleValide) {
        Map<String, Reaction> parNom = new LinkedHashMap<>();
        if (section == null) {
            return new ReglagesReactions(null, parNom);
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(key);
            if (r == null || key.equals("particules")) {
                continue;
            }
            String nom = key.toLowerCase(Locale.ROOT);
            Pour pour;
            String pourTexte = r.getString("pour", "membres").trim().toUpperCase(Locale.ROOT);
            try {
                pour = Pour.valueOf(pourTexte);
            } catch (IllegalArgumentException e) {
                avertir.accept("reactions." + key + ".pour « " + r.getString("pour") + " » inconnu (membres, visiteurs, tous) : membres est utilisé.");
                pour = Pour.MEMBRES;
            }
            List<String> si = new ArrayList<>();
            for (String c : r.getStringList("si")) {
                if (Condition.valide(c)) {
                    si.add(c);
                } else {
                    avertir.accept("reactions." + key + ".si « " + c + " » illisible : la réaction est ignorée.");
                    si = null;
                    break;
                }
            }
            if (si == null) {
                continue;
            }
            // les conditions sans placeholder d'abord : elles ne coûtent rien et peuvent éviter de lire les autres
            List<String> triees = new ArrayList<>(si.size());
            si.stream().filter(Condition::sansPlaceholder).forEach(triees::add);
            si.stream().filter(c -> !Condition.sansPlaceholder(c)).forEach(triees::add);
            String son = r.getString("son", "").trim();
            if (!son.isEmpty() && !sonValide(son)) {
                avertir.accept("reactions." + key + ".son « " + son + " » n'est pas un nom de son (minuscules, ex. entity.villager.yes) : ignoré.");
                son = "";
            }
            String particule = "";
            int nombre = 0;
            String p = r.getString("particules", "").trim();
            if (!p.isEmpty()) {
                String[] parts = p.split("\\s+");
                particule = parts[0].toUpperCase(Locale.ROOT);
                nombre = 1;
                if (parts.length > 1) {
                    try {
                        nombre = Math.max(1, Integer.parseInt(parts[1]));
                    } catch (NumberFormatException e) {
                        avertir.accept("reactions." + key + ".particules « " + p + " » : nombre illisible, 1 est utilisé.");
                    }
                }
                if (!particuleValide.test(particule)) {
                    avertir.accept("reactions." + key + ".particules : « " + parts[0] + " » inconnue ou demande des données (couleur...) : ignorée.");
                    particule = "";
                    nombre = 0;
                }
            }
            parNom.put(nom, new Reaction(nom, r.getBoolean("approche", false), List.copyOf(triees), pour,
                    r.getString("animation", "").trim(), r.getString("repliques", "").trim(),
                    son, (float) r.getDouble("volume", 1.0), (float) r.getDouble("pitch", 1.0),
                    particule, nombre, Math.round(Math.max(0, r.getDouble("pause-secondes", 0)) * 1000),
                    r.getInt("priorite", 0)));
        }
        return new ReglagesReactions(section, parNom);
    }

    /** Nom de son Minecraft ou d'un pack (« entity.villager.yes », « monpack:jake.salut »). */
    static boolean sonValide(String son) {
        return NamespacedKey.fromString(son) != null;
    }

    Reaction get(String nom) {
        return nom == null ? null : parNom.get(nom.toLowerCase(Locale.ROOT));
    }

    Map<String, Reaction> toutes() {
        return parNom;
    }

    List<Reaction> approche() {
        return approche;
    }

    /**
     * À l'arrivée d'un joueur : la réaction possible de plus haute priorité, ou null. Les conditions (placeholders) ne
     * sont lues que pour les réactions qui passent déjà les autres filtres, dans l'ordre des priorités.
     *
     * @param libre      faux si la réaction est en pause pour ce joueur
     * @param conditions vrai si toutes les conditions « si » sont vraies pour ce joueur
     */
    Reaction choisir(boolean membre, Predicate<Reaction> libre, Predicate<Reaction> conditions) {
        for (Reaction r : approche) {
            if (r.pour().accepte(membre) && libre.test(r) && conditions.test(r)) {
                return r;
            }
        }
        return null;
    }
}
