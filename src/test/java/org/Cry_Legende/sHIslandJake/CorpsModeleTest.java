package org.Cry_Legende.sHIslandJake;

import me.general_breddok.blockdisplaycreator.render.DisplayRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L'API du moteur lue par réflexion, contre un faux moteur sans « duration » ni « animations ». */
class CorpsModeleTest {

    /** Le plugin moteur : il donne son afficheur, comme BlockDisplayCreator. */
    public static final class FauxMoteur {
        private final DisplayRenderer renderer;

        FauxMoteur(DisplayRenderer renderer) {
            this.renderer = renderer;
        }

        public DisplayRenderer getDisplayRenderer() {
            return renderer;
        }
    }

    @Test
    void apiSansDuree() throws Exception {
        DisplayRenderer renderer = new DisplayRenderer();
        CorpsModele.Api api = CorpsModele.Api.lire(new FauxMoteur(renderer));
        assertNotNull(api.show());
        assertNotNull(api.stop());
        assertNull(api.duration(), "« duration » est facultative");
        assertNull(api.animations(), "« animations » est facultative");
        assertTrue(api.renderer() == renderer);
        // les méthodes trouvées s'appellent sur l'afficheur
        Object shown = api.show().invoke(renderer, null, "connu", null, 1f);
        assertNotNull(shown);
        assertEquals(Boolean.TRUE, api.shows().invoke(renderer, shown));
        assertEquals("PLAYED", ((Enum<?>) api.play().invoke(renderer, shown, "geste", java.util.List.of())).name());
        api.hide().invoke(renderer, shown);
        assertEquals(Boolean.FALSE, api.shows().invoke(renderer, shown));
        assertNull(api.show().invoke(renderer, null, "pas-lu", null, 1f));
        assertEquals(Boolean.TRUE, api.knows().invoke(renderer, "pas-lu"));
    }

    @Test
    void moteurInactif() {
        // un afficheur absent (moteur sans rendu par paquets) : pas d'API
        assertThrows(IllegalStateException.class, () -> CorpsModele.Api.lire(new FauxMoteur(null)));
    }
}
