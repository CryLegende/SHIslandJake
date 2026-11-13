package org.Cry_Legende.sHIslandJake;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Montre à chaque joueur les Jake proches de lui. On ne regarde que la grille autour du joueur : un joueur loin
 * de toute île ne coûte qu'une recherche par passage, et le nombre de Jake n'entre pas en compte. Fil principal.
 *
 * <p>Jake se tourne vers chaque joueur qui s'approche, et seulement pour lui : la rotation est envoyée à ce joueur
 * par {@link Npc#lookAt}. Le passage lent (« intervalle-ticks ») range dans {@code looks} les Jake à moins de
 * « distance-regard » + {@link #LOOK_MARGIN} blocs d'un joueur ; le passage rapide (« intervalle-regard-ticks »)
 * ne parcourt que ces Jake-là. Un Jake rendu à son orientation d'origine sort de la liste.
 */
final class JakeTracker {

    private static final long JOIN_DELAY = 40L;
    private static final double EYE_HEIGHT = 1.62;
    private static final long FORGET_SWEEP = 1200L;
    /** Blocs au-delà de « distance-regard » où un Jake est déjà suivi : un joueur en marche ne les franchit pas entre deux passages lents. */
    private static final double LOOK_MARGIN = 4.0;
    /** Blocs de plus pour qu'un Jake qui regarde un joueur le lâche : évite qu'il hésite au bord de la zone. */
    private static final double LOOK_HYSTERESIS = 1.0;
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
    private final Map<UUID, Set<Jake>> watched = new HashMap<>();
    private final Map<UUID, Map<Jake, Look>> looks = new HashMap<>();
    private final Set<UUID> joining = new HashSet<>();
    private BukkitTask visibilityTask;
    private BukkitTask lookTask;
    private BukkitTask forgetTask;
    private volatile boolean lookFailureLogged;

    JakeTracker(SHIslandJake plugin, JakeRegistry registry, JakeDisplay display) {
        this.plugin = plugin;
        this.registry = registry;
        this.display = display;
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
    }

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
        watched.clear();
        looks.clear();
    }

    void join(Player player) {
        UUID id = player.getUniqueId();
        joining.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> joining.remove(id), JOIN_DELAY);
    }

    void clientLoaded(Player player) {
        joining.remove(player.getUniqueId());
        refresh(player);
    }

    void quit(Player player) {
        UUID id = player.getUniqueId();
        joining.remove(id);
        watched.remove(id);
        looks.remove(id);
        display.forget(player);
    }

    /** Après une téléportation, la position n'est à jour qu'au tick suivant. */
    void refreshSoon(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        }, 2L);
    }

    /** Un Jake vient de changer (place, type, visibilité) : les joueurs proches le revoient tout de suite. */
    void refreshAround(Jake jake) {
        JakeData d = jake.data();
        int distance = plugin.settings().viewDistance;
        double range = (distance + 16.0) * (distance + 16.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            Set<Jake> previous = watched.get(player.getUniqueId());
            boolean near = location.getWorld().getName().equals(d.world())
                    && square(location.getX() - d.x()) + square(location.getZ() - d.z()) <= range;
            if (near || previous != null && previous.contains(jake)) {
                refresh(player);
            }
        }
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private void refresh(Player player) {
        UUID id = player.getUniqueId();
        if (joining.contains(id)) {
            return;
        }
        Settings settings = plugin.settings();
        Location location = player.getLocation();
        String world = location.getWorld().getName();
        Set<Jake> near = registry.near(world, location.getBlockX(), location.getBlockZ(), settings.viewDistance);
        Set<Jake> previous = watched.get(id);
        if (near.isEmpty() && previous == null) {
            return;
        }
        long now = System.currentTimeMillis();
        double range = (double) settings.viewDistance * settings.viewDistance;
        double lookRange = square(settings.lookDistance + LOOK_MARGIN);
        Map<Jake, Look> mine = looks.get(id);
        if (mine != null) {
            mine.values().forEach(look -> look.near = false);
        }
        Set<Jake> kept = null;
        for (Jake jake : near) {
            JakeData d = jake.data();
            if (!d.visible()) {
                continue;
            }
            Npc handle = display.handle(jake);
            if (handle == null) {
                continue;
            }
            jake.seen(now);
            handle.checkAndUpdateVisibility(player);
            double distance = square(location.getX() - d.x()) + square(location.getY() - d.y())
                    + square(location.getZ() - d.z());
            display.syncHologram(jake, player, distance <= range);
            if (kept == null) {
                kept = new HashSet<>();
            }
            kept.add(jake);
            if (settings.lookAtPlayers && distance <= lookRange) {
                if (mine == null) {
                    mine = new HashMap<>();
                    looks.put(id, mine);
                }
                Look look = mine.get(jake);
                if (look == null || look.handle != handle || look.world != location.getWorld()) {
                    look = new Look(handle, location.getWorld(), d.yaw(), d.pitch());
                    mine.put(jake, look);
                }
                look.near = true;
            }
        }
        if (previous != null) {
            for (Jake jake : previous) {
                if ((kept == null || !kept.contains(jake)) && jake.handle() != null) {
                    jake.handle().checkAndUpdateVisibility(player);
                    display.syncHologram(jake, player, false);
                }
            }
        }
        if (kept == null) {
            watched.remove(id);
        } else {
            watched.put(id, kept);
        }
    }

    /**
     * Passage rapide : chaque Jake suivi tourne d'un pas vers le joueur (dans la zone) ou vers son orientation
     * d'origine (hors de la zone). Rien n'est envoyé tant que l'angle n'a pas bougé de « angle-regard-minimum »,
     * et seuls les Jake de {@code looks} sont parcourus.
     */
    private void turnAll() {
        if (looks.isEmpty()) {
            return;
        }
        Settings settings = plugin.settings();
        double enter = square(settings.lookDistance);
        double leave = square(settings.lookDistance + LOOK_HYSTERESIS);
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
            double eyes = py + player.getEyeHeight();
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
                if (!turn(jake, look, player, px, py, pz, eyes, enter, leave, step, settings) && !look.near) {
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
     * orientation d'origine.
     */
    private boolean turn(Jake jake, Look look, Player player, double px, double py, double pz, double eyes,
                         double enter, double leave, double step, Settings settings) {
        JakeData d = jake.data();
        Npc handle = look.handle;
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
            return follow;
        }
        if (follow && remaining < settings.lookMinAngle) {
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

    /** Les PNJ qu'aucun joueur n'a vus depuis « oubli-minutes » sont rendus à la mémoire. */
    private void forgetIdle() {
        long limit = System.currentTimeMillis() - plugin.settings().forgetMillis;
        Set<Jake> watchedNow = new HashSet<>();
        watched.values().forEach(watchedNow::addAll);
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
