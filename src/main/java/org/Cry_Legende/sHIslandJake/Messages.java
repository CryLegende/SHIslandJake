package org.Cry_Legende.sHIslandJake;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Textes de messages.yml. Le fichier du jar sert de valeurs par défaut : une clé absente du fichier du serveur
 * (ajoutée dans une version plus récente) garde le texte du jar, une valeur vide posée exprès reste muette.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();

    private final YamlConfiguration file;

    Messages(YamlConfiguration file) {
        this.file = file;
    }

    /** Envoie chat.« key » ; un message vide n'est pas envoyé. */
    public void send(CommandSender to, String key, String... replacements) {
        String text = replace(file.getString("chat." + key), replacements);
        if (!text.isEmpty()) {
            to.sendMessage(LEGACY.deserialize(text));
        }
    }

    public String text(String key, String... replacements) {
        return replace(file.getString(key), replacements);
    }

    public List<String> lines(String key, String... replacements) {
        List<String> lines = new ArrayList<>();
        for (String line : file.getStringList(key)) {
            lines.add(replace(line, replacements));
        }
        return lines;
    }

    /** Nom affiché du type (types.<TYPE>.nom), à défaut le nom Minecraft. */
    public String typeName(EntityType type) {
        String name = file.getString("types." + type.name() + ".nom");
        return name == null || name.isEmpty() ? pretty(type) : name;
    }

    /** Nom de Jake sous ce type (types.<TYPE>.jake), à défaut types.defaut-jake. */
    public String jakeName(EntityType type) {
        String name = file.getString("types." + type.name() + ".jake");
        if (name == null || name.isEmpty()) {
            name = file.getString("types.defaut-jake");
        }
        return name == null || name.isEmpty() ? "Jake" : name;
    }

    public static Component chat(String text) {
        return LEGACY.deserialize(text);
    }

    /** Texte d'objet de menu : couleurs lues, sans l'italique que le jeu ajoute. */
    public static Component item(String text) {
        return LEGACY.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Texte & converti en MiniMessage, la forme que FancyNpcs et FancyHolograms lisent. */
    public static String miniMessage(String text) {
        return MiniMessage.miniMessage().serialize(LEGACY.deserialize(text));
    }

    public static String replace(String text, String... replacements) {
        if (text == null) {
            return "";
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return text;
    }

    private static String pretty(EntityType type) {
        String name = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
