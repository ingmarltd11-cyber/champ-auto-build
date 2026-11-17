package com.champ.autobuild;

import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class AutoBuildScreen extends Screen {
    private static final int ACCENT = 0xFFF2A93B;
    private static final int TEXT = 0xFFF7F2E8;
    private static final int MUTED = 0xFFB8AD9C;
    private static final int PANEL = 0xFF1B1710;
    private static final int BORDER = 0xFF463A26;

    private TextFieldWidget schematicField;
    private ButtonWidget activeButton;
    private ButtonWidget replaceButton;
    private ButtonWidget breakButton;
    private ButtonWidget tempButton;
    private ButtonWidget cleanupButton;
    private ButtonWidget smoothButton;
    private ButtonWidget commandButton;

    public AutoBuildScreen() {
        super(Text.literal("Champ Auto Build"));
    }

    @Override
    protected void init() {
        int panelW = Math.min(411, width - 12);
        int panelH = 232;
        int x = width / 2 - panelW / 2;
        int y = (height - panelH) / 2;
        schematicField = addDrawableChild(new TextFieldWidget(this.textRenderer, x + 20, y + 32, panelW - 40, 18,
            Text.literal("schematics/build.litematic")));
        schematicField.setMaxLength(512);
        schematicField.setText(AutoBuildConfig.schematic);
        schematicField.setChangedListener(value -> AutoBuildConfig.schematic = value);

        int half = (panelW - 46) / 2;
        activeButton = addDrawableChild(ButtonWidget.builder(Text.literal("Load & Start"), button -> {
            AutoBuildConfig.schematic = schematicField.getText();
            AutoBuildController.start(client);
            refresh();
        }).dimensions(x + 20, y + 53, half, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), button -> {
            AutoBuildController.stop(client, "Stopped");
            refresh();
        }).dimensions(x + 26 + half, y + 53, half, 18).build());

        int gap = 6;
        int toggleW = (panelW - 40 - gap) / 2;
        int row1 = y + 90;
        int row2 = row1 + 19;
        int row3 = row2 + 19;
        replaceButton = addDrawableChild(ButtonWidget.builder(Text.literal("Replace Existing"), button -> {
            AutoBuildConfig.replaceExisting = !AutoBuildConfig.replaceExisting;
            refresh();
        }).dimensions(x + 20, row1, toggleW, 16).build());
        breakButton = addDrawableChild(ButtonWidget.builder(Text.literal("Break Obstructions"), button -> {
            AutoBuildConfig.breakObstructions = !AutoBuildConfig.breakObstructions;
            refresh();
        }).dimensions(x + 20 + toggleW + gap, row1, toggleW, 16).build());
        tempButton = addDrawableChild(ButtonWidget.builder(Text.literal("Temp Blocks"), button -> {
            AutoBuildConfig.tempBlocks = !AutoBuildConfig.tempBlocks;
            refresh();
        }).dimensions(x + 20, row2, toggleW, 16).build());
        cleanupButton = addDrawableChild(ButtonWidget.builder(Text.literal("Clean Up Temp"), button -> {
            AutoBuildConfig.cleanupTempBlocks = !AutoBuildConfig.cleanupTempBlocks;
            refresh();
        }).dimensions(x + 20 + toggleW + gap, row2, toggleW, 16).build());
        smoothButton = addDrawableChild(ButtonWidget.builder(Text.literal("Smooth Aim"), button -> {
            AutoBuildConfig.smoothAim = !AutoBuildConfig.smoothAim;
            refresh();
        }).dimensions(x + 20, row3, toggleW, 16).build());
        commandButton = addDrawableChild(ButtonWidget.builder(Text.literal("Command Fallback"), button -> {
            AutoBuildConfig.commandFallback = !AutoBuildConfig.commandFallback;
            refresh();
        }).dimensions(x + 20 + toggleW + gap, row3, toggleW, 16).build());

        addDrawableChild(new FloatSlider(x + 20, y + 148, toggleW, 16,
            "Reach", AutoBuildConfig.reach, 2.0F, 6.0F, value -> AutoBuildConfig.reach = value));
        addDrawableChild(new IntSlider(x + 20 + toggleW + gap, y + 148, toggleW, 16,
            "Click delay", AutoBuildConfig.clickDelay, 0, 10, value -> AutoBuildConfig.clickDelay = value));
        addDrawableChild(new IntSlider(x + 20, y + 166, toggleW, 16,
            "Max rounds", AutoBuildConfig.maxRounds, 1, 20, value -> AutoBuildConfig.maxRounds = value));
        addDrawableChild(new IntSlider(x + 20 + toggleW + gap, y + 166, toggleW, 16,
            "Stuck ticks", AutoBuildConfig.stuckTicks, 40, 1200, value -> AutoBuildConfig.stuckTicks = value));
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
            .dimensions(x + 20, y + 190, panelW - 40, 18).build());
        refresh();
    }

    private void refresh() {
        activeButton.setMessage(Text.literal(AutoBuildController.isActive() ? "Rebuild" : "Load & Start"));
        replaceButton.setMessage(Text.literal((AutoBuildConfig.replaceExisting ? "[x] " : "[ ] ") + "Replace Existing"));
        breakButton.setMessage(Text.literal((AutoBuildConfig.breakObstructions ? "[x] " : "[ ] ") + "Break Obstructions"));
        tempButton.setMessage(Text.literal((AutoBuildConfig.tempBlocks ? "[x] " : "[ ] ") + "Temp Blocks"));
        cleanupButton.setMessage(Text.literal((AutoBuildConfig.cleanupTempBlocks ? "[x] " : "[ ] ") + "Clean Up Temp"));
        smoothButton.setMessage(Text.literal((AutoBuildConfig.smoothAim ? "[x] " : "[ ] ") + "Smooth Aim"));
        commandButton.setMessage(Text.literal((AutoBuildConfig.commandFallback ? "[x] " : "[ ] ") + "Command Fallback"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderBackground(context, mouseX, mouseY, deltaTicks);
        int panelW = Math.min(411, width - 12);
        int panelH = 232;
        int x = width / 2 - panelW / 2;
        int y = (height - panelH) / 2;
        context.fill(x, y, x + panelW, y + panelH, PANEL);
        context.fill(x, y, x + panelW, y + 2, ACCENT);
        context.fill(x, y + panelH - 1, x + panelW, y + panelH, BORDER);
        context.drawText(this.textRenderer, this.title, x + 20, y + 8, TEXT, false);
        context.drawText(this.textRenderer, Text.literal("Litematica builder - F10 opens, F11 toggles"),
            x + 20, y + 19, MUTED, false);
        context.drawText(this.textRenderer, Text.literal(shorten(AutoBuildController.status(), 58)),
            x + 20, y + 76, MUTED, false);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private static String shorten(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, length - 3) + "...";
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0xA6000000);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        AutoBuildConfig.schematic = schematicField.getText();
        AutoBuildConfig.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static final class FloatSlider extends SliderWidget {
        private final String label;
        private final float min;
        private final float max;
        private final Consumer<Float> setter;

        private FloatSlider(int x, int y, int w, int h, String label, float current, float min, float max,
                            Consumer<Float> setter) {
            super(x, y, w, h, Text.empty(), (current - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private float current() {
            return min + (float) value * (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + String.format(java.util.Locale.ROOT, "%.1f", current())));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }

    private static final class IntSlider extends SliderWidget {
        private final String label;
        private final int min;
        private final int max;
        private final Consumer<Integer> setter;

        private IntSlider(int x, int y, int w, int h, String label, int current, int min, int max,
                          Consumer<Integer> setter) {
            super(x, y, w, h, Text.empty(), (current - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
