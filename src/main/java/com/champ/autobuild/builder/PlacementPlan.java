package com.champ.autobuild.builder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

















public final class PlacementPlan {
    
    private static final Set<String> IGNORED = Set.of(
            "open", "powered", "lit", "extended", "triggered", "waterlogged", "disarmed");

    
    private static final double[] OFFSETS = {0.0, 0.36, -0.36};

    public record Plan(BlockPos clicked, Direction side, Vec3d hit, boolean visible, int score,
                       BlockState predicted) {
        public boolean exact() {
            return score >= 100;
        }
    }

    




    private static final Set<String> ORIENTATION = Set.of(
            "facing", "axis", "half", "face", "hinge", "attachment", "orientation",
            "vertical_direction");

    
    public static boolean hasOrientation(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (ORIENTATION.contains(property.getName())) return true;
        }
        return false;
    }

    
    public static boolean orientationMatches(BlockState want, BlockState got) {
        for (Property<?> property : want.getProperties()) {
            if (!ORIENTATION.contains(property.getName())) continue;
            if (!got.contains(property)) return false;
            if (!got.get(property).equals(want.get(property))) return false;
        }
        return true;
    }

    private PlacementPlan() {
    }

    
    public static Plan find(MinecraftClient client, BlockState want, BlockPos pos) {
        if (client.player == null) return null;
        return findFrom(client, want, pos, client.player.getEyePos(), 0, null);
    }

    





    public static Plan findFrom(MinecraftClient client, BlockState want, BlockPos pos, Vec3d eye,
                                double maxDist, BlockState facing) {
        if (client.player == null || client.world == null) return null;
        ItemStack stack = client.player.getMainHandStack();
        float oldYaw = client.player.getYaw();
        float oldPitch = client.player.getPitch();
        Plan best = null;
        try {
            for (Direction dir : Direction.values()) {
                BlockPos neighbour = pos.offset(dir);
                BlockState neighbourState = client.world.getBlockState(neighbour);
                if (neighbourState.isAir()) continue;
                if (neighbourState.getCollisionShape(client.world, neighbour).isEmpty()) continue;
                if (!neighbourState.isSolidBlock(client.world, neighbour)) continue;
                for (double a : OFFSETS) {
                    for (double b : OFFSETS) {
                        Vec3d hit = facePoint(pos, dir, a, b);
                        double distance = eye.squaredDistanceTo(hit);
                        if (maxDist > 0 && distance > maxDist * maxDist) continue;
                        client.player.setYaw(yawTo(eye, hit));
                        client.player.setPitch(pitchTo(eye, hit));
                        BlockHitResult hitResult = new BlockHitResult(hit, dir.getOpposite(), neighbour, false);
                        ItemPlacementContext ctx = new ItemPlacementContext(
                                client.player, Hand.MAIN_HAND, stack, hitResult);
                        BlockState got;
                        try {
                            got = want.getBlock().getPlacementState(ctx);
                        } catch (RuntimeException e) {
                            continue;
                        }
                        if (got == null || got.getBlock() != want.getBlock()) continue;
                        if (!got.canPlaceAt(client.world, pos)) continue;
                        if (!orientationMatches(want, got)) continue;
                        int score = score(want, got);
                        if (facing != null && score < 100) continue;
                        boolean visible = rayHits(client, eye, pos, dir, hit);
                        Plan plan = new Plan(neighbour, dir.getOpposite(), hit, visible, score, got);
                        if (best == null || better(plan, best, distance, eye.squaredDistanceTo(best.hit()))) {
                            best = plan;
                        }
                        
                        if (best.exact() && best.visible()) return best;
                    }
                }
            }
        } finally {
            client.player.setYaw(oldYaw);
            client.player.setPitch(oldPitch);
        }
        return best;
    }

    
    public static boolean canPlaceExactly(MinecraftClient client, BlockState want, BlockPos pos) {
        return findFrom(client, want, pos, client.player.getEyePos(), 0, want) != null;
    }

    private static boolean better(Plan candidate, Plan current, double candidateDist, double currentDist) {
        if (candidate.exact() != current.exact()) return candidate.exact();
        if (candidate.visible() != current.visible()) return candidate.visible();
        if (candidate.score() != current.score()) return candidate.score() > current.score();
        return candidateDist < currentDist;
    }

    
    public static int score(BlockState want, BlockState got) {
        int total = 0;
        int equal = 0;
        for (Property<?> property : want.getProperties()) {
            if (IGNORED.contains(property.getName())) continue;
            total++;
            if (got.contains(property) && got.get(property).equals(want.get(property))) equal++;
        }
        if (total == 0) return 100;
        return (equal * 100) / total;
    }

    



    private static Vec3d facePoint(BlockPos pos, Direction dir, double a, double b) {
        Vec3d center = Vec3d.ofCenter(pos);
        double x = center.x + dir.getOffsetX() * 0.5001;
        double y = center.y + dir.getOffsetY() * 0.5001;
        double z = center.z + dir.getOffsetZ() * 0.5001;
        if (dir.getAxis() == Direction.Axis.Y) {
            x += a;
            z += b;
        } else if (dir.getAxis() == Direction.Axis.X) {
            y += a;
            z += b;
        } else {
            x += a;
            y += b;
        }
        return new Vec3d(x, y, z);
    }

    private static boolean rayHits(MinecraftClient client, Vec3d eye, BlockPos pos, Direction dir, Vec3d hit) {
        if (client.world == null || client.player == null) return false;
        var result = client.world.raycast(new net.minecraft.world.RaycastContext(eye, hit,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, client.player));
        return result.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && ((BlockHitResult) result).getBlockPos().equals(pos.offset(dir))
                && ((BlockHitResult) result).getSide() == dir.getOpposite();
    }

    private static float yawTo(Vec3d eye, Vec3d target) {
        return (float) (Math.toDegrees(Math.atan2(target.z - eye.z, target.x - eye.x)) - 90.0);
    }

    private static float pitchTo(Vec3d eye, Vec3d target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }
}
