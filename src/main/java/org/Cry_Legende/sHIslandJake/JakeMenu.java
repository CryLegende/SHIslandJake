package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.island.Island;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paramètres de Jake (type, visibilité, position) et choix du type. Ouvert et cliqué sur le fil principal.
 */
final class JakeMenu implements InventoryHolder {

    enum Kind {
        MAIN,
        TYPES
    }

    private static final int SIZE = 27;

    private final SHIslandJake plugin;
    private final Kind kind;
    private final UUID island;
    private final Map<Integer, EntityType> typeSlots = new HashMap<>();
    private Inventory inventory;

    private JakeMenu(SHIslandJake plugin, Kind kind, UUID island) {
        this.plugin = plugin;
        this.kind = kind;
        this.island = island;
    }

    static void open(SHIslandJake plugin, Player player, Kind kind, Jake jake) {
        JakeMenu menu = new JakeMenu(plugin, kind, jake.island());
        menu.build(player, jake);
        player.openInventory(menu.inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void build(Player player, Jake jake) {
        Messages m = plugin.messages();
        Settings s = plugin.settings();
        JakeData d = jake.data();
        String name = m.jakeName(d.type());
        String titleKey = kind == Kind.MAIN ? "menu.principal.titre" : "menu.types.titre";
        inventory = Bukkit.createInventory(this, SIZE, Messages.chat(m.text(titleKey, "nom", name)));
        ItemStack filler = item(kind == Kind.MAIN ? s.mainFiller : s.typesFiller, " ", List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        if (kind == Kind.MAIN) {
            buildMain(player, d, name);
        } else {
            buildTypes(player, d);
        }
    }

    private void buildMain(Player player, JakeData d, String name) {
        Messages m = plugin.messages();
        Settings s = plugin.settings();
        ItemStack gray = item(s.mainGray, " ", List.of());
        for (int slot : s.mainGraySlots) {
            put(slot, gray);
        }

        List<String> typeLines = new ArrayList<>();
        boolean otherAllowed = false;
        for (Settings.TypeSettings type : s.types.values()) {
            String key = type.type() == d.type() ? "menu.principal.type.ligne-actuel" : "menu.principal.type.ligne";
            typeLines.add(m.text(key, "type", m.typeName(type.type())));
            otherAllowed |= type.type() != d.type() && type.allowed(player);
        }
        String reserve = otherAllowed ? null : m.text("menu.principal.type.reserve");
        List<String> lore = new ArrayList<>();
        for (String line : m.lines("menu.principal.type.description", "nom", name)) {
            if (line.equals("{types}")) {
                lore.addAll(typeLines);
            } else if (line.equals("{reserve}")) {
                if (reserve != null && !reserve.isEmpty()) {
                    lore.add(reserve);
                }
            } else {
                lore.add(line);
            }
        }
        Settings.TypeSettings current = s.types.get(d.type());
        Material icon = current != null ? current.icon() : Material.VILLAGER_SPAWN_EGG;
        put(s.typeSlot, item(icon, m.text("menu.principal.type.nom", "nom", name), lore));

        String state = m.text(d.visible() ? "menu.principal.visibilite.etat-visible" : "menu.principal.visibilite.etat-cache");
        put(s.visibilitySlot, item(d.visible() ? s.visibleMaterial : s.hiddenMaterial,
                m.text("menu.principal.visibilite.nom", "nom", name),
                m.lines("menu.principal.visibilite.description", "nom", name, "etat", state)));

        put(s.positionSlot, item(s.positionMaterial, m.text("menu.principal.position.nom", "nom", name),
                m.lines("menu.principal.position.description", "nom", name)));

        if (!s.backCommand.isEmpty()) {
            put(s.backSlot, item(s.backMaterial, m.text("menu.principal.retour.nom"), List.of()));
        }
    }

    private void buildTypes(Player player, JakeData d) {
        Messages m = plugin.messages();
        Settings s = plugin.settings();
        List<Integer> slots = s.typesSlots;
        int index = 0;
        for (Settings.TypeSettings type : s.types.values()) {
            if (index >= slots.size()) {
                plugin.getLogger().warning("menu.types.cases : pas assez de cases pour tous les types.");
                break;
            }
            int slot = slots.get(index++);
            String typeName = m.typeName(type.type());
            String jakeName = m.jakeName(type.type());
            String state = type.type() == d.type() ? "actuel" : type.allowed(player) ? "disponible" : "reserve";
            ItemStack stack = item(type.icon(), m.text("menu.types.type.nom", "type", typeName, "jake", jakeName),
                    m.lines("menu.types.type." + state, "type", typeName, "jake", jakeName));
            if (state.equals("actuel")) {
                ItemMeta meta = stack.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                stack.setItemMeta(meta);
            }
            put(slot, stack);
            typeSlots.put(slot, type.type());
        }
        put(s.typesBackSlot, item(s.typesBackMaterial, m.text("menu.types.retour.nom"), List.of()));
    }

    /** Clic dans le menu (fil principal) : l'île et le Jake sont revérifiés, le menu a pu rester ouvert. */
    void click(Player player, int slot) {
        Settings s = plugin.settings();
        Island isl = plugin.islands().byId(island);
        Jake jake = plugin.registry().get(island);
        if (isl == null || jake == null || !plugin.islands().canUse(player, isl, true)) {
            player.closeInventory();
            return;
        }
        if (kind == Kind.MAIN) {
            if (slot == s.typeSlot) {
                open(plugin, player, Kind.TYPES, jake);
            } else if (slot == s.visibilitySlot) {
                plugin.commands().toggleVisibility(player, jake);
                open(plugin, player, Kind.MAIN, jake);
            } else if (slot == s.positionSlot) {
                player.closeInventory();
                plugin.commands().startMove(player, jake);
            } else if (slot == s.backSlot && !s.backCommand.isEmpty()) {
                player.closeInventory();
                player.performCommand(s.backCommand);
            }
            return;
        }
        if (slot == s.typesBackSlot) {
            open(plugin, player, Kind.MAIN, jake);
            return;
        }
        EntityType type = typeSlots.get(slot);
        if (type != null && plugin.commands().changeType(player, jake, type)) {
            open(plugin, player, Kind.TYPES, jake);
        }
    }

    private void put(int slot, ItemStack item) {
        if (slot >= 0 && slot < SIZE) {
            inventory.setItem(slot, item);
        }
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.item(name));
        List<Component> lines = new ArrayList<>(lore.size());
        for (String line : lore) {
            lines.add(Messages.item(line));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
