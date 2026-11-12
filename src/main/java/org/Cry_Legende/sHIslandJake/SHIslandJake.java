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

    private volatile Settings settings;
    private volatile Messages messages;
    private Islands islands;
    private JakeRegistry registry;
    private JakeStore store;
    private JakeDisplay display;
    private JakeManager manager;
    private JakeTracker tracker;
    private MoveSessions moves;
    private JakeCommands commands;
    private IslandSubCommand islandCommand;
    private BukkitTask saveTask;
    private Thread mainThread;
    private long loadMillis;

    @Override
    public void onEnable() {
        mainThread = Thread.currentThread();
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        try {
            settings = new Settings(readYaml("config.yml"), getLogger());
            messages = new Messages(readMessages());
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.SEVERE, "Configuration illisible, SHIslandJake s'arrête : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        islands = new Islands();
        registry = new JakeRegistry();
        store = new JakeStore(getDataFolder(), getLogger());
        long start = System.nanoTime();
        try {
            for (JakeData data : store.load().jakes()) {
                registry.add(new Jake(data));
            }
            loadMillis = (System.nanoTime() - start) / 1_000_000;
        } catch (IOException | InvalidConfigurationException e) {
            // Mieux vaut ne rien dessiner que réécrire le fichier sans les Jake qu'on n'a pas su lire
            getLogger().log(Level.SEVERE, "jakes.yml illisible, SHIslandJake s'arrête sans y toucher : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        display = new JakeDisplay(this, registry);
        manager = new JakeManager(this, registry, store, display, islands);
        tracker = new JakeTracker(this, registry, display);
        manager.setTracker(tracker);
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
            new JakePlaceholders(this).register();
        }

        tracker.start();
        saveTask = getServer().getScheduler().runTaskTimer(this, () -> store.flush(registry), SAVE_TICKS, SAVE_TICKS);
        // Une fois le serveur lancé, SuperiorSkyblock2 connaît toutes les îles
        getServer().getScheduler().runTask(this, () -> {
            int removed = manager.pruneOrphans();
            if (removed > 0) {
                getLogger().info(removed + " Jake d'îles supprimées retirés.");
            }
        });
        getLogger().info(registry.size() + " Jake chargés (jakes.yml lu en " + loadMillis + " ms).");
    }

    @Override
    public void onDisable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (tracker != null) {
            tracker.stop();
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

    /** Relit config.yml et messages.yml ; un fichier invalide laisse l'ancienne configuration en place. */
    void reload(CommandSender sender) {
        Settings newSettings;
        try {
            newSettings = new Settings(readYaml("config.yml"), getLogger());
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().warning("config.yml invalide, rechargement annulé : " + e.getMessage());
            messages.send(sender, "recharge-erreur", "fichier", "config.yml");
            return;
        }
        Messages newMessages;
        try {
            newMessages = new Messages(readMessages());
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().warning("messages.yml invalide, rechargement annulé : " + e.getMessage());
            messages.send(sender, "recharge-erreur", "fichier", "messages.yml");
            return;
        }
        if (!newSettings.islandSubCommands.equals(settings.islandSubCommands)) {
            getLogger().info("sous-commandes-ile ne change qu'au prochain démarrage.");
        }
        tracker.stop();
        display.releaseAll();
        settings = newSettings;
        messages = newMessages;
        tracker.start();
        messages.send(sender, "recharge");
    }

    private YamlConfiguration readYaml(String name) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(getDataFolder(), name));
        return yaml;
    }

    private YamlConfiguration readMessages() throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = readYaml("messages.yml");
        try (InputStream in = getResource("messages.yml")) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
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
                    settings.pitch, row.type(), row.visible()));
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

    JakeRegistry registry() {
        return registry;
    }

    JakeManager manager() {
        return manager;
    }

    JakeTracker tracker() {
        return tracker;
    }

    MoveSessions moves() {
        return moves;
    }

    JakeCommands commands() {
        return commands;
    }
}
