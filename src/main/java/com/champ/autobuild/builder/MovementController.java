package com.champ.autobuild.builder;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;





public class MovementController {
    public enum PathResult {
        MOVING, ARRIVED, STUCK, NO_PATH
    }

    public enum ScaffoldResult {
        PLACED, MOVED, PILLARING, FAILED
    }

    private final BuildConfig cfg;
    private final Set<BlockPos> scaffoldBlocks;

    private List<BlockPos> path;
    private int index;
    private Vec3d lastPos;
    private int stuckTicks;
    private int pillarTicks;
    private Block scaffoldItem;
    private BlockPos hopTarget;
    private int hopTicks;

    public MovementController(BuildConfig cfg, Set<BlockPos> scaffoldBlocks) {
        this.cfg = cfg;
        this.scaffoldBlocks = scaffoldBlocks;
    }

    public void setPath(List<BlockPos> path) {
        this.path = path;
        this.index = 0;
        this.lastPos = null;
        this.stuckTicks = 0;

    }

    public boolean hasPath() {
        return path != null && index < path.size();
    }

    public String describePath() {
        if (path == null) return "none";
        String node = index < path.size() ? path.get(index).toShortString() : "end";
        return node + "/" + path.size() + " stuck=" + stuckTicks;
    }

    public void clearPath() {
        path = null;
        index = 0;
        lastPos = null;
        stuckTicks = 0;
    }

