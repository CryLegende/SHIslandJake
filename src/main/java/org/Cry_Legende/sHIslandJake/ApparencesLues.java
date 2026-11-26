package org.Cry_Legende.sHIslandJake;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Lecture de « types » et de « apparences.remplacements » sans toucher au serveur : les noms de mob et d'objet restent
 * des textes ({@link Settings} les vérifie). Une apparence sans « modele » ni « mob » prend son identifiant comme mob
 * (la forme des versions 1.x : VILLAGER, WITCH...).
 */
final class ApparencesLues {

    /** Une apparence telle qu'écrite ; {@code hauteur} et la zone valent NaN quand elles doivent venir du dessin. */
    record Brute(String id, String modele, float taille, String mob, String icone, double hauteur, double zoneLargeur,
                 double zoneHauteur, String permission, String caractere, Map<String, String> sons,
                 List<String> scenes) {

        boolean estModele() {
            return !modele.isEmpty();
        }
    }

    private ApparencesLues() {
    }

    static Map<String, Brute> lire(ConfigurationSection section, Consumer<String> avertir) {
        Map<String, Brute> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String id = Remplacements.id(key);
            String modele = entry.getString("modele", "").trim();
            String mob = modele.isEmpty() ? entry.getString("mob", key).trim().toUpperCase(Locale.ROOT) : "";
            double hauteur = entry.contains("hauteur") ? entry.getDouble("hauteur") : modele.isEmpty() ? 2.1 : Double.NaN;
            double zoneLargeur = entry.contains("zone-clic.largeur") ? Math.max(0.1, entry.getDouble("zone-clic.largeur")) : Double.NaN;
            double zoneHauteur = entry.contains("zone-clic.hauteur") ? Math.max(0.1, entry.getDouble("zone-clic.hauteur")) : Double.NaN;
            Map<String, String> sons = new HashMap<>();
            ConfigurationSection s = entry.getConfigurationSection("sons");
            if (s != null) {
                for (String reaction : s.getKeys(false)) {
                    String son = s.getString(reaction, "").trim();
                    if (!son.isEmpty() && !ReglagesReactions.sonValide(son)) {
                        avertir.accept("types." + key + ".sons." + reaction + " « " + son
                                + " » n'est pas un nom de son (minuscules, ex. entity.villager.yes) : ignoré.");
                        continue;
                    }
                    sons.put(reaction.toLowerCase(Locale.ROOT), son);
                }
            }
            List<String> scenes = new ArrayList<>();
            for (String scene : entry.getStringList("scenes")) {
                if (!scene.isBlank()) {
                    scenes.add(scene.trim());
                }
            }
            String caractere = entry.getString("caractere", "defaut").trim();
            result.put(id, new Brute(id, modele, (float) Math.max(0.05, entry.getDouble("taille", 1.0)), mob,
                    entry.getString("icone", "VILLAGER_SPAWN_EGG"), hauteur, zoneLargeur, zoneHauteur,
                    entry.getString("permission", "").trim(), caractere.isEmpty() ? "defaut" : caractere,
                    Map.copyOf(sons), List.copyOf(scenes)));
        }
        return result;
    }

    /**
     * Les remplacements dont la cible est une apparence de « types » (les autres sont ignorés, avec un avertissement) ;
     * avertit aussi quand un identifiant remplacé reste dans « types » (un joueur pourrait le reprendre).
     */
    static Remplacements filtrer(Remplacements lus, Set<String> ids, Consumer<String> avertir) {
        Map<String, String> gardes = new LinkedHashMap<>();
        lus.table().forEach((from, to) -> {
            if (!ids.contains(to)) {
                avertir.accept("apparences.remplacements : « " + from + ": " + to + " » ignoré, " + to + " n'est pas dans « types ».");
            } else {
                gardes.put(from, to);
                if (ids.contains(from)) {
                    avertir.accept("apparences.remplacements : " + from + " est remplacé par " + to
                            + " mais reste dans « types » : un joueur peut encore le choisir.");
                }
            }
        });
        return new Remplacements(gardes);
    }
}
