/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.managers.RotationManager;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class Surround extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-eat").description("Pauses while eating.").defaultValue(true).build());

    private final Setting<Boolean> protect = sgGeneral.add(new BoolSetting.Builder().name("protect")
            .description(
                    "Attempts to break crystals around surround positions to prevent surround break.")
            .defaultValue(true).build());

    private final Setting<AutoSelfTrapMode> autoSelfTrapMode =
            sgGeneral.add(new EnumSetting.Builder<AutoSelfTrapMode>().name("auto-self-trap-mode")
                    .description("When to build double high").defaultValue(AutoSelfTrapMode.Smart)
                    .build());

    private final Setting<Boolean> selfTrapHead = sgGeneral.add(new BoolSetting.Builder()
            .name("self-trap-head")
            .description("Places a block above your head to prevent you from velo failing upwards")
            .defaultValue(true).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render")
            .description("Renders a block overlay when you try to place obsidian.")
            .defaultValue(true).build());

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
            .name("fadeTime").description("How many seconds it takes to fade.").defaultValue(0.2)
            .min(0).sliderMax(1.0).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor =
            sgRender.add(new ColorSetting.Builder().name("side-color")
                    .description("The side color.").defaultValue(new SettingColor(85, 0, 255, 40))
                    .visible(() -> render.get() && shapeMode.get() != ShapeMode.Lines).build());

    private final Setting<SettingColor> lineColor =
            sgRender.add(new ColorSetting.Builder().name("line-color")
                    .description("The line color.").defaultValue(new SettingColor(255, 255, 255, 60))
                    .visible(() -> render.get() && shapeMode.get() != ShapeMode.Sides).build());

    private List<BlockPos> placePoses = new ArrayList<>();

    private Map<BlockPos, Long> renderLastPlacedBlock = new HashMap<>();

    private long lastTimeOfCrystalNearHead = 0;
    private long lastAttackTime = 0;

    public Surround() {
        super(Categories.Combat, "surround",
                "Surrounds you in blocks to prevent massive crystal damage.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {

    }

    // Render

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        update();

        if (render.get()) {
            draw(event);
        }
    }

    private void draw(Render3DEvent event) {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<BlockPos, Long> entry : renderLastPlacedBlock.entrySet()) {
            if (currentTime - entry.getValue() > fadeTime.get() * 1000) {
                continue;
            }

            double time = (currentTime - entry.getValue()) / 1000.0;

            double timeCompletion = time / fadeTime.get();

            Color fadedSideColor = sideColor.get().copy().a((int) (sideColor.get().a * (1 - timeCompletion)));
            Color fadedLineColor = lineColor.get().copy().a((int) (lineColor.get().a * (1 - timeCompletion)));

            event.renderer.box(entry.getKey(), fadedSideColor, fadedLineColor, shapeMode.get(), 0);
        }
    }

    private void update() {
        placePoses.clear();

        long currentTime = System.currentTimeMillis();

        Box boundingBox = mc.player.getBoundingBox().shrink(0.05, 0.1, 0.05); // Tighter bounding
                                                                              // box
        int feetY = mc.player.getBlockPos().getY();

        SilentMine silentMine = Modules.get().get(SilentMine.class);

        // Calculate the corners of the bounding box at the feet level
        int minX = (int) Math.floor(boundingBox.minX);
        int maxX = (int) Math.floor(boundingBox.maxX);
        int minZ = (int) Math.floor(boundingBox.minZ);
        int maxZ = (int) Math.floor(boundingBox.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos feetPos = new BlockPos(x, feetY, z);
                // BlockState feetState = mc.world.getBlockState(feetPos);

                // Iterate over adjacent blocks around the player's feet
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                        if (Math.abs(offsetX) + Math.abs(offsetZ) != 1) {
                            continue;
                        }

                        BlockPos adjacentPos = feetPos.add(offsetX, 0, offsetZ);
                        BlockState adjacentState = mc.world.getBlockState(adjacentPos);

                        // Don't place if we're mining that block
                        if (adjacentPos.equals(silentMine.getRebreakBlockPos())
                                || adjacentPos.equals(silentMine.getDelayedDestroyBlockPos())) {
                            // continue;
                        }

                        if (adjacentState.isAir() || adjacentState.isReplaceable()) {
                            placePoses.add(adjacentPos);
                        }

                        if (autoSelfTrapMode.get() == AutoSelfTrapMode.None) {
                            continue;
                        }

                        BlockPos facePlacePos = adjacentPos.add(0, 1, 0);
                        boolean shouldBuildDoubleHigh =
                                autoSelfTrapMode.get() == AutoSelfTrapMode.Always;

                        Box box = new Box(facePlacePos.getX() - 1, facePlacePos.getY() - 1,
                                facePlacePos.getZ() - 1, facePlacePos.getX() + 1,
                                facePlacePos.getY() + 1, facePlacePos.getZ() + 1);

                        if (autoSelfTrapMode.get() == AutoSelfTrapMode.Smart) {
                            Predicate<Entity> entityPredicate =
                                    entity -> entity instanceof EndCrystalEntity;

                            for (Entity crystal : mc.world.getOtherEntities(null, box,
                                    entityPredicate)) {
                                lastTimeOfCrystalNearHead = currentTime;
                                break;
                            }

                            if ((currentTime - lastTimeOfCrystalNearHead) / 1000.0 < 1.0) {
                                shouldBuildDoubleHigh = true;
                            }
                        }

                        if (shouldBuildDoubleHigh) {
                            BlockState facePlaceState = mc.world.getBlockState(facePlacePos);

                            if (facePlaceState.isAir() || facePlaceState.isReplaceable()) {
                                placePoses.add(facePlacePos);
                            }
                        }
                    }
                }

                // Blocks below players feet
                BlockPos belowFeetPos = new BlockPos(x, feetY - 1, z);
                BlockState belowFeetState = mc.world.getBlockState(belowFeetPos);

                // Don't place if we're mining that block
                if (belowFeetPos.equals(silentMine.getRebreakBlockPos())
                        || belowFeetPos.equals(silentMine.getDelayedDestroyBlockPos())) {
                    continue;
                }

                if (belowFeetState.isAir() || belowFeetState.isReplaceable()) {
                    placePoses.add(belowFeetPos);
                }


            }
        }

        if (selfTrapHead.get()) {
            placePoses.add(mc.player.getBlockPos().offset(Direction.UP, 2));
        }

        if (pauseEat.get() && mc.player.isUsingItem()) {
            return;
        }

        if (protect.get()) {
            placePoses.forEach(blockPos -> {
                Box box = new Box(blockPos.getX() - 1, blockPos.getY() - 1, blockPos.getZ() - 1,
                        blockPos.getX() + 1, blockPos.getY() + 1, blockPos.getZ() + 1);

                Predicate<Entity> entityPredicate = entity -> entity instanceof EndCrystalEntity;

                Entity blocking = null;

                for (Entity crystal : mc.world.getOtherEntities(null, box, entityPredicate)) {
                    blocking = crystal;
                    break;
                }

                if (blocking != null && System.currentTimeMillis() - lastAttackTime >= 50) {
                    MeteorClient.ROTATION.requestRotation(blocking.getPos(), 11);

                    if (!MeteorClient.ROTATION.lookingAt(blocking.getBoundingBox())
                            && RotationManager.lastGround) {
                        MeteorClient.ROTATION.snapAt(blocking.getPos());
                    }

                    if (MeteorClient.ROTATION.lookingAt(blocking.getBoundingBox())) {
                        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket
                                .attack(blocking, mc.player.isSneaking()));
                        blocking.discard();
                    }
                }
            });
        }

        List<BlockPos> actualPlacePositions =
                MeteorClient.BLOCK.filterCanPlace(placePoses.stream()).toList();

        if (!MeteorClient.BLOCK.beginPlacement(actualPlacePositions, Items.OBSIDIAN)) {
            return;
        }

        actualPlacePositions.forEach(blockPos -> {
            MeteorClient.BLOCK.placeBlock(blockPos);

            renderLastPlacedBlock.put(blockPos, currentTime);
        });

        MeteorClient.BLOCK.endPlacement();
    }

    public enum AutoSelfTrapMode {
        None, Smart, Always
    }
}
