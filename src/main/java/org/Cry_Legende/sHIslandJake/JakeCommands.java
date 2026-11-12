package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * /jake (paramètres du Jake de son île) et /shislandjake (administration). Les deux passent par le fil
 * principal, où vivent les Jake.
 */
final class JakeCommands {

    private static final Set<String> MOVE = Set.of("deplacer", "déplacer", "location", "move", "change");
    private static final Set<String> VISIBILITY = Set.of("visibilite", "visibilité", "visibility");
    private static final Set<String> TYPE = Set.of("type", "mob");
    private static final Set<String> MENU = Set.of("menu");
    private static final List<String> ADMIN = List.of("reload", "info", "delete", "import");
    /** Anciennes sous-commandes d'administration de /islandnpc. */
    private static final Set<String> OLD_ADMIN = Set.of("reload", "about", "delete", "import", "info");

    private final SHIslandJake plugin;

    JakeCommands(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    TabExecutor player() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                plugin.sync(() -> playerCommand(sender, args));
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return playerTab(sender, args);
            }
        };
    }

    TabExecutor admin() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                plugin.sync(() -> adminCommand(sender, args));
                return true;
            }

            @Override
            public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                return adminTab(sender, args);
            }
        };
    }

    //JOUEUR----------

    private void playerCommand(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (OLD_ADMIN.contains(sub) && sender.hasPermission("shislandjake.admin." + adminName(sub))) {
            String[] rest = args.clone();
            rest[0] = adminName(sub);
            adminCommand(sender, rest);
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "joueurs-seulement");
            return;
        }
        if (sub.isEmpty() || MENU.contains(sub)) {
            openMenu(player);
        } else if (MOVE.contains(sub)) {
            if (allowed(player, "shislandjake.deplacer")) {
                withJake(player, jake -> startMove(player, jake));
            }
        } else if (VISIBILITY.contains(sub)) {
            if (allowed(player, "shislandjake.visibilite")) {
                withJake(player, jake -> toggleVisibility(player, jake));
            }
        } else if (TYPE.contains(sub)) {
            if (!allowed(player, "shislandjake.use")) {
                return;
            }
            if (args.length < 2) {
                withJake(player, jake -> menu(player, JakeMenu.Kind.TYPES, jake));
                return;
            }
            EntityType type = typeByName(args[1]);
            if (type == null) {
                plugin.messages().send(player, "type-inconnu", "type", args[1]);
                return;
            }
            withJake(player, jake -> changeType(player, jake, type));
        } else {
            plugin.messages().send(player, "utilisation", "commandes", playerUsage(player));
        }
    }

    /** /jake, /is jake : paramètres du Jake de son île. */
    void openMenu(Player player) {
        if (allowed(player, "shislandjake.use")) {
            withJake(player, jake -> menu(player, JakeMenu.Kind.MAIN, jake));
        }
    }

    private void menu(Player player, JakeMenu.Kind kind, Jake jake) {
        plugin.sound(player, plugin.settings().soundMenu);
        JakeMenu.open(plugin, player, kind, jake);
    }

    void startMove(Player player, Jake jake) {
        if (allowed(player, "shislandjake.deplacer")) {
            plugin.moves().start(player, jake);
        }
    }

    void toggleVisibility(Player player, Jake jake) {
        if (!allowed(player, "shislandjake.visibilite")) {
            return;
        }
        boolean visible = plugin.manager().toggleVisible(jake);
        plugin.messages().send(player, visible ? "visible" : "cache", "nom", plugin.messages().jakeName(jake.data().type()));
    }

    /** Vrai si le type a changé. */
    boolean changeType(Player player, Jake jake, EntityType type) {
        Settings.TypeSettings settings = plugin.settings().types.get(type);
        String typeName = plugin.messages().typeName(type);
        if (settings == null) {
            plugin.messages().send(player, "type-inconnu", "type", type.name());
            return false;
        }
        if (jake.data().type() == type) {
            plugin.messages().send(player, "type-deja", "type", typeName);
            return false;
        }
        if (!settings.allowed(player)) {
            plugin.sound(player, plugin.settings().soundRefusal);
            plugin.messages().send(player, "type-reserve", "type", typeName);
            return false;
        }
        plugin.manager().setType(jake, type);
        plugin.sound(player, plugin.settings().soundSuccess);
        plugin.messages().send(player, "type-change", "type", typeName);
        return true;
    }

    /** Lance l'action avec le Jake de l'île du joueur, reposé à sa place de départ s'il avait disparu. */
    private void withJake(Player player, Consumer<Jake> action) {
        Island island = plugin.islands().of(player);
        if (island == null) {
            plugin.messages().send(player, "pas-d-ile");
            return;
        }
        boolean[] created = new boolean[1];
        Jake jake = plugin.manager().getOrCreate(island, created);
        if (jake == null) {
            plugin.messages().send(player, "pas-d-ile");
            return;
        }
        if (created[0]) {
            plugin.messages().send(player, "jake-revenu", "nom", plugin.messages().jakeName(jake.data().type()));
        }
        action.accept(jake);
    }

    private boolean allowed(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(player, "sans-permission");
        return false;
    }

    private EntityType typeByName(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (EntityType type : plugin.settings().types.keySet()) {
            if (type.name().equalsIgnoreCase(wanted) || plugin.messages().typeName(type).equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }

    private String playerUsage(CommandSender sender) {
        List<String> parts = new ArrayList<>();
        if (sender.hasPermission("shislandjake.deplacer")) {
            parts.add(plugin.messages().text("sous-commandes.deplacer"));
        }
        if (sender.hasPermission("shislandjake.visibilite")) {
            parts.add(plugin.messages().text("sous-commandes.visibilite"));
        }
        if (sender.hasPermission("shislandjake.use")) {
            parts.add(plugin.messages().text("sous-commandes.type"));
        }
        return "[" + String.join("|", parts) + "]";
    }

    private List<String> playerTab(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("shislandjake.deplacer")) {
                options.add("deplacer");
            }
            if (sender.hasPermission("shislandjake.visibilite")) {
                options.add("visibilite");
            }
            if (sender.hasPermission("shislandjake.use")) {
                options.add("type");
            }
        } else if (args.length == 2 && TYPE.contains(args[0].toLowerCase(Locale.ROOT))
                && sender.hasPermission("shislandjake.use")) {
            for (Settings.TypeSettings type : plugin.settings().types.values()) {
                if (type.allowed(sender)) {
                    options.add(type.type().name().toLowerCase(Locale.ROOT));
                }
            }
        }
        return filter(options, args[args.length - 1]);
    }

    //ADMINISTRATION----------

    private static String adminName(String sub) {
        return sub.equals("about") ? "info" : sub;
    }

    private void adminCommand(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : adminName(args[0].toLowerCase(Locale.ROOT));
        if (sub.equals("supprimer")) {
            sub = "delete";
        }
        if (!ADMIN.contains(sub) || !sender.hasPermission("shislandjake.admin." + sub)) {
            String usage = adminUsage(sender);
            if (usage.isEmpty()) {
                plugin.messages().send(sender, "sans-permission");
            } else {
                plugin.messages().send(sender, "utilisation-admin", "commandes", usage);
            }
            return;
        }
        switch (sub) {
            case "reload" -> plugin.reload(sender);
            case "info" -> info(sender, args);
            case "delete" -> delete(sender, args);
            case "import" -> plugin.importIslandNpc(sender);
            default -> {
            }
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            int hidden = 0;
            for (Jake jake : plugin.registry().all()) {
                if (!jake.data().visible()) {
                    hidden++;
                }
            }
            plugin.messages().send(sender, "info", "jakes", String.valueOf(plugin.registry().size()),
                    "caches", String.valueOf(hidden), "dessines", String.valueOf(plugin.registry().drawn().size()));
            return;
        }
        Island island = islandOfName(sender, args[1]);
        if (island == null) {
            return;
        }
        Jake jake = plugin.registry().get(island.getUniqueId());
        if (jake == null) {
            plugin.messages().send(sender, "info-sans-jake", "joueur", args[1]);
            return;
        }
        JakeData d = jake.data();
        plugin.messages().send(sender, "info-joueur", "joueur", args[1], "type", d.type().name(), "monde", d.world(),
                "x", format(d.x()), "y", format(d.y()), "z", format(d.z()),
                "visible", plugin.messages().text(d.visible() ? "placeholders.visible" : "placeholders.cache"));
    }

    private void delete(CommandSender sender, String[] args) {
        Island island;
        if (args.length >= 2) {
            island = islandOfName(sender, args[1]);
            if (island == null) {
                return;
            }
        } else if (sender instanceof Player player) {
            island = plugin.islands().at(player.getLocation());
            if (island == null) {
                plugin.messages().send(sender, "aucune-ile-ici");
                return;
            }
        } else {
            plugin.messages().send(sender, "utilisation-admin", "commandes", "delete <joueur>");
            return;
        }
        plugin.manager().delete(island.getUniqueId());
        plugin.messages().send(sender, "supprime");
    }

    /** Île d'un joueur par son pseudo : en ligne d'abord, puis la fiche SSB2 (jamais de recherche Mojang). */
    private Island islandOfName(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        Island island = null;
        if (online != null) {
            island = plugin.islands().of(online);
        } else {
            SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(name);
            if (superiorPlayer != null) {
                island = superiorPlayer.getIsland();
            }
        }
        if (island == null) {
            plugin.messages().send(sender, "joueur-sans-ile", "joueur", name);
        }
        return island;
    }

    private String adminUsage(CommandSender sender) {
        List<String> parts = new ArrayList<>();
        for (String sub : ADMIN) {
            if (sender.hasPermission("shislandjake.admin." + sub)) {
                parts.add(sub);
            }
        }
        return parts.isEmpty() ? "" : "<" + String.join("|", parts) + ">";
    }

    private List<String> adminTab(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : ADMIN) {
                if (sender.hasPermission("shislandjake.admin." + sub)) {
                    options.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("delete"))
                && sender.hasPermission("shislandjake.admin." + args[0].toLowerCase(Locale.ROOT))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        }
        return filter(options, args[args.length - 1]);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
