package com.champ.autobuild.builder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;




public class Schematic {
    public record Entry(BlockPos rel, BlockState state) {
    }

    public record Target(BlockPos pos, BlockState state) {
    }

    private final String name;
    private final BlockPos size;
    private final List<Entry> entries;

    public Schematic(String name, BlockPos size, List<Entry> entries) {
        this.name = name;
        this.size = size;
        this.entries = entries;
    }

    public String getName() {
        return name;
    }

    public BlockPos getSize() {
        return size;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int volume() {
        return Math.abs(size.getX() * size.getY() * size.getZ());
    }

    




    public static Item placementItem(BlockState state) {
        return placementItem(state.getBlock());
    }

    public static Item placementItem(Block block) {
        if (block == Blocks.WATER) return Items.WATER_BUCKET;
        if (block == Blocks.LAVA) return Items.LAVA_BUCKET;
        if (block == Blocks.KELP || block == Blocks.KELP_PLANT) return Items.KELP;
        if (block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS) return Items.SEAGRASS;
        return block.asItem();
    }

    



    public static boolean isPlaceable(BlockState state) {
        return !state.isAir() && placementItem(state) != Items.AIR;
    }

    





    public static boolean sameBlock(Block have, Block want) {
        if (have == want) return true;
        boolean kelp = (have == Blocks.KELP || have == Blocks.KELP_PLANT)
                && (want == Blocks.KELP || want == Blocks.KELP_PLANT);
        if (kelp) return true;
        return (have == Blocks.SEAGRASS || have == Blocks.TALL_SEAGRASS)
                && (want == Blocks.SEAGRASS || want == Blocks.TALL_SEAGRASS);
    }

    
    public static boolean needsWaterToPlant(BlockState state) {
        return state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT)
                || state.isOf(Blocks.SEAGRASS) || state.isOf(Blocks.TALL_SEAGRASS);
    }

    
    public int unplaceableBlocks() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.state().isAir()) continue;
            if (!isPlaceable(entry.state())) count++;
        }
        return count;
    }

    




    public List<Target> targets(BlockView world, BlockPos origin, boolean replaceExisting) {
        List<Target> out = new ArrayList<>();
        for (Entry entry : entries) {
            BlockState want = entry.state();
            if (want.isAir()) continue;
            
            if (!isPlaceable(want)) continue;
            BlockPos pos = origin.add(entry.rel());
            BlockState have = world.getBlockState(pos);
            
            if (sameBlock(have.getBlock(), want.getBlock())) continue;
            if (!replaceExisting && !have.isAir() && !have.isReplaceable()) continue;
            out.add(new Target(pos, want));
        }
        return out;
    }

    
    public Map<Item, Integer> materials() {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (!isPlaceable(entry.state())) continue;
            out.merge(placementItem(entry.state()), 1, Integer::sum);
        }
        return out;
    }

    
    public static Map<Block, Integer> counts(List<Target> targets) {
        Map<Block, Integer> map = new LinkedHashMap<>();
        for (Target target : targets) {
            if (!isPlaceable(target.state())) continue;
            map.merge(target.state().getBlock(), 1, Integer::sum);
        }
        return map;
    }

    



    public List<Target> missing(BlockView world, BlockPos origin) {
        List<Target> out = new ArrayList<>();
        for (Entry entry : entries) {
            BlockPos pos = origin.add(entry.rel());
            if (!sameBlock(world.getBlockState(pos).getBlock(), entry.state().getBlock())) {
                out.add(new Target(pos, entry.state()));
            }
        }
        return out;
    }

    
    public List<BlockPos> obstructions(BlockView world, BlockPos origin) {
        List<BlockPos> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.state().isAir()) continue;
            BlockPos pos = origin.add(entry.rel());
            BlockState have = world.getBlockState(pos);
            if (!have.isAir() && !have.isReplaceable() && !have.equals(entry.state())) {
                out.add(pos);
            }
        }
        return out;
    }
}
