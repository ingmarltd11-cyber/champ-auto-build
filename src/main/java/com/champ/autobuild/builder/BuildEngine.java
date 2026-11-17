package com.champ.autobuild.builder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;









public class BuildEngine {
    public enum State {
        IDLE, RUNNING, PAUSED, WAITING_MATERIALS, CLEANUP, DONE, STOPPED, ERROR
    }

    private final BuildConfig cfg;
    private final Placer placer;
    private final MovementController movement;
    private final Set<BlockPos> scaffolds = new LinkedHashSet<>();
    private final LinkedHashMap<BlockPos, Schematic.Target> pending = new LinkedHashMap<>();
    private final List<BlockPos> failed = new ArrayList<>();
    
    private final Set<BlockPos> waiting = new LinkedHashSet<>();
    
    private final Map<BlockPos, Integer> failedPlacements = new LinkedHashMap<>();
    
    private final Map<BlockPos, Integer> navAttempts = new LinkedHashMap<>();
    
    private final Map<BlockPos, Integer> roamAttempts = new LinkedHashMap<>();
    
    private final Map<BlockPos, Integer> deferCounts = new LinkedHashMap<>();

    private State state = State.IDLE;
    private State pausedFrom;
    private String status = "idle";
    private Block missing;
    private int total;
    private int placedCount;
    private int failedCount;
    private int stalled;
    private int heartbeat;

    
    private boolean allowCommands;

    
    private BlockPos focus;

    private BlockPos verifyPos;
    private BlockState verifyState;
    private int verifyTicks;
    private int verifyAttempts;

    private int scaffoldAttempts;
    private int idleTicks;
    private int waterEscapeTicks;
    private int stairSteps;
    private BlockPos pathTarget;
    private BlockPos stuckNav;
    private int stairLog;
    private int noProgressTicks;
    private BlockPos stillPos;
    private int stillTicks;
    private int sneakWait;
    private long sneakSince = -1;
    private int cleanupIndex;
    private int cleanupTicks;
    private int cleanupAge;
    private int waitingCycles;
    private List<BlockPos> cleanupOrder = List.of();
    private int actionCooldown;

    public BuildEngine(BuildConfig cfg) {
        this.cfg = cfg;
        this.placer = new Placer(cfg);
        this.movement = new MovementController(cfg, scaffolds);
    }

    public void setAllowCommands(boolean allowCommands) {
        this.allowCommands = allowCommands;
    }

    public void start(List<Schematic.Target> targets) {
        start(targets, false);
    }

    




    public void continueWith(List<Schematic.Target> targets) {
        start(targets, true);
    }

    private void start(List<Schematic.Target> targets, boolean keepTries) {
        pending.clear();
        failed.clear();
        waiting.clear();
        failedPlacements.clear();
        navAttempts.clear();
        roamAttempts.clear();
        if (!keepTries) deferCounts.clear();
        scaffolds.clear();
        List<Schematic.Target> sorted = new ArrayList<>(targets);
        
        sorted.sort(Comparator.comparingInt((Schematic.Target t) -> t.pos().getY())
                .thenComparingInt(t -> waterFirst(t.state()))
                .thenComparingInt(t -> t.pos().getX())
                .thenComparingInt(t -> t.pos().getZ()));
        for (Schematic.Target target : sorted) pending.put(target.pos(), target);
        total = sorted.size();
        placedCount = 0;
        failedCount = 0;
        missing = null;
        stalled = 0;
        focus = null;
        verifyPos = null;
        verifyState = null;
        verifyTicks = 0;
        verifyAttempts = 0;
        scaffoldAttempts = 0;
        stairSteps = 0;
        stairLog = 0;
        idleTicks = 0;
        noProgressTicks = 0;
        stillTicks = 0;
        stillPos = null;
        sneakWait = 0;
        sneakSince = -1;
        pausedFrom = null;
        actionCooldown = 0;
        cleanupIndex = 0;
        cleanupTicks = 0;
        cleanupAge = 0;
        waitingCycles = 0;
        cleanupOrder = List.of();
        waterEscapeTicks = 0;
        movement.clearPath();
        state = State.RUNNING;
        status = "building 0/" + total;
    }

    



