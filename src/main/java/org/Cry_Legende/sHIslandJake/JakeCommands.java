package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * /jake (Jake et ses réglages) et /shislandjake (administration). Les deux passent par le fil
 * principal, où vivent les Jake.
 */
final class JakeCommands {

    private static final Set<String> MOVE = Set.of("deplacer", "déplacer", "location", "move", "change");
    private static final Set<String> VISIBILITY = Set.of("visibilite", "visibilité", "visibility");
    private static final Set<String> TYPE = Set.of("type", "mob", "apparence");
    private static final Set<String> MENU = Set.of("menu", "reglages", "réglages", "settings");
    private static final Set<String> DISCREET = Set.of("discret", "discreet");
    private static final List<String> ADMIN = List.of("reload", "info", "delete", "import", "stats", "react", "say");
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

    void playerCommand(CommandSender sender, String[] args) {
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
        // toute forme de /jake (et /is jake) ramène d'abord le Jake de l'île s'il manque
        ramener(player);
        if (sub.isEmpty() && !plugin.settings().noArgCommand.isEmpty()) {
            player.performCommand(plugin.settings().noArgCommand);
        } else if (sub.isEmpty() || MENU.contains(sub)) {
            openMenu(player, args.length > 1 ? args[1] : "");
        } else if (MOVE.contains(sub)) {
            if (allowed(player, "shislandjake.deplacer")) {
                withJake(player, jake -> startMove(player, jake));
            }
        } else if (VISIBILITY.contains(sub)) {
            if (allowed(player, "shislandjake.visibilite")) {
                withJake(player, jake -> toggleVisibility(player, jake));
            }
        } else if (DISCREET.contains(sub)) {
            if (allowed(player, "shislandjake.use")) {
                toggleDiscreet(player);
            }
        } else if (TYPE.contains(sub)) {
            if (!allowed(player, "shislandjake.use")) {
                return;
            }
            if (args.length < 2) {
                withJake(player, jake -> menu(player, JakeMenu.Kind.TYPES, jake));
                return;
            }
            Settings.Apparence type = typeByName(args[1]);
            if (type == null) {
                plugin.messages().send(player, "type-inconnu", "type", args[1]);
                return;
            }
            withJake(player, jake -> changeType(player, jake, type));
        } else if (!plugin.settings().unknownCommand.isEmpty()) {
            player.performCommand(Messages.replace(plugin.settings().unknownCommand, "arguments", String.join(" ", args)));
        } else {
            plugin.messages().send(player, "utilisation", "commandes", playerUsage(player));
        }
    }

    /** Accroupi + clic : réglages du Jake de son île. */
    void openMenu(Player player) {
        openMenu(player, "");
    }

    /** @param origin menu d'où vient le joueur (menu.principal.retours) ; vide = retour-commande */
    void openMenu(Player player, String origin) {
        if (allowed(player, "shislandjake.use")) {
            String back = plugin.settings().backByOrigin.getOrDefault(origin.toLowerCase(Locale.ROOT),
                    plugin.settings().backCommand);
            withJake(player, jake -> {
                plugin.sound(player, plugin.settings().soundMenu);
                JakeMenu.open(plugin, player, JakeMenu.Kind.MAIN, jake, back);
            });
        }
    }

