/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;


public class BreakIndicators extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> completionAmount =
            sgGeneral.add(new DoubleSetting.Builder().name("full-completion-amount")
                    .description("Determines how fast rendering increases. Smaller is faster.")
                    .defaultValue(1.0).min(0).sliderMax(1.5).build());

    private final Setting<Double> removeCompletionAmount = sgGeneral.add(new DoubleSetting.Builder()
            .name("force-remove-completion-amount")
            .description(
                    "Determines how long it takes to forcibly remove a block from being rendered.")
            .defaultValue(1.3).min(0.0).sliderMax(1.5).build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-friends").description("Doesn't render blocks that friends are breaking.")
            .defaultValue(false).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("do-render")
            .description("Renders the blocks in queue to be broken.").defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).visible(render::get).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").description("The side color of the rendering.")
            .defaultValue(new SettingColor(255, 0, 80, 10))
            .visible(() -> render.get() && shapeMode.get().sides()).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").description("The line color of the rendering.")
            .defaultValue(new SettingColor(255, 255, 255, 40))
            .visible(() -> render.get() && shapeMode.get().lines()).build());

    private final Queue<BlockBreak> _breakPackets = new ConcurrentLinkedQueue<>();
    private final Map<BlockPos, BlockBreak> breakStartTimes = new HashMap<>();

    public BreakIndicators() {
        super(Categories.Render, "break-indicators",
                "Renders the progress of a block being broken.");
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof BlockBreakingProgressS2CPacket packet) {
            Entity entity = mc.world.getEntityById(packet.getEntityId());

            _breakPackets.add(new BlockBreak(packet.getPos(),
                    RenderUtils.getCurrentGameTickCalculated(), entity));
        }
    }

    public boolean isBlockBeingBroken(BlockPos blockPos) {
        return breakStartTimes.containsKey(blockPos);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double currentGameTickCalculated = RenderUtils.getCurrentGameTickCalculated();

        // Concurrent queue implementation to not have to block the networking thread
        while (!_breakPackets.isEmpty()) {
            BlockBreak breakEvent = _breakPackets.remove();

            if (!breakStartTimes.containsKey(breakEvent.blockPos)) {
                breakStartTimes.put(breakEvent.blockPos, breakEvent);
            }
        }

        Iterator<Map.Entry<BlockPos, BlockBreak>> iterator = breakStartTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BlockBreak> entry = iterator.next();
            // Remove block if it is
            // - Air (broken)
            // - Past removeCompletionAmount
            // - Can't be broken (such as water)
            if (mc.world.getBlockState(entry.getKey()).isAir()
                    || entry.getValue().getBreakProgress(
                            currentGameTickCalculated) > removeCompletionAmount.get()
                    || !BlockUtils.canBreak(entry.getKey())) {

                iterator.remove();
            }
        }

        for (Map.Entry<BlockPos, BlockBreak> entry : breakStartTimes.entrySet()) {
            if (ignoreFriends.get() && entry.getValue().entity != null
                    && entry.getValue().entity instanceof PlayerEntity player
                    && Friends.get().isFriend(player)) {
                continue;
            }

            entry.getValue().renderBlock(event, currentGameTickCalculated);
        }
    }

    private class BlockBreak {
        public BlockPos blockPos;

        public double startTick;

        public Entity entity;

        public BlockBreak(BlockPos blockPos, double startTick, Entity entity) {
            this.blockPos = blockPos;
            this.startTick = startTick;
            this.entity = entity;
        }

        public void renderBlock(Render3DEvent event, double currentTick) {
            VoxelShape shape = mc.world.getBlockState(blockPos).getOutlineShape(mc.world, blockPos);
            if (shape == null || shape.isEmpty()) {
                event.renderer.box(blockPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                return;
            }

            Box orig = shape.getBoundingBox();

            double shrinkFactor = Math.clamp(
                    1d - (getBreakProgress(currentTick) * (1 / completionAmount.get())), 0, 1.0);
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

            event.renderer.box(x1, y1, z1, x2, y2, z2, color, lineColor.get(), shapeMode.get(), 0);
        }

        private double getBreakProgress(double currentTick) {
            BlockState state = mc.world.getBlockState(blockPos);

            FindItemResult slot = InvUtils.findFastestTool(mc.world.getBlockState(blockPos));

            double breakingSpeed = BlockUtils.getBlockBreakingSpeedNoOnGround(
                    slot.found() ? slot.slot() : mc.player.getInventory().selectedSlot, state);

            return BlockUtils.getBreakDelta(breakingSpeed, state)
                    * (double) (currentTick - startTick);
        }
    }
}
