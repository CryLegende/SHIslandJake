package org.Cry_Legende.sHIslandJake;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObtentionTest {

    private static final String NIVEAU = "%shjobs_player_level_bucheron%";
    private static final List<String> BUCHERON_10 = List.of(NIVEAU + " >= 10");

    private static UnaryOperator<String> niveau(String n) {
        return t -> t.replace(NIVEAU, n);
    }

    private static Predicate<String> droits(String... d) {
        return Set.of(d)::contains;
    }

    @Test
    void sansObtentionATousLeMonde() {
        // l'Aventurier : ni droit ni condition, même sans le droit d'usage
        assertEquals(Settings.Etat.DISPONIBLE,
                Obtention.etat("", List.of(), droits(), niveau("0"), "shislandjake.personnages"));
    }

    @Test
    void obtenueEnJouantUtilisableAvecLeDroitDUsage() {
        String usage = "shislandjake.personnages";
        assertEquals(Settings.Etat.NON_OBTENUE, Obtention.etat("", BUCHERON_10, droits(usage), niveau("4"), usage));
        assertEquals(Settings.Etat.RESERVE, Obtention.etat("", BUCHERON_10, droits(), niveau("10"), usage));
        assertEquals(Settings.Etat.DISPONIBLE, Obtention.etat("", BUCHERON_10, droits(usage), niveau("12"), usage));
        // sans droit d'usage configuré, l'obtention suffit
        assertEquals(Settings.Etat.DISPONIBLE, Obtention.etat("", BUCHERON_10, droits(), niveau("12"), ""));
    }

    @Test
    void droitEtConditionsDoiventPasserTousLesDeux() {
        String usage = "shislandjake.personnages";
        assertEquals(Settings.Etat.NON_OBTENUE,
                Obtention.etat("shislandjake.type.arbre", BUCHERON_10, droits(usage), niveau("12"), usage));
        assertEquals(Settings.Etat.NON_OBTENUE,
                Obtention.etat("shislandjake.type.arbre", BUCHERON_10, droits(usage, "shislandjake.type.arbre"), niveau("3"), usage));
        assertEquals(Settings.Etat.DISPONIBLE,
                Obtention.etat("shislandjake.type.arbre", BUCHERON_10, droits(usage, "shislandjake.type.arbre"), niveau("12"), usage));
        // un droit seul (sans condition) compte aussi comme une obtention : le droit d'usage est demandé
        assertEquals(Settings.Etat.RESERVE,
                Obtention.etat("shislandjake.type.arbre", List.of(), droits("shislandjake.type.arbre"), niveau("0"), usage));
    }

    @Test
    void placeholderNonLuOuConditionIllisibleNeDonneRien() {
        // PlaceholderAPI absent : le placeholder reste tel quel, ce n'est pas un nombre
        assertEquals(Settings.Etat.NON_OBTENUE, Obtention.etat("", BUCHERON_10, droits(), t -> t, ""));
        assertEquals(Settings.Etat.NON_OBTENUE, Obtention.etat("", List.of("illisible"), droits(), t -> t, ""));
    }

    @Test
    void objectifLuDansLaCondition() {
        assertEquals(10.0, Obtention.objectif(BUCHERON_10, NIVEAU).getAsDouble());
        assertEquals(10.0, Obtention.objectif(BUCHERON_10, "").getAsDouble(), "sans valeur : la première condition à placeholder");
        assertEquals(25.0, Obtention.objectif(List.of("%vault_eco_balance% >= 1000", NIVEAU + " > 25"), NIVEAU).getAsDouble());
        assertFalse(Obtention.objectif(List.of(NIVEAU + " <= 10"), NIVEAU).isPresent());
        assertFalse(Obtention.objectif(List.of("%autre% >= 10"), NIVEAU).isPresent());
        assertEquals("10", Obtention.nombre(10.0));
        assertEquals("2,5", Obtention.nombre(2.5));
    }

    @Test
    void ligneDesAutresApparencesDuBoutonApparence() {
        // au lancement : l'Aventurier seul, aucune ligne
        assertEquals(null, Obtention.ligneAutres(List.of()));
        // une autre utilisable : aucune ligne
        assertEquals(null, Obtention.ligneAutres(List.of(Settings.Etat.RESERVE, Settings.Etat.DISPONIBLE)));
        // une obtenue mais réservée : la ligne des grades
        assertEquals("reserve", Obtention.ligneAutres(List.of(Settings.Etat.NON_OBTENUE, Settings.Etat.RESERVE)));
        // toutes à obtenir : pas « réservée VIP », mais à obtenir en jouant
        assertEquals("a-obtenir", Obtention.ligneAutres(List.of(Settings.Etat.NON_OBTENUE, Settings.Etat.NON_OBTENUE)));
    }

    @Test
    void conditionCoupeeEnTrois() {
        String[] p = Condition.parties(NIVEAU + " >= 10");
        assertEquals(List.of(NIVEAU, ">=", "10"), List.of(p));
        assertTrue(Condition.parties("rien") == null);
    }
}
