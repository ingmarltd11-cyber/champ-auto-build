package com.champ.autobuild.builder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;




public final class PathFinder {
    private PathFinder() {
    }

    private static class Node implements Comparable<Node> {
        final BlockPos pos;
        final Node parent;
        final double g;
        final double f;

        Node(BlockPos pos, Node parent, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(f, other.f);
        }
    }

    public static List<BlockPos> find(BlockPos start, BlockPos goalHint, Predicate<BlockPos> goal, int maxNodes) {
        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null) return null;

        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<BlockPos, Double> best = new HashMap<>();
        Node startNode = new Node(start, null, 0, heuristic(start, goalHint));
        open.add(startNode);
        best.put(start, 0d);
        int expanded = 0;

        while (!open.isEmpty() && expanded < maxNodes) {
            Node node = open.poll();
            Double known = best.get(node.pos);
            if (known != null && node.g > known + 1e-6) continue;
            expanded++;

            if (goal.test(node.pos)) {
                return reconstruct(node);
            }

            for (Node next : neighbours(world, node)) {
                Double old = best.get(next.pos);
                if (old != null && next.g >= old) continue;
                best.put(next.pos, next.g);
                open.add(new Node(next.pos, next.parent, next.g, next.g + heuristic(next.pos, goalHint)));
            }
        }
        return null;
    }

    private static double heuristic(BlockPos pos, BlockPos goal) {
        return Math.abs(pos.getX() - goal.getX())
                + Math.abs(pos.getZ() - goal.getZ())
                + Math.abs(pos.getY() - goal.getY()) * 2.0;
    }

    private static List<BlockPos> reconstruct(Node node) {
        List<BlockPos> path = new ArrayList<>();
        for (Node n = node; n != null; n = n.parent) path.add(n.pos);
        Collections.reverse(path);
        
        
        
        if (!path.isEmpty()) path.remove(0);
        return path;
    }

    private static List<Node> neighbours(World world, Node node) {
        List<Node> out = new ArrayList<>(6);
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos base = node.pos.offset(dir);
            if (standable(world, base)) {
                
                
                out.add(child(node, base, 1.0 + (touchesFluid(world, base) ? 0.8 : 0)));
                continue;
            }
            BlockPos up = base.up();
            
            
            
            if (standable(world, up)) {
                out.add(child(node, up, 1.4));
                continue;
            }
            if (!passable(world, base)) continue;
            
            for (int drop = 1; drop <= 4; drop++) {
                BlockPos below = base.down(drop);
                if (standable(world, below)) {
                    out.add(child(node, below, 1.0 + drop * 0.35));
                    break;
                }
                if (!passable(world, below)) break;
            }
        }
        return out;
    }

    
    private static boolean touchesFluid(World world, BlockPos pos) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (!world.getFluidState(pos.offset(dir)).isEmpty()) return true;
        }
        return false;
    }

    private static Node child(Node parent, BlockPos pos, double cost) {
        return new Node(pos, parent, parent.g + cost, 0);
    }

    public static boolean standable(World world, BlockPos pos) {
        return passable(world, pos) && passable(world, pos.up()) && solid(world, pos.down());
    }

    




    public static boolean passable(World world, BlockPos pos) {
        var state = world.getBlockState(pos);
        if (!state.getCollisionShape(world, pos).isEmpty()) return false;
        return world.getFluidState(pos).isEmpty();
    }

    
    public static boolean open(World world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    public static boolean solid(World world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return !state.getCollisionShape(world, pos).isEmpty();
    }
}
