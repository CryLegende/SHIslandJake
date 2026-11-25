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
 * Réglages de Jake (apparence, visibilité, position) et choix de l'apparence. Ouvert et cliqué sur le fil principal.
 */
final class JakeMenu implements InventoryHolder {

    enum Kind {
        MAIN,
        TYPES
    }

    private final SHIslandJake plugin;
    private final Kind kind;
    private final UUID island;
    /** Commande du bouton retour, gardée quand le menu se rouvre (visibilité, choix du type). */
    private final String back;
    private final Map<Integer, EntityType> typeSlots = new HashMap<>();
    private Inventory inventory;

    private JakeMenu(SHIslandJake plugin, Kind kind, UUID island, String back) {
        this.plugin = plugin;
        this.kind = kind;
        this.island = island;
        this.back = back == null ? "" : back;
    }

    static void open(SHIslandJake plugin, Player player, Kind kind, Jake jake, String back) {
        JakeMenu menu = new JakeMenu(plugin, kind, jake.island(), back);
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
        String menu = kind == Kind.MAIN ? "menu.principal." : "menu.types.";
        Settings.Frame frame = kind == Kind.MAIN ? s.mainFrame : s.typesFrame;
        inventory = Bukkit.createInventory(this, frame.size(), Messages.chat(m.text(menu + "titre", "nom", name)));
        if (frame.fill() != null) {
            for (int slot = 0; slot < frame.size(); slot++) {
                put(slot, pane(frame.fill()));
            }
        }
        panes(frame.ring(), frame.ringSlots());
        panes(frame.accent(), frame.accentSlots());
        if (frame.headSlot() >= 0) {
            put(frame.headSlot(), item(Icons.make(frame.headIcon()), m.text(menu + "jake.nom", "nom", name),
                    m.lines(menu + "jake.description", "nom", name, "type", m.typeName(d.type()))));
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

        String state = d.visible() ? "visible" : "cache";
        put(s.visibilitySlot, item(d.visible() ? s.visibleMaterial : s.hiddenMaterial,
                m.text("menu.principal.visibilite.nom", "nom", name),
                m.lines("menu.principal.visibilite.description", "nom", name,
                        "etat", m.text("menu.principal.visibilite.etat-" + state, "nom", name),
                        "action", m.text("menu.principal.visibilite.action-" + state, "nom", name))));

        put(s.positionSlot, item(s.positionMaterial, m.text("menu.principal.position.nom", "nom", name),
                m.lines("menu.principal.position.description", "nom", name)));

        if (!back.isEmpty()) {
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
                open(plugin, player, Kind.TYPES, jake, back);
            } else if (slot == s.visibilitySlot) {
                plugin.commands().toggleVisibility(player, jake);
                open(plugin, player, Kind.MAIN, jake, back);
            } else if (slot == s.positionSlot) {
                player.closeInventory();
                plugin.commands().startMove(player, jake);
            } else if (slot == s.backSlot && !back.isEmpty()) {
                goBack(player);
            }
            return;
        }
        if (slot == s.typesBackSlot) {
            open(plugin, player, Kind.MAIN, jake, back);
            return;
        }
        EntityType type = typeSlots.get(slot);
        if (type != null && plugin.commands().changeType(player, jake, type)) {
            open(plugin, player, Kind.TYPES, jake, back);
        }
    }

    /** Le menu de la commande remplace celui-ci ; si elle n'en ouvre pas, celui-ci se ferme. */
    private void goBack(Player player) {
        player.performCommand(back);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder(false) == this) {
                player.closeInventory();
            }
        }, plugin.settings().closeTicks);
    }

    private void put(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    private void panes(Material material, List<Integer> slots) {
        if (material != null) {
            for (int slot : slots) {
                put(slot, pane(material));
            }
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack stack = item(material, " ", List.of());
        ItemMeta meta = stack.getItemMeta();
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        return item(new ItemStack(material), name, lore);
    }

    private static ItemStack item(ItemStack stack, String name, List<String> lore) {
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
