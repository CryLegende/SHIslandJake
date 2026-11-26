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
        assertTrue(types.get("VILLAGER").vie().isEmpty());
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
                    vie:
                      - respire
                      - " respire2 "
                      - ""
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
        assertEquals(List.of("respire", "respire2"), a.vie());
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
                java.util.Set.of("AVENTURIER", "VILLAGER", "WITCH"), "", avertissements::add);
        assertEquals("AVENTURIER", r.appliquer("VILLAGER"));
        // cible absente de « types » : ignorée
        assertEquals("WITCH", r.appliquer("WITCH"));
        assertTrue(avertissements.stream().anyMatch(m -> m.contains("INEXISTANTE")), avertissements.toString());
        // identifiant remplacé resté dans « types » sans servir : signalé
        assertTrue(avertissements.stream().anyMatch(m -> m.contains("reste dans « types »")), avertissements.toString());
    }

    @Test
    void leSecoursPeutEtreRemplaceSansEtreMontreNiPerdu() throws Exception {
        List<String> avertissements = new ArrayList<>();
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("""
                apparences:
                  remplacements:
                    VILLAGER: AVENTURIER
                    EVOKER: AVENTURIER
                """);
        java.util.Set<String> ids = java.util.Set.of("AVENTURIER", "VILLAGER");
        Remplacements r = ApparencesLues.filtrer(Remplacements.lire(c.getConfigurationSection("apparences.remplacements")),
                ids, "VILLAGER", avertissements::add);
        // les Jake passent à l'Aventurier, le secours reste dans « types » sans avertissement
        assertEquals("AVENTURIER", r.appliquer("VILLAGER"));
        assertEquals("AVENTURIER", r.appliquer("EVOKER"));
        assertTrue(avertissements.isEmpty(), avertissements.toString());
        // un Jake resté VILLAGER (secours caché) ou d'une apparence disparue est dessiné avec jake.type
        assertEquals("AVENTURIER", ApparencesLues.resolue("villager", ids, "AVENTURIER", "VILLAGER"));
        assertEquals("AVENTURIER", ApparencesLues.resolue("WITCH", ids, "AVENTURIER", "VILLAGER"));
        // sans secours nommé, VILLAGER reste une apparence comme les autres
        assertEquals("VILLAGER", ApparencesLues.resolue("VILLAGER", ids, "AVENTURIER", ""));
    }

    @Test
    void obtentionLue() throws Exception {
        List<String> avertissements = new ArrayList<>();
        Map<String, ApparencesLues.Brute> types = lire("""
                types:
                  ARBRE:
                    modele: JAKE_arbre
                    icone: hdb-81626
                    obtention:
                      - "%shjobs_player_level_bucheron% >= 10"
                      - "illisible"
                    obtention-valeur: " %shjobs_player_level_bucheron% "
                  AVENTURIER:
                    modele: JAKE_aventurier
                """, avertissements);
        ApparencesLues.Brute a = types.get("ARBRE");
        assertEquals(List.of("%shjobs_player_level_bucheron% >= 10", "illisible"), a.obtention());
        assertEquals("%shjobs_player_level_bucheron%", a.obtentionValeur());
        assertEquals("hdb-81626", a.icone());
        // une condition illisible est gardée (fausse) et signalée : l'apparence n'est jamais donnée à tous
        assertTrue(avertissements.stream().anyMatch(m -> m.contains("illisible")), avertissements.toString());
        assertTrue(types.get("AVENTURIER").obtention().isEmpty());
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
        // l'Aventurier est le Jake de tout le monde : sans obtention
        assertEquals("AVENTURIER", c.getString("jake.type"));
        assertTrue(types.get("AVENTURIER").obtention().isEmpty() && types.get("AVENTURIER").permission().isEmpty());
        assertEquals(List.of("respire", "respire2"), types.get("AVENTURIER").vie());
    }
}
