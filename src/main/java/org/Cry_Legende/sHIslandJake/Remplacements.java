package org.Cry_Legende.sHIslandJake;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * apparences.remplacements : ancien identifiant d'apparence → nouveau, appliqué aux Jake au chargement de jakes.yml
 * et au rechargement. Un seul pas : « A: B » et « B: C » font passer A à B, pas à C.
 */
final class Remplacements {

    private final Map<String, String> table;

    Remplacements(Map<String, String> table) {
        Map<String, String> copy = new LinkedHashMap<>();
        table.forEach((from, to) -> {
            String f = id(from);
            String t = id(to);
            if (!f.isEmpty() && !t.isEmpty() && !f.equals(t)) {
                copy.put(f, t);
            }
        });
        this.table = Map.copyOf(copy);
    }

    static Remplacements lire(ConfigurationSection section) {
        Map<String, String> table = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                table.put(key, section.getString(key, ""));
            }
        }
        return new Remplacements(table);
    }

    /** Le nouvel identifiant, ou celui donné s'il n'est pas remplacé. */
    String appliquer(String type) {
        String found = table.get(id(type));
        return found == null ? type : found;
    }

    boolean vide() {
        return table.isEmpty();
    }

    Map<String, String> table() {
        return table;
    }

    static String id(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
