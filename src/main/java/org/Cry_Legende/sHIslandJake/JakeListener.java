package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.events.IslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Événements. Avec les mondes en parallèle (DivineMC), ils arrivent sur le fil du monde du joueur : tout ce qui
 * touche aux Jake repasse par le fil principal ({@link SHIslandJake#sync}). Seule l'annulation se décide sur place.
 */
final class JakeListener implements Listener {

    private final SHIslandJake plugin;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    JakeListener(SHIslandJake plugin) {
        this.plugin = plugin;
    }

    //JOUEURS----------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.sync(() -> plugin.tracker().join(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClientLoaded(PlayerClientLoadedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.sync(() -> {
            if (player.isOnline()) {
                plugin.tracker().clientLoaded(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastClick.remove(player.getUniqueId());
        plugin.sync(() -> {
            plugin.moves().forget(player.getUniqueId());
            plugin.tracker().quit(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        plugin.sync(() -> plugin.tracker().refreshSoon(player));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.sync(() -> {
            plugin.moves().cancel(player);
            plugin.tracker().refreshSoon(player);
        });
    }

    //CLIC SUR JAKE----------

    @EventHandler
    public void onClickJake(PlayerUseUnknownEntityEvent event) {
        boolean attack = event.isAttack();
        if (attack ? !plugin.settings().leftClick : event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        int entityId = event.getEntityId();
        plugin.sync(() -> clickJake(player, entityId));
    }

    private void clickJake(Player player, int entityId) {
        Jake jake = plugin.registry().byEntity(entityId);
        if (jake == null || !player.isOnline()) {
            return;
        }
        // Un clic droit envoie plusieurs paquets (interact_at, interact) : un seul compte.
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < plugin.settings().clickDelayMillis) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);

        Settings settings = plugin.settings();
        Island island = plugin.islands().byId(jake.island());
        boolean member = island != null && plugin.islands().canUse(player, island, settings.membersCanClick);
        if (!member && !player.hasPermission("shislandjake.bypass")) {
            plugin.sound(player, settings.soundRefusal);
            plugin.messages().send(player, "pas-membre", "nom", plugin.messages().jakeName(jake.data().type()));
            return;
        }
        if (settings.sneakOpensMenu && player.isSneaking() && member) {
            plugin.commands().openMenu(player);
            return;
        }
        if (!settings.clickCommand.isEmpty()) {
            plugin.sound(player, settings.soundClick);
            player.performCommand(settings.clickCommand);
        }
    }

    //DÉPLACEMENT----------

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.moves().isMoving(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        if (block == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        plugin.sync(() -> plugin.moves().click(player, block));
    }

    //MENUS----------

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        // Sans le false, Paper recopie l'état du bloc à chaque clic dans un coffre.
        if (!(top.getHolder(false) instanceof JakeMenu menu)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.sync(() -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) == menu) {
                menu.click(player, slot);
            }
        });
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof JakeMenu) {
            event.setCancelled(true);
        }
    }

    //ÎLES----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCreate(IslandCreateEvent event) {
        if (!plugin.settings().createWithIsland) {
            return;
        }
        Island island = event.getIsland();
        plugin.sync(() -> {
            if (plugin.registry().get(island.getUniqueId()) == null) {
                plugin.manager().create(island);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDisband(IslandDisbandEvent event) {
        UUID island = event.getIsland().getUniqueId();
        plugin.sync(() -> plugin.manager().delete(island));
    }
}
