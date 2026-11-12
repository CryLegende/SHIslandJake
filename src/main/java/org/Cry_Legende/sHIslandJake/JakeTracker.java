package org.Cry_Legende.sHIslandJake;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Montre à chaque joueur les Jake proches de lui. On ne regarde que la grille autour du joueur : un joueur loin
 * de toute île ne coûte qu'une recherche par passage, et le nombre de Jake n'entre pas en compte. Fil principal.
 */
final class JakeTracker {

    private static final long JOIN_DELAY = 40L;
    private static final double EYE_HEIGHT = 1.62;
    private static final long FORGET_SWEEP = 1200L;

    private final SHIslandJake plugin;
    private final JakeRegistry registry;
    private final JakeDisplay display;
    private final Map<UUID, Set<Jake>> watched = new HashMap<>();
    private final Set<UUID> joining = new HashSet<>();
    private BukkitTask visibilityTask;
    private BukkitTask forgetTask;

    JakeTracker(SHIslandJake plugin, JakeRegistry registry, JakeDisplay display) {
        this.plugin = plugin;
        this.registry = registry;
        this.display = display;
    }

    void start() {
        stop();
        int interval = plugin.settings().trackerInterval;
        visibilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, interval, interval);
        forgetTask = Bukkit.getScheduler().runTaskTimer(plugin, this::forgetIdle, FORGET_SWEEP, FORGET_SWEEP);
    }

    void stop() {
        if (visibilityTask != null) {
            visibilityTask.cancel();
            visibilityTask = null;
        }
        if (forgetTask != null) {
            forgetTask.cancel();
            forgetTask = null;
        }
        watched.clear();
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
        if (plugin.settings().lookAtPlayers) {
            turnAll();
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
            boolean shown = square(location.getX() - d.x()) + square(location.getY() - d.y())
                    + square(location.getZ() - d.z()) <= range;
            display.syncHologram(jake, player, shown);
            if (kept == null) {
                kept = new HashSet<>();
            }
            kept.add(jake);
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

    /** Jake tourne la tête vers les joueurs proches ; un joueur immobile ne coûte aucun paquet. */
    private void turnAll() {
        ExecutorService thread = FancyNpcsPlugin.get().getNpcThread();
        int distance = plugin.settings().lookDistance;
        double range = (double) distance * distance;
        watched.forEach((id, jakes) -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                return;
            }
            Location eyes = player.getEyeLocation();
            for (Jake jake : jakes) {
                Npc handle = jake.handle();
                Location look = jake.location();
                if (handle == null || look == null || look.getWorld() != eyes.getWorld()
                        || look.distanceSquared(eyes) >= range || !handle.isShownFor(player)) {
                    continue;
                }
                look.setY(look.getY() + EYE_HEIGHT);
                look.setDirection(eyes.toVector().subtract(look.toVector()));
                look.setY(jake.data().y());
                int rounded = Math.round(look.getYaw()) * 1000 + Math.round(look.getPitch());
                if (!jake.alreadyLooking(id, rounded)) {
                    thread.submit(() -> handle.lookAt(player, look));
                }
            }
        });
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
