package com.champ.autobuild;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class AutoBuildMod implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY =
        KeyBinding.Category.create(Identifier.of("champ-auto-build", "main"));
    private static KeyBinding openKey;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        AutoBuildConfig.load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.champ-auto-build.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F10, CATEGORY));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.champ-auto-build.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F11, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        while (openKey.wasPressed()) client.setScreen(new AutoBuildScreen());
        while (toggleKey.wasPressed()) AutoBuildController.toggle(client);
        AutoBuildController.tick(client);
    }
}
