package org.Cry_Legende.sHIslandJake;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import com.bgsoftware.superiorskyblock.api.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Montre à chaque joueur les Jake proches de lui : leur corps, leurs textes et, pour un modèle, la zone cliquable. On
 * ne regarde que la grille autour du joueur : un joueur loin de toute île ne coûte qu'une recherche par passage, et le
 * nombre de Jake n'entre pas en compte. Ce qu'un joueur a reçu d'un Jake n'est renvoyé que s'il change, et un joueur
 * qui n'a pas bougé, pour qui aucun Jake n'a changé et dont l'alerte n'est pas à relire est sauté (seule son ambiance
 * est regardée). Repère aussi l'arrivée d'un joueur près de Jake ({@link Arrivees}). Fil principal.
 *
 * <p>Corps de secours : Jake se tourne vers chaque joueur qui s'approche, et seulement pour lui, par
 * {@link Npc#lookAt}. Le passage lent (« intervalle-ticks ») range dans {@code looks} les Jake à moins de
 * « distance-regard » + {@link #LOOK_MARGIN} blocs d'un joueur ; le passage rapide (« intervalle-regard-ticks ») ne
 * parcourt que ceux-là, et saute un joueur immobile dont Jake a fini de tourner. Un modèle regarde seul (son fichier).
 */
final class JakeTracker {

    private static final long JOIN_DELAY = 40L;
    private static final double EYE_HEIGHT = 1.62;
    private static final long FORGET_SWEEP = 1200L;
    /** Blocs au-delà de « distance-regard » où un Jake est déjà suivi : un joueur en marche ne les franchit pas entre deux passages lents. */
    private static final double LOOK_MARGIN = 4.0;
    /** Blocs de plus pour qu'un Jake lâche un joueur (regard, arrivée) : évite qu'il hésite au bord de la zone. */
    private static final double HYSTERESIS = 1.0;
    /** Sous cette distance horizontale, la direction du joueur n'a plus de sens : Jake garde son regard. */
    private static final double LOOK_MIN_HORIZONTAL = 0.2;

    /** Orientation de Jake telle que ce joueur la voit, et si elle suit le joueur. */
    private static final class Look {
        final Npc handle;
        final World world;
        float yaw;
        float pitch;
        boolean following;
        /** Vrai si le joueur est assez près de Jake à la dernière vérification lente. */
        boolean near;
        /** Jake a fini de tourner pour cette position du joueur : rien à faire tant qu'il ne bouge pas. */
        boolean settled;
        double px = Double.NaN;
        double py;
        double pz;

        Look(Npc handle, World world, float yaw, float pitch) {
            this.handle = handle;
            this.world = world;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private final SHIslandJake plugin;
    private final JakeRegistry registry;
    private final JakeDisplay display;
    private final Textes textes;
    private final Arrivees arrivees = new Arrivees();
    private final Map<UUID, Map<Jake, Textes.Vue>> vues = new HashMap<>();
    private final Map<UUID, Map<Jake, Look>> looks = new HashMap<>();
    private final Map<UUID, Raccourci> etats = new HashMap<>();
    private final Set<UUID> joining = new HashSet<>();
    /** Liste réutilisée d'un passage à l'autre (fil principal, jamais deux passages à la fois). */
    private final List<Jake> nearBuffer = new ArrayList<>();
    private final Location ici = new Location(null, 0, 0, 0);
    private final Signalements erreurs = new Signalements();
    private BukkitTask visibilityTask;
    private BukkitTask lookTask;
    private BukkitTask forgetTask;
    private BukkitTask blocksTask;
    private int pass;
    private volatile boolean lookFailureLogged;

    JakeTracker(SHIslandJake plugin, JakeRegistry registry, JakeDisplay display, Textes textes) {
        this.plugin = plugin;
        this.registry = registry;
        this.display = display;
        this.textes = textes;
        // chargement à chaud : là où sont déjà les joueurs n'est pas une arrivée
        for (Player player : Bukkit.getOnlinePlayers()) {
            arrivees.amorcer(player.getUniqueId());
        }
    }

    void start() {
        stop();
        Settings settings = plugin.settings();
        int interval = settings.trackerInterval;
        visibilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, interval, interval);
        if (settings.lookAtPlayers) {
            int lookInterval = settings.lookIntervalTicks;
            lookTask = Bukkit.getScheduler().runTaskTimer(plugin, this::turnAll, lookInterval, lookInterval);
        }
        forgetTask = Bukkit.getScheduler().runTaskTimer(plugin, this::forgetIdle, FORGET_SWEEP, FORGET_SWEEP);
        // blocs au-dessus des Jake vus : un passage par seconde, chaque Jake à son rythme (verification-secondes)
        blocksTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkBlocks, 20L, 20L);
    }

    /** Les Jake qui ont un spectateur et dont la lecture des blocs est due : textes abaissés ou remontés si besoin. */
    private void checkBlocks() {
        int tick = Bukkit.getCurrentTick();
        Set<Jake> vus = new HashSet<>();
        vues.values().forEach(m -> vus.addAll(m.keySet()));
        for (Jake jake : vus) {
            if (jake.corps() != null && tick >= jake.prochainBlocs) {
                display.blocs(jake, true);
            }
        }
    }

    /** Arrête le suivi et retire aux joueurs tout ce qu'ils ont reçu en textes et zones (arrêt, rechargement). */
    void stop() {
        if (visibilityTask != null) {
            visibilityTask.cancel();
            visibilityTask = null;
        }
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
        if (forgetTask != null) {
            forgetTask.cancel();
            forgetTask = null;
        }
        if (blocksTask != null) {
            blocksTask.cancel();
            blocksTask = null;
        }
        for (Map.Entry<UUID, Map<Jake, Textes.Vue>> entry : vues.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                Paquets.Envoi envoi = Paquets.envoi(player);
                entry.getValue().forEach((jake, vue) -> textes.cacher(envoi, player, jake, vue));
                envoi.envoyer();
            }
        }
        vues.clear();
        looks.clear();
        etats.clear();
    }

    void join(Player player) {
        UUID id = player.getUniqueId();
        joining.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> joining.remove(id), JOIN_DELAY);
    }

    void clientLoaded(Player player) {
        joining.remove(player.getUniqueId());
        refresh(player, true);
    }

    void quit(Player player) {
        UUID id = player.getUniqueId();
        joining.remove(id);
        vues.remove(id);
        looks.remove(id);
        etats.remove(id);
        arrivees.oublier(id);
        display.forget(player);
        textes.oublier(id);
        plugin.alerts().forget(player);
    }

    /**
     * Changement de monde ou réapparition : tout ce que le joueur a reçu est détruit (le client garde les entités
     * d'une réapparition dans le même monde, et le suivi pourrait avoir renvoyé un texte juste avant), puis renvoyé.
     *
     * @param ailleurs vrai pour un changement de monde : son retour près d'un Jake sera une arrivée
     */
    void reset(Player player, boolean ailleurs) {
        UUID id = player.getUniqueId();
        plugin.reactions().perdu(id);
        Map<Jake, Textes.Vue> mine = vues.remove(id);
        looks.remove(id);
        etats.remove(id);
        if (ailleurs) {
            arrivees.oublier(id);
        }
        if (mine != null) {
            Paquets.Envoi envoi = Paquets.envoi(player);
            for (Map.Entry<Jake, Textes.Vue> entry : mine.entrySet()) {
                display.voir(entry.getKey(), player, false);
                textes.cacher(envoi, player, entry.getKey(), entry.getValue());
            }
            envoi.envoyer();
        }
        refreshSoon(player);
    }

    /** Après une téléportation, la position n'est à jour qu'au tick suivant. */
    void refreshSoon(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refresh(player, true);
            }
        }, 2L);
    }

    /** Un Jake vient de changer (place, apparence, visibilité) : les joueurs proches le revoient tout de suite. */
    void refreshAround(Jake jake) {
        JakeData d = jake.data();
        int distance = plugin.settings().viewDistance;
        double range = (distance + 16.0) * (distance + 16.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getLocation(ici);
            Map<Jake, Textes.Vue> previous = vues.get(player.getUniqueId());
            boolean near = ici.getWorld().getName().equals(d.world())
                    && square(ici.getX() - d.x()) + square(ici.getZ() - d.z()) <= range;
            if (near || previous != null && previous.containsKey(jake)) {
                refresh(player, true);
            }
        }
    }

    /**
     * Ce Jake n'est plus dessiné (ou l'est autrement) : ce que les joueurs en ont reçu est retiré, et leur prochain
     * passage est complet (il leur sera renvoyé). Les autres joueurs gardent leur raccourci.
     */
    void forgetJake(Jake jake) {
        for (Map.Entry<UUID, Map<Jake, Textes.Vue>> entry : vues.entrySet()) {
            Textes.Vue vue = entry.getValue().remove(jake);
            if (vue == null) {
                continue;
            }
            Raccourci r = etats.get(entry.getKey());
            if (r != null) {
                r.salir();
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                Paquets.Envoi envoi = Paquets.envoi(player);
                textes.cacher(envoi, player, jake, vue);
                envoi.envoyer();
            }
        }
        vues.values().removeIf(Map::isEmpty);
        for (Map<Jake, Look> mine : looks.values()) {
            mine.remove(jake);
        }
    }

    /** Jake déplacé ou île supprimée : l'état « près » de cette île est oublié pour tous. */
    void oublierIle(UUID island) {
        arrivees.oublierIle(island);
    }

    /** Jake que ce joueur voit en ce moment, avec ce qu'il en a reçu ; null s'il n'en voit aucun. */
    Map<Jake, Textes.Vue> vues(UUID player) {
        return vues.get(player);
    }

    /** Joueurs qui ont au moins un Jake près d'eux. */
    int watchedPlayers() {
        return vues.size();
    }

    private void refreshAll() {
        long start = plugin.mesures().debut();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                refresh(player, false);
            } catch (RuntimeException e) {
                // un joueur qui pose problème n'empêche pas le passage des suivants
                if (erreurs.premiere("suivi", e)) {
                    plugin.getLogger().log(Level.WARNING, "Suivi de " + player.getName() + " impossible", e);
                }
            }
        }
        plugin.mesures().fin(Mesures.Tache.SUIVI, start);
    }

    private void refresh(Player player, boolean force) {
        UUID id = player.getUniqueId();
        if (joining.contains(id)) {
            return;
        }
        Settings settings = plugin.settings();
        player.getLocation(ici);
        World world = ici.getWorld();
        int tick = Bukkit.getCurrentTick();
        Raccourci etat = etats.computeIfAbsent(id, k -> new Raccourci());
        Map<Jake, Textes.Vue> mine = vues.get(id);
        if (!force && etat.saute(world, ici.getX(), ici.getY(), ici.getZ(), tick, settings.moveThresholdSquared)) {
            // rien n'a bougé : alerte et appartenance relues à leur rythme, et l'ambiance (il a pu tourner la tête)
            if (mine != null) {
                leger(player, mine, etat, tick);
                plugin.reactions().ambiance(player, mine, ici);
            } else {
                plugin.reactions().perdu(id);
            }
            return;
        }
        List<Jake> near = nearBuffer;
        registry.near(world.getName(), ici.getBlockX(), ici.getBlockZ(), settings.viewDistance, near);
        if (near.isEmpty() && mine == null) {
            plugin.reactions().perdu(id);
            etat.complet(world, ici.getX(), ici.getY(), ici.getZ(), tick + settings.fullPassTicks);
            etat.alerteA = Integer.MAX_VALUE;
            etat.membreA = Integer.MAX_VALUE;
            arrivees.finPassage(id);
            return;
        }
        int current = ++pass;
        long now = System.currentTimeMillis();
        double range = (double) settings.viewDistance * settings.viewDistance;
        double lookRange = square(settings.lookDistance + LOOK_MARGIN);
        double enter = square(settings.reactions.distance);
        double leave = square(settings.reactions.distance + HYSTERESIS);
        int prochain = tick + settings.fullPassTicks;
        int membreA = Integer.MAX_VALUE;
        boolean manque = false;
        Map<Jake, Look> myLooks = looks.get(id);
        if (myLooks != null) {
            myLooks.values().forEach(look -> look.near = false);
        }
        Paquets.Envoi envoi = null;
        List<Jake> arrivals = null;
        boolean alerteLue = false;
        for (Jake jake : near) {
            JakeData d = jake.data();
            if (!d.visible()) {
                continue;
            }
            double distance = square(ici.getX() - d.x()) + square(ici.getY() - d.y()) + square(ici.getZ() - d.z());
            Textes.Vue vue = mine == null ? null : mine.get(jake);
            if (distance > range) {
                continue;
            }
            if (!display.montrer(jake, vue == null)) {
                // chunk pas encore chargé, modèle en lecture : nouvel essai au prochain passage
                manque = true;
                continue;
            }
            jake.seen(now);
            if (mine == null) {
                mine = new HashMap<>();
                vues.put(id, mine);
            }
            // un Jake redessiné pendant ce passage a pu retirer la vue
            vue = mine.get(jake);
            if (vue == null) {
                vue = new Textes.Vue();
                vue.membreRelu = Integer.MIN_VALUE;
                mine.put(jake, vue);
            }
            if (tick >= vue.membreRelu) {
                Island island = plugin.islands().byId(d.island());
                vue.membre = island != null && plugin.islands().canUse(player, island, true);
                vue.membreRelu = tick + settings.memberCheckTicks;
            }
            membreA = Math.min(membreA, vue.membreRelu);
            vue.passe = current;
            vue.distance2 = distance;
            if (envoi == null) {
                envoi = Paquets.envoi(player);
            }
            display.voir(jake, player, true);
            textes.montrer(envoi, jake, vue);
            String alert = "";
            if (vue.membre) {
                alert = plugin.alerts().text(player);
                alerteLue = true;
            }
            textes.alerte(envoi, player, jake, vue, alert);
            if (arrivees.passe(id, d.island(), distance, enter, leave)) {
                if (arrivals == null) {
                    arrivals = new ArrayList<>(1);
                }
                arrivals.add(jake);
            }
            Npc handle = jake.handle();
            if (settings.lookAtPlayers && handle != null && distance <= lookRange) {
                if (myLooks == null) {
                    myLooks = new HashMap<>();
                    looks.put(id, myLooks);
                }
                Look look = myLooks.get(jake);
                if (look == null || look.handle != handle || look.world != world) {
                    look = new Look(handle, world, d.yaw(), d.pitch());
                    myLooks.put(jake, look);
                }
                look.near = true;
            }
        }
        if (mine != null) {
            Iterator<Map.Entry<Jake, Textes.Vue>> it = mine.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Jake, Textes.Vue> entry = it.next();
                if (entry.getValue().passe != current) {
                    if (envoi == null) {
                        envoi = Paquets.envoi(player);
                    }
                    Jake jake = entry.getKey();
                    display.voir(jake, player, false);
                    textes.cacher(envoi, player, jake, entry.getValue());
                    // il ne le voit plus : s'il s'est éloigné, son retour sera une arrivée ; un Jake caché ne l'est pas
                    JakeData d = jake.data();
                    if (!d.world().equals(world.getName()) || square(ici.getX() - d.x()) + square(ici.getY() - d.y())
                            + square(ici.getZ() - d.z()) > leave) {
                        arrivees.loin(id, jake.island());
                    }
                    it.remove();
                }
            }
            // un Jake redessiné pendant le passage a pu retirer cette liste de « vues » : elle y est remise
            if (mine.isEmpty()) {
                vues.remove(id);
                mine = null;
            } else {
                vues.put(id, mine);
            }
        }
        if (envoi != null) {
            envoi.envoyer();
        }
        etat.complet(world, ici.getX(), ici.getY(), ici.getZ(), manque ? tick : prochain);
        etat.alerteA = alerteLue ? plugin.alerts().prochaineLecture(player) : Integer.MAX_VALUE;
        etat.membreA = membreA;
        // après un chargement à chaud, l'amorce n'est consommée que par un passage où tout a pu être dessiné
        if (!manque) {
            arrivees.finPassage(id);
        }
        if (arrivals != null) {
            for (Jake jake : arrivals) {
                Textes.Vue vue = mine == null ? null : mine.get(jake);
                if (vue != null) {
                    plugin.reactions().arrivee(player, jake, vue);
                }
            }
        }
        if (mine != null) {
            plugin.reactions().ambiance(player, mine, ici);
        } else {
            plugin.reactions().perdu(id);
        }
    }

    /**
     * Joueur immobile : son alerte et son appartenance à l'île sont relues à leur rythme, sans passage complet ; une
     * alerte dont le texte n'a pas changé n'envoie rien. Une appartenance qui change rend le prochain passage complet.
     */
    private void leger(Player player, Map<Jake, Textes.Vue> mine, Raccourci etat, int tick) {
        if (tick >= etat.membreA) {
            int prochain = Integer.MAX_VALUE;
            for (Map.Entry<Jake, Textes.Vue> entry : mine.entrySet()) {
                Textes.Vue vue = entry.getValue();
                if (tick >= vue.membreRelu) {
                    Island island = plugin.islands().byId(entry.getKey().data().island());
                    boolean membre = island != null && plugin.islands().canUse(player, island, true);
                    vue.membreRelu = tick + plugin.settings().memberCheckTicks;
                    if (membre != vue.membre) {
                        // gardée : le passage complet qui suit montre ou retire l'alerte sans relire l'île
                        vue.membre = membre;
                        etat.salir();
                    }
                }
                prochain = Math.min(prochain, vue.membreRelu);
            }
            etat.membreA = prochain;
        }
        if (tick >= etat.alerteA) {
            String alert = plugin.alerts().text(player);
            Paquets.Envoi envoi = null;
            for (Map.Entry<Jake, Textes.Vue> entry : mine.entrySet()) {
                Textes.Vue vue = entry.getValue();
                if (vue.membre && !alert.equals(vue.alerte)) {
                    if (envoi == null) {
                        envoi = Paquets.envoi(player);
                    }
                    textes.alerte(envoi, player, entry.getKey(), vue, alert);
                }
            }
            if (envoi != null) {
                envoi.envoyer();
            }
            etat.alerteA = plugin.alerts().prochaineLecture(player);
        }
    }

    /**
     * Passage rapide : chaque Jake suivi tourne d'un pas vers le joueur (dans la zone) ou vers son orientation
     * d'origine (hors de la zone). Rien n'est envoyé tant que l'angle n'a pas bougé de « angle-regard-minimum », et
     * un joueur immobile dont Jake a fini de tourner ne coûte qu'une comparaison.
     */
    private void turnAll() {
        if (looks.isEmpty()) {
            return;
        }
        long start = plugin.mesures().debut();
        turnAllNow();
        plugin.mesures().fin(Mesures.Tache.REGARD, start);
    }

    private void turnAllNow() {
        Settings settings = plugin.settings();
        double enter = square(settings.lookDistance);
        double leave = square(settings.lookDistance + HYSTERESIS);
        double step = settings.lookSpeed <= 0 ? 360.0 : settings.lookSpeed * settings.lookIntervalTicks / 20.0;
        Iterator<Map.Entry<UUID, Map<Jake, Look>>> players = looks.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Map<Jake, Look>> entry = players.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                players.remove();
                continue;
            }
            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();
            World world = player.getWorld();
            Iterator<Map.Entry<Jake, Look>> jakes = entry.getValue().entrySet().iterator();
            while (jakes.hasNext()) {
                Map.Entry<Jake, Look> pair = jakes.next();
                Jake jake = pair.getKey();
                Look look = pair.getValue();
                if (jake.handle() != look.handle || look.world != world) {
                    jakes.remove();
                    continue;
                }
                if (look.settled && look.near && px == look.px && py == look.py && pz == look.pz) {
                    continue;
                }
                double eyes = py + player.getEyeHeight();
                boolean moving = turn(jake, look, player, px, py, pz, eyes, enter, leave, step, settings);
                if (!moving && !look.near) {
                    jakes.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                players.remove();
            }
        }
    }

    /**
     * Fait un pas de ce Jake vers ce que ce joueur doit voir. Renvoie vrai tant que Jake n'est pas revenu à son
     * orientation d'origine. Note dans {@code look.settled} si rien n'a été envoyé (Jake a fini de tourner).
     */
    private boolean turn(Jake jake, Look look, Player player, double px, double py, double pz, double eyes,
                         double enter, double leave, double step, Settings settings) {
        JakeData d = jake.data();
        Npc handle = look.handle;
        look.px = px;
        look.py = py;
        look.pz = pz;
        look.settled = false;
        if (!handle.isShownFor(player)) {
            // pas encore envoyé, ou retiré au joueur : il le recevra à son orientation d'origine
            look.yaw = d.yaw();
            look.pitch = d.pitch();
            look.following = false;
            return false;
        }
        double dx = px - d.x();
        double dy = py - d.y();
        double dz = pz - d.z();
        double distance = dx * dx + dy * dy + dz * dz;
        boolean follow = distance <= (look.following ? leave : enter);
        look.following = follow;
        float targetYaw = d.yaw();
        float targetPitch = d.pitch();
        if (follow) {
            double horizontal = Math.hypot(dx, dz);
            targetYaw = horizontal < LOOK_MIN_HORIZONTAL ? look.yaw : (float) Math.toDegrees(Math.atan2(-dx, dz));
            double rise = eyes - (d.y() + eyeHeight(handle));
            double limit = settings.lookMaxPitch;
            targetPitch = (float) Math.max(-limit, Math.min(limit, -Math.toDegrees(Math.atan2(rise, horizontal))));
        }
        float turnYaw = wrap(targetYaw - look.yaw);
        float turnPitch = targetPitch - look.pitch;
        double remaining = Math.max(Math.abs(turnYaw), Math.abs(turnPitch));
        if (remaining < 0.01) {
            look.settled = true;
            return follow;
        }
        if (follow && remaining < settings.lookMinAngle) {
            look.settled = true;
            return true;
        }
        look.yaw = wrap(look.yaw + (float) Math.max(-step, Math.min(step, turnYaw)));
        look.pitch += (float) Math.max(-step, Math.min(step, turnPitch));
        Location where = new Location(look.world, d.x(), d.y(), d.z(), look.yaw, look.pitch);
        FancyNpcsPlugin.get().getNpcThread().submit(() -> {
            try {
                handle.lookAt(player, where);
            } catch (Exception | LinkageError e) {
                if (!lookFailureLogged) {
                    lookFailureLogged = true;
                    plugin.getLogger().log(Level.WARNING, "Jake ne peut pas tourner vers le joueur : " + e, e);
                }
            }
        });
        return true;
    }

    private static double eyeHeight(Npc handle) {
        float height = handle.getEyeHeight();
        return height > 0 ? height : EYE_HEIGHT;
    }

    /** Ramène un angle en degrés dans [-180, 180]. */
    private static float wrap(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped > 180f) {
            wrapped -= 360f;
        } else if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }

    /** Les Jake qu'aucun joueur n'a vus depuis « oubli-minutes » ne sont plus dessinés ; ils reviennent avec un joueur. */
    private void forgetIdle() {
        long limit = System.currentTimeMillis() - plugin.settings().forgetMillis;
        Set<Jake> watchedNow = new HashSet<>();
        vues.values().forEach(m -> watchedNow.addAll(m.keySet()));
        for (Jake jake : new ArrayList<>(registry.drawn())) {
            if (jake.lastSeen() < limit && !watchedNow.contains(jake)) {
                display.release(jake);
            }
        }
    }

    private static double square(double value) {
        return value * value;
    }
}
