package io.tebex.plugin.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.tebex.plugin.FabricPluginPlatform;
import io.tebex.plugin.gui.BuyGUI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

public class BuyCommand {
    private final FabricPluginPlatform platform;

    public BuyCommand(FabricPluginPlatform platform) {
        this.platform = platform;
    }

    public int execute(CommandContext<CommandSourceStack> context) {
        return execute(context.getSource(), null);
    }

    public int executeWithMenu(CommandContext<CommandSourceStack> context) {
        return execute(context.getSource(), StringArgumentType.getString(context, "menu"));
    }

    public CompletableFuture<Suggestions> suggestMenus(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        new BuyGUI(platform).getMenuSuggestions(builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private int execute(CommandSourceStack source, String menu) {
        if (!platform.isSetup()) {
            source.sendSystemMessage(Component.nullToEmpty("Â§cTebex is not setup yet!"));
            return 1;
        }

        try {
            ServerPlayer player = source.getPlayer();
            BuyGUI buyGUI = new BuyGUI(platform);
            if (menu == null || menu.trim().isEmpty()) {
                buyGUI.open(player);
            } else {
                buyGUI.open(player, menu);
            }
        } catch (Exception e) {
            e.printStackTrace();
            source.sendSystemMessage(Component.nullToEmpty("Â§b[Tebex] Â§7You must be a player to run this command!"));
        }

        return 1;
    }
}
