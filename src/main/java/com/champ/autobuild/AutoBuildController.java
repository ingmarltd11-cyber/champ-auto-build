package com.champ.autobuild;

import com.champ.autobuild.builder.BuildConfig;
import com.champ.autobuild.builder.BuildEngine;
import com.champ.autobuild.builder.BuilderLog;
import com.champ.autobuild.builder.LitematicaLoader;
import com.champ.autobuild.builder.Schematic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class AutoBuildController {
    private static BuildEngine engine;
    private static Schematic loaded;
    private static BlockPos origin;
    private static int rounds;
    private static int retryTicks;
    private static boolean active;
    private static String status = "Load a .litematic or .schem file";

    private AutoBuildController() {
    }

    public static boolean isActive() {
        return active;
    }

    public static String status() {
        if (engine == null) return status;
        return engine.state().name().toLowerCase() + " | " + engine.placed() + "/" + engine.total()
            + " placed | " + engine.remaining() + " left";
    }

    public static void toggle(MinecraftClient client) {
        if (active) stop(client, "Stopped");
        else start(client);
    }

    public static void start(MinecraftClient client) {
        if (active) stop(client, "Restarted");
        if (client.player == null || client.world == null) {
            status = "Join a world first";
            return;
        }
        String raw = AutoBuildConfig.schematic;
        if (raw == null || raw.isBlank()) {
            status = "Set a schematic path first";
            notify(client, status);
            return;
        }
        try {
            Path file = LitematicaLoader.resolve(raw);
            if (!Files.isRegularFile(file)) {
                status = "Schematic not found: " + raw;
                notify(client, status);
                return;
            }
            Schematic schematic = LitematicaLoader.load(file);
            BuildConfig config = config();
            BlockPos anchor = client.player.getBlockPos().add(
                -schematic.getSize().getX() / 2, 0, -schematic.getSize().getZ() / 2);
            List<Schematic.Target> targets = schematic.targets(client.world, anchor, config.replaceExisting);
            if (targets.isEmpty()) {
                status = "Nothing to build: " + schematic.getName() + " is already there";
                notify(client, status);
                return;
            }
            BuildEngine next = new BuildEngine(config);
            next.setAllowCommands(AutoBuildConfig.commandFallback && canUseCommands(client));
            next.start(targets);
            engine = next;
            loaded = schematic;
            origin = anchor;
            rounds = 0;
            retryTicks = 0;
            active = true;
            status = "Building " + schematic.getName();
            notify(client, "Building " + schematic.getName() + ": " + targets.size() + " blocks");
        } catch (Throwable error) {
            engine = null;
            loaded = null;
            active = false;
            status = "Could not load " + raw + ": " + error.getMessage();
            notify(client, status);
            BuilderLog.LOGGER.warn("schematic load failed: {}", raw, error);
        }
    }

    public static void stop(MinecraftClient client, String reason) {
        if (engine != null) {
            try {
                engine.stop();
            } catch (Throwable ignored) {
            }
        }
        engine = null;
        loaded = null;
        active = false;
        rounds = 0;
        retryTicks = 0;
        status = reason;
        AutoBuildConfig.save();
        notify(client, "Auto Build: " + reason);
    }

    public static void tick(MinecraftClient client) {
        if (!active || engine == null) return;
        if (client.player == null || client.world == null) {
            stop(client, "World unloaded");
            return;
        }
        try {
            engine.tick();
        } catch (Throwable error) {
            BuilderLog.LOGGER.warn("engine tick failed", error);
            stop(client, "Stopped after an error: " + error.getClass().getSimpleName());
            return;
        }
        BuildEngine.State state = engine.state();
        if (state == BuildEngine.State.WAITING_MATERIALS) {
            status = "Waiting for materials";
            if (++retryTicks > 40) {
                retryTicks = 0;
                engine.resume();
            }
            return;
        }
        retryTicks = 0;
        if (state == BuildEngine.State.DONE) continueRounds(client);
        if (state == BuildEngine.State.ERROR || state == BuildEngine.State.STOPPED) {
            stop(client, "Builder stopped");
        }
    }

    private static void continueRounds(MinecraftClient client) {
        if (engine == null) return;
        List<Schematic.Target> leftovers = new ArrayList<>(engine.pendingTargets());
        leftovers.removeIf(target -> !Schematic.isPlaceable(target.state()));
        if (rounds < AutoBuildConfig.maxRounds && !leftovers.isEmpty()) {
            rounds++;
            engine.continueWith(leftovers);
            status = "Round " + (rounds + 1) + ": " + leftovers.size() + " blocks left";
            notify(client, status);
            return;
        }
        String result = "Build finished: " + engine.placed() + " placed, " + engine.failed() + " skipped";
        if (!leftovers.isEmpty()) result += ", " + leftovers.size() + " could not be placed";
        status = result;
        notify(client, result);
        engine = null;
        loaded = null;
        active = false;
        rounds = 0;
        AutoBuildConfig.save();
    }

    private static BuildConfig config() {
        BuildConfig config = new BuildConfig();
        config.reach = AutoBuildConfig.reach;
        config.replaceExisting = AutoBuildConfig.replaceExisting;
        config.breakObstructions = AutoBuildConfig.breakObstructions;
        config.useScaffold = AutoBuildConfig.tempBlocks;
        config.cleanupScaffold = AutoBuildConfig.tempBlocks && AutoBuildConfig.cleanupTempBlocks;
        config.humanMouse = AutoBuildConfig.smoothAim;
        config.humanClickDelay = AutoBuildConfig.clickDelay;
        config.stuckTicks = AutoBuildConfig.stuckTicks;
        config.maxBuildRounds = AutoBuildConfig.maxRounds;
        config.verbose = AutoBuildConfig.verbose;
        List<String> materials = new ArrayList<>();
        if (AutoBuildConfig.tempMaterials != null) {
            for (String part : AutoBuildConfig.tempMaterials.split(",")) {
                String id = part.trim();
                if (!id.isEmpty()) materials.add(id);
            }
        }
        if (materials.isEmpty()) materials.add("minecraft:dirt");
        config.scaffoldBlocks = materials;
        return config;
    }

    private static boolean canUseCommands(MinecraftClient client) {
        return client.player != null && client.isInSingleplayer()
            && client.player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS);
    }

    private static void notify(MinecraftClient client, String message) {
        if (!AutoBuildConfig.notifications || client.player == null) return;
        client.player.sendMessage(Text.literal(message), false);
    }

    public static String fileName() {
        return loaded == null ? "" : loaded.getName();
    }

    public static BlockPos anchor() {
        return origin;
    }
}
