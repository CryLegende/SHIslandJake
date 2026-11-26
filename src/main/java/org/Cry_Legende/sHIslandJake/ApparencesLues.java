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
                 List<String> scenes, List<String> vie, List<String> obtention, String obtentionValeur) {

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
            List<String> vie = new ArrayList<>();
            for (String animation : entry.getStringList("vie")) {
                if (!animation.isBlank()) {
                    vie.add(animation.trim());
                }
            }
            List<String> obtention = new ArrayList<>();
            for (String condition : entry.getStringList("obtention")) {
                if (condition.isBlank()) {
                    continue;
                }
                if (!Condition.valide(condition)) {
                    // gardée : une condition fausse vaut mieux qu'une apparence donnée à tout le monde
                    avertir.accept("types." + key + ".obtention « " + condition + " » illisible (opérateur == != >= <= > < ~"
                            + " entouré d'espaces) : cette apparence ne s'obtient pas.");
                }
                obtention.add(condition.trim());
            }
            String caractere = entry.getString("caractere", "defaut").trim();
            result.put(id, new Brute(id, modele, (float) Math.max(0.05, entry.getDouble("taille", 1.0)), mob,
                    entry.getString("icone", "VILLAGER_SPAWN_EGG"), hauteur, zoneLargeur, zoneHauteur,
                    entry.getString("permission", "").trim(), caractere.isEmpty() ? "defaut" : caractere,
                    Map.copyOf(sons), List.copyOf(scenes), List.copyOf(vie), List.copyOf(obtention),
                    entry.getString("obtention-valeur", "").trim()));
        }
        return result;
    }

    /**
     * Les remplacements dont la cible est une apparence de « types » (les autres sont ignorés, avec un avertissement) ;
     * avertit aussi quand un identifiant remplacé reste dans « types » (un joueur pourrait le reprendre).
     */
    static Remplacements filtrer(Remplacements lus, Set<String> ids, String secours, Consumer<String> avertir) {
        Map<String, String> gardes = new LinkedHashMap<>();
        lus.table().forEach((from, to) -> {
            if (!ids.contains(to)) {
                avertir.accept("apparences.remplacements : « " + from + ": " + to + " » ignoré, " + to + " n'est pas dans « types ».");
            } else {
                gardes.put(from, to);
                if (ids.contains(from) && !from.equals(secours)) {
                    avertir.accept("apparences.remplacements : " + from + " est remplacé par " + to
                            + " mais reste dans « types » sans servir (il n'est plus proposé) : retirez-le de « types »,"
                            + " sauf s'il est l'apparence de secours.");
                }
            }
        });
        return new Remplacements(gardes);
    }

    /**
     * L'apparence dessinée pour un Jake de type « id » : la sienne si elle est dans « types », proposée ou non ;
     * celle de jake.type si elle n'y est plus ou si c'est l'apparence de secours cachée (le secours ne se dessine qu'à
     * la place d'un modèle qui manque).
     */
    static String resolue(String id, Set<String> ids, String defaut, String secoursCache) {
        String i = Remplacements.id(id);
        if (ids.contains(i) && !i.equals(secoursCache)) {
            return i;
        }
        return defaut;
    }
}
