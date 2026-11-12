package org.Cry_Legende.sHIslandJake;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * jakes.yml : un Jake par île. Le fichier est réécrit en entier, hors du fil principal, quelques secondes après
 * une modification ; on passe par un fichier temporaire renommé, un arrêt brutal laisse donc l'ancienne version.
 */
final class JakeStore {

    /** Contenu lu au démarrage. */
    record Loaded(List<JakeData> jakes, String importMarker) {
    }

    private final File file;
    private final Logger log;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SHIslandJake-ecriture");
        thread.setDaemon(true);
        return thread;
    });
    private volatile String importMarker;
    private boolean dirty;

    JakeStore(File folder, Logger log) {
        this.file = new File(folder, "jakes.yml");
        this.log = log;
    }

    String importMarker() {
        return importMarker;
    }

    void setImportMarker(String marker) {
        this.importMarker = marker;
        dirty = true;
    }

    /**
     * Le fichier est lu ligne par ligne tant qu'il a la forme que le plugin écrit (quelques millisecondes pour des
     * milliers d'îles) ; une forme différente, après une modification à la main, passe par le lecteur YAML.
     */
    Loaded load() throws IOException, InvalidConfigurationException {
        if (!file.exists()) {
            return new Loaded(new ArrayList<>(), null);
        }
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        Loaded loaded = readLines(lines);
        if (loaded == null) {
            loaded = readYaml(String.join("\n", lines));
        }
        importMarker = loaded.importMarker();
        return loaded;
    }

    private Loaded readLines(List<String> lines) {
        List<JakeData> jakes = new ArrayList<>();
        String marker = null;
        String island = null;
        Map<String, String> entry = new HashMap<>();
        int invalid = 0;
        boolean inIslands = false;
        for (String raw : lines) {
            if (raw.isBlank() || raw.startsWith("#")) {
                continue;
            }
            if (!inIslands) {
                if (raw.startsWith("import-islandnpc: ")) {
                    marker = unquote(raw.substring("import-islandnpc: ".length()));
                } else if (raw.equals("iles:")) {
                    inIslands = true;
                } else {
                    return null;
                }
            } else if (raw.startsWith("  ") && !raw.startsWith("   ") && raw.endsWith(":")) {
                if (island != null && !added(jakes, island, entry)) {
                    invalid++;
                }
                island = raw.substring(2, raw.length() - 1);
                entry.clear();
            } else if (raw.startsWith("    ") && island != null && raw.indexOf(": ") > 4) {
                int colon = raw.indexOf(": ");
                entry.put(raw.substring(4, colon), unquote(raw.substring(colon + 2)));
            } else {
                return null;
            }
        }
        if (island != null && !added(jakes, island, entry)) {
            invalid++;
        }
        warnInvalid(invalid);
        return new Loaded(jakes, marker);
    }

    /** Ajoute l'entrée ; faux si elle est illisible. */
    private boolean added(List<JakeData> jakes, String island, Map<String, String> entry) {
        JakeData data = parse(island, entry.get("monde"), entry.get("position"), entry.get("type"), entry.get("visible"));
        if (data != null) {
            jakes.add(data);
        }
        return data != null;
    }

    private Loaded readYaml(String content) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        List<JakeData> jakes = new ArrayList<>();
        ConfigurationSection islands = yaml.getConfigurationSection("iles");
        int invalid = 0;
        if (islands != null) {
            for (String key : islands.getKeys(false)) {
                ConfigurationSection e = islands.getConfigurationSection(key);
                JakeData data = e == null ? null : parse(key, e.getString("monde"), e.getString("position"),
                        e.getString("type"), e.getString("visible"));
                if (data == null) {
                    invalid++;
                } else {
                    jakes.add(data);
                }
            }
        }
        warnInvalid(invalid);
        return new Loaded(jakes, yaml.getString("import-islandnpc"));
    }

    private void warnInvalid(int invalid) {
        if (invalid > 0) {
            log.warning(invalid + " entrée(s) illisible(s) dans jakes.yml, ignorée(s).");
        }
    }

    private JakeData parse(String key, String world, String position, String typeName, String visible) {
        try {
            UUID island = UUID.fromString(key);
            String[] p = position == null ? new String[0] : position.trim().split("\\s+");
            EntityType type = Settings.entityType(typeName == null ? "VILLAGER" : typeName, log, "jakes.yml " + key);
            if (world == null || p.length != 5 || type == null) {
                return null;
            }
            return new JakeData(island, world, Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4]), type,
                    !"false".equalsIgnoreCase(visible == null ? "" : visible.trim()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Valeur YAML simple : 'texte' (avec '' pour une apostrophe) ou texte nu. */
    private static String unquote(String value) {
        value = value.trim();
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    void markDirty() {
        dirty = true;
    }

    /**
     * Fil principal : écrit plus tard si quelque chose a changé. Seule la liste des données (immuables) est copiée
     * ici, le texte du fichier est construit sur le fil d'écriture.
     */
    void flush(JakeRegistry registry) {
        if (!dirty) {
            return;
        }
        dirty = false;
        List<JakeData> snapshot = registry.snapshot();
        String marker = importMarker;
        writer.execute(() -> write(render(snapshot, marker)));
    }

    /** Arrêt du plugin : dernière écriture, attendue. */
    void close(JakeRegistry registry) {
        flush(registry);
        writer.shutdown();
        try {
            if (!writer.awaitTermination(20, TimeUnit.SECONDS)) {
                log.severe("jakes.yml n'a pas fini d'être écrit à l'arrêt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Écriture immédiate, attendue (après l'import). */
    void saveNow(JakeRegistry registry) {
        dirty = false;
        List<JakeData> snapshot = registry.snapshot();
        String marker = importMarker;
        try {
            writer.submit(() -> write(render(snapshot, marker))).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Écriture de jakes.yml impossible", e);
        }
    }

    private static String render(List<JakeData> jakes, String marker) {
        StringBuilder out = new StringBuilder(jakes.size() * 160 + 256);
        out.append("# SHIslandJake : le Jake de chaque île (identifiant SuperiorSkyblock2).\n");
        out.append("# position : x y z yaw pitch. Fichier réécrit par le plugin.\n");
        if (marker != null) {
            out.append("import-islandnpc: '").append(marker.replace("'", "''")).append("'\n");
        }
        out.append("iles:\n");
        for (JakeData d : jakes) {
            out.append("  ").append(d.island()).append(":\n");
            out.append("    monde: '").append(d.world().replace("'", "''")).append("'\n");
            out.append("    position: '").append(d.x()).append(' ').append(d.y()).append(' ').append(d.z())
                    .append(' ').append(d.yaw()).append(' ').append(d.pitch()).append("'\n");
            out.append("    type: ").append(d.type().name()).append('\n');
            out.append("    visible: ").append(d.visible()).append('\n');
        }
        return out.toString();
    }

    private void write(String content) {
        Path target = file.toPath();
        Path tmp = target.resolveSibling("jakes.yml.tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Écriture de jakes.yml impossible", e);
        }
    }
}
