package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;

public final class SHIslandJake extends JavaPlugin {

    private static final long SAVE_TICKS = 100L;
    private static final long CLEAN_TICKS = 6000L;
    private static final long ENGINE_TICKS = 600L;

    private volatile Settings settings;
    private volatile Messages messages;
    private Islands islands;
    private AlertCondition alerts;
    private JakeRegistry registry;
    private JakeStore store;
    private Textes textes;
    private JakeDisplay display;
    private JakeManager manager;
    private JakeTracker tracker;
    private Reactions reactions;
    private Discret discret;
    private MoveSessions moves;
    private JakeCommands commands;
    private IslandSubCommand islandCommand;
    private BukkitTask saveTask;
    private BukkitTask cleanTask;
    private BukkitTask engineTask;
    private JakePlaceholders placeholders;
    private Thread mainThread;
    private long loadMillis;
    private final Mesures mesures = new Mesures();

    @Override
    public void onEnable() {
        mainThread = Thread.currentThread();
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        try {
            settings = new Settings(FichiersConfig.lire(getDataFolder(), "config.yml"), getLogger());
            messages = new Messages(readMessages());
        } catch (FichiersConfig.FichierInvalide e) {
            getLogger().log(Level.SEVERE, "Configuration illisible, SHIslandJake s'arrête : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        islands = new Islands();
        alerts = new AlertCondition(this);
        registry = new JakeRegistry();
        store = new JakeStore(getDataFolder(), getLogger());
        long start = System.nanoTime();
        int replaced = 0;
        try {
            int[] changes = new int[1];
            for (JakeData data : settings.remplacements.appliquer(store.load().jakes(), changes)) {
                registry.add(new Jake(data));
            }
            replaced = changes[0];
            loadMillis = (System.nanoTime() - start) / 1_000_000;
        } catch (IOException | InvalidConfigurationException e) {
            // Mieux vaut ne rien dessiner que réécrire le fichier sans les Jake qu'on n'a pas su lire
            getLogger().log(Level.SEVERE, "jakes.yml illisible, SHIslandJake s'arrête sans y toucher : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (replaced > 0) {
            store.markDirty();
            getLogger().info("apparences.remplacements : " + replaced + " Jake ont changé d'apparence.");
        }
        discret = new Discret(this);
        textes = new Textes(this);
        display = new JakeDisplay(this, registry, textes);
        manager = new JakeManager(this, registry, store, display, islands);
        tracker = new JakeTracker(this, registry, display, textes);
        display.setTracker(tracker);
        manager.setTracker(tracker);
        reactions = new Reactions(this);
        moves = new MoveSessions(this);
        commands = new JakeCommands(this);

        if (store.importMarker() == null) {
            IslandNpcImport source = new IslandNpcImport(getDataFolder().getParentFile(), getDataFolder(), getLogger());
            if (source.available()) {
                runImport(source, null);
            } else {
                store.setImportMarker("aucune base IslandNPC au premier démarrage");
                store.saveNow(registry);
            }
        }

        getServer().getPluginManager().registerEvents(new JakeListener(this), this);
        PluginCommand jake = getCommand("jake");
        jake.setExecutor(commands.player());
        jake.setTabCompleter(commands.player());
        PluginCommand admin = getCommand("shislandjake");
        admin.setExecutor(commands.admin());
        admin.setTabCompleter(commands.admin());
        if (!settings.islandSubCommands.isEmpty()) {
            islandCommand = new IslandSubCommand(this, settings.islandSubCommands);
            SuperiorSkyblockAPI.registerCommand(islandCommand);
        }
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholders = new JakePlaceholders(this);
            placeholders.register();
        }
        // rechargement à chaud : les joueurs déjà là gardent leur réglage « discret »
        for (Player player : Bukkit.getOnlinePlayers()) {
            discret.charger(player);
        }

        tracker.start();
        saveTask = getServer().getScheduler().runTaskTimer(this, () -> store.flush(registry), SAVE_TICKS, SAVE_TICKS);
        cleanTask = getServer().getScheduler().runTaskTimer(this, () -> reactions.nettoyer(), CLEAN_TICKS, CLEAN_TICKS);
        // un « /bdc reload » peut rallumer ou éteindre le rendu du moteur sans activer ni arrêter le plugin
        engineTask = getServer().getScheduler().runTaskTimer(this, () -> display.moteurChange(), ENGINE_TICKS, ENGINE_TICKS);
        // Une fois le serveur lancé, SuperiorSkyblock2 connaît toutes les îles
        getServer().getScheduler().runTask(this, () -> {
            int removed = manager.pruneOrphans();
            if (removed > 0) {
                getLogger().info(removed + " Jake d'îles supprimées retirés.");
            }
        });
        getLogger().info(registry.size() + " Jake chargés (jakes.yml lu en " + loadMillis + " ms), "
                + settings.reactions.toutes().size() + " réactions.");
    }

    @Override
    public void onDisable() {
        // un menu resté ouvert ne serait plus protégé des clics
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof JakeMenu) {
                player.closeInventory();
            }
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (cleanTask != null) {
            cleanTask.cancel();
        }
        if (engineTask != null) {
            engineTask.cancel();
        }
        if (placeholders != null) {
            try {
                placeholders.unregister();
            } catch (RuntimeException | LinkageError ignored) {
                // PlaceholderAPI déjà arrêté
            }
        }
        // tout ce qui a été envoyé en paquets est détruit chez les joueurs (textes, bulles, zones)
        if (tracker != null) {
            tracker.stop();
        }
        if (textes != null) {
            textes.arreter();
        }
        if (moves != null) {
            moves.clear();
        }
        if (islandCommand != null) {
            try {
                SuperiorSkyblockAPI.getCommands().unregisterCommand(islandCommand);
            } catch (RuntimeException ignored) {
                // SSB2 déjà arrêté
            }
        }
        if (display != null) {
            display.releaseAll();
        }
        if (store != null && registry != null) {
            store.close(registry);
        }
    }

    //RECHARGEMENT----------

    /** Relit config.yml (réactions comprises) et messages.yml ; un fichier invalide laisse l'ancienne configuration. */
    void reload(CommandSender sender) {
        Settings newSettings;
        Messages newMessages;
        try {
            newSettings = new Settings(FichiersConfig.lire(getDataFolder(), "config.yml"), getLogger());
            newMessages = new Messages(readMessages());
        } catch (FichiersConfig.FichierInvalide e) {
            getLogger().warning(e.fichier() + " invalide, rechargement annulé : " + e.getCause().getMessage());
            messages.send(sender, "recharge-erreur", "fichier", e.fichier());
            return;
        }
        if (!newSettings.islandSubCommands.equals(settings.islandSubCommands)) {
            getLogger().info("sous-commandes-ile ne change qu'au prochain démarrage.");
        }
        tracker.stop();
        textes.arreter();
        display.releaseAll();
        settings = newSettings;
        messages = newMessages;
        reactions.recharge();
        int replaced = manager.applyReplacements(newSettings.remplacements);
        if (replaced > 0) {
            getLogger().info("apparences.remplacements : " + replaced + " Jake ont changé d'apparence.");
        }
        tracker.start();
        messages.send(sender, "recharge", "reactions", String.valueOf(newSettings.reactions.toutes().size()));
    }

    private YamlConfiguration readMessages() throws FichiersConfig.FichierInvalide {
        YamlConfiguration yaml = FichiersConfig.lire(getDataFolder(), "messages.yml");
        try (InputStream in = getResource("messages.yml")) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new FichiersConfig.FichierInvalide("messages.yml", e);
        }
        return yaml;
    }

