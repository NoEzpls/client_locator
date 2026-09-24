package ru.platinumsuite.clientlocator;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/** Registers the server-only player location command. */
@Mod(ClientLocatorMod.MOD_ID)
public final class ClientLocatorMod {
    public static final String MOD_ID = "client_locator";

    public ClientLocatorMod() {
        NeoForge.EVENT_BUS.addListener(ClientLocatorMod::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // This is the public command. It deliberately has no permission
        // requirement, so every player can use it without being an operator.
        dispatcher.register(Commands.literal("playerlocate")
                .then(playerArgument()));

        // Brigadier merges this node with Minecraft's existing /locate node. The
        // vanilla biome/structure/poi branches therefore continue to work. The
        // vanilla /locate root itself requires operator permissions, so this is
        // only a compatibility alias; non-operators use /playerlocate.
        dispatcher.register(Commands.literal("locate")
                .then(playerArgument()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> playerArgument() {
        return Commands.argument("player", EntityArgument.player())
                .executes(context -> locatePlayer(
                        context.getSource(),
                        EntityArgument.getPlayer(context, "player")
                ));
    }

    private static int locatePlayer(CommandSourceStack source, ServerPlayer player) {
        BlockPos blockPos = player.blockPosition();
        String dimension = player.level().dimension().identifier().toString();
        String precisePosition = String.format(
                Locale.ROOT,
                "%.2f, %.2f, %.2f",
                player.getX(),
                player.getY(),
                player.getZ()
        );

        Component result = Component.literal("Игрок ")
                .append(player.getDisplayName())
                .append(Component.literal(" находится в измерении " + dimension
                        + " на координатах " + precisePosition
                        + " (блок " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ() + ")"));

        source.sendSuccess(() -> result, false);
        return 1;
    }
}