    private void menu(Player player, JakeMenu.Kind kind, Jake jake) {
        plugin.sound(player, plugin.settings().soundMenu);
        JakeMenu.open(plugin, player, kind, jake, plugin.settings().backCommand);
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

    /** Jake discret pour ce joueur (réglage à lui seul, sur toutes les îles). */
    void toggleDiscreet(Player player) {
        boolean discret = plugin.discret().basculer(player);
        plugin.sound(player, plugin.settings().soundSuccess);
        plugin.messages().send(player, discret ? "discret" : "bavard", "nom", jakeNameOf(player));
    }

    private String jakeNameOf(Player player) {
        Island island = plugin.islands().of(player);
        Jake jake = island == null ? null : plugin.registry().get(island.getUniqueId());
        return plugin.messages().jakeName(jake == null ? plugin.settings().defaultType : jake.data().type());
    }

    /** Vrai si l'apparence a changé. Les droits sont vérifiés ici, au moment du choix. */
    boolean changeType(Player player, Jake jake, Settings.Apparence type) {
        Settings s = plugin.settings();
        String typeName = plugin.messages().typeName(type.id());
        if (s.remplacee(type.id())) {
            plugin.sound(player, s.soundRefusal);
            plugin.messages().send(player, "type-remplace", "type", typeName);
            return false;
        }
        if (!s.proposee(type)) {
            // l'apparence de secours n'est pas proposée
            plugin.sound(player, s.soundRefusal);
            plugin.messages().send(player, "type-inconnu", "type", typeName);
            return false;
        }
        if (s.apparence(jake.data().type()).id().equals(type.id())) {
            plugin.messages().send(player, "type-deja", "type", typeName);
            return false;
        }
        switch (s.etat(player, type)) {
            case NON_OBTENUE -> {
                java.util.OptionalDouble but = Obtention.objectif(type.obtention(), type.obtentionValeur());
                String obtention = plugin.messages().typeText(type.id(), "obtention-texte", "objectif",
                        but.isPresent() ? Obtention.nombre(but.getAsDouble()) : "");
                plugin.sound(player, s.soundRefusal);
                plugin.messages().send(player, obtention.isEmpty() ? "type-non-obtenue" : "type-non-obtenue-comment",
                        "type", typeName, "obtention", obtention);
                return false;
            }
            case RESERVE -> {
                plugin.sound(player, plugin.settings().soundRefusal);
                plugin.messages().send(player, "type-reserve", "type", typeName);
                return false;
            }
            default -> {
            }
        }
        plugin.manager().setType(jake, type.id());
        plugin.sound(player, plugin.settings().soundSuccess);
        plugin.messages().send(player, "type-change", "type", typeName);
        return true;
    }

    /** Le Jake de l'île du joueur, reposé à sa place de départ (avec un message) s'il manque. */
    private void ramener(Player player) {
        Island island = plugin.islands().of(player);
        if (island == null || plugin.registry().get(island.getUniqueId()) != null || !plugin.islands().canUse(player, island, true)) {
            return;
        }
        Jake jake = plugin.manager().create(island);
        if (jake != null) {
            plugin.messages().send(player, "jake-revenu", "nom", plugin.messages().jakeName(jake.data().type()));
        }
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

    private Settings.Apparence typeByName(String name) {
        String wanted = Remplacements.id(name);
        for (Settings.Apparence type : plugin.settings().types.values()) {
            if (type.id().equals(wanted) || plugin.messages().typeName(type.id()).equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }

    private String playerUsage(CommandSender sender) {
        List<String> parts = new ArrayList<>();
        if (!plugin.settings().noArgCommand.isEmpty() && sender.hasPermission("shislandjake.use")) {
            parts.add(plugin.messages().text("sous-commandes.reglages"));
        }
        if (sender.hasPermission("shislandjake.deplacer")) {
            parts.add(plugin.messages().text("sous-commandes.deplacer"));
        }
        if (sender.hasPermission("shislandjake.visibilite")) {
            parts.add(plugin.messages().text("sous-commandes.visibilite"));
        }
        if (sender.hasPermission("shislandjake.use")) {
            parts.add(plugin.messages().text("sous-commandes.type"));
            parts.add(plugin.messages().text("sous-commandes.discret"));
        }
        return "[" + String.join("|", parts) + "]";
    }

    List<String> playerTab(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (!plugin.settings().unknownCommand.isEmpty()) {
                options.addAll(plugin.settings().unknownSuggestions);
            }
            if (!plugin.settings().noArgCommand.isEmpty() && sender.hasPermission("shislandjake.use")) {
                options.add("reglages");
            }
            if (sender.hasPermission("shislandjake.deplacer")) {
                options.add("deplacer");
            }
            if (sender.hasPermission("shislandjake.visibilite")) {
                options.add("visibilite");
            }
            if (sender.hasPermission("shislandjake.use")) {
                options.add("type");
                options.add("discret");
            }
        } else if (args.length == 2 && TYPE.contains(args[0].toLowerCase(Locale.ROOT))
                && sender.hasPermission("shislandjake.use")) {
            for (Settings.Apparence type : plugin.settings().types.values()) {
                if (plugin.settings().proposee(type) && plugin.settings().etat(sender, type) == Settings.Etat.DISPONIBLE) {
                    options.add(type.id().toLowerCase(Locale.ROOT));
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
            case "stats" -> stats(sender);
            case "react" -> react(sender, args);
            case "say" -> say(sender, args);
            default -> {
            }
        }
    }

    /**
     * /shislandjake react <joueur> <réaction> [texte…] : lancée par d'autres plugins à chaque usage, elle ne dit rien,
     * qu'elle joue ou soit abandonnée (joueur absent ou loin de Jake). Seule une réaction inconnue est signalée.
     */
    private void react(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "utilisation-admin", "commandes", plugin.messages().text("sous-commandes.react"));
            return;
        }
        if (plugin.settings().reactions.get(args[2]) == null) {
            plugin.messages().send(sender, "reaction-inconnue", "reaction", args[2]);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            return;
        }
        String texte = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        plugin.reactions().commande(target, args[2], texte);
    }

    /** /shislandjake say <joueur> <texte…> : une bulle seule, sans un mot en retour. */
    private void say(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "utilisation-admin", "commandes", plugin.messages().text("sous-commandes.say"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target != null) {
            plugin.reactions().dire(target, String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
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
        plugin.messages().send(sender, "info-joueur", "joueur", args[1], "type", d.type(), "monde", d.world(),
                "x", format(d.x()), "y", format(d.y()), "z", format(d.z()),
                "visible", plugin.messages().text(d.visible() ? "placeholders.visible" : "placeholders.cache"),
                "corps", corps(jake));
    }

    /** Ce qui dessine ce Jake en ce moment (messages.yml, corps). */
    private String corps(Jake jake) {
        if (jake.corps() == null) {
            return plugin.messages().text("corps.aucun");
        }
        Settings.Apparence a = jake.dessinee();
        return plugin.messages().text(a != null && a.estModele() ? "corps.modele" : "corps.mob",
                "apparence", a == null ? "?" : a.id());
    }

    /** Coût des tâches de Jake sur la dernière minute. */
    private void stats(CommandSender sender) {
        Mesures mesures = plugin.mesures();
        double fenetre = mesures.fenetreSecondes();
        int joueurs = Bukkit.getOnlinePlayers().size();
        plugin.messages().send(sender, "stats-entete", "fenetre", String.valueOf(Math.round(fenetre)),
                "joueurs", String.valueOf(joueurs));
        for (Mesures.Tache tache : Mesures.Tache.values()) {
            Mesures.Bilan bilan = mesures.bilan(tache);
            double parTick = bilan.totalNanos() / 1000.0 / (fenetre * 20);
            plugin.messages().send(sender, "stats-ligne", "tache", plugin.messages().text("stats." + tache.cle),
                    "passages", String.valueOf(bilan.passages()), "moyenne", micros(bilan.moyenneMicros()),
                    "max", micros(bilan.maxMicros()), "par-tick", micros(parTick),
                    "par-joueur", joueurs == 0 ? "-" : micros(parTick / joueurs));
        }
        plugin.messages().send(sender, "stats-pied", "dessines", String.valueOf(plugin.registry().drawn().size()),
                "suivis", String.valueOf(plugin.tracker().watchedPlayers()));
    }

    private static String micros(double value) {
        return String.format(Locale.ROOT, value < 10 ? "%.2f" : "%.1f", value);
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
        String sub = args.length > 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        boolean allowed = !sub.isEmpty() && sender.hasPermission("shislandjake.admin." + sub);
        if (args.length == 1) {
            for (String s : ADMIN) {
                if (sender.hasPermission("shislandjake.admin." + s)) {
                    options.add(s);
                }
            }
        } else if (args.length == 2 && allowed && Set.of("info", "delete", "react", "say").contains(sub)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        } else if (args.length == 3 && allowed && sub.equals("react")) {
            options.addAll(plugin.settings().reactions.toutes().keySet());
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
