/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.MeteorClient;
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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.List;

public class AutoTrap extends Module {
    private static final double GRAVITY = -0.08;
    private static final double TERMINAL_VELOCITY = -3.92;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPrediction = settings.createGroup("Prediction");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks =
            sgGeneral.add(new BlockListSetting.Builder().name("whitelist")
                    .description("Which blocks to use.").defaultValue(Blocks.OBSIDIAN).build());

    private final Setting<Integer> range =
            sgGeneral.add(new IntSetting.Builder().name("target-range")
                    .description("The range players can be targeted.").defaultValue(4).build());

    private final Setting<SortPriority> priority =
            sgGeneral.add(new EnumSetting.Builder<SortPriority>().name("target-priority")
                    .description("How to select the player to target.")
                    .defaultValue(SortPriority.LowestHealth).build());

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-eat").description("Pauses while eating.").defaultValue(true).build());

    private final Setting<Boolean> prediction =
            sgPrediction.add(new BoolSetting.Builder().name("predicition")
                    .description("Places blocks where the player will be in the future.")
                    .defaultValue(true).build());

    private final Setting<Double> predictionSeconds = sgPrediction.add(new DoubleSetting.Builder()
            .name("prediction-amount")
            .description(
                    "The number of seconds to calculate movement into the future. Should be around 1.5x your ping.")
            .defaultValue(0.1).min(0).sliderMax(0.4).build());

    // Render settings

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

    public AutoTrap() {
        super(Categories.Combat, "auto-trap", "Traps people in a box to prevent them from moving.");
    }

    @Override
    public void onActivate() {
        target = null;
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

        if (pauseEat.get() && mc.player.isUsingItem()) {
            return;
        }

        Item useItem = findUseItem();

        if (useItem == null) {
            return;
        }

        List<BlockPos> placePoses = getBlockPoses();

        Vec3d predictedPoint = target.getEyePos();

        if (prediction.get()) {
            predictedPoint = predictPosition(target);
        }

        Vec3d point = predictedPoint;
        placePoses.sort((x, y) -> {
            return Double.compare(x.getSquaredDistance(point), y.getSquaredDistance(point));
        });

        List<BlockPos> actualPlacePositions =
                MeteorClient.BLOCK.filterCanPlace(placePoses.stream()).toList();

        if (!MeteorClient.BLOCK.beginPlacement(actualPlacePositions, useItem)) {
            return;
        }

        

        actualPlacePositions.forEach(blockPos -> {
            boolean isCrystalBlock = false;
            for (Direction dir : Direction.Type.HORIZONTAL) {
                if (blockPos.equals(target.getBlockPos().offset(dir))) {
                    isCrystalBlock = true;
                }
            }

            if (isCrystalBlock) {
                return;
            }

            MeteorClient.BLOCK.placeBlock(blockPos);
        });

        MeteorClient.BLOCK.endPlacement();
    }

    private Item findUseItem() {
        FindItemResult result = InvUtils.findInHotbar(itemStack -> {
            for (Block blocks : blocks.get()) {
                if (blocks.asItem() == itemStack.getItem()) {
                    return true;
                }
            }

            return false;
        });

        if (!result.found()) {
            return null;
        }

        return mc.player.getInventory().getStack(result.slot()).getItem();
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

        List<BlockPos> poses = getBlockPoses();

        Vec3d predictedPoint = target.getEyePos();

        if (prediction.get()) {
            predictedPoint = predictPosition(target);
        }

        Vec3d point = predictedPoint;
        poses.sort((x, y) -> {
            return Double.compare(x.getSquaredDistance(point), y.getSquaredDistance(point));
        });

        event.renderer.box(Box.of(predictedPoint, 0.1, 0.1, 0.1), Color.RED.a(50), Color.RED.a(50),
                ShapeMode.Both, 0);

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

            if (BlockUtils.canPlace(pos, true)) {
                event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }

    public Vec3d predictPosition(PlayerEntity player) {
        Vec3d currentPosition = player.getPos();
        Vec3d currentVelocity = player.getVelocity();

        int ticks = (int) Math.ceil(predictionSeconds.get() * 20);

        Vec3d predictedPosition = currentPosition;
        Vec3d velocity = currentVelocity;

        for (int i = 0; i < ticks; i++) {
            // Flying causes little to no velo changes
            if (!player.isFallFlying()) {
                // Gravity
                velocity = velocity.add(0, GRAVITY, 0);

                // Don't fall more than terminal
                if (velocity.y < TERMINAL_VELOCITY) {
                    velocity = new Vec3d(velocity.x, TERMINAL_VELOCITY, velocity.z);
                }
            }

            predictedPosition = predictedPosition.add(velocity);

            // Vertical movement
            double groundLevel = getGroundLevel(predictedPosition);
            if (predictedPosition.y <= groundLevel) {
                predictedPosition =
                        new Vec3d(predictedPosition.x, groundLevel, predictedPosition.z);
                velocity = new Vec3d(velocity.x, 0, velocity.z);
            }
        }

        return predictedPosition;
    }


    // Calculate the position where they will land yknow
    private double getGroundLevel(Vec3d position) {
        Vec3d rayStart = new Vec3d(position.x, position.y, position.z);
        Vec3d rayEnd = new Vec3d(position.x, position.y - 256, position.z);

        BlockHitResult hitResult = mc.world
                .raycast(new RaycastContext(rayStart, rayEnd, RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE, (ShapeContext) null));

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            return hitResult.getPos().y;
        }

        return 0.0;
    }

    public enum TopMode {
        Full, Top, Face, None
    }

    public enum BottomMode {
        Single, Platform, Full, None
    }
}
