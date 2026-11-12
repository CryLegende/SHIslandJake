package org.Cry_Legende.sHIslandJake;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.commands.SuperiorCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Sous-commande de /is (« sous-commandes-ile », /is jake et /is npc par défaut) : paramètres de Jake.
 */
final class IslandSubCommand implements SuperiorCommand {

    private final SHIslandJake plugin;
    private final List<String> aliases;

    IslandSubCommand(SHIslandJake plugin, List<String> aliases) {
        this.plugin = plugin;
        this.aliases = aliases;
    }

    @Override
    public List<String> getAliases() {
        return aliases;
    }

    @Override
    public String getPermission() {
        return "shislandjake.use";
    }

    @Override
    public String getUsage(Locale locale) {
        return aliases.getFirst();
    }

    @Override
    public String getDescription(Locale locale) {
        return plugin.messages().text("sous-commande-ile.description");
    }

    @Override
    public int getMinArgs() {
        return 1;
    }

    @Override
    public int getMaxArgs() {
        return 1;
    }

    @Override
    public boolean canBeExecutedByConsole() {
        return false;
    }

    @Override
    public boolean displayCommand() {
        return true;
    }

    @Override
    public void execute(SuperiorSkyblock superiorSkyblock, CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            plugin.sync(() -> plugin.commands().openMenu(player));
        }
    }

    @Override
    public List<String> tabComplete(SuperiorSkyblock superiorSkyblock, CommandSender sender, String[] args) {
        return List.of();
    }
}
