package org.Cry_Legende.sHIslandJake;

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
    private final Map<String, Reaction> parNom;
    /** Réactions d'approche, de la plus haute priorité à la plus basse (ordre du fichier à priorité égale). */
    private final List<Reaction> approche;

    private ReglagesReactions(double distance, double porteeCommande, int attenteMenuTicks, int menuFermeTicks,
                              Map<String, Reaction> parNom) {
        this.distance = distance;
        this.porteeCommande = porteeCommande;
        this.attenteMenuTicks = attenteMenuTicks;
        this.menuFermeTicks = menuFermeTicks;
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

    /** @param avertir reçoit un message par réaction mal écrite (elle est ignorée ou corrigée) */
    static ReglagesReactions lire(ConfigurationSection section, Consumer<String> avertir) {
        if (section == null) {
            return new ReglagesReactions(6, 16, 600, 3, new LinkedHashMap<>());
        }
        double distance = Math.max(1, section.getDouble("distance", 6));
        double portee = Math.max(1, section.getDouble("portee-commande", 16));
        int attente = (int) Math.round(Math.max(0, section.getDouble("attente-menu-secondes", 30)) * 20);
        int ferme = Math.max(1, section.getInt("menu-ferme-ticks", 3));
        Map<String, Reaction> parNom = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(key);
            if (r == null) {
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
            }
            parNom.put(nom, new Reaction(nom, r.getBoolean("approche", false), List.copyOf(si), pour,
                    r.getString("animation", "").trim(), r.getString("repliques", "").trim(),
                    r.getString("son", "").trim(), (float) r.getDouble("volume", 1.0), (float) r.getDouble("pitch", 1.0),
                    particule, nombre, Math.round(Math.max(0, r.getDouble("pause-secondes", 0)) * 1000),
                    r.getInt("priorite", 0)));
        }
        return new ReglagesReactions(distance, portee, attente, ferme, parNom);
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
