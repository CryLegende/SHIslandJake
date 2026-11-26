package org.Cry_Legende.sHIslandJake;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FichiersConfigTest {

    @Test
    void fichierInvalideRefuseEtNomme(@TempDir Path dossier) throws IOException {
        Files.writeString(dossier.resolve("config.yml"), """
                reactions:
                  joie:
                    animation: joie
                   repliques: joie
                """, StandardCharsets.UTF_8);
        FichiersConfig.FichierInvalide e = assertThrows(FichiersConfig.FichierInvalide.class,
                () -> FichiersConfig.lire(dossier.toFile(), "config.yml"));
        assertEquals("config.yml", e.fichier());
        assertTrue(e.getMessage().startsWith("config.yml"));
    }

    @Test
    void fichierAbsentNomme(@TempDir Path dossier) {
        FichiersConfig.FichierInvalide e = assertThrows(FichiersConfig.FichierInvalide.class,
                () -> FichiersConfig.lire(dossier.toFile(), "messages.yml"));
        assertEquals("messages.yml", e.fichier());
    }

    @Test
    void fichierValide(@TempDir Path dossier) throws Exception {
        Files.writeString(dossier.resolve("config.yml"), "reactions:\n  distance: 4\n", StandardCharsets.UTF_8);
        YamlConfiguration c = FichiersConfig.lire(dossier.toFile(), "config.yml");
        assertEquals(4, c.getInt("reactions.distance"));
    }
}
