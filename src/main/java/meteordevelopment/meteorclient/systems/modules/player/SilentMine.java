/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import static meteordevelopment.meteorclient.MeteorClient.FOLDER;
import static meteordevelopment.meteorclient.utils.Utils.resolveAddress;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.meteor.SilentMineFinishedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

public class SilentMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-switch").description("Automatically switches to the best tool.")
            .defaultValue(true).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("do-render")
            .description("Renders the blocks in queue to be broken.").defaultValue(true).build());

    private final Setting<Boolean> renderBlock = sgRender.add(new BoolSetting.Builder()
            .name("render-block").description("Whether to render the block being broken.")
            .defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).visible(renderBlock::get).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").description("The side color of the rendering.")
            .defaultValue(new SettingColor(225, 0, 0, 75))
            .visible(() -> renderBlock.get() && shapeMode.get().sides()).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").description("The line color of the rendering.")
            .defaultValue(new SettingColor(225, 0, 0, 255))
            .visible(() -> renderBlock.get() && shapeMode.get().lines()).build());

    private final Setting<Boolean> debugRenderPrimary =
            sgRender.add(new BoolSetting.Builder().name("debug-render-primary")
                    .description("Render the primary block differently for debugging.")
                    .defaultValue(true).build());

    private FastRebreakBlock rebreakBlock = null;
    private FastRebreakBlock singleBreakBlock = null;

    private final long initTime = System.nanoTime();
    private double currentGameTickCalculated = 0;

    public SilentMine() {
        super(Categories.Player, "silent-mine",
                "Allows you to mine blocks without holding a pickaxe");

        currentGameTickCalculated = (double) (System.nanoTime() - initTime)
                / (double) (java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50L));
    }

    @Override
    public void onDeactivate() {

    }

    int swapBackTimeout = 0;
    boolean needSwapBack = false;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        currentGameTickCalculated = (double) (System.nanoTime() - initTime)
                / (double) (java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50L));

        if (rebreakBlock != null) {
            if (mc.world.getBlockState(rebreakBlock.blockPos).isAir()) {
                rebreakBlock.beenAir = true;

                MeteorClient.EVENT_BUS
                        .post(new SilentMineFinishedEvent.Post(rebreakBlock.blockPos, true));
            }

            boolean outOfRange = Utils.distance(mc.player.getX() - 0.5,
                    mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
                    mc.player.getZ() - 0.5, rebreakBlock.blockPos.getX(),
                    rebreakBlock.blockPos.getY(),
                    rebreakBlock.blockPos.getZ()) > mc.player.getBlockInteractionRange() + 1.0;

            if (outOfRange) {
                rebreakBlock.cancelBreaking();
                rebreakBlock = null;
            }
        }

        if (isMiningSinglebreakBlock()
                && mc.world.getBlockState(singleBreakBlock.blockPos).isAir()) {
            MeteorClient.EVENT_BUS
                    .post(new SilentMineFinishedEvent.Post(singleBreakBlock.blockPos, false));

            stopMiningSingleBreak(false);
        }

        if (isMiningSinglebreakBlock() && singleBreakBlock.timesBroken > 5) {
            StartBreakingBlockEvent newEvent = new StartBreakingBlockEvent();
            newEvent.blockPos = singleBreakBlock.blockPos;
            newEvent.direction = singleBreakBlock.breakDreiction;

            singleBreakBlock = new FastRebreakBlock(newEvent, currentGameTickCalculated);
            singleBreakBlock.startBreaking();
        }

        if (rebreakBlock != null && rebreakBlock.timesBroken > 10 && !canRebreak()) {
            rebreakBlock.cancelBreaking();
            rebreakBlock = null;
        }

        int tempSlot = -1;
        if (isMiningSinglebreakBlock()) {
            BlockState blockState = mc.world.getBlockState(singleBreakBlock.blockPos);

            if (!blockState.isAir()) {
                FindItemResult result = InvUtils.findFastestTool(blockState); 

                if (result.found()) {
                    tempSlot = result.slot();
                }
            } 
        }

        if (mc.player.isOnGround() && (tempSlot == -1 || tempSlot == mc.player.getInventory().selectedSlot) && swapBackTimeout >= 0) {
            swapBackTimeout--;
        }

        // Update our doublemine block
        if (isMiningSinglebreakBlock()) {
            BlockState blockState = mc.world.getBlockState(singleBreakBlock.blockPos);

            if (!blockState.isAir()) {
                FindItemResult slot = InvUtils.findFastestTool(blockState);

                if (singleBreakBlock.isReady(currentGameTickCalculated, false) 
                        && !mc.player.isUsingItem()) {
                    if (autoSwitch.get() && slot.found()
                            && mc.player.getInventory().selectedSlot != slot.slot()) {
                        InvUtils.swap(slot.slot(), true);

                        swapBackTimeout = 3;

                        needSwapBack = true;

                        singleBreakBlock.timesBroken++;

                        MeteorClient.EVENT_BUS.post(
                                new SilentMineFinishedEvent.Pre(singleBreakBlock.blockPos, false));
                    }
                }
            }
        }

        // Update our primary mine block
        if (rebreakBlock != null) {
            BlockState blockState = mc.world.getBlockState(rebreakBlock.blockPos);

            if (!blockState.isAir()) {
                FindItemResult slot = InvUtils.findFastestTool(blockState);

                if (rebreakBlock.isReady(currentGameTickCalculated, true)
                        && !mc.player.isUsingItem()) {
                    if (autoSwitch.get() && slot.found()
                            && mc.player.getInventory().selectedSlot != slot.slot()
                            && !needSwapBack) {
                        InvUtils.swap(slot.slot(), true);

                        needSwapBack = true;
                    }

                    MeteorClient.EVENT_BUS
                            .post(new SilentMineFinishedEvent.Pre(rebreakBlock.blockPos, true));

                    rebreakBlock.tryBreak();

                }
            }
        }

        if (needSwapBack && (swapBackTimeout <= 0 || singleBreakBlock == null)) {
            InvUtils.swapBack();
            needSwapBack = false;
        }
    }

    @EventHandler
    public void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!BlockUtils.canBreak(event.blockPos))
            return;

        event.cancel();

        if ((rebreakBlock != null && event.blockPos.equals(rebreakBlock.blockPos))
                || (singleBreakBlock != null && event.blockPos.equals(singleBreakBlock.blockPos))) {
            return;
        }

        if (singleBreakBlock == null) {
            singleBreakBlock = new FastRebreakBlock(event, currentGameTickCalculated - 0.1);

            singleBreakBlock.startBreaking();

            if (rebreakBlock != null) {
                StartBreakingBlockEvent newEvent = new StartBreakingBlockEvent();
                newEvent.blockPos = rebreakBlock.blockPos;
                newEvent.direction = rebreakBlock.breakDreiction;

                rebreakBlock = new FastRebreakBlock(newEvent, currentGameTickCalculated);
                rebreakBlock.startBreaking();
            }
        }

        if ((rebreakBlock != null && event.blockPos.equals(rebreakBlock.blockPos))
                || (singleBreakBlock != null && event.blockPos.equals(singleBreakBlock.blockPos))) {
            return;
        }

        if (rebreakBlock != null) {
            if (singleBreakBlock != null) {
                rebreakBlock = null;
            }
        }

        if (rebreakBlock == null) {
            rebreakBlock = new FastRebreakBlock(event, currentGameTickCalculated);

            rebreakBlock.startBreaking();
        }
    }

    public void silentBreakBlock(BlockPos pos) {
        silentBreakBlock(pos, Direction.UP);
    }

    public void silentBreakBlock(BlockPos pos, Direction direction) {
        if (!isActive()) {
            return;
        }

        if (pos == null) {
            return;
        }

        StartBreakingBlockEvent breakEvent = new StartBreakingBlockEvent();
        breakEvent.blockPos = pos;
        breakEvent.direction = direction;

        onStartBreakingBlock(breakEvent);
    }

    public boolean isMiningSinglebreakBlock() {
        return singleBreakBlock != null;
    }

    public boolean isMiningRebreakBlock() {
        return rebreakBlock != null && !rebreakBlock.beenAir;
    }

    public void stopMiningSingleBreak(boolean sendAbort) {
        if (isMiningSinglebreakBlock()) {
            if (sendAbort) {
                singleBreakBlock.cancelBreaking();
            }
            singleBreakBlock = null;
        }
    }

    public BlockPos getSingleBreakBlockPos() {
        if (singleBreakBlock == null) {
            return null;
        }

        return singleBreakBlock.blockPos;
    }

    public double getSingleBreakBlockProgress() {
        if (singleBreakBlock == null) {
            return 0;
        }

        return singleBreakBlock.getBreakProgress(currentGameTickCalculated);
    }

    public BlockPos getRebreakBlockPos() {
        if (rebreakBlock == null) {
            return null;
        }

        return rebreakBlock.blockPos;
    }

    public double getRebreakBlockProgress() {
        if (rebreakBlock == null) {
            return 0;
        }

        return rebreakBlock.getBreakProgress(currentGameTickCalculated);
    }

    public boolean canRebreak() {
        if (rebreakBlock == null) {
            return false;
        }

        return rebreakBlock.beenAir;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get()) {
            if (rebreakBlock != null) {
                rebreakBlock.render(event, currentGameTickCalculated, true);
            }

            if (singleBreakBlock != null) {
                singleBreakBlock.render(event, currentGameTickCalculated, false);
            }
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Send event) {
        // https://github.com/GrimAnticheat/Grim/issues/1296
        /*
         * if (event.packet instanceof PlayerActionC2SPacket packet && packet.getAction() ==
         * PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) { mc.getNetworkHandler() .sendPacket(new
         * PlayerActionC2SPacket( PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
         * packet.getPos().up(), packet.getDirection())); }
         */
    }

    class FastRebreakBlock {
        public BlockPos blockPos;

        public Direction breakDreiction;

        public boolean started = false;

        public int timesBroken = 0;

        public boolean beenAir = false;

        private double destroyProgressStart = 0;

        public FastRebreakBlock(StartBreakingBlockEvent event, double currentTick) {
            blockPos = event.blockPos;

            breakDreiction = event.direction;

            destroyProgressStart = currentTick;
        }

        public boolean isReady(double currentTick, boolean isRebreak) {
            double breakProgressSingleTick = getBreakProgress(destroyProgressStart + 1.0);
            double threshold = isRebreak ? 0.7 : 1.0 - breakProgressSingleTick;

            return getBreakProgress(currentTick) >= threshold || timesBroken > 0;
        }

        public void startBreaking() {
            int s1 = mc.world.getPendingUpdateManager().incrementSequence().getSequence();
            int s2 = mc.world.getPendingUpdateManager().incrementSequence().getSequence();
            int s3 = mc.world.getPendingUpdateManager().incrementSequence().getSequence();
            int s4 = mc.world.getPendingUpdateManager().incrementSequence().getSequence();
            int s5 = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, breakDreiction, s1));

            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos,
                            breakDreiction, s2));

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, breakDreiction, s3));

            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos,
                            breakDreiction, s4));

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, breakDreiction, s5));

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, breakDreiction));

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, breakDreiction));

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, breakDreiction));

            started = true;
        }

        public void tryBreak() {
            int s = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, breakDreiction, s));

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, breakDreiction));

            timesBroken++;
        }

        public void cancelBreaking() {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, breakDreiction));
        }

        public double getBreakProgress(double currentTick) {
            BlockState state = mc.world.getBlockState(blockPos);

            FindItemResult slot = InvUtils.findFastestTool(mc.world.getBlockState(blockPos));

            double breakingSpeed = BlockUtils.getBlockBreakingSpeed(
                    slot.found() ? slot.slot() : mc.player.getInventory().selectedSlot, state);

            return Math.min(BlockUtils.getBreakDelta(breakingSpeed, state)
                    * (double) (currentTick - destroyProgressStart), 1.0);
        }

        public void render(Render3DEvent event, double currentTick, boolean isPrimary) {
            VoxelShape shape = mc.world.getBlockState(blockPos).getOutlineShape(mc.world, blockPos);
            if (shape == null || shape.isEmpty()) {
                event.renderer.box(blockPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                return;
            }

            Box orig = shape.getBoundingBox();

            // The primary block can be broken at 0.7 completion, so speed up the visual by the
            // reciprical
            double shrinkFactor = 1d - (isPrimary ? getBreakProgress(currentTick) * (1 / 0.7)
                    : getBreakProgress(currentTick));
            BlockPos pos = blockPos;


            Box box = orig.shrink(orig.getLengthX() * shrinkFactor,
                    orig.getLengthY() * shrinkFactor, orig.getLengthZ() * shrinkFactor);

            double xShrink = (orig.getLengthX() * shrinkFactor) / 2;
            double yShrink = (orig.getLengthY() * shrinkFactor) / 2;
            double zShrink = (orig.getLengthZ() * shrinkFactor) / 2;

            double x1 = pos.getX() + box.minX + xShrink;
            double y1 = pos.getY() + box.minY + yShrink;
            double z1 = pos.getZ() + box.minZ + zShrink;
            double x2 = pos.getX() + box.maxX + xShrink;
            double y2 = pos.getY() + box.maxY + yShrink;
            double z2 = pos.getZ() + box.maxZ + zShrink;

            Color color = sideColor.get();

            if (debugRenderPrimary.get() && isPrimary) {
                color = Color.ORANGE.a(40);
            }

            event.renderer.box(x1, y1, z1, x2, y2, z2, color, lineColor.get(), shapeMode.get(), 0);
        }
    }
}
