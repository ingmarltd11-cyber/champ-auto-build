package com.champ.autobuild.builder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;

import java.util.List;






public final class Tools {
    public enum Kind {
        SHOVEL, PICKAXE, AXE, HOE, SHEARS, SWORD, NONE
    }

    
    private static final List<Item> SHOVELS = List.of(Items.NETHERITE_SHOVEL, Items.DIAMOND_SHOVEL,
            Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL, Items.STONE_SHOVEL, Items.WOODEN_SHOVEL);
    private static final List<Item> PICKAXES = List.of(Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE,
            Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE);
    private static final List<Item> AXES = List.of(Items.NETHERITE_AXE, Items.DIAMOND_AXE,
            Items.IRON_AXE, Items.GOLDEN_AXE, Items.STONE_AXE, Items.WOODEN_AXE);
    private static final List<Item> HOES = List.of(Items.NETHERITE_HOE, Items.DIAMOND_HOE,
            Items.IRON_HOE, Items.GOLDEN_HOE, Items.STONE_HOE, Items.WOODEN_HOE);
    private static final List<Item> SWORDS = List.of(Items.NETHERITE_SWORD, Items.DIAMOND_SWORD,
            Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD);

    private Tools() {
    }

    
    public static Kind kindFor(BlockState state) {
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.WOOL)
                || state.isOf(Blocks.COBWEB) || state.isOf(Blocks.VINE)) {
            return Kind.SHEARS;
        }
        if (state.isIn(BlockTags.SHOVEL_MINEABLE)) return Kind.SHOVEL;
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) return Kind.PICKAXE;
        if (state.isIn(BlockTags.AXE_MINEABLE)) return Kind.AXE;
        if (state.isIn(BlockTags.HOE_MINEABLE)) return Kind.HOE;
        if (state.isIn(BlockTags.SWORD_EFFICIENT)) return Kind.SWORD;
        return Kind.NONE;
    }

    public static List<Item> options(Kind kind) {
        return switch (kind) {
            case SHOVEL -> SHOVELS;
            case PICKAXE -> PICKAXES;
            case AXE -> AXES;
            case HOE -> HOES;
            case SWORD -> SWORDS;
            case SHEARS -> List.of(Items.SHEARS);
            case NONE -> List.of();
        };
    }

    
    public static Item bestInInventory(Kind kind, ClientPlayerEntity player) {
        if (player == null || kind == Kind.NONE) return null;
        for (Item candidate : options(kind)) {
            if (InvUtil.count(player, candidate) > 0) return candidate;
        }
        return null;
    }

    
    public static Item itemFor(Kind kind, String tier) {
        String t = tier == null ? "iron" : tier.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (kind) {
            case SHOVEL -> switch (t) {
                case "wood", "wooden" -> Items.WOODEN_SHOVEL;
                case "stone" -> Items.STONE_SHOVEL;
                case "gold", "golden" -> Items.GOLDEN_SHOVEL;
                case "diamond" -> Items.DIAMOND_SHOVEL;
                case "netherite" -> Items.NETHERITE_SHOVEL;
                default -> Items.IRON_SHOVEL;
            };
            case PICKAXE -> switch (t) {
                case "wood", "wooden" -> Items.WOODEN_PICKAXE;
                case "stone" -> Items.STONE_PICKAXE;
                case "gold", "golden" -> Items.GOLDEN_PICKAXE;
                case "diamond" -> Items.DIAMOND_PICKAXE;
                case "netherite" -> Items.NETHERITE_PICKAXE;
                default -> Items.IRON_PICKAXE;
            };
            case AXE -> switch (t) {
                case "wood", "wooden" -> Items.WOODEN_AXE;
                case "stone" -> Items.STONE_AXE;
                case "gold", "golden" -> Items.GOLDEN_AXE;
                case "diamond" -> Items.DIAMOND_AXE;
                case "netherite" -> Items.NETHERITE_AXE;
                default -> Items.IRON_AXE;
            };
            case HOE -> switch (t) {
                case "wood", "wooden" -> Items.WOODEN_HOE;
                case "stone" -> Items.STONE_HOE;
                case "gold", "golden" -> Items.GOLDEN_HOE;
                case "diamond" -> Items.DIAMOND_HOE;
                case "netherite" -> Items.NETHERITE_HOE;
                default -> Items.IRON_HOE;
            };
            case SWORD -> switch (t) {
                case "wood", "wooden" -> Items.WOODEN_SWORD;
                case "stone" -> Items.STONE_SWORD;
                case "gold", "golden" -> Items.GOLDEN_SWORD;
                case "diamond" -> Items.DIAMOND_SWORD;
                case "netherite" -> Items.NETHERITE_SWORD;
                default -> Items.IRON_SWORD;
            };
            case SHEARS -> Items.SHEARS;
            case NONE -> null;
        };
    }

    
    public static boolean isToolFor(Kind kind, ItemStack stack) {
        return !stack.isEmpty() && options(kind).contains(stack.getItem());
    }
}
