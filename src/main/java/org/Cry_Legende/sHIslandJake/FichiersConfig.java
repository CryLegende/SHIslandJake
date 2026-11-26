package org.Cry_Legende.sHIslandJake;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Lecture d'un fichier de configuration : une erreur nomme le fichier refusé. */
final class FichiersConfig {

    /** Fichier refusé : l'ancienne configuration reste en place. */
    static final class FichierInvalide extends Exception {

        private final String fichier;

        FichierInvalide(String fichier, Throwable cause) {
            super(fichier + " : " + cause.getMessage(), cause);
            this.fichier = fichier;
        }

        String fichier() {
            return fichier;
        }
    }

    private FichiersConfig() {
    }

    static YamlConfiguration lire(File dossier, String nom) throws FichierInvalide {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new File(dossier, nom));
        } catch (IOException | InvalidConfigurationException e) {
            throw new FichierInvalide(nom, e);
        }
        return yaml;
    }
}
