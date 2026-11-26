package org.Cry_Legende.sHIslandJake;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lecture des apparences de config.yml (la partie qui ne touche pas au serveur : {@link Settings} ne fait ensuite que
 * vérifier les noms de mob et d'objet auprès de lui).
 */
class SettingsTest {

    private static Map<String, ApparencesLues.Brute> lire(String yaml, List<String> avertissements) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return ApparencesLues.lire(c.getConfigurationSection("types"), avertissements::add);
    }

    @Test
    void anciensTypesSansMob() throws Exception {
        List<String> avertissements = new ArrayList<>();
        // forme des versions 1.x : l'identifiant est le type Minecraft, sans « mob »
        Map<String, ApparencesLues.Brute> types = lire("""
                types:
                  VILLAGER:
                    icone: VILLAGER_SPAWN_EGG
                    hauteur: 2.1
                    permission: shislandjake.type.villager
                  witch:
                    icone: WITCH_SPAWN_EGG
                    hauteur: 2.55
                    permission: shislandjake.type.witch
                """, avertissements);
        assertEquals(List.of("VILLAGER", "WITCH"), List.copyOf(types.keySet()));
        assertEquals("WITCH", types.get("WITCH").mob());
        assertFalse(types.get("WITCH").estModele());
        assertEquals(2.55, types.get("WITCH").hauteur());
        assertEquals("defaut", types.get("VILLAGER").caractere());
        assertEquals("shislandjake.type.villager", types.get("VILLAGER").permission());
        assertTrue(types.get("VILLAGER").scenes().isEmpty());
        assertTrue(avertissements.isEmpty(), avertissements.toString());
    }

    @Test
    void modelesEtSons() throws Exception {
        List<String> avertissements = new ArrayList<>();
        Map<String, ApparencesLues.Brute> types = lire("""
                types:
                  AVENTURIER:
                    modele: JAKE_aventurier
                    taille: 1.2
                    icone: SPYGLASS
                    caractere: aventurier
                    scenes:
                      - carte
                      - sac
                      - somnole
                    sons:
                      bonjour: ENTITY_VILLAGER_YES
                      joie: entity.villager.celebrate
                  VILLAGER:
                    mob: villager
                """, avertissements);
        ApparencesLues.Brute a = types.get("AVENTURIER");
        assertTrue(a.estModele());
        assertEquals("JAKE_aventurier", a.modele());
        assertEquals(1.2f, a.taille(), 1e-6);
        assertTrue(Double.isNaN(a.hauteur()), "hauteur d'après le dessin du modèle");
        assertTrue(Double.isNaN(a.zoneLargeur()));
        assertEquals(List.of("carte", "sac", "somnole"), a.scenes());
        // un son en majuscules n'est pas un nom de son : retiré, avec un avertissement
        assertFalse(a.sons().containsKey("bonjour"));
        assertEquals("entity.villager.celebrate", a.sons().get("joie"));
        assertTrue(avertissements.stream().anyMatch(m -> m.contains("ENTITY_VILLAGER_YES")), avertissements.toString());
        assertEquals("VILLAGER", types.get("VILLAGER").mob());
    }

    @Test
    void remplacementsFiltres() throws Exception {
        List<String> avertissements = new ArrayList<>();
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("""
                apparences:
                  remplacements:
                    VILLAGER: AVENTURIER
                    WITCH: INEXISTANTE
                """);
        Remplacements r = ApparencesLues.filtrer(Remplacements.lire(c.getConfigurationSection("apparences.remplacements")),
                java.util.Set.of("AVENTURIER", "VILLAGER", "WITCH"), avertissements::add);
        assertEquals("AVENTURIER", r.appliquer("VILLAGER"));
        // cible absente de « types » : ignorée
        assertEquals("WITCH", r.appliquer("WITCH"));
        assertTrue(avertissements.stream().anyMatch(m -> m.contains("INEXISTANTE")), avertissements.toString());
        // identifiant remplacé encore choisissable : signalé
        assertTrue(avertissements.stream().anyMatch(m -> m.contains("reste dans « types »")), avertissements.toString());
    }

    @Test
    void configurationDuJar() {
        List<String> avertissements = new ArrayList<>();
        YamlConfiguration c = YamlConfiguration.loadConfiguration(new InputStreamReader(
                SettingsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        Map<String, ApparencesLues.Brute> types = ApparencesLues.lire(c.getConfigurationSection("types"), avertissements::add);
        assertTrue(avertissements.isEmpty(), avertissements.toString());
        assertTrue(types.containsKey(c.getString("jake.type")));
        assertTrue(types.containsKey(c.getString("apparences.secours")));
        assertTrue(c.getConfigurationSection("ambiance") != null);
    }
}
