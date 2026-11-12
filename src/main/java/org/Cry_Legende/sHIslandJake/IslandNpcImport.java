package org.Cry_Legende.sHIslandJake;

import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Lecture de la base d'IslandNPC (plugins/IslandNPC/database.db, table npc_data : une ligne par île, identifiant
 * de l'île en 16 octets, type, visibilité, monde et position). La base est d'abord copiée : la lecture ne touche
 * jamais au fichier d'origine. IslandNPC ne garde pas d'orientation par PNJ : celle de « jake.position » est prise.
 */
final class IslandNpcImport {

    record Row(UUID island, EntityType type, boolean visible, String world, double x, double y, double z) {
    }

    private final File source;
    private final File copy;
    private final Logger log;

    IslandNpcImport(File pluginsFolder, File dataFolder, Logger log) {
        this.source = new File(new File(pluginsFolder, "IslandNPC"), "database.db");
        this.copy = new File(dataFolder, "islandnpc-import.db");
        this.log = log;
    }

    boolean available() {
        return source.isFile();
    }

    /** Lignes lisibles de npc_data ; un type inconnu prend « defaultType ». */
    List<Row> read(EntityType defaultType) throws IOException, SQLException {
        Files.createDirectories(copy.getParentFile().toPath());
        Files.copy(source.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        List<Row> rows = new ArrayList<>();
        int skipped = 0;
        try {
            // Pilote SQLite fourni par le serveur, chargé avant la première connexion
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("pilote SQLite absent du serveur", e);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + copy.getAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, entityType, visible, world, x, y, z FROM npc_data")) {
            while (result.next()) {
                byte[] id = result.getBytes(1);
                String world = result.getString(4);
                if (id == null || id.length != 16 || world == null) {
                    skipped++;
                    continue;
                }
                ByteBuffer buffer = ByteBuffer.wrap(id);
                UUID island = new UUID(buffer.getLong(), buffer.getLong());
                String typeName = result.getString(2);
                EntityType type = typeName == null ? null : Settings.entityType(typeName, null, null);
                rows.add(new Row(island, type == null ? defaultType : type, result.getBoolean(3), world,
                        result.getDouble(5), result.getDouble(6), result.getDouble(7)));
            }
        } finally {
            Files.deleteIfExists(copy.toPath());
        }
        if (skipped > 0) {
            log.warning("IslandNPC : " + skipped + " ligne(s) illisible(s) ignorée(s).");
        }
        return rows;
    }
}
