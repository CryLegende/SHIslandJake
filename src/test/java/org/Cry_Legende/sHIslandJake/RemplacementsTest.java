package org.Cry_Legende.sHIslandJake;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemplacementsTest {

    private static Remplacements lire(String yaml) throws InvalidConfigurationException {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return Remplacements.lire(c.getConfigurationSection("apparences.remplacements"));
    }

    @Test
    void remplace() throws InvalidConfigurationException {
        Remplacements r = lire("""
                apparences:
                  remplacements:
                    VILLAGER: AVENTURIER
                    witch: sorciere
                    PILLAGER: ''
                    EVOKER: EVOKER
                """);
        assertEquals("AVENTURIER", r.appliquer("VILLAGER"));
        assertEquals("AVENTURIER", r.appliquer("villager"));
        assertEquals("SORCIERE", r.appliquer("WITCH"));
        // vide ou identique : rien ne change
        assertEquals("PILLAGER", r.appliquer("PILLAGER"));
        assertEquals("EVOKER", r.appliquer("EVOKER"));
        assertEquals("AVENTURIER", r.appliquer("AVENTURIER"));
        assertEquals(2, r.table().size());
    }

    @Test
    void unSeulPas() throws InvalidConfigurationException {
        Remplacements r = lire("""
                apparences:
                  remplacements:
                    A: B
                    B: C
                """);
        assertEquals("B", r.appliquer("A"));
        assertEquals("C", r.appliquer("B"));
    }

    @Test
    void absentOuVide() throws InvalidConfigurationException {
        assertTrue(lire("apparences:\n  secours: VILLAGER\n").vide());
        assertTrue(lire("apparences:\n  remplacements:\n").vide());
        assertTrue(Remplacements.lire(null).vide());
        assertEquals("VILLAGER", Remplacements.lire(null).appliquer("VILLAGER"));
    }
}
