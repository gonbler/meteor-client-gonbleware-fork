/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoTrap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<List<Block>> blocks = sgGeneral.add(
            new BlockListSetting.Builder().name("whitelist").description("Which blocks to use.")
                    .defaultValue(Blocks.OBSIDIAN, Blocks.NETHERITE_BLOCK).build());

    private final Setting<Integer> range =
            sgGeneral.add(new IntSetting.Builder().name("target-range")
                    .description("The range players can be targeted.").defaultValue(4).build());

    private final Setting<SortPriority> priority =
            sgGeneral.add(new EnumSetting.Builder<SortPriority>().name("target-priority")
                    .description("How to select the player to target.")
                    .defaultValue(SortPriority.LowestHealth).build());

    private final Setting<Integer> places = sgGeneral.add(new IntSetting.Builder().name("places")
            .description("How many places each tick").defaultValue(1).build());

    private final Setting<Boolean> grimBypass =
            sgGeneral.add(new BoolSetting.Builder().name("grim-bypass")
                    .description("Bypasses Grim for airplace.").defaultValue(true).build());

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-eat").description("Pauses while eating.").defaultValue(true).build());


    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render")
            .description("Renders an overlay where blocks will be placed.").defaultValue(true)
            .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").description("The side color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232, 10)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").description("The line color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232)).build());

    private PlayerEntity target;
    private Map<BlockPos, Long> placeCooldowns = new HashMap<>();

    public AutoTrap() {
        super(Categories.Combat, "auto-trap", "Traps people in a box to prevent them from moving.");
    }

    @Override
    public void onActivate() {
        target = null;
    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (target == null || TargetUtils.isBadTarget(target, range.get())) {
            target = TargetUtils.getPlayerTarget(range.get(), priority.get());
            if (TargetUtils.isBadTarget(target, range.get()))
                return;
        }

        if (target == null) {
            return;
        }

        int placed = 0;

        List<BlockPos> poses = getBlockPoses();

        if (poses.size() > 0 && !startPlace()) {
            return;
        }

        if (pauseEat.get() && mc.player.isUsingItem()) {
            return;
        }

        for (BlockPos pos : poses) {
            boolean isCrystalBlock = false;
            for (Direction dir : Direction.Type.HORIZONTAL) {
                if (pos.equals(target.getBlockPos().offset(dir))) {
                    isCrystalBlock = true;
                    break;
                }
            }

            if (isCrystalBlock) {
                continue;
            }

            if (placed > places.get()) {
                break;
            }

            if (place(pos)) {
                placed++;
            }
        }


        endPlace();
    }

    private List<BlockPos> getBlockPoses() {
        List<BlockPos> list = new ArrayList<>();

        Box boundingBox = target.getBoundingBox().shrink(0.05, 0.1, 0.05);
        double feetY = target.getY();

        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        for (BlockPos pos : BlockPos.iterate((int) Math.floor(feetBox.minX),
                (int) Math.floor(feetBox.minY), (int) Math.floor(feetBox.minZ),
                (int) Math.floor(feetBox.maxX), (int) Math.floor(feetBox.maxY),
                (int) Math.floor(feetBox.maxZ))) {

            for (int y = -1; y < 3; y++) {
                if (y < 2) {
                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        BlockPos actualPos = pos.add(0, y, 0).offset(dir);

                        list.add(actualPos);
                    }
                }

                list.add(pos.add(0, y, 0));
            }
        }

        return list;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get())
            return;

        if (target == null) {
            return;
        }

        int placed = 0;

        List<BlockPos> poses = getBlockPoses();

        for (BlockPos pos : poses) {

            boolean isCrystalBlock = false;
            for (Direction dir : Direction.Type.HORIZONTAL) {
                if (pos.equals(target.getBlockPos().offset(dir))) {
                    isCrystalBlock = true;
                }
            }

            if (isCrystalBlock) {
                continue;
            }

            if (placed > places.get()) {
                break;
            }

            

            if (BlockUtils.canPlace(pos, true) && (!placeCooldowns.containsKey(pos) || System.currentTimeMillis() - placeCooldowns.get(pos) > 50)) {
                placed++;

                event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }

    private boolean startPlace() {
        FindItemResult result = InvUtils.findInHotbar(itemStack -> {
            for (Block blocks : blocks.get()) {
                if (blocks.asItem() == itemStack.getItem())
                    return true;
            }
            return false;
        });

        if (!result.found()) {
            return false;
        }

        InvUtils.swap(result.slot(), true);

        return true;
    }

    private boolean place(BlockPos blockPos) {
        if (!BlockUtils.canPlace(blockPos, true)) {
            return false;
        }

        if (placeCooldowns.containsKey(blockPos)) {
            if (System.currentTimeMillis() - placeCooldowns.get(blockPos) < 50) {
                return false;
            }
        }

        placeCooldowns.put(blockPos, System.currentTimeMillis());

        if (grimBypass.get()) {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            new BlockPos(0, 0, 0), Direction.DOWN));
        }

        Vec3d eyes = mc.player.getEyePos();
        boolean inside = eyes.x > blockPos.getX() && eyes.x < blockPos.getX() + 1
                && eyes.y > blockPos.getY() && eyes.y < blockPos.getY() + 1
                && eyes.z > blockPos.getZ() && eyes.z < blockPos.getZ() + 1;
        int s = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                grimBypass.get() ? Hand.OFF_HAND : Hand.MAIN_HAND,
                new BlockHitResult(blockPos.toCenterPos(), Direction.DOWN, blockPos, inside), s));

        if (grimBypass.get()) {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            new BlockPos(0, 0, 0), Direction.DOWN));
        }

        return true;
    }

    private void endPlace() {
        InvUtils.swapBack();
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }

    public enum TopMode {
        Full, Top, Face, None
    }

    public enum BottomMode {
        Single, Platform, Full, None
    }
}
