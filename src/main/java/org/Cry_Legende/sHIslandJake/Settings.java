package org.Cry_Legende.sHIslandJake;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Réglages lus dans config.yml, figés jusqu'au prochain reload.
 */
public final class Settings {

    /** Un type que les joueurs peuvent choisir. */
    public record TypeSettings(EntityType type, Material icon, double height, String permission) {

        public boolean allowed(org.bukkit.permissions.Permissible player) {
            return permission.isEmpty() || player.hasPermission(permission);
        }
    }

    public final EntityType defaultType;
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
    public final Display.Billboard billboard;
    public final String background;
    public final boolean textShadow;
    public final double defaultHeight;

    public final String clickCommand;
    public final boolean leftClick;
    public final boolean sneakOpensMenu;
    public final boolean membersCanClick;
    public final long clickDelayMillis;

    public final int moveSeconds;
    public final List<String> islandSubCommands;

    public final Map<EntityType, TypeSettings> types;

    public final Material mainFiller;
    public final List<Integer> mainGraySlots;
    public final Material mainGray;
    public final int typeSlot;
    public final int visibilitySlot;
    public final Material visibleMaterial;
    public final Material hiddenMaterial;
    public final int positionSlot;
    public final Material positionMaterial;
    public final int backSlot;
    public final Material backMaterial;
    public final String backCommand;
    public final Material typesFiller;
    public final List<Integer> typesSlots;
    public final int typesBackSlot;
    public final Material typesBackMaterial;

    public final String soundClick;
    public final String soundMenu;
    public final String soundSuccess;
    public final String soundRefusal;

    Settings(FileConfiguration c, Logger log) {
        types = Collections.unmodifiableMap(readTypes(c.getConfigurationSection("types"), log));
        EntityType type = entityType(c.getString("jake.type", "VILLAGER"), log, "jake.type");
        if (type == null || !types.containsKey(type)) {
            type = types.isEmpty() ? EntityType.VILLAGER : types.keySet().iterator().next();
            log.warning("jake.type absent de « types » : " + type.name() + " est utilisé.");
        }
        defaultType = type;
        defaultVisible = c.getBoolean("jake.visible", true);
        createWithIsland = c.getBoolean("jake.creation-avec-ile", true);
        offset = new Vector(c.getDouble("jake.position.x", 3), c.getDouble("jake.position.y", 0),
                c.getDouble("jake.position.z", 6));
        yaw = (float) c.getDouble("jake.position.yaw", 180);
        pitch = (float) c.getDouble("jake.position.pitch", 0);

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
        billboard = billboard(c.getString("hologramme.orientation", "CENTER"), log);
        background = c.getString("hologramme.fond", "transparent");
        textShadow = c.getBoolean("hologramme.ombre-texte", true);
        defaultHeight = c.getDouble("hologramme.hauteur", 2.1);

        clickCommand = stripSlash(c.getString("clic.commande", ""));
        leftClick = c.getBoolean("clic.clic-gauche", true);
        sneakOpensMenu = c.getBoolean("clic.accroupi-ouvre-menu", true);
        membersCanClick = c.getBoolean("clic.membres", true);
        clickDelayMillis = Math.max(0, c.getLong("clic.delai-ms", 500));

        moveSeconds = Math.max(1, c.getInt("deplacement.temps-secondes", 15));
        List<String> sub = new ArrayList<>();
        for (String name : c.getStringList("sous-commandes-ile")) {
            if (!name.isBlank()) {
                sub.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        islandSubCommands = List.copyOf(sub);

        mainFiller = material(c, "menu.principal.remplissage", Material.BLACK_STAINED_GLASS_PANE, log);
        mainGraySlots = List.copyOf(c.getIntegerList("menu.principal.cases-grises"));
        mainGray = material(c, "menu.principal.materiau-gris", Material.GRAY_STAINED_GLASS_PANE, log);
        typeSlot = c.getInt("menu.principal.type", 11);
        visibilitySlot = c.getInt("menu.principal.visibilite", 13);
        visibleMaterial = material(c, "menu.principal.materiau-visible", Material.LIME_DYE, log);
        hiddenMaterial = material(c, "menu.principal.materiau-cache", Material.GRAY_DYE, log);
        positionSlot = c.getInt("menu.principal.position", 15);
        positionMaterial = material(c, "menu.principal.position-materiau", Material.COMPASS, log);
        backSlot = c.getInt("menu.principal.retour", 22);
        backMaterial = material(c, "menu.principal.retour-materiau", Material.PAPER, log);
        backCommand = stripSlash(c.getString("menu.principal.retour-commande", ""));
        typesFiller = material(c, "menu.types.remplissage", Material.BLACK_STAINED_GLASS_PANE, log);
        typesSlots = List.copyOf(c.getIntegerList("menu.types.cases"));
        typesBackSlot = c.getInt("menu.types.retour", 22);
        typesBackMaterial = material(c, "menu.types.retour-materiau", Material.PAPER, log);

        soundClick = c.getString("sons.clic", "");
        soundMenu = c.getString("sons.menu", "");
        soundSuccess = c.getString("sons.succes", "");
        soundRefusal = c.getString("sons.refus", "");
    }

    /** Hauteur de l'hologramme pour ce type. */
    public double height(EntityType type) {
        TypeSettings settings = types.get(type);
        return settings == null ? defaultHeight : settings.height();
    }

    private Map<EntityType, TypeSettings> readTypes(ConfigurationSection section, Logger log) {
        Map<EntityType, TypeSettings> result = new LinkedHashMap<>();
        if (section == null) {
            log.warning("Aucun type dans « types » : seul VILLAGER est proposé.");
            result.put(EntityType.VILLAGER, new TypeSettings(EntityType.VILLAGER, Material.VILLAGER_SPAWN_EGG, 2.1, ""));
            return result;
        }
        for (String key : section.getKeys(false)) {
            EntityType type = entityType(key, log, "types." + key);
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (type == null || entry == null) {
                continue;
            }
            Material icon = Material.matchMaterial(entry.getString("icone", "VILLAGER_SPAWN_EGG"));
            if (icon == null || !icon.isItem()) {
                log.warning("types." + key + ".icone inconnue : VILLAGER_SPAWN_EGG est utilisée.");
                icon = Material.VILLAGER_SPAWN_EGG;
            }
            result.put(type, new TypeSettings(type, icon, entry.getDouble("hauteur", 2.1),
                    entry.getString("permission", "").trim()));
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

    private static Display.Billboard billboard(String name, Logger log) {
        try {
            return Display.Billboard.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("hologramme.orientation inconnue : CENTER est utilisée.");
            return Display.Billboard.CENTER;
        }
    }

    private static Material material(FileConfiguration c, String path, Material fallback, Logger log) {
        String name = c.getString(path);
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            log.warning(path + " : objet « " + name + " » inconnu, " + fallback.name() + " est utilisé.");
            return fallback;
        }
        return material;
    }

    private static String stripSlash(String command) {
        command = command == null ? "" : command.trim();
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
