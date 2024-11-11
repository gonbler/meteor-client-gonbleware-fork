/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
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

    private final Setting<Boolean> doubleMine = sgGeneral.add(new BoolSetting.Builder()
            .name("double-mine").description("Allows you to mine 2 blocks at once.")
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

    public final List<FastRebreakBlock> blockQueue = new ArrayList<>();

    public SilentMine() {
        super(Categories.Player, "silent-mine",
                "Allows you to mine blocks without holding a pickaxe");
    }

    @Override
    public void onDeactivate() {
        blockQueue.clear();
    }

    int swapBackTimeout = 0;
    boolean needSwapBack = false;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        blockQueue.removeIf(x -> {
            boolean outOfRange = Utils.distance(mc.player.getX() - 0.5,
                    mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
                    mc.player.getZ() - 0.5, x.blockPos.getX() + x.breakDreiction.getOffsetX(),
                    x.blockPos.getY() + x.breakDreiction.getOffsetY(),
                    x.blockPos.getZ() + x.breakDreiction.getOffsetZ()) > mc.player
                            .getBlockInteractionRange();

            if (mc.world.getBlockState(x.blockPos).isAir()) {
                x.beenAir = true;
            }

            if ((mc.world.getBlockState(x.blockPos).isAir())
                    && blockQueue.indexOf(x) != blockQueue.size() - 1) {

                x.cancelBreaking();

                return true;
            }

            if (x.timesBroken > 40 && !x.beenAir) {
                return true;
            }

            if (outOfRange) {
                return true;
            }

            return false;
        });

        for (FastRebreakBlock block : blockQueue) {
            BlockState blockState = mc.world.getBlockState(block.blockPos);

            if (blockState.isAir()) {
                continue;
            }

            // TODO: This?
            /*
             * if (!block.started) { if (block.breakTimeout <= 0) { block.startBreaking(); } else {
             * block.breakTimeout--; } continue; }
             */

            FindItemResult slot = InvUtils.findFastestTool(blockState);

            double breakingSpeed = BlockUtils.getBlockBreakingSpeed(
                    slot.found() ? slot.slot() : mc.player.getInventory().selectedSlot, blockState);

            block.progress += BlockUtils.getBreakDelta(breakingSpeed, blockState);

            if (block.isReady()) {
                if (autoSwitch.get() && !needSwapBack) {
                    if (slot.found() && mc.player.getInventory().selectedSlot != slot.slot()) {
                        mc.player.networkHandler
                                .sendPacket(new UpdateSelectedSlotC2SPacket(slot.slot()));

                        swapBackTimeout = 5;
                        needSwapBack = true;
                    }
                }

                if (block.needBroken) {
                    block.tryBreak();
                }
            }
        }

        if (needSwapBack && (swapBackTimeout-- <= 0 || blockQueue.size() > 1)) {
            mc.player.networkHandler.sendPacket(
                    new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            needSwapBack = false;
        }
    }

    @EventHandler
    public void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!BlockUtils.canBreak(event.blockPos))
            return;

        for (FastRebreakBlock block : blockQueue) {
            if (event.blockPos.equals(block.blockPos)) {
                return;
            }
        }

        event.cancel();

        // Clear and stop breaking every block in the queue
        blockQueue.removeIf(x -> {
            x.cancelBreaking();

            if (doubleMine.get()) {
                if (blockQueue.indexOf(x) != blockQueue.size() - 1) {
                    x.needBroken = false;
                    return false;
                }
            }

            return true;
        });

        FastRebreakBlock block = new FastRebreakBlock(event);

        blockQueue.add(block);

        block.startBreaking();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get()) {
            for (FastRebreakBlock block : blockQueue) {
                block.render(event);
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

        public int breakTimeout = 0;

        public boolean started = false;

        public double progress = 0.0;

        public int timesBroken = 0;

        public boolean beenAir = false;

        public boolean needBroken = true;

        public boolean isReady() {
            return progress >= 1.0 || timesBroken > 0;
        }

        public FastRebreakBlock(StartBreakingBlockEvent event) {
            blockPos = event.blockPos;

            breakDreiction = event.direction;
        }

        public void startBreaking() {
            FindItemResult slot = InvUtils.findFastestTool(mc.world.getBlockState(blockPos));

            boolean needSwapBack = false;
            if (autoSwitch.get() && !needSwapBack) {
                if (slot.found() && mc.player.getInventory().selectedSlot != slot.slot()) {
                    InvUtils.swap(slot.slot(), true);
                    needSwapBack = true;
                }
            }

            double breakingSpeed = BlockUtils.getBlockBreakingSpeed(
                    slot.found() ? slot.slot() : mc.player.getInventory().selectedSlot,
                    mc.world.getBlockState(blockPos));

            progress += BlockUtils.getBreakDelta(breakingSpeed, mc.world.getBlockState(blockPos));

            int s = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, breakDreiction));

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, breakDreiction, s));

            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, breakDreiction));

            started = true;

            if (needSwapBack) {
                InvUtils.swapBack();
            }
        }

        public void tryBreak() {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, breakDreiction));

            timesBroken++;
        }

        public void cancelBreaking() {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, breakDreiction));
        }

        public void render(Render3DEvent event) {
            VoxelShape shape = mc.world.getBlockState(blockPos).getOutlineShape(mc.world, blockPos);
            if (shape == null || shape.isEmpty()) {
                event.renderer.box(blockPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                return;
            }

            Box orig = shape.getBoundingBox();

            double progressNormalised = progress > 1 ? 1 : progress;
            double shrinkFactor = 1d - progressNormalised;
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

            event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor.get(), lineColor.get(),
                    shapeMode.get(), 0);
        }
    }
}
