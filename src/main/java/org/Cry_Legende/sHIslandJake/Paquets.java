package org.Cry_Legende.sHIslandJake;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Entités envoyées en paquets à un seul joueur : les textes au-dessus de Jake (« text display ») et la zone cliquable
 * d'un modèle (« interaction »). Le serveur n'en garde aucune entité. Un paquet packetevents n'est jamais envoyé à deux
 * joueurs (il garde son tampon) : chaque envoi en construit un, à partir de données préparées une fois. Les paquets
 * d'un joueur sont écrits puis envoyés ensemble ({@link Envoi#envoyer()}). Seule classe qui touche à packetevents.
 */
final class Paquets {

    // Métadonnées (26.3) : celles d'un display, d'un text display, d'une interaction
    private static final int DELAY = 8;
    private static final int SCALE_TICKS = 9;
    private static final int SCALE = 12;
    private static final int BILLBOARD = 15;
    private static final int TEXT = 23;
    private static final int LINE_WIDTH = 24;
    private static final int BACKGROUND = 25;
    private static final int STYLE = 27;
    private static final byte SHADOW = 0x01;
    private static final int BOX_WIDTH = 8;
    private static final int BOX_HEIGHT = 9;
    private static final int BOX_RESPONSIVE = 10;

    /** Apparence d'un texte : orientation, fond (null = celui de Minecraft), ombre, largeur de ligne. */
    record Style(byte billboard, Integer background, boolean shadow, int lineWidth) {
    }

    /**
     * Les paquets d'un joueur, écrits puis envoyés ensemble. La connexion du joueur n'est cherchée qu'au premier paquet :
     * un passage du suivi qui n'a rien à envoyer ne coûte rien.
     */
    static final class Envoi {

        private final Player player;
        private User user;
        private boolean cherche;
        private boolean ecrit;

        private Envoi(Player player) {
            this.player = player;
        }

        private void ecrire(PacketWrapper<?> packet) {
            if (!cherche) {
                cherche = true;
                try {
                    user = PacketEvents.getAPI().getPlayerManager().getUser(player);
                } catch (RuntimeException e) {
                    user = null;
                }
            }
            if (user == null) {
                return;
            }
            try {
                user.writePacketSilently(packet);
                ecrit = true;
            } catch (RuntimeException ignored) {
                // joueur en cours de déconnexion
            }
        }

        void envoyer() {
            if (ecrit) {
                ecrit = false;
                try {
                    user.flushPackets();
                } catch (RuntimeException ignored) {
                    // joueur en cours de déconnexion
                }
            }
        }
    }

    private Paquets() {
    }

    static Envoi envoi(Player player) {
        return new Envoi(player);
    }

    static int nouveauNumero() {
        return SpigotReflectionUtil.generateEntityId();
    }

    /** Données d'un texte à son apparition, préparées une fois et réutilisées pour chaque joueur. */
    static List<EntityData<?>> donneesTexte(Component text, Style style, float scale) {
        List<EntityData<?>> data = new ArrayList<>(7);
        data.add(new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)));
        data.add(new EntityData<>(BILLBOARD, EntityDataTypes.BYTE, style.billboard()));
        data.add(new EntityData<>(TEXT, EntityDataTypes.ADV_COMPONENT, text));
        data.add(new EntityData<>(LINE_WIDTH, EntityDataTypes.INT, style.lineWidth()));
        if (style.background() != null) {
            data.add(new EntityData<>(BACKGROUND, EntityDataTypes.INT, style.background()));
        }
        if (style.shadow()) {
            data.add(new EntityData<>(STYLE, EntityDataTypes.BYTE, SHADOW));
        }
        return data;
    }

    /** Fait apparaître un texte (ou le remplace s'il existe déjà sous ce numéro). */
    static void texte(Envoi envoi, int id, UUID uuid, double x, double y, double z, List<EntityData<?>> donnees) {
        envoi.ecrire(new WrapperPlayServerSpawnEntity(id, Optional.of(uuid), EntityTypes.TEXT_DISPLAY,
                new Vector3d(x, y, z), 0f, 0f, 0f, 0, Optional.empty()));
        envoi.ecrire(new WrapperPlayServerEntityMetadata(id, donnees));
    }

    static void changerTexte(Envoi envoi, int id, Component text) {
        envoi.ecrire(new WrapperPlayServerEntityMetadata(id, List.of(new EntityData<>(TEXT, EntityDataTypes.ADV_COMPONENT, text))));
    }

    /** Taille atteinte en « ticks » (0 = d'un coup). */
    static void echelle(Envoi envoi, int id, float scale, int ticks) {
        envoi.ecrire(new WrapperPlayServerEntityMetadata(id, List.of(
                new EntityData<>(DELAY, EntityDataTypes.INT, 0),
                new EntityData<>(SCALE_TICKS, EntityDataTypes.INT, ticks),
                new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)))));
    }

    /** Zone cliquable : entité « interaction » posée aux pieds de Jake. */
    static void zone(Envoi envoi, int id, UUID uuid, double x, double y, double z, float largeur, float hauteur) {
        envoi.ecrire(new WrapperPlayServerSpawnEntity(id, Optional.of(uuid), EntityTypes.INTERACTION,
                new Vector3d(x, y, z), 0f, 0f, 0f, 0, Optional.empty()));
        envoi.ecrire(new WrapperPlayServerEntityMetadata(id, List.of(
                new EntityData<>(BOX_WIDTH, EntityDataTypes.FLOAT, largeur),
                new EntityData<>(BOX_HEIGHT, EntityDataTypes.FLOAT, hauteur),
                new EntityData<>(BOX_RESPONSIVE, EntityDataTypes.BOOLEAN, true))));
    }

    static void detruire(Envoi envoi, int... ids) {
        if (ids.length > 0) {
            envoi.ecrire(new WrapperPlayServerDestroyEntities(ids));
        }
    }
}