    public PathResult tickPath() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || path == null || index >= path.size()) {
            releaseKeys();
            return PathResult.NO_PATH;
        }
        BlockPos node = path.get(index);
        
        if (client.world != null && (!client.world.getFluidState(node).isEmpty()
                || !client.world.getFluidState(node.up()).isEmpty())) {
            log("move: refusing wet path node {} fluid={} head={}", node,
                    client.world.getFluidState(node), client.world.getFluidState(node.up()));
            releaseKeys();
            clearPath();
            return PathResult.STUCK;
        }
        Vec3d center = Vec3d.ofCenter(node);
        double dx = center.x - client.player.getX();
        double dz = center.z - client.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (cfg.verbose) {
            log("move: tick node={} h={} dy={} onGround={}", node, String.format("%.2f", horizontal),
                    String.format("%.2f", client.player.getY() - node.getY()), client.player.isOnGround());
        }

        
        
        
        if (client.player.getBlockPos().equals(node)) {
            index++;
            if (index >= path.size()) {
                releaseKeys();
                return PathResult.ARRIVED;
            }
            node = path.get(index);
            center = Vec3d.ofCenter(node);
            dx = center.x - client.player.getX();
            dz = center.z - client.player.getZ();
            horizontal = Math.sqrt(dx * dx + dz * dz);
        }

        
        
        
        if (client.world != null && node.getY() <= client.player.getBlockY()) {
            BlockPos feet = client.player.getBlockPos();
            List<Direction> towards = new ArrayList<>(2);
            if (node.getX() != feet.getX()) {
                towards.add(node.getX() > feet.getX() ? Direction.EAST : Direction.WEST);
            }
            if (node.getZ() != feet.getZ()) {
                towards.add(node.getZ() > feet.getZ() ? Direction.SOUTH : Direction.NORTH);
            }
            if (towards.isEmpty()) towards.add(client.player.getHorizontalFacing());
            boolean open = false;
            for (Direction dir : towards) {
                BlockPos ahead = feet.offset(dir);
                if (!PathFinder.solid(client.world, ahead)
                        || !PathFinder.solid(client.world, ahead.up())) {
                    open = true;
                    break;
                }
            }
            if (!open) {
                log("move: wall in the way at {} (a player cannot walk through blocks)",
                        feet.offset(towards.get(0)));
                releaseMovement();
                clearPath();
                return PathResult.STUCK;
            }
        }

        lookHorizontally(center);
        press(client.options.forwardKey, true);
        boolean needsJump = node.getY() > client.player.getBlockY()
                || client.player.horizontalCollision;
        press(client.options.jumpKey, needsJump);
        press(client.options.sprintKey, horizontal > 4.0);
        press(client.options.sneakKey, false);

        Vec3d now = client.player.getEntityPos();
        if (lastPos != null && now.squaredDistanceTo(lastPos) < 1.0E-4) {
            stuckTicks++;
            if (stuckTicks > 60) {
                releaseKeys();
                clearPath();
                return PathResult.STUCK;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = now;
        return PathResult.MOVING;
    }

    





    public ScaffoldResult stepUp(BlockPos target, Placer placer) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !cfg.useScaffold) {
            return ScaffoldResult.FAILED;
        }
        World world = client.world;
        releaseMovement();
        
        if (!client.player.isOnGround()) return ScaffoldResult.PILLARING;
        BlockPos feet = client.player.getBlockPos();

        
        
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos beside = feet.offset(dir);
            if (PathFinder.solid(world, beside) && PathFinder.passable(world, beside.up())
                    && beside.getY() == feet.getY()) {
                hopOnto(beside);
                return ScaffoldResult.PILLARING;
            }
        }

        BlockStateAndItem block = scaffoldBlock(client);
        if (cfg.verbose) {
            BuilderLog.LOGGER.info(
                    "move: stepUp feet={} target={} onGround={} block={} hotbar={}",
                    feet, target, client.player.isOnGround(),
                    block == null ? "none" : net.minecraft.registry.Registries.BLOCK.getId(block.item()),
                    client.player.getInventory().getSelectedSlot());
        }
        if (block == null) return ScaffoldResult.FAILED;

        for (Direction dir : stepDirections(feet, target)) {
            BlockPos step = feet.offset(dir);
            if (!PathFinder.passable(world, step) || !PathFinder.passable(world, step.up())) continue;
            if (!world.getFluidState(step).isEmpty()) continue;
            
            
            if (touchesBody(step)) continue;
            if (!placer.canReach(step) || !placer.canPlaceAt(step)) continue;
            Placer.Ready ready = placer.aim(block.state(), step);
            if (ready == Placer.Ready.AIMING) return ScaffoldResult.PILLARING;
            if (ready == Placer.Ready.CLICK && placer.clickAimed(block.state(), step)) {
                scaffoldBlocks.add(step);
                if (cfg.verbose) {
                    BuilderLog.LOGGER.info(
                            "move: step {} at {} toward {}",
                            net.minecraft.registry.Registries.BLOCK.getId(block.item()), step, target);
                }
                return ScaffoldResult.PLACED;
            }
        }
        return ScaffoldResult.FAILED;
    }

    



    public void swimToward(BlockPos target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        lookHorizontally(Vec3d.ofCenter(target));
        press(client.options.forwardKey, true);
        press(client.options.jumpKey, true);
        press(client.options.sneakKey, false);
    }

    



    public boolean hopOnto(BlockPos step) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return false;
        
        
        
        if (client.player.getY() >= step.getY() + 1.0 - 0.02) {
            releaseMovement();
            hopTarget = null;
            return true;
        }
        if (!step.equals(hopTarget)) {
            hopTarget = step;
            hopTicks = 0;
        }
        if (hopTicks++ > 30) {
            releaseMovement();
            hopTarget = null;
            return false;
        }
        lookHorizontally(Vec3d.ofCenter(step));
        press(client.options.forwardKey, true);
        press(client.options.jumpKey, true);
        press(client.options.sneakKey, false);
        return false;
    }

    public void resetHop() {
        hopTarget = null;
        hopTicks = 0;
    }

    
    private static boolean touchesBody(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return client.player.getBoundingBox().intersects(new net.minecraft.util.math.Box(pos));
    }

    
    private static List<Direction> stepDirections(BlockPos feet, BlockPos target) {
        List<Direction> order = new ArrayList<>(4);
        int dx = target.getX() - feet.getX();
        int dz = target.getZ() - feet.getZ();
        if (Math.abs(dx) + Math.abs(dz) > 0) {
            order.add(Math.abs(dx) >= Math.abs(dz)
                    ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                    : (dz >= 0 ? Direction.SOUTH : Direction.NORTH));
        }
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (!order.contains(dir)) order.add(dir);
        }
        return order;
    }

    



    public ScaffoldResult scaffoldTowards(BlockPos target, Placer placer) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !cfg.useScaffold) {
            return ScaffoldResult.FAILED;
        }
        World world = client.world;
        BlockPos feet = client.player.getBlockPos();
        if (!client.player.isOnGround()) return ScaffoldResult.PILLARING;

        if (target.getY() > feet.getY()) {
            return stepUp(target, placer);
        }

        Direction dir = Math.abs(target.getX() - feet.getX()) >= Math.abs(target.getZ() - feet.getZ())
                ? (target.getX() > feet.getX() ? Direction.EAST : Direction.WEST)
                : (target.getZ() > feet.getZ() ? Direction.SOUTH : Direction.NORTH);
        BlockPos front = feet.offset(dir);

        if (!PathFinder.passable(world, front) || !PathFinder.passable(world, front.up())) {
            return ScaffoldResult.FAILED;
        }
        if (PathFinder.solid(world, front.down())) {
            lookHorizontally(Vec3d.ofCenter(front));
            press(client.options.forwardKey, true);
            return ScaffoldResult.MOVED;
        }

        
        BlockPos support = front.down();
        if (world.getBlockState(support).isAir() || world.getBlockState(support).isReplaceable()) {
            BlockStateAndItem block = scaffoldBlock(client);
            if (block != null && placer.canReach(support)) {
                Placer.Ready ready = placer.aim(block.state(), support);
                if (ready == Placer.Ready.AIMING) return ScaffoldResult.PILLARING;
                if (ready == Placer.Ready.CLICK && placer.clickAimed(block.state(), support)) {
                    scaffoldBlocks.add(support);
                    return ScaffoldResult.PLACED;
                }
            }
            return ScaffoldResult.FAILED;
        }
        press(client.options.forwardKey, true);
        return ScaffoldResult.MOVED;
    }

    private record BlockStateAndItem(net.minecraft.block.BlockState state, Block item) {
    }

    private BlockStateAndItem scaffoldBlock(MinecraftClient client) {
        if (client.player == null) return null;
        for (String id : cfg.scaffoldBlocks) {
            var identifier = net.minecraft.util.Identifier.tryParse(id);
            if (identifier == null) continue;
            Block block = net.minecraft.registry.Registries.BLOCK.get(identifier);
            if (block == net.minecraft.block.Blocks.AIR) continue;
            net.minecraft.item.Item item = block.asItem();
            for (int i = 0; i < 36; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == item) {
                    scaffoldItem = block;
                    return new BlockStateAndItem(block.getDefaultState(), block);
                }
            }
        }
        return null;
    }

    public Block scaffoldItem() {
        return scaffoldItem;
    }

    



    public Block tempBlockNow() {
        BlockStateAndItem block = scaffoldBlock(MinecraftClient.getInstance());
        return block == null ? null : block.item();
    }

    public void releaseKeys() {
        releaseMovement();
        MinecraftClient client = MinecraftClient.getInstance();
        press(client.options.sneakKey, false);
    }

    
    public void releaseMovement() {
        MinecraftClient client = MinecraftClient.getInstance();
        press(client.options.forwardKey, false);
        press(client.options.backKey, false);
        press(client.options.leftKey, false);
        press(client.options.rightKey, false);
        press(client.options.jumpKey, false);
        press(client.options.sprintKey, false);
    }

    private static void log(String message, Object... args) {
        BuilderLog.LOGGER.info(message, args);
    }

    private static void press(net.minecraft.client.option.KeyBinding key, boolean down) {
        key.setPressed(down);
    }

    private void lookAt(Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        Vec3d eye = client.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        client.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        client.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    private void lookHorizontally(Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        double dx = target.x - client.player.getX();
        double dz = target.z - client.player.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        client.player.setYaw(yaw);
        client.player.setPitch(15.0f);
    }
}