    //IMPORT----------

    /** /shislandjake import : reprend les PNJ d'IslandNPC des îles qui n'ont pas encore de Jake. */
    void importIslandNpc(CommandSender sender) {
        IslandNpcImport source = new IslandNpcImport(getDataFolder().getParentFile(), getDataFolder(), getLogger());
        if (!source.available()) {
            messages.send(sender, "import-absent");
            return;
        }
        runImport(source, sender);
    }

    private void runImport(IslandNpcImport source, CommandSender sender) {
        List<IslandNpcImport.Row> rows;
        try {
            rows = source.read(settings.defaultType);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Lecture de la base IslandNPC impossible", e);
            if (sender != null) {
                messages.send(sender, "import-erreur");
            }
            return;
        }
        int imported = 0;
        int present = 0;
        for (IslandNpcImport.Row row : rows) {
            if (registry.get(row.island()) != null) {
                present++;
                continue;
            }
            manager.add(new JakeData(row.island(), row.world(), row.x(), row.y(), row.z(), settings.yaw,
                    settings.pitch, settings.remplacements.appliquer(row.type()), row.visible()));
            imported++;
        }
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        store.setImportMarker(when + " : " + imported + " repris, " + present + " déjà présents");
        store.saveNow(registry);
        getLogger().info("Import IslandNPC : " + imported + " Jake repris, " + present + " îles avaient déjà le leur.");
        if (sender != null) {
            messages.send(sender, "import", "importes", String.valueOf(imported), "presents", String.valueOf(present));
        }
        if (tracker != null && imported > 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                tracker.refreshSoon(player);
            }
        }
    }

    //OUTILS----------

    /**
     * Exécute la tâche sur le fil principal, tout de suite si on y est déjà. Avec les mondes en parallèle
     * (DivineMC), les événements d'un joueur arrivent sur le fil de son monde et Bukkit.isPrimaryThread() y répond
     * vrai : on compare donc au fil relevé dans onEnable. Les tâches du fil principal ne tournent jamais en même
     * temps que les mondes.
     */
    void sync(Runnable task) {
        if (Thread.currentThread() == mainThread) {
            task.run();
        } else if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    void sound(Player player, String sound) {
        if (sound != null && !sound.isEmpty()) {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        }
    }

    Settings settings() {
        return settings;
    }

    Messages messages() {
        return messages;
    }

    Islands islands() {
        return islands;
    }

    AlertCondition alerts() {
        return alerts;
    }

    JakeRegistry registry() {
        return registry;
    }

    JakeManager manager() {
        return manager;
    }

    JakeDisplay display() {
        return display;
    }

    JakeTracker tracker() {
        return tracker;
    }

    Textes textes() {
        return textes;
    }

    Reactions reactions() {
        return reactions;
    }

    Discret discret() {
        return discret;
    }

    MoveSessions moves() {
        return moves;
    }

    JakeCommands commands() {
        return commands;
    }

    Mesures mesures() {
        return mesures;
    }
}
