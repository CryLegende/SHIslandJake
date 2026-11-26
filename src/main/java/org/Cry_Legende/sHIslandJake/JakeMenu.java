package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.island.Island;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Réglages de Jake (apparence, visibilité, position, Jake discret) et choix de l'apparence. Ouvert et cliqué sur le
 * fil principal.
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
    private final Map<Integer, Settings.Apparence> typeSlots = new HashMap<>();
    /** Page des apparences (à partir de 0) ; flèches montrées seulement s'il y a une page avant ou après. */
    private int page;
    private boolean precedente;
    private boolean suivante;
    private Inventory inventory;

    private JakeMenu(SHIslandJake plugin, Kind kind, UUID island, String back, int page) {
        this.plugin = plugin;
        this.kind = kind;
        this.island = island;
        this.back = back == null ? "" : back;
        this.page = Math.max(0, page);
    }

    static void open(SHIslandJake plugin, Player player, Kind kind, Jake jake, String back) {
        open(plugin, player, kind, jake, back, 0);
    }

    static void open(SHIslandJake plugin, Player player, Kind kind, Jake jake, String back, int page) {
        JakeMenu menu = new JakeMenu(plugin, kind, jake.island(), back, page);
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
        String actuelle = s.apparence(d.type()).id();
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
                    m.lines(menu + "jake.description", "nom", name, "type", m.typeName(actuelle))));
        }
        if (kind == Kind.MAIN) {
            buildMain(player, d, name, actuelle);
        } else {
            buildTypes(player, actuelle);
        }
    }

    private void buildMain(Player player, JakeData d, String name, String actuelle) {
        Messages m = plugin.messages();
        Settings s = plugin.settings();
        List<String> typeLines = new ArrayList<>();
        boolean otherAllowed = false;
        for (Settings.Apparence type : s.types.values()) {
            if (!s.proposee(type)) {
                continue;
            }
            boolean current = type.id().equals(actuelle);
            typeLines.add(m.text(current ? "menu.principal.type.ligne-actuel" : "menu.principal.type.ligne",
                    "type", m.typeName(type.id())));
            otherAllowed |= !current && s.etat(player, type) == Settings.Etat.DISPONIBLE;
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
        put(s.typeSlot, item(Icons.make(s.apparence(d.type()).icone()), m.text("menu.principal.type.nom", "nom", name), lore));

        String state = d.visible() ? "visible" : "cache";
        put(s.visibilitySlot, item(d.visible() ? s.visibleMaterial : s.hiddenMaterial,
                m.text("menu.principal.visibilite.nom", "nom", name),
                m.lines("menu.principal.visibilite.description", "nom", name,
                        "etat", m.text("menu.principal.visibilite.etat-" + state, "nom", name),
                        "action", m.text("menu.principal.visibilite.action-" + state, "nom", name))));

        put(s.positionSlot, item(s.positionMaterial, m.text("menu.principal.position.nom", "nom", name),
                m.lines("menu.principal.position.description", "nom", name)));

        boolean discret = plugin.discret().est(player.getUniqueId());
        String mode = discret ? "discret" : "bavard";
        put(s.discreetSlot, item(discret ? s.discreetMaterial : s.talkativeMaterial,
                m.text("menu.principal.discret.nom", "nom", name),
                m.lines("menu.principal.discret.description", "nom", name,
                        "etat", m.text("menu.principal.discret.etat-" + mode, "nom", name),
                        "action", m.text("menu.principal.discret.action-" + mode, "nom", name))));

        if (!back.isEmpty()) {
            put(s.backSlot, item(s.backMaterial, m.text("menu.principal.retour.nom"), List.of()));
        }
    }

    /**
     * Une carte par apparence proposée, dans l'ordre de « types », répartie toute seule dans les lignes du milieu (une
     * page de plus seulement si elles ne tiennent pas) : actuelle, utilisable, pas obtenue, ou obtenue mais réservée.
     */
    private void buildTypes(Player player, String actuelle) {
        Messages m = plugin.messages();
        Settings s = plugin.settings();
        List<Settings.Apparence> cartes = new ArrayList<>();
        for (Settings.Apparence type : s.types.values()) {
            if (s.proposee(type)) {
                cartes.add(type);
            }
        }
        int lignes = s.typesFrame.rows();
        int pages = DispositionCartes.pages(cartes.size(), lignes);
        page = Math.min(page, pages - 1);
        List<Integer> cases = DispositionCartes.cases(cartes.size(), lignes, page);
        int premiere = page * DispositionCartes.parPage(lignes);
        UnaryOperator<String> lecteur = Reactions.lecteur(player);
        for (int i = 0; i < cases.size(); i++) {
            Settings.Apparence type = cartes.get(premiere + i);
            String etat = type.id().equals(actuelle) ? "actuel" : switch (s.etat(player, type)) {
                case DISPONIBLE -> "disponible";
                case NON_OBTENUE -> "non-obtenue";
                case RESERVE -> "reserve";
            };
            String typeName = m.typeName(type.id());
            ItemStack stack = item(Icons.make(type.icone()), m.text("menu.types.type.nom", "type", typeName,
                    "jake", m.jakeName(type.id())), carte(type, etat, lecteur));
            if (etat.equals("actuel")) {
                ItemMeta meta = stack.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                stack.setItemMeta(meta);
            }
            put(cases.get(i), stack);
            typeSlots.put(cases.get(i), type);
        }
        put(s.typesBackSlot, item(s.typesBackMaterial, m.text("menu.types.retour.nom"), List.of()));
        precedente = page > 0;
        suivante = page < pages - 1;
        String numero = String.valueOf(page + 1);
        String total = String.valueOf(pages);
        if (precedente) {
            put(s.typesPreviousSlot, item(s.typesPageMaterial, m.text("menu.types.page-precedente.nom", "page", numero,
                    "pages", total), m.lines("menu.types.page-precedente.description", "page", numero, "pages", total)));
        }
        if (suivante) {
            put(s.typesNextSlot, item(s.typesPageMaterial, m.text("menu.types.page-suivante.nom", "page", numero,
                    "pages", total), m.lines("menu.types.page-suivante.description", "page", numero, "pages", total)));
        }
    }

    /**
     * Lignes d'une carte (menu.types.type.<état>) : {description} = types.<ID>.description, {obtention} =
     * types.<ID>.obtention-texte, {progression} = où en est le joueur (types.<ID>.obtention-valeur sur le nombre à
     * atteindre de la condition). Une ligne dont le texte manque disparaît, et la ligne vide qui la suit avec elle.
     */
    private List<String> carte(Settings.Apparence type, String etat, UnaryOperator<String> lecteur) {
        Messages m = plugin.messages();
        String typeName = m.typeName(type.id());
        String jakeName = m.jakeName(type.id());
        OptionalDouble but = Obtention.objectif(type.obtention(), type.obtentionValeur());
        String objectif = but.isPresent() ? Obtention.nombre(but.getAsDouble()) : "";
        String obtention = m.typeText(type.id(), "obtention-texte", "objectif", objectif);
        String progression = "";
        if (!type.obtentionValeur().isEmpty() && !objectif.isEmpty() && etat.equals("non-obtenue")) {
            String lu = lecteur.apply(type.obtentionValeur()).trim();
            OptionalDouble n = Num.parse(lu);
            String valeur = n.isPresent() ? Obtention.nombre(Math.min(n.getAsDouble(), but.getAsDouble())) : lu;
            String modele = m.typeText(type.id(), "progression");
            progression = Messages.replace(modele.isEmpty() ? m.text("menu.types.type.progression") : modele,
                    "valeur", valeur, "objectif", objectif);
        }
        List<String> description = m.typeLines(type.id(), "description", "type", typeName, "jake", jakeName);
        List<String> out = new ArrayList<>();
        boolean sauter = false;
        for (String line : m.lines("menu.types.type." + etat, "type", typeName, "jake", jakeName)) {
            if (sauter && line.isEmpty()) {
                sauter = false;
                continue;
            }
            sauter = false;
            if (line.equals("{description}")) {
                out.addAll(description);
                sauter = description.isEmpty();
            } else if (line.contains("{obtention}") && obtention.isEmpty()
                    || line.contains("{progression}") && progression.isEmpty()) {
                sauter = true;
            } else {
                out.add(Messages.replace(line, "obtention", obtention, "progression", progression));
            }
        }
        return out;
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
            } else if (slot == s.discreetSlot) {
                plugin.commands().toggleDiscreet(player);
                open(plugin, player, Kind.MAIN, jake, back);
            } else if (slot == s.backSlot && !back.isEmpty()) {
                goBack(player);
            }
            return;
        }
        if (slot == s.typesBackSlot) {
            open(plugin, player, Kind.MAIN, jake, back);
            return;
        }
        if (precedente && slot == s.typesPreviousSlot || suivante && slot == s.typesNextSlot) {
            plugin.sound(player, s.soundMenu);
            open(plugin, player, Kind.TYPES, jake, back, slot == s.typesNextSlot ? page + 1 : page - 1);
            return;
        }
        Settings.Apparence type = typeSlots.get(slot);
        if (type != null && plugin.commands().changeType(player, jake, type)) {
            open(plugin, player, Kind.TYPES, jake, back, page);
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
