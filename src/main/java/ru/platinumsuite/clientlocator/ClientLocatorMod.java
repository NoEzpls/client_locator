package ru.platinumsuite.clientlocator;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Registers the server-only player location command. */
@Mod(ClientLocatorMod.MOD_ID)
public final class ClientLocatorMod {
    public static final String MOD_ID = "client_locator";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_PATH = Path.of("config", "client-locator.properties");
    private static final String ALLOWED_UUIDS_KEY = "allowed-uuids";
    private static Set<UUID> allowedPlayerUuids = Set.of();

    public ClientLocatorMod() {
        allowedPlayerUuids = loadAllowedPlayerUuids();
        NeoForge.EVENT_BUS.addListener(ClientLocatorMod::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Access is based on UUID instead of the OP level. This lets the owner
        // use the command without OP while keeping it hidden from other players.
        dispatcher.register(Commands.literal("playerlocate")
                .requires(ClientLocatorMod::isAllowedPlayer)
                .then(playerArgument()));
    }

    private static boolean isAllowedPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                && allowedPlayerUuids.contains(player.getUUID());
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

    private static Set<UUID> loadAllowedPlayerUuids() {
        Properties properties = new Properties();

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.notExists(CONFIG_PATH)) {
                properties.setProperty(ALLOWED_UUIDS_KEY, "");
                try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                    properties.store(writer, "Comma-separated UUIDs allowed to use /playerlocate");
                }
                LOGGER.warn("Created {}. Add your UUID to '{}' and restart the server.",
                        CONFIG_PATH, ALLOWED_UUIDS_KEY);
                return Set.of();
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                properties.load(reader);
            }
        } catch (IOException exception) {
            LOGGER.error("Could not read Client Locator config {}; access is disabled", CONFIG_PATH, exception);
            return Set.of();
        }

        Set<UUID> result = new HashSet<>();
        Arrays.stream(properties.getProperty(ALLOWED_UUIDS_KEY, "").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(value -> addUuid(result, value));

        LOGGER.info("Loaded {} allowed player UUID(s) for Client Locator", result.size());
        return Collections.unmodifiableSet(result);
    }

    private static void addUuid(Set<UUID> result, String value) {
        try {
            result.add(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Ignoring invalid UUID '{}' in {}", value, CONFIG_PATH);
        }
    }
}
