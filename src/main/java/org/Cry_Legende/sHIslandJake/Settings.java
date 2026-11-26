package org.Cry_Legende.sHIslandJake;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.permissions.Permissible;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Réglages lus dans config.yml, figés jusqu'au prochain reload.
 */
public final class Settings {

    /**
     * Une apparence de Jake. {@code modele} non vide = un modèle de BlockDisplayCreator ({@code taille} = son échelle),
     * sinon {@code mob} = le corps de secours dessiné par FancyNpcs. {@code hauteur} et la zone cliquable valent NaN
     * quand elles doivent venir du dessin du modèle.
     */
    public record Apparence(String id, String modele, float taille, EntityType mob, Material icon, double hauteur,
                            double zoneLargeur, double zoneHauteur, String permission, String caractere,
                            Map<String, String> sons) {

        public boolean estModele() {
            return !modele.isEmpty();
        }
    }

    /** Ce qu'un joueur peut faire d'une apparence dans le menu Apparence. */
    public enum Etat {
        DISPONIBLE,
        /** Il n'a pas le droit d'obtention de l'apparence. */
        NON_OBTENUE,
        /** Obtenue, mais son grade ne permet pas de l'utiliser. */
        RESERVE
    }

    /** Cadre d'un menu : lignes, vitres du bord et de l'accent, objet des autres cases (null = vides), tête de Jake. */
    public record Frame(int rows, Material fill, Material ring, List<Integer> ringSlots, Material accent,
                        List<Integer> accentSlots, int headSlot, String headIcon) {

        public int size() {
            return rows * 9;
        }
    }

    /** Apparence de secours quand la configuration n'en a aucune dessinable par FancyNpcs. */
    private static final Apparence VILLAGEOIS = new Apparence("VILLAGER", "", 1f, EntityType.VILLAGER,
            Material.VILLAGER_SPAWN_EGG, 2.1, Double.NaN, Double.NaN, "", "defaut", Map.of());

    public final String defaultType;
    public final boolean defaultVisible;
    public final boolean createWithIsland;
    public final Vector offset;
    public final float yaw;
    public final float pitch;

    public final int viewDistance;
    public final int trackerInterval;
    public final long forgetMillis;
    public final boolean lookAtPlayers;
    public final int lookDistance;
    public final int lookIntervalTicks;
    public final double lookSpeed;
    public final double lookMinAngle;
    public final double lookMaxPitch;
    public final boolean glowing;
    public final NamedTextColor glowColor;

    public final boolean hologram;
    public final byte billboard;
    /** Couleur ARGB du fond du nom et de l'alerte ; null = fond gris de Minecraft. */
    public final Integer background;
    public final boolean textShadow;
    public final int lineWidth;
    public final double defaultHeight;

    public final Integer bubbleBackground;
    public final int bubbleLineWidth;
    public final int ticksPerWord;
    public final double bubbleBaseSeconds;
    public final double bubblePerCharacter;
    public final int bubbleGrowTicks;

    public final String clickCommand;
    public final boolean leftClick;
    public final boolean sneakOpensMenu;
    public final boolean membersCanClick;
    public final long clickDelayMillis;
    public final String clickAnimation;

    public final String noArgCommand;
    /** /jake avec un argument que le plugin ne connaît pas ; {arguments} = ce que le joueur a tapé. Vide = l'utilisation. */
    public final String unknownCommand;
    public final List<String> unknownSuggestions;

    public final boolean alertEnabled;
    public final String alertPlaceholder;
    public final String alertValue;
    public final double alertOffset;
    public final int alertCheckTicks;

    public final int moveSeconds;
    public final List<String> islandSubCommands;

    /** Apparences, dans l'ordre du menu, par identifiant en majuscules. */
    public final Map<String, Apparence> types;
    /** Apparence dessinée par FancyNpcs quand un modèle manque. */
    public final Apparence secours;
    public final String usagePermission;
    public final Remplacements remplacements;
    public final double defaultZoneWidth;
    public final double defaultZoneHeight;

