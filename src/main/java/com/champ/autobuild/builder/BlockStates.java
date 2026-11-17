package com.champ.autobuild.builder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.Map;




public final class BlockStates {
    private BlockStates() {
    }

    public static BlockState parse(String name, Map<String, String> properties) {
        Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
        Block block = id == null ? null : Registries.BLOCK.get(id);
        if (block == null) return null;
        BlockState state = block.getDefaultState();
        if (properties == null || properties.isEmpty()) return state;
        for (Map.Entry<String, String> e : properties.entrySet()) {
            Property<?> property = block.getStateManager().getProperty(e.getKey());
            if (property == null) continue;
            state = with(state, property, e.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.parse(value).map(v -> state.with(property, v)).orElse(state);
    }
}
