package io.tebex.plugin.command;

import io.tebex.plugin.FoliaPluginPlatform;
import io.tebex.plugin.gui.BuyGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class BuyCommand extends Command {
    private final FoliaPluginPlatform platform;

    public BuyCommand(String command, FoliaPluginPlatform platform) {
        super(command);
        this.platform = platform;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if(! platform.isSetup()) {
            sender.sendMessage(ChatColor.RED + "Tebex is not setup yet!");
            return true;
        }

        if (sender instanceof Player) {
            BuyGUI buyGUI = new BuyGUI(platform);
            if (args.length == 0) {
                buyGUI.open((Player) sender);
            } else {
                buyGUI.open((Player) sender, String.join(" ", args));
            }
            return true;
        }

        sender.sendMessage(ChatColor.RED + "The buy command cannot be used from the console.");
        return false;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length > 1) {
            return Collections.emptyList();
        }

        String input = args.length == 0 ? "" : args[0];
        return new BuyGUI(platform).getMenuSuggestions(input);
    }
}