    public final ReglagesReactions reactions;

    public final int closeTicks;
    public final Frame mainFrame;
    public final int typeSlot;
    public final int visibilitySlot;
    public final Material visibleMaterial;
    public final Material hiddenMaterial;
    public final int positionSlot;
    public final Material positionMaterial;
    public final int discreetSlot;
    public final Material discreetMaterial;
    public final Material talkativeMaterial;
    public final int backSlot;
    public final Material backMaterial;
    public final String backCommand;
    /** Retour selon d'où le menu a été ouvert : /jake reglages <origine> ; une origine inconnue garde backCommand. */
    public final Map<String, String> backByOrigin;
    public final Frame typesFrame;
    public final List<Integer> typesSlots;
    public final int typesBackSlot;
    public final Material typesBackMaterial;

    public final String soundClick;
    public final String soundMenu;
    public final String soundSuccess;
    public final String soundRefusal;

    Settings(FileConfiguration c, Logger log) {
        types = Collections.unmodifiableMap(readTypes(c.getConfigurationSection("types"), log));
        String type = Remplacements.id(c.getString("jake.type", "VILLAGER"));
        if (!types.containsKey(type)) {
            String first = types.isEmpty() ? VILLAGEOIS.id() : types.keySet().iterator().next();
            log.warning("jake.type « " + type + " » absent de « types » : " + first + " est utilisé.");
            type = first;
        }
        defaultType = type;
        defaultVisible = c.getBoolean("jake.visible", true);
        createWithIsland = c.getBoolean("jake.creation-avec-ile", true);
        offset = new Vector(c.getDouble("jake.position.x", 3), c.getDouble("jake.position.y", 0),
                c.getDouble("jake.position.z", 6));
        yaw = (float) c.getDouble("jake.position.yaw", 180);
        pitch = (float) c.getDouble("jake.position.pitch", 0);

        String fallback = Remplacements.id(c.getString("apparences.secours", ""));
        Apparence secoursLu = types.get(fallback);
        if (secoursLu == null || secoursLu.estModele()) {
            secoursLu = null;
            for (Apparence a : types.values()) {
                if (!a.estModele()) {
                    secoursLu = a;
                    break;
                }
            }
            if (!fallback.isEmpty()) {
                log.warning("apparences.secours « " + fallback + " » n'est pas une apparence « mob » de « types » : "
                        + (secoursLu == null ? VILLAGEOIS.id() : secoursLu.id()) + " est utilisée.");
            }
        }
        secours = secoursLu == null ? VILLAGEOIS : secoursLu;
        usagePermission = c.getString("apparences.permission-usage", "").trim();
        remplacements = Remplacements.lire(c.getConfigurationSection("apparences.remplacements"));
        defaultZoneWidth = Math.max(0.1, c.getDouble("apparences.zone-clic.largeur", 1.0));
        defaultZoneHeight = Math.max(0.1, c.getDouble("apparences.zone-clic.hauteur", 2.0));

        viewDistance = Math.max(1, c.getInt("affichage.distance", 32));
        trackerInterval = Math.max(1, c.getInt("affichage.intervalle-ticks", 10));
        forgetMillis = Math.max(1, c.getInt("affichage.oubli-minutes", 10)) * 60_000L;
        lookAtPlayers = c.getBoolean("affichage.regarder-joueurs", true);
        lookDistance = Math.max(1, c.getInt("affichage.distance-regard", 6));
        lookIntervalTicks = Math.min(20, Math.max(1, c.getInt("affichage.intervalle-regard-ticks", 2)));
        lookSpeed = Math.max(0, c.getDouble("affichage.vitesse-regard", 180));
        lookMinAngle = Math.max(0.1, c.getDouble("affichage.angle-regard-minimum", 2));
        lookMaxPitch = Math.min(90, Math.max(0, c.getDouble("affichage.inclinaison-regard-max", 40)));
        glowing = c.getBoolean("affichage.brillance", false);
        NamedTextColor color = NamedTextColor.NAMES.value(c.getString("affichage.couleur-brillance", "BLUE").toLowerCase(Locale.ROOT));
        if (color == null) {
            log.warning("affichage.couleur-brillance inconnue : BLUE est utilisée.");
            color = NamedTextColor.BLUE;
        }
        glowColor = color;

        hologram = c.getBoolean("hologramme.active", true);
        billboard = billboard(c.getString("hologramme.orientation", "VERTICAL"), log);
        background = color(c.getString("hologramme.fond", "transparent"), "hologramme.fond", log);
        textShadow = c.getBoolean("hologramme.ombre-texte", true);
        lineWidth = Math.max(10, c.getInt("hologramme.largeur-ligne", 200));
        defaultHeight = c.getDouble("hologramme.hauteur", 2.1);

        bubbleBackground = color(c.getString("bulle.fond", "transparent"), "bulle.fond", log);
        bubbleLineWidth = Math.max(10, c.getInt("bulle.largeur-ligne", 150));
        ticksPerWord = Math.max(0, c.getInt("bulle.ticks-par-mot", 2));
        bubbleBaseSeconds = Math.max(0, c.getDouble("bulle.duree-base-secondes", 2.5));
        bubblePerCharacter = Math.max(0, c.getDouble("bulle.duree-par-caractere", 0.06));
        bubbleGrowTicks = Math.max(0, c.getInt("bulle.apparition-ticks", 3));

        clickCommand = stripSlash(c.getString("clic.commande", ""));
        leftClick = c.getBoolean("clic.clic-gauche", true);
        sneakOpensMenu = c.getBoolean("clic.accroupi-ouvre-menu", true);
        membersCanClick = c.getBoolean("clic.membres", true);
        clickDelayMillis = Math.max(0, c.getLong("clic.delai-ms", 500));
        clickAnimation = c.getString("clic.animation", "clic").trim();

        noArgCommand = stripSlash(c.getString("jake-sans-argument.commande", ""));
        unknownCommand = stripSlash(c.getString("jake-argument-inconnu.commande", ""));
        unknownSuggestions = List.copyOf(c.getStringList("jake-argument-inconnu.suggestions"));

        alertEnabled = c.getBoolean("alerte.actif", false);
        alertPlaceholder = c.getString("alerte.placeholder", "");
        alertValue = c.getString("alerte.valeur", "oui");
        alertOffset = c.getDouble("alerte.decalage", 0.6);
        alertCheckTicks = Math.max(1, c.getInt("alerte.verification-ticks", 20));

        reactions = ReglagesReactions.lire(c.getConfigurationSection("reactions"), log::warning);

        moveSeconds = Math.max(1, c.getInt("deplacement.temps-secondes", 15));
        List<String> sub = new ArrayList<>();
        for (String name : c.getStringList("sous-commandes-ile")) {
            if (!name.isBlank()) {
                sub.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        islandSubCommands = List.copyOf(sub);

        closeTicks = Math.max(0, c.getInt("menu.fermeture-ticks", 6));
        mainFrame = frame(c, "menu.principal", log);
        typeSlot = c.getInt("menu.principal.type", 20);
        visibilitySlot = c.getInt("menu.principal.visibilite", 22);
        visibleMaterial = material(c, "menu.principal.materiau-visible", Material.LIME_DYE, log);
        hiddenMaterial = material(c, "menu.principal.materiau-cache", Material.GRAY_DYE, log);
        positionSlot = c.getInt("menu.principal.position", 24);
        positionMaterial = material(c, "menu.principal.position-materiau", Material.COMPASS, log);
        discreetSlot = c.getInt("menu.principal.discret", 31);
        discreetMaterial = material(c, "menu.principal.materiau-discret", Material.GRAY_DYE, log);
        talkativeMaterial = material(c, "menu.principal.materiau-bavard", Material.WRITABLE_BOOK, log);
        backSlot = c.getInt("menu.principal.retour", 36);
        backMaterial = material(c, "menu.principal.retour-materiau", Material.PAPER, log);
        backCommand = stripSlash(c.getString("menu.principal.retour-commande", ""));
        Map<String, String> origins = new HashMap<>();
        ConfigurationSection retours = c.getConfigurationSection("menu.principal.retours");
        if (retours != null) {
            for (String k : retours.getKeys(false)) {
                origins.put(k.toLowerCase(Locale.ROOT), stripSlash(retours.getString(k, "")));
            }
        }
        backByOrigin = Map.copyOf(origins);
        typesFrame = frame(c, "menu.types", log);
        typesSlots = List.copyOf(c.getIntegerList("menu.types.cases"));
        typesBackSlot = c.getInt("menu.types.retour", 36);
        typesBackMaterial = material(c, "menu.types.retour-materiau", Material.PAPER, log);

        soundClick = c.getString("sons.clic", "");
        soundMenu = c.getString("sons.menu", "");
        soundSuccess = c.getString("sons.succes", "");
        soundRefusal = c.getString("sons.refus", "");
    }

    /** Apparence de cet identifiant ; une apparence inconnue (retirée de la configuration) prend celle de jake.type. */
    public Apparence apparence(String id) {
        Apparence a = types.get(Remplacements.id(id));
        if (a == null) {
            a = types.get(defaultType);
        }
        return a == null ? secours : a;
    }

    /** Ce que ce joueur peut faire de cette apparence. Sans permission-usage, le droit de l'apparence suffit. */
    public Etat etat(Permissible player, Apparence a) {
        if (a.permission().isEmpty()) {
            return Etat.DISPONIBLE;
        }
        boolean obtenue = player.hasPermission(a.permission());
        if (usagePermission.isEmpty()) {
            return obtenue ? Etat.DISPONIBLE : Etat.RESERVE;
        }
        if (!obtenue) {
            return Etat.NON_OBTENUE;
        }
        return player.hasPermission(usagePermission) ? Etat.DISPONIBLE : Etat.RESERVE;
    }

    /** Cadre du menu « path » ; sans cases écrites, le bord est le tour du menu et l'accent les colonnes 2 et 8. */
    private static Frame frame(FileConfiguration c, String path, Logger log) {
        int rows = Math.min(6, Math.max(1, c.getInt(path + ".lignes", 5)));
        List<Integer> ring = new ArrayList<>();
        List<Integer> accent = new ArrayList<>();
        for (int slot = 0; slot < rows * 9; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                ring.add(slot);
            } else if (column == 1 || column == 7) {
                accent.add(slot);
            }
        }
        if (c.isList(path + ".contour-cases")) {
            ring = c.getIntegerList(path + ".contour-cases");
        }
        if (c.isList(path + ".accent-cases")) {
            accent = c.getIntegerList(path + ".accent-cases");
        }
        return new Frame(rows, pane(c, path + ".remplissage", null, log),
                pane(c, "menu.contour", Material.BLACK_STAINED_GLASS_PANE, log), List.copyOf(ring),
                pane(c, "menu.accent", Material.CYAN_STAINED_GLASS_PANE, log), List.copyOf(accent),
                c.getInt(path + ".jake", 4), c.getString("menu.jake-objet", "PLAYER_HEAD"));
    }

    /** Objet facultatif : null quand la clé est vide. */
    private static Material pane(FileConfiguration c, String path, Material fallback, Logger log) {
        String name = c.getString(path);
        return name != null && name.isBlank() ? null : material(c, path, fallback, log);
    }

    private static Map<String, Apparence> readTypes(ConfigurationSection section, Logger log) {
        Map<String, Apparence> result = new LinkedHashMap<>();
        if (section == null) {
            log.warning("Aucune apparence dans « types » : seul VILLAGER est proposé.");
            result.put(VILLAGEOIS.id(), VILLAGEOIS);
            return result;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String id = Remplacements.id(key);
            String modele = entry.getString("modele", "").trim();
            EntityType mob = null;
            if (modele.isEmpty()) {
                // sans « mob », l'identifiant lui-même (VILLAGER, WITCH...) : la forme des versions 1.x
                String mobName = entry.getString("mob", key);
                mob = entityType(mobName, log, "types." + key + ".mob");
                if (mob == null) {
                    continue;
                }
            }
            Material icon = Material.matchMaterial(entry.getString("icone", "VILLAGER_SPAWN_EGG"));
            if (icon == null || !icon.isItem()) {
                log.warning("types." + key + ".icone inconnue : VILLAGER_SPAWN_EGG est utilisée.");
                icon = Material.VILLAGER_SPAWN_EGG;
            }
            double height = entry.contains("hauteur") ? entry.getDouble("hauteur") : modele.isEmpty() ? 2.1 : Double.NaN;
            double zoneWidth = entry.contains("zone-clic.largeur") ? Math.max(0.1, entry.getDouble("zone-clic.largeur")) : Double.NaN;
            double zoneHeight = entry.contains("zone-clic.hauteur") ? Math.max(0.1, entry.getDouble("zone-clic.hauteur")) : Double.NaN;
            Map<String, String> sons = new HashMap<>();
            ConfigurationSection s = entry.getConfigurationSection("sons");
            if (s != null) {
                for (String reaction : s.getKeys(false)) {
                    sons.put(reaction.toLowerCase(Locale.ROOT), s.getString(reaction, "").trim());
                }
            }
            String caractere = entry.getString("caractere", "defaut").trim();
            result.put(id, new Apparence(id, modele, (float) Math.max(0.05, entry.getDouble("taille", 1.0)), mob, icon,
                    height, zoneWidth, zoneHeight, entry.getString("permission", "").trim(),
                    caractere.isEmpty() ? "defaut" : caractere, Map.copyOf(sons)));
        }
        if (result.isEmpty()) {
            log.warning("Aucune apparence lisible dans « types » : seul VILLAGER est proposé.");
            result.put(VILLAGEOIS.id(), VILLAGEOIS);
        }
        return result;
    }

    /** Type d'entité que FancyNpcs sait dessiner sans skin ; null s'il est inconnu. */
    static EntityType entityType(String name, Logger log, String where) {
        try {
            EntityType type = EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
            if (type != EntityType.PLAYER && type != EntityType.UNKNOWN && type.isAlive()) {
                return type;
            }
        } catch (IllegalArgumentException ignored) {
            // signalé ci-dessous
        }
        if (log != null) {
            log.warning(where + " : type « " + name + " » inconnu ou non vivant, ignoré.");
        }
        return null;
    }

    /** Orientation des textes, en octet du protocole : FIXED 0, VERTICAL 1, HORIZONTAL 2, CENTER 3. */
    private static byte billboard(String name, Logger log) {
        try {
            return switch (Display.Billboard.valueOf(name.trim().toUpperCase(Locale.ROOT))) {
                case FIXED -> 0;
                case VERTICAL -> 1;
                case HORIZONTAL -> 2;
                case CENTER -> 3;
            };
        } catch (IllegalArgumentException e) {
            log.warning("hologramme.orientation inconnue : VERTICAL est utilisée.");
            return 1;
        }
    }

    /** transparent, default (null : fond gris de Minecraft) ou #AARRGGBB. */
    private static Integer color(String value, String path, Logger log) {
        if (value == null || value.equalsIgnoreCase("transparent")) {
            return 0;
        }
        if (value.equalsIgnoreCase("default")) {
            return null;
        }
        try {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            log.warning(path + " « " + value + " » illisible : fond transparent.");
            return 0;
        }
    }

    private static Material material(FileConfiguration c, String path, Material fallback, Logger log) {
        String name = c.getString(path);
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            log.warning(path + " : objet « " + name + " » inconnu"
                    + (fallback == null ? "." : ", " + fallback.name() + " est utilisé."));
            return fallback;
        }
        return material;
    }

    private static String stripSlash(String command) {
        command = command == null ? "" : command.trim();
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
