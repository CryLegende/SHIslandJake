package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionTest {

    /** Remplace les placeholders connus, comme PlaceholderAPI pour un joueur. */
    private static UnaryOperator<String> papi(Map<String, String> values) {
        return text -> {
            String out = text;
            for (Map.Entry<String, String> e : values.entrySet()) {
                out = out.replace(e.getKey(), e.getValue());
            }
            return out;
        };
    }

    @Test
    void nombres() {
        UnaryOperator<String> p = papi(Map.of("%shguide_nouveautes_non_lues%", "2"));
        assertTrue(Condition.vraie("%shguide_nouveautes_non_lues% >= 1", p));
        assertTrue(Condition.vraie("%shguide_nouveautes_non_lues% > 1", p));
        assertFalse(Condition.vraie("%shguide_nouveautes_non_lues% < 2", p));
        assertTrue(Condition.vraie("%shguide_nouveautes_non_lues% <= 2", p));
        assertTrue(Condition.vraie("%shguide_nouveautes_non_lues% == 2.0", p));
        // un nombre écrit à la française
        assertTrue(Condition.vraie("%x% >= 1000", papi(Map.of("%x%", "12 273"))));
    }

    @Test
    void texte() {
        UnaryOperator<String> p = papi(Map.of("%shguide_recompense_prete%", "oui", "%q%", "/"));
        assertTrue(Condition.vraie("%shguide_recompense_prete% == oui", p));
        assertTrue(Condition.vraie("%shguide_recompense_prete% == OUI", p));
        assertFalse(Condition.vraie("%shguide_recompense_prete% != oui", p));
        assertFalse(Condition.vraie("%q% != /", p));
        // > et < demandent deux nombres
        assertFalse(Condition.vraie("%shguide_recompense_prete% > 1", p));
        // placeholder non remplacé (plugin absent) : la condition est fausse, pas une erreur
        assertFalse(Condition.vraie("%inconnu% >= 1", p));
    }

    @Test
    void expressionReguliere() {
        UnaryOperator<String> p = papi(Map.of("%quete%", "p03_07"));
        assertTrue(Condition.vraie("%quete% ~ ^p\\d\\d_0[1-7]$", p));
        assertFalse(Condition.vraie("%quete% ~ ^p\\d\\d_1", p));
    }

    @Test
    void toutesEtValidite() {
        UnaryOperator<String> p = papi(Map.of("%a%", "1", "%b%", "non"));
        assertTrue(Condition.toutes(List.of(), p));
        assertTrue(Condition.toutes(List.of("%a% >= 1", "%b% == non"), p));
        assertFalse(Condition.toutes(List.of("%a% >= 1", "%b% == oui"), p));
        assertTrue(Condition.valide("%a% >= 1"));
        assertFalse(Condition.valide("%a% =>1"));
        assertFalse(Condition.valide("%a% ~ ("));
        assertFalse(Condition.valide(null));
    }
}
