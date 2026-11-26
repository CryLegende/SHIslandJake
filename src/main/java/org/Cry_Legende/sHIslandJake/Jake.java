package org.Cry_Legende.sHIslandJake;

import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Le Jake d'une île. Ses données changent par remplacement de {@link JakeData} ; son corps (PNJ FancyNpcs ou modèle de
 * BlockDisplayCreator) n'est créé qu'au passage d'un joueur et n'est touché que depuis le fil principal. Les textes
 * et la zone cliquable gardent les mêmes numéros d'entité toute la vie du Jake : un nouvel envoi remplace toujours
 * ce qu'un joueur aurait gardé.
 */
final class Jake {

    private volatile JakeData data;
    /** Corps qui le dessine en ce moment ; null tant que personne n'est passé. */
    private Corps corps;
    /** Apparence réellement dessinée (celle de secours quand le modèle manque). */
    private Settings.Apparence dessinee;
    private Npc handle;
    /** Modèle montré par BlockDisplayCreator, gardé en Object pour que le plugin tourne sans lui. */
    private Object modele;
    /** Bas du nom au-dessus des pieds, et zone cliquable d'un modèle (0 = pas de zone). */
    private double hauteurTexte;
    /** Bas du nom sans obstacle au-dessus de Jake (hauteurTexte peut être plus bas, sous un feuillage). */
    private double hauteurNormale;
    /** Tick de la prochaine lecture des blocs au-dessus de Jake. */
    int prochainBlocs;
    private float zoneLargeur;
    private float zoneHauteur;
    private int nomId;
    private int paroleId;
    private int zoneId;
    private UUID nomUuid;
    private UUID paroleUuid;
    private UUID zoneUuid;
    private long lastSeen;
    /** Dessiné par le corps de secours alors que son apparence a un modèle (il réessaiera le modèle). */
    private boolean secours;
    /** Hauteur et zone prises par défaut : le moteur n'avait pas encore lu le dessin du modèle. */
    private boolean bornesProvisoires;
    /** Ticks du serveur : prochaine question au moteur (encore montré ?), prochain essai du modèle depuis le secours. */
    int prochainControle;
    int prochainEssaiModele;
    /** Dernier tick où ce Jake a été vérifié par le suivi : plusieurs joueurs autour ne le vérifient qu'une fois. */
    int verifie = Integer.MIN_VALUE;
    /** Demandes de dessin restées sans réponse du moteur (modèle pas encore lu), et dernier tick où il est relancé. */
    int essaisPlusTard;
    int relance = Integer.MIN_VALUE;

    Jake(JakeData data) {
        this.data = data;
    }

    JakeData data() {
        return data;
    }

    void setData(JakeData data) {
        this.data = data;
    }

    UUID island() {
        return data.island();
    }

    /** Null tant que le monde n'est pas chargé. */
    Location location() {
        JakeData d = data;
        World world = Bukkit.getWorld(d.world());
        return world == null ? null : new Location(world, d.x(), d.y(), d.z(), d.yaw(), d.pitch());
    }

    Corps corps() {
        return corps;
    }

    Settings.Apparence dessinee() {
        return dessinee;
    }

    /** Le corps qui dessine ce Jake, l'apparence dessinée, la hauteur du nom et la zone cliquable (0 = aucune). */
    void dessine(Corps corps, Settings.Apparence apparence, double hauteurTexte, float zoneLargeur, float zoneHauteur) {
        this.corps = corps;
        this.dessinee = apparence;
        this.hauteurTexte = hauteurTexte;
        this.hauteurNormale = hauteurTexte;
        this.zoneLargeur = zoneLargeur;
        this.zoneHauteur = zoneHauteur;
    }

    void efface() {
        this.corps = null;
        this.dessinee = null;
        this.zoneLargeur = 0;
        this.zoneHauteur = 0;
        this.secours = false;
        this.bornesProvisoires = false;
        this.verifie = Integer.MIN_VALUE;
    }

    boolean secours() {
        return secours;
    }

    void setSecours(boolean secours) {
        this.secours = secours;
    }

    boolean bornesProvisoires() {
        return bornesProvisoires;
    }

    void setBornesProvisoires(boolean provisoires) {
        this.bornesProvisoires = provisoires;
    }

    Npc handle() {
        return handle;
    }

    void setHandle(Npc handle) {
        this.handle = handle;
    }

    Object modele() {
        return modele;
    }

    void setModele(Object modele) {
        this.modele = modele;
    }

    double hauteurTexte() {
        return hauteurTexte;
    }

    double hauteurNormale() {
        return hauteurNormale;
    }

    /** Bas du nom réellement utilisé (abaissé sous un bloc, ou rendu à sa hauteur normale). */
    void setHauteurTexte(double hauteur) {
        this.hauteurTexte = hauteur;
    }

    boolean aUneZone() {
        return zoneLargeur > 0 && zoneHauteur > 0;
    }

    float zoneLargeur() {
        return zoneLargeur;
    }

    float zoneHauteur() {
        return zoneHauteur;
    }

    /** Numéros d'entité des textes et de la zone, tirés une fois (au premier joueur qui voit ce Jake). */
    void numeros(int nom, int parole, int zone) {
        this.nomId = nom;
        this.paroleId = parole;
        this.zoneId = zone;
        this.nomUuid = UUID.randomUUID();
        this.paroleUuid = UUID.randomUUID();
        this.zoneUuid = UUID.randomUUID();
    }

    boolean numerote() {
        return nomId != 0;
    }

    int nomId() {
        return nomId;
    }

    int paroleId() {
        return paroleId;
    }

    int zoneId() {
        return zoneId;
    }

    UUID nomUuid() {
        return nomUuid;
    }

    UUID paroleUuid() {
        return paroleUuid;
    }

    UUID zoneUuid() {
        return zoneUuid;
    }

    long lastSeen() {
        return lastSeen;
    }

    void seen(long now) {
        lastSeen = now;
    }

    /** Joueur parti : le PNJ FancyNpcs l'oublie. */
    void forget(UUID player) {
        if (handle != null) {
            handle.getIsVisibleForPlayer().remove(player);
            handle.getIsLookingAtPlayer().remove(player);
            handle.getIsTeamCreated().remove(player);
            handle.getLastPlayerInteraction().remove(player);
        }
    }
}
