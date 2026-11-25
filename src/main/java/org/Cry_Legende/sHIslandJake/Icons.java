package org.Cry_Legende.sHIslandJake;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Objet d'un menu : un matériau ({@code CHEST}), une tête de HeadDatabase ({@code hdb-81626}, si le plugin est là) ou une
 * tête texturée ({@code basehead-<valeur base64>}). Un nom inconnu devient du papier.
 */
final class Icons {

    private Icons() {
    }

    static ItemStack make(String spec) {
        String s = spec == null ? "" : spec.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("hdb-")) {
            return headDatabase(s.substring(4));
        }
        if (lower.startsWith("basehead-")) {
            return textured(s.substring("basehead-".length()));
        }
        Material material = Material.matchMaterial(s);
        return new ItemStack(material == null || !material.isItem() ? Material.PAPER : material);
    }

    /** HeadDatabase lu par réflexion : pas de dépendance à la compilation, tête simple si le plugin manque ou charge encore. */
    private static ItemStack headDatabase(String id) {
        if (Bukkit.getPluginManager().isPluginEnabled("HeadDatabase")) {
            try {
                Class<?> api = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI", true,
                        Bukkit.getPluginManager().getPlugin("HeadDatabase").getClass().getClassLoader());
                Object item = api.getMethod("getItemHead", String.class).invoke(api.getConstructor().newInstance(), id);
                if (item instanceof ItemStack head) {
                    return head.clone();
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // tête simple
            }
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    private static ItemStack textured(String value) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (value.isEmpty() || !(head.getItemMeta() instanceof SkullMeta meta)) {
            return head;
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", value));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }
}
