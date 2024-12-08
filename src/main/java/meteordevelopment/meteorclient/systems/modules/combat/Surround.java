/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Surround extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Integer> places = sgGeneral.add(new IntSetting.Builder().name("places")
            .description("Places to do each tick.").min(1).defaultValue(1).build());

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-eat").description("Pauses while eating.").defaultValue(true).build());

    private final Setting<Boolean> grimBypass =
            sgGeneral.add(new BoolSetting.Builder().name("grim-bypass")
                    .description("Bypasses Grim for airplace.").defaultValue(true).build());

    private final Setting<Boolean> protect = sgGeneral.add(new BoolSetting.Builder().name("protect")
            .description(
                    "Attempts to break crystals around surround positions to prevent surround break.")
            .defaultValue(true).build());

    private final Setting<Double> placeTime =
            sgGeneral.add(new DoubleSetting.Builder().name("place-time")
                    .description("Time between places").defaultValue(0.06).min(0).max(0.5).build());

    private final Setting<AutoSelfTrapMode> autoSelfTrapMode =
            sgGeneral.add(new EnumSetting.Builder<AutoSelfTrapMode>().name("auto-self-trap-mode")
                    .description("When to build double high").defaultValue(AutoSelfTrapMode.Smart)
                    .build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render")
            .description("Renders a block overlay where the obsidian will be placed.")
            .defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> normalSideColor = sgRender.add(new ColorSetting.Builder()
            .name("normal-side-color").description("The side color for normal blocks.")
            .defaultValue(new SettingColor(0, 255, 238, 12))
            .visible(() -> render.get() && shapeMode.get() != ShapeMode.Lines).build());

    private final Setting<SettingColor> normalLineColor = sgRender.add(new ColorSetting.Builder()
            .name("normal-line-color").description("The line color for normal blocks.")
            .defaultValue(new SettingColor(0, 255, 238, 100))
            .visible(() -> render.get() && shapeMode.get() != ShapeMode.Sides).build());

    // private final BlockPos.Mutable placePos = new BlockPos.Mutable();
    // private final BlockPos.Mutable renderPos = new BlockPos.Mutable();
    // private final BlockPos.Mutable testPos = new BlockPos.Mutable();
    // private int ticks;

    private long lastPlaceTimeMS = 0;

    private List<BlockPos> placePoses = new ArrayList<>();

    private Vec3d snapPos = null;

    private long lastTimeOfCrystalNearHead = 0;

    public Surround() {
        super(Categories.Combat, "surround",
                "Surrounds you in blocks to prevent massive crystal damage.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (snapPos != null) {
            MeteorClient.ROTATION.snapAt(snapPos, true);

            snapPos = null;
        }
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
        for (BlockPos pos : placePoses) {
            event.renderer.box(pos, normalSideColor.get(), normalLineColor.get(), shapeMode.get(),
                    0);
        }
    }

    private void update() {
        placePoses.clear();

        Box boundingBox = mc.player.getBoundingBox().shrink(0.05, 0.1, 0.05); // Tighter bounding
                                                                              // box
        int feetY = mc.player.getBlockPos().getY();

        SilentMine silentMine = Modules.get().get(SilentMine.class);
        AutoCrystal autoCrystal = Modules.get().get(AutoCrystal.class);

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

                            Long currentTime = System.currentTimeMillis();

                            for (Entity crystal : mc.world.getOtherEntities(null, box,
                                    entityPredicate)) {
                                MeteorClient.ROTATION.snapAt(crystal.getPos(), true);

                                lastTimeOfCrystalNearHead = currentTime;
                            }

                            if ((currentTime - lastTimeOfCrystalNearHead)
                                    / 1000.0 < 1.0) {
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

        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastPlaceTimeMS) / 1000.0 > placeTime.get()) {
            lastPlaceTimeMS = currentTime;
        } else {
            return;
        }

        if (pauseEat.get() && mc.player.isUsingItem()) {
            return;
        }

        Iterator<BlockPos> iterator = placePoses.iterator();

        boolean needSwapBack = false;
        int placed = 0;
        while (placed < places.get() && iterator.hasNext()) {
            BlockPos placePos = iterator.next();

            if (protect.get()) {
                Box box = new Box(placePos.getX() - 1, placePos.getY() - 1, placePos.getZ() - 1,
                        placePos.getX() + 1, placePos.getY() + 1, placePos.getZ() + 1);

                Predicate<Entity> entityPredicate = entity -> entity instanceof EndCrystalEntity;

                for (Entity crystal : mc.world.getOtherEntities(null, box, entityPredicate)) {
                    if (autoCrystal.breakCrystal(crystal)) {
                        crystal.discard();
                    }
                }
            }

            if (!BlockUtils.canPlace(placePos, true)) {
                continue;
            }

            FindItemResult result = InvUtils.findInHotbar(Items.OBSIDIAN);

            if (!result.found()) {
                break;
            }

            if (!needSwapBack && mc.player.getInventory().selectedSlot != result.slot()) {
                InvUtils.swap(result.slot(), true);

                needSwapBack = true;
            }

            place(placePos);
        }

        if (needSwapBack) {
            InvUtils.swapBack();
        }
    }

    private boolean place(BlockPos blockPos) {
        if (!BlockUtils.canPlace(blockPos, true)) {
            return false;
        }

        Direction dir = null;
        for (Direction test : Direction.values()) {
            Direction placeOnDir = AutoCrystal.getPlaceOnDirection(blockPos.offset(test));
            if (placeOnDir != null && blockPos.offset(test).offset(placeOnDir).equals(blockPos)) {
                dir = placeOnDir;
                break;
            }
        }

        Hand hand = Hand.MAIN_HAND;

        if (dir == null && grimBypass.get()) {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            new BlockPos(0, 0, 0), Direction.DOWN));

            hand = Hand.OFF_HAND;
        }

        /*
         * boolean grr = BlockUtils.place(blockPos, grimBypass.get() ? Hand.OFF_HAND :
         * Hand.MAIN_HAND, mc.player.getInventory().selectedSlot, false, 0, true, true, false);
         */

        Vec3d eyes = mc.player.getEyePos();
        boolean inside = eyes.x > blockPos.getX() && eyes.x < blockPos.getX() + 1
                && eyes.y > blockPos.getY() && eyes.y < blockPos.getY() + 1
                && eyes.z > blockPos.getZ() && eyes.z < blockPos.getZ() + 1;
        int s = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, new BlockHitResult(
                blockPos.toCenterPos(), dir == null ? Direction.DOWN : dir, blockPos, inside), s));

        if (dir == null && grimBypass.get()) {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            new BlockPos(0, 0, 0), Direction.DOWN));
        }

        return true;
    }

    public enum AutoSelfTrapMode {
        None, Smart, Always
    }
}
