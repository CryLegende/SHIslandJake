package org.Cry_Legende.sHIslandJake;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReglagesReactionsTest {

    private static final String YAML = """
            reactions:
              distance: 5
              portee-commande: 12
              attente-menu-secondes: 10
              bonjour:
                approche: true
                priorite: 0
                pause-secondes: 900
                animation: coucou
                repliques: bonjour
              nouveaute:
                approche: true
                priorite: 20
                si:
                  - "%shguide_nouveautes_non_lues% >= 1"
                animation: coucou
                repliques: nouveaute
              cadeau:
                approche: true
                priorite: 10
                si:
                  - "%shguide_recompense_prete% == oui"
                animation: cadeau
              visiteur:
                approche: true
                pour: visiteurs
                repliques: visiteur
              joie:
                animation: joie
                particules: HAPPY_VILLAGER 8
                son: entity.villager.celebrate
                volume: 0.8
                pitch: 1.2
              cassee:
                approche: true
                si:
                  - "%x% => 1"
              etrange:
                pour: personne
            """;

    static ReglagesReactions lire(String yaml, List<String> avertissements) throws InvalidConfigurationException {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return ReglagesReactions.lire(c.getConfigurationSection("reactions"), avertissements::add);
    }

    @Test
    void lecture() throws InvalidConfigurationException {
        List<String> avertissements = new ArrayList<>();
        ReglagesReactions r = lire(YAML, avertissements);
        assertEquals(5, r.distance);
        assertEquals(12, r.porteeCommande);
        assertEquals(200, r.attenteMenuTicks);
        // les clés simples sont des réglages, pas des réactions ; « cassee » (condition illisible) est ignorée
        assertEquals(List.of("bonjour", "nouveaute", "cadeau", "visiteur", "joie", "etrange"), List.copyOf(r.toutes().keySet()));
        assertEquals(2, avertissements.size(), avertissements.toString());
        ReglagesReactions.Reaction joie = r.get("JOIE");
        assertNotNull(joie);
        assertFalse(joie.approche());
        assertEquals("HAPPY_VILLAGER", joie.particule());
        assertEquals(8, joie.particules());
        assertEquals(0.8f, joie.volume(), 1e-6);
        assertEquals(1.2f, joie.pitch(), 1e-6);
        assertEquals(900_000L, r.get("bonjour").pauseMillis());
        assertEquals(ReglagesReactions.Pour.MEMBRES, r.get("etrange").pour());
        assertEquals(ReglagesReactions.Pour.VISITEURS, r.get("visiteur").pour());
        // ordre des réactions d'approche : priorité décroissante, ordre du fichier à priorité égale
        assertEquals(List.of("nouveaute", "cadeau", "bonjour", "visiteur"),
                r.approche().stream().map(ReglagesReactions.Reaction::nom).toList());
    }

    @Test
    void sansSection() {
        ReglagesReactions r = ReglagesReactions.lire(null, s -> { });
        assertTrue(r.toutes().isEmpty());
        assertNull(r.choisir(true, x -> true, x -> true));
    }

    @Test
    void choixParPrioriteEtConditions() throws InvalidConfigurationException {
        ReglagesReactions r = lire(YAML, new ArrayList<>());
        List<String> lues = new ArrayList<>();
        // nouveauté et cadeau vrais : la nouveauté (priorité 20) gagne, et les conditions du cadeau ne sont pas lues
        ReglagesReactions.Reaction c = r.choisir(true, x -> true, x -> {
            lues.add(x.nom());
            return true;
        });
        assertEquals("nouveaute", c.nom());
        assertEquals(List.of("nouveaute"), lues);
        // pas de nouveauté : le cadeau
        c = r.choisir(true, x -> true, x -> !x.nom().equals("nouveaute"));
        assertEquals("cadeau", c.nom());
        // rien de spécial : bonjour
        c = r.choisir(true, x -> true, x -> x.si().isEmpty());
        assertEquals("bonjour", c.nom());
        // un visiteur n'a que « visiteur »
        c = r.choisir(false, x -> true, x -> true);
        assertEquals("visiteur", c.nom());
    }

    @Test
    void pause() throws InvalidConfigurationException {
        ReglagesReactions r = lire(YAML, new ArrayList<>());
        Pauses pauses = new Pauses();
        UUID joueur = UUID.randomUUID();
        long t = 1_000_000L;
        ReglagesReactions.Reaction bonjour = r.get("bonjour");
        assertTrue(pauses.libre(joueur, "bonjour", t));
        pauses.poser(joueur, "bonjour", t, bonjour.pauseMillis());
        assertFalse(pauses.libre(joueur, "bonjour", t + 899_999));
        assertTrue(pauses.libre(joueur, "bonjour", t + 900_000));
        assertTrue(pauses.libre(UUID.randomUUID(), "bonjour", t + 1));
        // en pause, bonjour est sauté : plus rien pour un membre sans nouveauté ni cadeau
        ReglagesReactions.Reaction c = r.choisir(true, x -> pauses.libre(joueur, x.nom(), t + 10), x -> x.si().isEmpty());
        assertNull(c);
        // une pause nulle ne pose rien ; le ménage retire les pauses finies
        pauses.poser(joueur, "joie", t, 0);
        assertTrue(pauses.libre(joueur, "joie", t));
        pauses.nettoyer(t + 900_000);
        assertTrue(pauses.libre(joueur, "bonjour", t));
    }

    @Test
    void configurationDuJar() {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(new InputStreamReader(
                ReglagesReactionsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        YamlConfiguration m = YamlConfiguration.loadConfiguration(new InputStreamReader(
                ReglagesReactionsTest.class.getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        List<String> avertissements = new ArrayList<>();
        ReglagesReactions r = ReglagesReactions.lire(c.getConfigurationSection("reactions"), avertissements::add);
        assertTrue(avertissements.isEmpty(), avertissements.toString());
        assertFalse(r.toutes().isEmpty());
        // chaque liste de répliques citée existe dans messages.yml
        for (ReglagesReactions.Reaction reaction : r.toutes().values()) {
            if (!reaction.repliques().isEmpty()) {
                assertFalse(m.getStringList("repliques.defaut." + reaction.repliques()).isEmpty(), reaction.nom());
            }
        }
    }
}