    private static int waterFirst(BlockState state) {
        if (Schematic.needsWaterToPlant(state)) return 2;
        if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) return 1;
        return 0;
    }

    public void pause() {
        if (state == State.RUNNING || state == State.WAITING_MATERIALS || state == State.CLEANUP) {
            pausedFrom = state;
            state = State.PAUSED;
            movement.clearPath();
            movement.releaseKeys();
            placer.resetBreaking();
        }
    }

    public void resume() {
        if (state == State.PAUSED || state == State.WAITING_MATERIALS) {
            
            state = pausedFrom == State.CLEANUP ? State.CLEANUP : State.RUNNING;
            pausedFrom = null;
            missing = null;
            placer.clearMissing();
        }
    }

    
    public Item neededTool() {
        return placer.missingTool();
    }

    public void clearNeededTool() {
        placer.clearMissingTool();
    }

    public void stop() {
        state = State.STOPPED;
        movement.clearPath();
        movement.releaseKeys();
        placer.resetBreaking();
        status = "stopped";
    }

    public State state() {
        return state;
    }

    public String status() {
        return status;
    }

    public int placed() {
        return placedCount;
    }

    public int total() {
        return total;
    }

    public int failed() {
        return failedCount;
    }

    public int remaining() {
        return pending.size();
    }

    public Block missingBlock() {
        return missing;
    }

    public boolean hasPath() {
        return movement.hasPath();
    }

    public String pathInfo() {
        return movement.describePath();
    }

    public Set<BlockPos> scaffolds() {
        return scaffolds;
    }

    
    public java.util.Collection<Schematic.Target> pendingTargets() {
        return pending.values();
    }

    
    public Block tempBlock() {
        return movement.tempBlockNow();
    }

    
    public Map<Block, Integer> remainingMaterials() {
        Map<Block, Integer> map = new LinkedHashMap<>();
        for (Schematic.Target target : pending.values()) {
            if (!Schematic.isPlaceable(target.state())) continue;
            map.merge(target.state().getBlock(), 1, Integer::sum);
        }
        return map;
    }

    public void tick() {
        if (state != State.RUNNING && state != State.CLEANUP) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (cfg.verbose && ++heartbeat % 40 == 0) {
            BuilderLog.LOGGER.info(
                    "engine: tick state={} screen={} pending={} waiting={} cooldown={} status='{}'",
                    state, client.currentScreen, pending.size(), waiting.size(), actionCooldown, status);
        }
        if (client.currentScreen != null) {
            movement.releaseKeys();
            
            
            boolean editor = client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>
                    || client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.SignEditScreen;
            if (editor
                    && !(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)
                    && client.player != null) {
                client.player.closeHandledScreen();
                client.setScreen(null);
            }
            return;
        }
        
        
        if (state == State.CLEANUP && ++cleanupAge > 1200) {
            cleanupAge = 0;
            status = "left " + Math.max(0, cleanupOrder.size() - cleanupIndex) + " temp blocks";
            finish();
            return;
        }

        
        
        if (isWet(client)) {
            tickWaterEscape(client);
            return;
        }
        waterEscapeTicks = 0;

        if (actionCooldown-- > 0) return;

        
        
        
        if (state == State.RUNNING && ++idleTicks > 600) {
            
            
            
            idleTicks = 0;
            movement.releaseKeys();
            movement.clearPath();
            status = "round finished with " + pending.size() + " block(s) left";
            beginCleanup();
            return;
        }

        if (state == State.CLEANUP) {
            tickCleanup(client);
            return;
        }

        tickVerification(client);
        
        
        if (verifyPos != null) return;

        if (pending.isEmpty()) {
            beginCleanup();
            return;
        }

        Schematic.Target target = focus == null ? null : pending.get(focus);
        if (target == null) {
            focus = null;
            target = chooseTarget(client);
        }
        if (target == null) {
            beginCleanup();
            return;
        }
        BlockPos pos = target.pos();
        BlockState want = target.state();

        
        if (!Schematic.isPlaceable(want)) {
            skip(pos, "no item for " + blockName(want.getBlock()));
            return;
        }

        if (placer.isCorrect(pos, want)) {
            complete(pos);
            return;
        }

        World world = client.world;
        BlockState current = world.getBlockState(pos);

        
        
        if (Schematic.needsWaterToPlant(want)) {
            tickPlant(client, target, pos, want, current);
            return;
        }

        
        
        
        if (!want.isOf(Blocks.WATER) && !want.isOf(Blocks.LAVA)
                && !world.getFluidState(pos).isEmpty()) {
            
            
            if (setblock(pos, want)) {
                if (cfg.verbose) {
                    BuilderLog.LOGGER.info(
                            "engine: water in the way of {} at {}, built with setblock",
                            blockName(want.getBlock()), shortPos(pos));
                }
                return;
            }
            defer(target, "water in the way of " + blockName(want.getBlock()));
            return;
        }

        boolean open = current.isAir() || current.isReplaceable();

        
        
        if (!open && scaffolds.contains(pos)) {
            breakOwnBlock(client, target, pos);
            return;
        }

        if (!open) {
            if (cfg.breakObstructions || cfg.replaceExisting) {
                if (placer.canReach(pos)) {
                    movement.clearPath();
                    movement.releaseKeys();
                    if (placer.breakTick(pos)) {
                        placer.resetBreaking();
                    }
                    return;
                }
                navigateTo(client, target);
                return;
            }
            
            
            skip(pos, "occupied by " + blockName(current.getBlock()));
            return;
        }

        
        if (client.player != null) {
            BlockPos feet = client.player.getBlockPos();
            if (pos.equals(feet) || pos.equals(feet.up())) {
                
                
                if (walkingPath()) return;
                if (stepAway(client, pos)) return;
                defer(target, "standing in the way");
                return;
            }
        }

        if (placer.canReach(pos) && open && placer.canPlaceVisibly(pos, want)) {
            movement.clearPath();
            if (cfg.verbose) {
                BuilderLog.LOGGER.info(
                        "engine: place branch {} current={} sneaking={} focus={} y={}",
                        shortPos(pos), current.getBlock(), client.player.isSneaking(),
                        focus == null ? "null" : shortPos(focus), client.player.getY());
            }
            if (focus == null) {
                movement.releaseKeys();
            } else {
                movement.releaseMovement();
            }

            
            
            
            if (waitForSneak(client, pos, "place " + blockName(want.getBlock()))) return;

            
            
            if ((want.isOf(Blocks.WATER) || want.isOf(Blocks.LAVA))
                    && placer.findVisibleFace(pos) == null) {
                if (walkingPath()) return;
                BlockPos vantage = placer.vantageFor(pos);
                List<BlockPos> path = vantage == null ? null : planPathTo(client, vantage);
                if (path != null && !path.isEmpty()) {
                    movement.setPath(path);
                    actionCooldown = 1;
                    status = "moving to place " + blockName(want.getBlock());
                    return;
                }
                
                
                if (placeTempSupport(client, pos)) {
                    actionCooldown = 3;
                    defer(target, "support for a water face");
                    return;
                }
                defer(target, "no visible " + blockName(want.getBlock()) + " face");
                return;
            }

            Placer.Ready ready = placer.aim(want, pos);
            if (ready == Placer.Ready.AIMING) {
                actionCooldown = 1;
                status = "aiming at " + shortPos(pos);
                return;
            }
            if (placer.clickAimed(want, pos)) {
                verifyPos = pos;
                verifyState = want;
                verifyTicks = 12;
                verifyAttempts = 0;
                actionCooldown = 2;
                focus = null;
                status = "placing " + blockName(want.getBlock()) + " at " + shortPos(pos);
            } else if (placer.missingBlock() != null) {
                if (cannotUse(client, target, want)) return;
                missing = placer.missingBlock();
                state = State.WAITING_MATERIALS;
                status = "missing " + blockName(missing);
            } else if (placeTempSupport(client, pos)) {
                
                actionCooldown = 3;
                defer(target, "support for " + blockName(want.getBlock()));
            } else {
                defer(target, "no face for " + blockName(want.getBlock()));
            }
            return;
        }

        navigateTo(client, target);
    }

    




    private boolean waitForSneak(MinecraftClient client, BlockPos pos, String what) {
        if (client.player != null && client.player.isSneaking()) {
            sneakWait = 0;
            sneakSince = -1;
            return false;
        }
        if (client.world == null) return false;
        client.options.sneakKey.setPressed(true);
        focus = pos;
        
        
        long now = client.world.getTime();
        if (sneakSince < 0) sneakSince = now;
        if (now - sneakSince < 2) {
            actionCooldown = 2;
            status = "sneaking to " + what;
            return true;
        }
        sneakSince = -1;
        sneakWait = 0;
        return false;
    }

    
    private boolean floodsNeighbour(BlockPos pos) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (needsSolid(pos.offset(dir))) return true;
        }
        return needsSolid(pos.up());
    }

    private boolean needsSolid(BlockPos pos) {
        Schematic.Target target = pending.get(pos);
        if (target == null) return false;
        return !target.state().isOf(Blocks.WATER) && !Schematic.needsWaterToPlant(target.state());
    }

    




    private boolean cannotUse(MinecraftClient client, Schematic.Target target, BlockState want) {
        Item item = Schematic.placementItem(want);
        int have = client.player == null || item == Items.AIR
                ? 0 : InvUtil.count(client.player, item);
        if (cfg.verbose) {
            int slot = client.player == null ? -1
                    : InvUtil.findSlot(client.player, s -> s.getItem() == item);
            BuilderLog.LOGGER.info(
                    "engine: click produced nothing for {} at {} item={} have={} slot={} inv={}",
                    blockName(want.getBlock()), shortPos(target.pos()), item, have, slot,
                    client.player == null ? "-" : client.player.getInventory().size());
        }
        if (have <= 0) return false;
        defer(target, "could not use " + blockName(want.getBlock()) + " (item is in the inventory)");
        return true;
    }

    
    private BlockPos scaffoldBlockingView(MinecraftClient client, BlockPos pos) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos beside = pos.offset(dir);
            if (scaffolds.contains(beside)) return beside;
        }
        BlockPos above = pos.up();
        return scaffolds.contains(above) ? above : null;
    }

    
    private void breakOwnBlock(MinecraftClient client, Schematic.Target target, BlockPos pos) {
        if (placer.canReach(pos)) {
            movement.clearPath();
            movement.releaseKeys();
            if (placer.breakTick(pos)) {
                placer.resetBreaking();
                scaffolds.remove(pos);
                actionCooldown = 2;
            }
            status = "removing my own temp block at " + shortPos(pos);
            return;
        }
        navigateTo(client, target);
    }

    



    private void tickPlant(MinecraftClient client, Schematic.Target target, BlockPos pos,
                           BlockState want, BlockState current) {
        World world = client.world;
        boolean water = world.getFluidState(pos).isIn(FluidTags.WATER);

        if (!water) {
            if (!current.isAir() && !current.isReplaceable()) {
                skip(pos, blockName(want.getBlock()) + " needs water here, but the spot holds "
                        + blockName(current.getBlock()));
                return;
            }
            
            
            BlockPos below = pos.down();
            if (world.getBlockState(below).getCollisionShape(world, below).isEmpty()) {
                defer(target, "no floor to hold water for " + blockName(want.getBlock()));
                return;
            }
            if (world.getFluidState(pos).isIn(FluidTags.LAVA)) {
                skip(pos, "lava in the way of " + blockName(want.getBlock()));
                return;
            }
            
            
            if (floodsNeighbour(pos)) {
                defer(target, "waiting for the walls around the water");
                return;
            }
            if (!placer.canReach(pos) || !placer.canPlaceVisibly(pos)) {
                navigateTo(client, target);
                return;
            }
            movement.clearPath();
            if (focus == null) {
                movement.releaseKeys();
            } else {
                movement.releaseMovement();
            }
            
            
            if (waitForSneak(client, pos, "pour water for " + blockName(want.getBlock()))) return;
            Placer.Ready pour = placer.aim(Blocks.WATER.getDefaultState(), pos);
            if (pour == Placer.Ready.AIMING) {
                actionCooldown = 1;
                return;
            }
            if (placer.clickAimed(Blocks.WATER.getDefaultState(), pos)) {
                actionCooldown = 3;
                defer(target, "placing water for " + blockName(want.getBlock()));
                return;
            }
            if (placer.missingBlock() != null) {
                missing = placer.missingBlock();
                state = State.WAITING_MATERIALS;
                status = "missing " + blockName(missing);
                return;
            }
            deferAfterPlantFailure(client, target, pos, want, "no spot to pour water for ");
            return;
        }

        if (!placer.canReach(pos)) {
            navigateTo(client, target);
            return;
        }
        movement.clearPath();
        if (focus == null) {
            movement.releaseKeys();
        } else {
            movement.releaseMovement();
        }
        if (waitForSneak(client, pos, "plant " + blockName(want.getBlock()))) return;
        Placer.Ready plant = placer.aim(want, pos);
        if (plant == Placer.Ready.AIMING) {
            actionCooldown = 1;
            return;
        }
        if (placer.clickAimed(want, pos)) {
            verifyPos = pos;
            verifyState = want;
            verifyTicks = 12;
            verifyAttempts = 0;
            actionCooldown = 2;
            focus = null;
            status = "planting " + blockName(want.getBlock()) + " at " + shortPos(pos);
            return;
        }
        if (placer.missingBlock() != null) {
            missing = placer.missingBlock();
            state = State.WAITING_MATERIALS;
            status = "missing " + blockName(missing);
            return;
        }
        deferAfterPlantFailure(client, target, pos, want, "no water face to plant ");
    }

    
    private void deferAfterPlantFailure(MinecraftClient client, Schematic.Target target, BlockPos pos,
                                        BlockState want, String reason) {
        BlockPos vantage = placer.vantageFor(pos);
        List<BlockPos> path = vantage == null ? null : planPathTo(client, vantage);
        if (path != null && !path.isEmpty()) {
            movement.setPath(path);
            actionCooldown = 1;
            status = "moving to " + blockName(want.getBlock()) + " at " + shortPos(pos);
            return;
        }
        if (placeTempSupport(client, pos)) {
            actionCooldown = 3;
            defer(target, "support for " + blockName(want.getBlock()));
            return;
        }
        defer(target, reason + blockName(want.getBlock()));
    }

    private boolean isWet(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;
        BlockPos feet = client.player.getBlockPos();
        return !client.world.getFluidState(feet).isEmpty()
                || !client.world.getFluidState(feet.up()).isEmpty();
    }

    
    private void tickWaterEscape(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (++waterEscapeTicks > 600) {
            movement.releaseKeys();
            state = State.STOPPED;
            status = "stopped: could not get out of the water";
            return;
        }
        if (movement.hasPath()) {
            MovementController.PathResult result = movement.tickPath();
            if (result == MovementController.PathResult.MOVING) {
                status = "climbing out of the water";
                return;
            }
            movement.clearPath();
        }
        BlockPos start = client.player.getBlockPos();
        List<BlockPos> path = PathFinder.find(start, start, node ->
                PathFinder.standable(client.world, node)
                        && client.world.getFluidState(node).isEmpty()
                        && client.world.getFluidState(node.up()).isEmpty(), 8000);
        if (path != null && !path.isEmpty()) {
            movement.setPath(path);
            movement.tickPath();
            status = "climbing out of the water";
            return;
        }
        BlockPos dry = nearestDry(client, 24);
        if (dry != null) {
            movement.swimToward(dry);
            status = "swimming to dry land at " + shortPos(dry);
        } else {
            movement.releaseKeys();
            status = "in water and no dry land within 24 blocks";
        }
    }

    private BlockPos nearestDry(MinecraftClient client, int radius) {
        BlockPos feet = client.player.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = feet.add(dx, dy, dz);
                    if (!PathFinder.standable(client.world, candidate)) continue;
                    if (!client.world.getFluidState(candidate).isEmpty()) continue;
                    if (!client.world.getFluidState(candidate.up()).isEmpty()) continue;
                    double distance = candidate.getSquaredDistance(feet);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private void complete(BlockPos pos) {
        if (pending.remove(pos) != null) {
            placedCount++;
            idleTicks = 0;
        }
        waiting.remove(pos);
        failedPlacements.remove(pos);
        navAttempts.clear();
        roamAttempts.remove(pos);
        focus = null;
        verifyPos = null;
        noProgressTicks = 0;
        waitingCycles = 0;
        stalled = 0;
        status = "building " + placedCount + "/" + total;
    }

    




    private void defer(Schematic.Target target, String reason) {
        Schematic.Target removed = pending.remove(target.pos());
        if (removed == null) return;
        if (cfg.verbose) {
            BuilderLog.LOGGER.info("engine: defer {} at {} ({})",
                    blockName(target.state().getBlock()), shortPos(target.pos()), reason);
        }
        pending.put(removed.pos(), removed);
        waiting.add(target.pos());
        roamAttempts.remove(target.pos());
        navAttempts.remove(target.pos());
        deferCounts.merge(target.pos(), 1, Integer::sum);
        
        stuckNav = null;
        pathTarget = null;
        focus = null;
        stalled++;
        noProgressTicks = 0;
        status = "waiting: " + reason + " at " + shortPos(target.pos());
        int budget = allowCommands ? 12 : Math.max(40, pending.size() * 5);
        if (stalled > budget) {
            stalled = 0;
            skip(target.pos(), reason + " (gave up after " + budget + " tries)");
            return;
        }
        actionCooldown = 1;
    }

    

    private void navigateTo(MinecraftClient client, Schematic.Target target) {
        
        movement.releaseKeys();
        if (cfg.verbose) {
            BuilderLog.LOGGER.info(
                    "engine: nav enter target={} pathTarget={} hasPath={}",
                    shortPos(target.pos()), pathTarget == null ? "null" : shortPos(pathTarget),
                    movement.hasPath());
        }
        
        if (!target.pos().equals(pathTarget)) {
            movement.clearPath();
            pathTarget = target.pos();
            stuckNav = null;
        }
        
        
        if (movement.hasPath() && client.player.getBlockPos().equals(stillPos)) {
            if (++stillTicks > 80) {
                movement.clearPath();
                stillTicks = 0;
                stuckNav = target.pos();
                if (cfg.verbose) {
                    BuilderLog.LOGGER.info(
                            "engine: nav watchdog, standing still at {} for 4s",
                            client.player.getBlockPos());
                }
            }
        } else {
            stillTicks = 0;
        }
        stillPos = client.player.getBlockPos();
        if (movement.hasPath()) {
            MovementController.PathResult result = movement.tickPath();
            if (result == MovementController.PathResult.STUCK) {
                
                
                movement.clearPath();
                stuckNav = target.pos();
            } else if (result == MovementController.PathResult.ARRIVED
                    || result == MovementController.PathResult.NO_PATH) {
                movement.clearPath();
            } else {
                noProgressTicks = 0;
                return;
            }
        }

        
        
        
        if (!placer.canReach(target.pos()) && target.pos().getY() >= client.player.getBlockY() + 2) {
            if (ascendToward(client, target)) return;
        }

        List<BlockPos> path = target.pos().equals(stuckNav) ? null : planPath(client, target.pos());
        if (path != null && !path.isEmpty()) {
            movement.setPath(path);
            actionCooldown = 1;
            return;
        }

        
        
        if (placer.canReach(target.pos())) {
            
            BlockPos blocker = scaffoldBlockingView(client, target.pos());
            if (blocker != null && placer.canReach(blocker)) {
                breakOwnBlock(client, target, blocker);
                return;
            }
            
            
            
            int angleAttempts = navAttempts.merge(target.pos(), 1, Integer::sum);
            if (angleAttempts > 60) {
                navAttempts.remove(target.pos());
                defer(target, "no angle to place it");
                return;
            }
            if (placeTempSupport(client, target.pos())) {
                actionCooldown = 3;
                defer(target, "support for " + blockName(target.state().getBlock()));
                return;
            }
            
            
            
            int roam = roamAttempts.merge(target.pos(), 1, Integer::sum);
            if (roam > 8) {
                roamAttempts.remove(target.pos());
                defer(target, "no spot to stand and click it");
                return;
            }
            if (moveToVantage(client, target)) return;
            if (stepCloser(client, target)) return;
        }

        if (!cfg.useScaffold) {
            defer(target, "unreachable and temp blocks disabled");
            return;
        }

        MovementController.ScaffoldResult scaffold = movement.scaffoldTowards(target.pos(), placer);
        switch (scaffold) {
            case PLACED -> {
                scaffoldAttempts = 0;
                actionCooldown = 3;
            }
            case PILLARING, MOVED -> {
                scaffoldAttempts = 0;
                actionCooldown = 2;
            }
            case FAILED -> {
                scaffoldAttempts++;
                if (scaffoldAttempts > 8) {
                    scaffoldAttempts = 0;
                    defer(target, "cannot reach it");
                    return;
                }
                actionCooldown = 5;
            }
        }
        noProgressTicks++;
        if (noProgressTicks > cfg.stuckTicks) {
            noProgressTicks = 0;
            defer(target, "stuck on the way");
        }
    }

    
    private boolean moveToVantage(MinecraftClient client, Schematic.Target target) {
        
        
        BlockPos vantage = placer.vantageFor(target.pos(), target.state());
        if (vantage == null) vantage = placer.vantageFor(target.pos());
        if (vantage == null || vantage.equals(client.player.getBlockPos())) return false;
        
        if (Vec3d.ofCenter(vantage).squaredDistanceTo(client.player.getEntityPos()) < 0.9) return false;
        List<BlockPos> path = planPathTo(client, vantage);
        if (path == null || path.isEmpty()) return false;
        movement.setPath(path);
        actionCooldown = 1;
        status = "moving for a better angle at " + shortPos(target.pos());
        return true;
    }

    



    private boolean stepCloser(MinecraftClient client, Schematic.Target target) {
        BlockPos feet = client.player.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            for (int radius = 1; radius <= 2; radius++) {
                BlockPos candidate = target.pos().offset(dir, radius);
                if (!PathFinder.standable(client.world, candidate)) continue;
                if (candidate.equals(feet)) continue;
                
                
                if (Vec3d.ofCenter(candidate).squaredDistanceTo(client.player.getEntityPos()) < 0.9) continue;
                double distance = candidate.getSquaredDistance(feet);
                if (distance >= bestDistance) continue;
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best == null) return false;
        List<BlockPos> path = planPathTo(client, best);
        if (path == null || path.isEmpty()) return false;
        movement.setPath(path);
        actionCooldown = 1;
        status = "walking to " + shortPos(best) + " for " + shortPos(target.pos());
        return true;
    }

    



    private boolean ascendToward(MinecraftClient client, Schematic.Target target) {
        if (client.player == null || client.world == null || !cfg.useScaffold) return false;
        if (cfg.verbose && ++stairLog % 20 == 0) {
            BuilderLog.LOGGER.info(
                    "engine: ascend to {} feet={} steps={}", shortPos(target.pos()),
                    client.player.getBlockPos(), stairSteps);
        }
        BlockPos feet = client.player.getBlockPos();
        Direction dir = horizontalDirection(feet, target.pos());
        if (dir == null) return false;
        BlockPos step = feet.offset(dir);
        
        if (PathFinder.solid(client.world, step) && PathFinder.passable(client.world, step.up())) {
            movement.hopOnto(step);
            actionCooldown = 0;
            status = "hopping onto " + shortPos(step);
            return true;
        }
        if (stairSteps++ > 8) {
            stairSteps = 0;
            return false;
        }
        MovementController.ScaffoldResult result = movement.stepUp(target.pos(), placer);
        if (result == MovementController.ScaffoldResult.PLACED) {
            actionCooldown = 2;
            status = "stair step to " + shortPos(target.pos());
            return true;
        }
        if (result == MovementController.ScaffoldResult.PILLARING) {
            actionCooldown = 1;
            return true;
        }
        stairSteps = 0;
        return false;
    }

    private static Direction horizontalDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) return null;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    



    private boolean walkingPath() {
        if (!movement.hasPath()) return false;
        if (movement.tickPath() == MovementController.PathResult.MOVING) return true;
        movement.clearPath();
        return false;
    }

    
    private boolean stepAway(MinecraftClient client, BlockPos avoid) {
        if (client.player == null || client.world == null) return false;
        List<BlockPos> path = PathFinder.find(client.player.getBlockPos(), avoid, node ->
                node.getSquaredDistance(avoid) >= 9 && PathFinder.standable(client.world, node), 4000);
        if (path == null || path.isEmpty()) return false;
        movement.setPath(path);
        actionCooldown = 1;
        status = "stepping aside at " + shortPos(avoid);
        return true;
    }

    private List<BlockPos> planPathTo(MinecraftClient client, BlockPos goal) {
        if (client.player == null) return null;
        BlockPos start = client.player.getBlockPos();
        return PathFinder.find(start, goal, node -> node.equals(goal), 8000);
    }

    private List<BlockPos> planPath(MinecraftClient client, BlockPos target) {
        if (client.player == null) return null;
        double reach = Math.max(1.5, cfg.reach - 0.6);
        double reachSq = reach * reach;
        BlockPos start = client.player.getBlockPos();
        return PathFinder.find(start, target, node -> {
            Vec3d eye = Vec3d.ofCenter(node).add(0, 1.62, 0);
            return eye.squaredDistanceTo(Vec3d.ofCenter(target)) <= reachSq;
        }, 6000);
    }

    




    private Schematic.Target chooseTarget(MinecraftClient client) {
        if (pending.isEmpty()) return null;
        
        int lowest = Integer.MAX_VALUE;
        for (Schematic.Target target : pending.values()) {
            lowest = Math.min(lowest, target.pos().getY());
        }
        
        
        
        BlockPos from = client.player == null ? null : client.player.getBlockPos();
        Schematic.Target first = null;
        Schematic.Target[] best = new Schematic.Target[3];
        double[] bestDistance = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        for (Schematic.Target target : pending.values()) {
            if (target.pos().getY() != lowest) continue;
            if (first == null) first = target;
            if (waiting.contains(target.pos())) continue;
            int kind = Math.min(2, waterFirst(target.state()));
            double distance = from == null ? 0 : target.pos().getSquaredDistance(from);
            if (distance < bestDistance[kind]) {
                bestDistance[kind] = distance;
                best[kind] = target;
            }
        }
        for (Schematic.Target target : best) {
            if (target != null) return target;
        }
        
        
        
        if (++waitingCycles > 100) {
            waitingCycles = 0;
            return null;
        }
        waiting.clear();
        return first;
    }

    

    private void tickVerification(MinecraftClient client) {
        if (verifyPos == null) return;
        if (placer.isCorrect(verifyPos, verifyState)) {
            complete(verifyPos);
            return;
        }
        verifyTicks--;
        if (verifyTicks > 0) return;
        verifyAttempts++;
        if (cfg.verbose && client.world != null) {
            BuilderLog.LOGGER.info(
                    "engine: verify failed at {} want {} have {}", shortPos(verifyPos),
                    verifyState, client.world.getBlockState(verifyPos));
        }
        int tries = failedPlacements.merge(verifyPos, 1, Integer::sum);
        BlockPos failedPos = verifyPos;
        verifyPos = null;
        if (tries >= 3) {
            failedPlacements.remove(failedPos);
            skip(failedPos, "placement did not stick");
        } else {
            actionCooldown = 2;
        }
    }

    
    public static String blockName(Block block) {
        Item item = block.asItem();
        if (item != net.minecraft.item.Items.AIR) {
            return net.minecraft.registry.Registries.ITEM.getId(item).toString();
        }
        return net.minecraft.registry.Registries.BLOCK.getId(block).toString();
    }

    private void skip(BlockPos pos, String reason) {
        Schematic.Target pendingTarget = pending.get(pos);
        if (pendingTarget != null && setblock(pos, pendingTarget.state())) {
            return;
        }
        Schematic.Target removed = pending.remove(pos);
        if (removed != null) {
            failed.add(pos);
            roamAttempts.remove(pos);
            navAttempts.remove(pos);
            failedCount++;
            if (cfg.verbose) {
                BuilderLog.warn("Skipping " + shortPos(pos) + ": " + reason);
            }
        }
        verifyPos = null;
        waiting.remove(pos);
        focus = null;
        stalled = 0;
        noProgressTicks = 0;
        status = "building " + placedCount + "/" + total + " (" + failedCount + " skipped)";
    }

    



    private boolean setblock(BlockPos pos, BlockState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!allowCommands || client.player == null || client.getNetworkHandler() == null) return false;
        if (!client.isInSingleplayer()
                || !client.player.getPermissions().hasPermission(
                        net.minecraft.command.DefaultPermissions.GAMEMASTERS)) {
            return false;
        }
        String id = stateString(state);
        client.getNetworkHandler().sendChatCommand(
                "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + id + " replace");
        verifyPos = pos;
        verifyState = state;
        verifyTicks = 20;
        verifyAttempts = 0;
        actionCooldown = 3;
        status = "setblock " + id + " at " + shortPos(pos);
        if (cfg.verbose) {
            BuilderLog.LOGGER.info("engine: setblock {} at {}", id, shortPos(pos));
        }
        return true;
    }

    




    public static String stateString(BlockState state) {
        StringBuilder sb = new StringBuilder(
                net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString());
        
        
        boolean fluid = state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA);
        List<String> properties = new ArrayList<>();
        for (Map.Entry<net.minecraft.state.property.Property<?>, Comparable<?>> entry
                : state.getEntries().entrySet()) {
            if (fluid && "level".equals(entry.getKey().getName())) continue;
            properties.add(entry.getKey().getName() + "=" + propertyName(entry.getKey(), entry.getValue()));
        }
        if (!properties.isEmpty()) {
            sb.append('[').append(String.join(",", properties)).append(']');
        }
        return sb.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(net.minecraft.state.property.Property property, Comparable value) {
        return property.name(value);
    }

    private boolean placeTempSupport(MinecraftClient client, BlockPos pos) {
        if (client.player == null || client.world == null || !cfg.useScaffold) return false;
        BlockState scaffold = scaffoldState(client);
        if (scaffold == null) return false;
        for (BlockPos candidate : supportSpots(pos)) {
            if (!supportFree(client, candidate)) continue;
            if (!placer.canReach(candidate) || !placer.canPlaceAt(candidate)) continue;
            if (placer.place(scaffold, candidate)) {
                scaffolds.add(candidate);
                if (cfg.verbose) {
                    BuilderLog.LOGGER.info(
                            "engine: support {} at {} for {}", blockName(scaffold.getBlock()),
                            shortPos(candidate), shortPos(pos));
                }
                return true;
            }
        }
        
        
        for (BlockPos candidate : supportSpots(pos)) {
            if (!supportFree(client, candidate) || !placer.canReach(candidate)) continue;
            if (raiseSupport(client, candidate, scaffold, 6)) return true;
        }
        return false;
    }

    
    private boolean raiseSupport(MinecraftClient client, BlockPos spot, BlockState scaffold, int depth) {
        if (depth < 0) return false;
        if (supportFree(client, spot) && placer.canReach(spot) && placer.canPlaceAt(spot)) {
            if (placer.place(scaffold, spot)) {
                scaffolds.add(spot);
                if (cfg.verbose) {
                    BuilderLog.LOGGER.info(
                            "engine: raised {} at {}", blockName(scaffold.getBlock()), shortPos(spot));
                }
                return true;
            }
        }
        BlockPos below = spot.down();
        if (!supportFree(client, below)) return false;
        return raiseSupport(client, below, scaffold, depth - 1);
    }

    private boolean supportFree(MinecraftClient client, BlockPos pos) {
        if (client.world == null || pending.containsKey(pos)) return false;
        if (client.player != null) {
            BlockPos feet = client.player.getBlockPos();
            if (pos.equals(feet) || pos.equals(feet.up())) return false;
        }
        BlockState at = client.world.getBlockState(pos);
        if (!at.isAir() && !at.isReplaceable()) return false;
        return client.world.getFluidState(pos).isEmpty();
    }

    
    private static List<BlockPos> supportSpots(BlockPos pos) {
        return List.of(pos.down(), pos.north(), pos.south(), pos.west(), pos.east(), pos.up());
    }

    private BlockState scaffoldState(MinecraftClient client) {
        if (client.player == null) return null;
        for (String id : cfg.scaffoldBlocks) {
            var identifier = net.minecraft.util.Identifier.tryParse(id);
            if (identifier == null) continue;
            Block block = net.minecraft.registry.Registries.BLOCK.get(identifier);
            if (block == Blocks.AIR) continue;
            Item item = block.asItem();
            for (int i = 0; i < 36; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == item) {
                    return block.getDefaultState();
                }
            }
        }
        return null;
    }

    

    private void beginCleanup() {
        if (!cfg.cleanupScaffold || scaffolds.isEmpty()) {
            finish();
            return;
        }
        cleanupOrder = new ArrayList<>(scaffolds);
        java.util.Collections.reverse(cleanupOrder);
        cleanupIndex = 0;
        cleanupTicks = 0;
        cleanupAge = 0;
        waitingCycles = 0;
        state = State.CLEANUP;
        status = "cleaning up " + cleanupOrder.size() + " temp blocks";
    }

    private void tickCleanup(MinecraftClient client) {
        if (cleanupIndex >= cleanupOrder.size()) {
            finish();
            return;
        }
        BlockPos pos = cleanupOrder.get(cleanupIndex);
        if (client.world == null || client.world.getBlockState(pos).isAir()) {
            scaffolds.remove(pos);
            cleanupIndex++;
            return;
        }
        if (placer.canReach(pos)) {
            boolean gone = placer.breakTick(pos);
            
            
            if (gone || ++cleanupTicks > 240) {
                if (!gone) placer.resetBreaking();
                cleanupTicks = 0;
                scaffolds.remove(pos);
                cleanupIndex++;
            }
            status = "cleaning up " + (cleanupOrder.size() - cleanupIndex) + " left";
            return;
        }
        
        List<BlockPos> path = planPath(client, pos);
        if (path != null && !path.isEmpty()) {
            movement.setPath(path);
            movement.tickPath();
            return;
        }
        if (movement.scaffoldTowards(pos, placer) == MovementController.ScaffoldResult.FAILED) {
            cleanupIndex++;
            status = "left " + (cleanupOrder.size() - cleanupIndex) + " temp blocks (unreachable)";
        }
    }

    private void finish() {
        movement.releaseKeys();
        movement.clearPath();
        state = State.DONE;
        status = "done: " + placedCount + "/" + total + (failedCount > 0 ? ", " + failedCount + " skipped" : "");
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
