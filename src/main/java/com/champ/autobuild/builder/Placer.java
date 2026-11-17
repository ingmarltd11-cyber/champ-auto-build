package com.champ.autobuild.builder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;




public class Placer {
    public record Face(BlockPos neighbour, Direction side, Vec3d hit) {
    }

    private final BuildConfig cfg;
    private Block missing;
    private Item missingTool;
    private BlockPos breaking;
    private int breakTicks;

    public Placer(BuildConfig cfg) {
        this.cfg = cfg;
    }

    public Block missingBlock() {
        return missing;
    }

    public void clearMissing() {
        missing = null;
    }

    
    public Item missingTool() {
        return missingTool;
    }

    public void clearMissingTool() {
        missingTool = null;
    }

    public boolean canReach(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        Vec3d eye = client.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        return eye.squaredDistanceTo(center) <= cfg.reach * cfg.reach;
    }

    
    public Face findFace(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.offset(dir);
            BlockState state = client.world.getBlockState(neighbour);
            if (state.isAir()) continue;
            if (state.getCollisionShape(client.world, neighbour).isEmpty()) continue;
            Vec3d aim = facePoint(pos, dir);
            return new Face(neighbour, dir.getOpposite(), aim);
        }
        return null;
    }

    




    public Face findVisibleFace(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return null;
        double reach = Math.max(2.0, cfg.reach);
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.offset(dir);
            if (!hasFace(client, pos, dir)) continue;
            Vec3d aim = facePoint(pos, dir);
            lookAt(aim);
            if (hitsFace(client, client.player.getEyePos(), pos, dir, aim)) {
                return new Face(neighbour, dir.getOpposite(), aim);
            }
        }
        return null;
    }

    
    public BlockPos vantageFor(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return null;
        for (Direction horizontal : Direction.Type.HORIZONTAL) {
            for (int dist = 2; dist <= 4; dist++) {
                BlockPos base = pos.offset(horizontal, dist);
                for (int drop = 0; drop <= 5; drop++) {
                    BlockPos stand = base.down(drop);
                    if (!PathFinder.standable(client.world, stand)) continue;
                    Vec3d eye = Vec3d.ofCenter(stand).add(0, 1.62, 0);
                    for (Direction dir : Direction.values()) {
                        if (!hasFace(client, pos, dir)) continue;
                        Vec3d aim = facePoint(pos, dir);
                        if (eye.squaredDistanceTo(aim) > cfg.reach * cfg.reach) continue;
                        if (hitsFace(client, eye, pos, dir, aim)) return stand;
                    }
                    break;
                }
            }
        }
        return null;
    }

    private static Vec3d facePoint(BlockPos pos, Direction dir) {
        Vec3d center = Vec3d.ofCenter(pos);
        return center.add(dir.getOffsetX() * 0.5001, dir.getOffsetY() * 0.5001, dir.getOffsetZ() * 0.5001);
    }

    private static boolean hasFace(MinecraftClient client, BlockPos pos, Direction dir) {
        if (client.world == null) return false;
        BlockPos neighbour = pos.offset(dir);
        BlockState state = client.world.getBlockState(neighbour);
        return !state.isAir() && !state.getCollisionShape(client.world, neighbour).isEmpty();
    }

    private static boolean hitsFace(MinecraftClient client, Vec3d eye, BlockPos pos, Direction dir, Vec3d aim) {
        if (client.world == null || client.player == null) return false;
        var hit = client.world.raycast(new net.minecraft.world.RaycastContext(eye, aim,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, client.player));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && ((BlockHitResult) hit).getBlockPos().equals(pos.offset(dir))
                && ((BlockHitResult) hit).getSide() == dir.getOpposite();
    }

    
    public Face visibleFace(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return null;
        Vec3d eye = client.player.getEyePos();
        double reachSq = cfg.reach * cfg.reach;
        for (Direction dir : Direction.values()) {
            if (!hasFace(client, pos, dir)) continue;
            Vec3d aim = facePoint(pos, dir);
            if (eye.squaredDistanceTo(aim) > reachSq) continue;
            if (hitsFace(client, eye, pos, dir, aim)) {
                return new Face(pos.offset(dir), dir.getOpposite(), aim);
            }
        }
        return null;
    }

    
    public boolean canPlaceVisibly(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        BlockState current = client.world.getBlockState(pos);
        if (!current.isAir() && !current.isReplaceable()) return false;
        return visibleFace(pos) != null;
    }

    



    public boolean canPlaceVisibly(BlockPos pos, BlockState want) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (PlacementPlan.find(client, want, pos) != null) return true;
        
        
        if (PlacementPlan.hasOrientation(want)) return false;
        return canPlaceVisibly(pos);
    }

    
    public BlockPos vantageFor(BlockPos pos, BlockState want) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return null;
        for (Direction horizontal : Direction.Type.HORIZONTAL) {
            for (int dist = 2; dist <= 4; dist++) {
                BlockPos base = pos.offset(horizontal, dist);
                for (int drop = 0; drop <= 5; drop++) {
                    BlockPos stand = base.down(drop);
                    if (!PathFinder.standable(client.world, stand)) continue;
                    Vec3d eye = Vec3d.ofCenter(stand).add(0, 1.62, 0);
                    if (PlacementPlan.findFrom(client, want, pos, eye, cfg.reach, want) != null) return stand;
                    break;
                }
            }
        }
        return null;
    }

    
    public boolean canPlaceAt(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        BlockState current = client.world.getBlockState(pos);
        if (!current.isAir() && !current.isReplaceable()) return false;
        return findFace(pos) != null;
    }

    public enum Ready {
        
        CLICK,
        
        AIMING,
        
        NO_FACE,
        
        NO_ITEM
    }

    private Vec3d preparedHit;
    private BlockPos preparedClicked;
    private BlockPos preparedPos;
    private BlockState preparedWant;
    private Direction preparedSide;
    private boolean preparedBucket;
    

    private int preparedUses;
    private BlockPos clickSpot;
    private long clickAt;
    private final java.util.Random random = new java.util.Random();

    





    public boolean place(BlockState state, BlockPos pos) {
        return prepare(state, pos, true) == Ready.CLICK && click(state, pos);
    }

    



    public Ready aim(BlockState state, BlockPos pos) {
        return prepare(state, pos, false);
    }

    
    public boolean clickAimed(BlockState state, BlockPos pos) {
        return click(state, pos);
    }

    private Ready prepare(BlockState state, BlockPos pos, boolean instantaneous) {
        missing = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || client.world == null) return Ready.NO_FACE;

        Item item = Schematic.placementItem(state);
        if (item == Items.AIR) {
            missing = state.getBlock();
            return Ready.NO_ITEM;
        }
        if (!InvUtil.selectItem(client, item)) {
            missing = state.getBlock();
            return Ready.NO_ITEM;
        }
        if (cfg.verbose) {
            BuilderLog.LOGGER.info("placer: want {} at {} held {}",
                    Schematic.placementItem(state), pos.toShortString(),
                    client.player.getInventory().getStack(client.player.getInventory().getSelectedSlot()));
        }

        Vec3d hit;
        BlockPos clicked;
        Direction side;
        boolean bucket = state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA);
        
        
        if (state == preparedWant && pos.equals(preparedPos) && preparedUses < 80
                && preparedHit != null && canReach(preparedClicked)
                && client.world.getBlockState(preparedClicked).isSolidBlock(client.world, preparedClicked)) {
            preparedUses++;
            if (instantaneous) {
                lookAt(preparedHit);
                return Ready.CLICK;
            }
            if (!turnTo(preparedHit)) return Ready.AIMING;
            if (!pauseOver(pos)) return Ready.AIMING;
            return Ready.CLICK;
        }
        if (Schematic.needsWaterToPlant(state)) {
            
            if (client.world.getFluidState(pos).isEmpty()) return Ready.NO_FACE;
            clicked = pos.down();
            if (client.world.getBlockState(clicked).getCollisionShape(client.world, clicked).isEmpty()) {
                return Ready.NO_FACE;
            }
            hit = Vec3d.ofCenter(clicked).add(0, 0.49, 0);
            side = Direction.UP;
        } else if (bucket) {
            Face face = findVisibleFace(pos);
            if (face == null) return Ready.NO_FACE;
            hit = face.hit();
            clicked = face.neighbour();
            side = face.side();
        } else {
            
            
            
            PlacementPlan.Plan plan = PlacementPlan.find(client, state, pos);
            if (plan != null) {
                hit = plan.hit();
                clicked = plan.clicked();
                side = plan.side();
            } else if (PlacementPlan.hasOrientation(state)) {
                
                
                return Ready.NO_FACE;
            } else {
                
                
                Face face = visibleFace(pos);
                if (face == null) face = findFace(pos);
                if (face == null) return Ready.NO_FACE;
                hit = face.hit();
                clicked = face.neighbour();
                side = face.side();
            }
        }

        preparedHit = hit;
        preparedClicked = clicked;
        preparedPos = pos;
        preparedWant = state;
        preparedSide = side;
        preparedBucket = bucket;
        preparedUses = 0;

        if (instantaneous) {
            lookAt(hit);
            return Ready.CLICK;
        }
        if (!turnTo(hit)) return Ready.AIMING;
        if (!pauseOver(pos)) return Ready.AIMING;
        return Ready.CLICK;
    }

    private static boolean lookHits(MinecraftClient client, Vec3d point, double margin) {
        if (client.player == null) return false;
        Vec3d eye = client.player.getEyePos();
        Vec3d direction = point.subtract(eye);
        if (direction.lengthSquared() < 1.0E-6) return true;
        Vec3d look = Vec3d.fromPolar(client.player.getPitch(), client.player.getYaw());
        return look.dotProduct(direction.normalize()) >= 1.0 - margin;
    }

    private boolean click(BlockState state, BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || client.world == null) return false;
        Vec3d hit = preparedHit;
        BlockPos clicked = preparedClicked;
        Direction side = preparedSide;
        boolean bucket = preparedBucket;
        if (hit == null || preparedPos == null || !preparedPos.equals(pos)) {
            if (prepare(state, pos, true) != Ready.CLICK) return false;
            hit = preparedHit;
            clicked = preparedClicked;
            side = preparedSide;
            bucket = preparedBucket;
        }
        lookAt(hit);
        if (!lookHits(client, hit, 0.15)) return false;
        
        
        if (bucket) {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            client.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        var result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(hit, side, clicked, false));
        
        
        
        boolean accepted = result.isAccepted();
        if (cfg.verbose) {
            BuilderLog.LOGGER.info("placer: result {} for {} at {} ({})",
                    result, Schematic.placementItem(state), pos.toShortString(), side);
        }
        client.player.swingHand(Hand.MAIN_HAND);
        return accepted;
    }

    




    private boolean turnTo(Vec3d point) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        if (!cfg.humanMouse) {
            lookAt(point);
            return true;
        }
        float wantYaw = yawTo(point);
        float wantPitch = pitchTo(point);
        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        float dYaw = net.minecraft.util.math.MathHelper.wrapDegrees(wantYaw - yaw);
        float dPitch = wantPitch - pitch;
        if (Math.abs(dYaw) < 1.2f && Math.abs(dPitch) < 1.2f) {
            client.player.setYaw(wantYaw);
            client.player.setPitch(wantPitch);
            return true;
        }
        float stepYaw = Math.min(26.0f, Math.max(3.5f, Math.abs(dYaw) * 0.38f));
        float stepPitch = Math.min(18.0f, Math.max(2.5f, Math.abs(dPitch) * 0.38f));
        client.player.setYaw(yaw + Math.max(-stepYaw, Math.min(stepYaw, dYaw)));
        client.player.setPitch(pitch + Math.max(-stepPitch, Math.min(stepPitch, dPitch)));
        return false;
    }

    
    private boolean pauseOver(BlockPos pos) {
        if (!cfg.humanMouse || cfg.humanClickDelay <= 0) return true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return true;
        long now = client.world.getTime();
        if (!pos.equals(clickSpot)) {
            clickSpot = pos;
            clickAt = now + 1 + random.nextInt(Math.max(1, cfg.humanClickDelay + 1));
            return false;
        }
        return now >= clickAt;
    }

    



    public boolean isCorrect(BlockPos pos, BlockState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        BlockState current = client.world.getBlockState(pos);
        return Schematic.sameBlock(current.getBlock(), state.getBlock());
    }

    
    public boolean breakTick(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || client.world == null) return false;
        if (client.world.getBlockState(pos).isAir()) {
            resetBreaking();
            return true;
        }
        if (breaking == null || !breaking.equals(pos)) {
            breaking = pos;
            breakTicks = 0;
            
            
            Tools.Kind kind = Tools.kindFor(client.world.getBlockState(pos));
            Item tool = Tools.bestInInventory(kind, client.player);
            if (tool != null) {
                InvUtil.selectItem(client, tool);
                missingTool = null;
            } else {
                missingTool = Tools.itemFor(kind, cfg.toolTier);
            }
            if (cfg.verbose) {
                BuilderLog.LOGGER.info("placer: break {} with {} (wanted {})",
                        pos.toShortString(),
                        tool == null ? "bare hands" : net.minecraft.registry.Registries.ITEM.getId(tool),
                        missingTool == null ? "nothing"
                                : net.minecraft.registry.Registries.ITEM.getId(missingTool));
            }
            client.interactionManager.attackBlock(pos, Direction.UP);
        }
        lookAt(Vec3d.ofCenter(pos));
        client.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
        client.player.swingHand(Hand.MAIN_HAND);
        breakTicks++;
        if (breakTicks > 20 * 12) {
            resetBreaking();
            return false;
        }
        return client.world.getBlockState(pos).isAir();
    }

    public void resetBreaking() {
        breaking = null;
        breakTicks = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
    }

    public void lookAt(Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.setYaw(yawTo(target));
        client.player.setPitch(pitchTo(target));
    }

    private float yawTo(Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return 0.0f;
        Vec3d eye = client.player.getEyePos();
        return (float) (Math.toDegrees(Math.atan2(target.z - eye.z, target.x - eye.x)) - 90.0);
    }

    private float pitchTo(Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return 0.0f;
        Vec3d eye = client.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

}
