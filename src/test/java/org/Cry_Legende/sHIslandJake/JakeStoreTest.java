package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JakeStoreTest {

    /** jakes.yml tel que l'écrit la 1.4.1 : VILLAGER, WITCH, et une apparence qui n'existe plus. */
    private static final String FICHIER_141 = """
            # SHIslandJake : le Jake de chaque île (identifiant SuperiorSkyblock2).
            # position : x y z yaw pitch. Fichier réécrit par le plugin.
            import-islandnpc: '2026-11-12 03:10 : 4121 repris, 0 déjà présents'
            iles:
              11111111-1111-1111-1111-111111111111:
                monde: 'SuperiorWorld'
                position: '1203.5 100.0 6.5 180.0 0.0'
                type: VILLAGER
                visible: true
              22222222-2222-2222-2222-222222222222:
                monde: 'SuperiorWorld'
                position: '-1196.5 100.0 1206.5 90.0 0.0'
                type: WITCH
                visible: false
              33333333-3333-3333-3333-333333333333:
                monde: 'SuperiorWorld'
                position: '0.5 100.0 2406.5 0.0 0.0'
                type: ZOMBIE_HORSE
                visible: true
            """;

    @Test
    void allerRetourDuFormat141(@TempDir Path dossier) throws Exception {
        Files.writeString(dossier.resolve("jakes.yml"), FICHIER_141, StandardCharsets.UTF_8);
        JakeStore store = new JakeStore(dossier.toFile(), Logger.getLogger("essai"));
        List<JakeData> lus = store.load().jakes();
        assertEquals(3, lus.size());
        assertEquals("VILLAGER", lus.get(0).type());
        assertEquals("WITCH", lus.get(1).type());
        assertFalse(lus.get(1).visible());
        assertEquals(90f, lus.get(1).yaw());
        // une apparence inconnue de la configuration est gardée telle quelle : rien n'est perdu
        assertEquals("ZOMBIE_HORSE", lus.get(2).type());
        assertTrue(store.importMarker().contains("4121"));

        // réécrit par la 2.0 puis relu : identique
        JakeRegistry registry = new JakeRegistry();
        lus.forEach(d -> registry.add(new Jake(d)));
        store.saveNow(registry);
        store.close(registry);
        String ecrit = Files.readString(dossier.resolve("jakes.yml"), StandardCharsets.UTF_8);
        assertTrue(ecrit.contains("type: WITCH"));
        JakeStore relu = new JakeStore(dossier.toFile(), Logger.getLogger("essai"));
        List<JakeData> encore = relu.load().jakes();
        assertEquals(3, encore.size());
        assertTrue(encore.containsAll(lus), encore.toString());
        assertEquals(store.importMarker(), relu.importMarker());
    }

    @Test
    void remplacementsAuChargement(@TempDir Path dossier) throws Exception {
        Files.writeString(dossier.resolve("jakes.yml"), FICHIER_141, StandardCharsets.UTF_8);
        Remplacements r = new Remplacements(java.util.Map.of("VILLAGER", "AVENTURIER"));
        List<JakeData> lus = new JakeStore(dossier.toFile(), Logger.getLogger("essai")).load().jakes();
        List<String> types = lus.stream().map(d -> r.appliquer(d.type())).toList();
        assertEquals(List.of("AVENTURIER", "WITCH", "ZOMBIE_HORSE"), types);
    }
}
