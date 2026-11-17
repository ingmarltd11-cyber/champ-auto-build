package com.champ.autobuild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class AutoBuildConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static String schematic = "";
    public static boolean replaceExisting;
    public static boolean breakObstructions;
    public static boolean tempBlocks = true;
    public static boolean cleanupTempBlocks = true;
    public static String tempMaterials = "minecraft:dirt";
    public static float reach = 4.5F;
    public static boolean smoothAim = true;
    public static int clickDelay = 1;
    public static int stuckTicks = 240;
    public static int maxRounds = 6;
    public static boolean commandFallback;
    public static boolean notifications = true;
    public static boolean verbose;

    private AutoBuildConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("champ-auto-build.json");
    }

    public static void load() {
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) return;
            Data data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
            if (data == null) return;
            schematic = data.schematic == null ? "" : data.schematic;
            replaceExisting = data.replaceExisting;
            breakObstructions = data.breakObstructions;
            tempBlocks = data.tempBlocks;
            cleanupTempBlocks = data.cleanupTempBlocks;
            tempMaterials = data.tempMaterials == null ? "minecraft:dirt" : data.tempMaterials;
            reach = Math.max(2.0F, Math.min(6.0F, data.reach));
            smoothAim = data.smoothAim;
            clickDelay = Math.max(0, Math.min(10, data.clickDelay));
            stuckTicks = Math.max(40, Math.min(1200, data.stuckTicks));
            maxRounds = Math.max(1, Math.min(20, data.maxRounds));
            commandFallback = data.commandFallback;
            notifications = data.notifications;
            verbose = data.verbose;
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(new Data()), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }

    private static final class Data {
        String schematic = AutoBuildConfig.schematic;
        boolean replaceExisting = AutoBuildConfig.replaceExisting;
        boolean breakObstructions = AutoBuildConfig.breakObstructions;
        boolean tempBlocks = AutoBuildConfig.tempBlocks;
        boolean cleanupTempBlocks = AutoBuildConfig.cleanupTempBlocks;
        String tempMaterials = AutoBuildConfig.tempMaterials;
        float reach = AutoBuildConfig.reach;
        boolean smoothAim = AutoBuildConfig.smoothAim;
        int clickDelay = AutoBuildConfig.clickDelay;
        int stuckTicks = AutoBuildConfig.stuckTicks;
        int maxRounds = AutoBuildConfig.maxRounds;
        boolean commandFallback = AutoBuildConfig.commandFallback;
        boolean notifications = AutoBuildConfig.notifications;
        boolean verbose = AutoBuildConfig.verbose;
    }
}
