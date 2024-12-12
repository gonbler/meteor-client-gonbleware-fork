/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.managers.RotationManager;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
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
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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

    private final Setting<SwitchMode> switchMode =
            sgGeneral.add(new EnumSetting.Builder<SwitchMode>().name("Switch Mode")
                    .description("Which method of switching should be used.")
                    .defaultValue(SwitchMode.SilentHotbar).build());

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
    private Map<BlockPos, Long> placeCooldowns = new HashMap<>();

    private List<BlockPos> placePoses = new ArrayList<>();

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
        Iterator<BlockPos> iterator = placePoses.iterator();

        int placed = 0;
        while (placed < places.get() && iterator.hasNext()) {
            BlockPos placePos = iterator.next();

            if (!BlockUtils.canPlace(placePos, true)) {
                continue;
            }

            if (placeCooldowns.containsKey(placePos)) {
                if (System.currentTimeMillis() - placeCooldowns.get(placePos) < 50) {
                    continue;
                }
            }

            event.renderer.box(placePos, normalSideColor.get(), normalLineColor.get(),
                    shapeMode.get(), 0);

            placed++;
        }
    }

    private void update() {
        placePoses.clear();

        if (switch (switchMode.get()) {
            case SilentHotbar -> !InvUtils.findInHotbar(Items.OBSIDIAN).found();
            case SilentSwap -> !InvUtils.find(Items.OBSIDIAN).found();
        }) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long placeCount =
                placeCooldowns.values().stream().filter(x -> currentTime - x <= 1000).count();
        if (placeCount > 20) {
            return;
        }

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

        if ((currentTime - lastPlaceTimeMS) / 1000.0 > placeTime.get()) {
            lastPlaceTimeMS = currentTime;
        } else {
            return;
        }

        if (pauseEat.get() && mc.player.isUsingItem()) {
            return;
        }

        if (placePoses.isEmpty()) {
            return;
        }

        int invSlot = InvUtils.find(Items.OBSIDIAN).slot();
        int selectedSlot = mc.player.getInventory().selectedSlot;
        boolean didSilentSwap = false;
        boolean needSwapBack = false;

        Iterator<BlockPos> iterator = placePoses.iterator();

        int placed = 0;
        while (placed < places.get() && iterator.hasNext()) {
            BlockPos placePos = iterator.next();

            if (protect.get()) {
                Box box = new Box(placePos.getX() - 1, placePos.getY() - 1, placePos.getZ() - 1,
                        placePos.getX() + 1, placePos.getY() + 1, placePos.getZ() + 1);

                Predicate<Entity> entityPredicate = entity -> entity instanceof EndCrystalEntity;

                Entity blocking = null;

                for (Entity crystal : mc.world.getOtherEntities(null, box, entityPredicate)) {
                    blocking = crystal;
                    break;
                }

                if (blocking != null && System.currentTimeMillis() - lastAttackTime >= 50) {
                    MeteorClient.ROTATION.requestRotation(blocking.getPos(), 11);

                    boolean snapped = false;
                    if (mc.player.isOnGround()) {
                        snapped = true;
                        float[] angle = MeteorClient.ROTATION.getRotation(blocking.getPos());
                        mc.getNetworkHandler()
                                .sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(),
                                        mc.player.getY(), mc.player.getZ(), angle[0], angle[1],
                                        RotationManager.lastGround));
                        snapped = true;
                    }

                    if (snapped || MeteorClient.ROTATION.lookingAt(blocking.getBoundingBox())) {
                        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket
                                .attack(blocking, mc.player.isSneaking()));
                        blocking.discard();
                    }
                }
            }

            if (!BlockUtils.canPlace(placePos, true)) {
                continue;
            }

            if (!needSwapBack) {
                switch (switchMode.get()) {
                    case SilentHotbar -> {
                        InvUtils.swap(InvUtils.findInHotbar(Items.OBSIDIAN).slot(), true);
                    }
                    case SilentSwap -> {
                        if (invSlot != mc.player.getInventory().selectedSlot) {
                            InvUtils.quickSwap().fromId(selectedSlot).to(invSlot);
                            didSilentSwap = true;
                        }
                    }
                }

                needSwapBack = true;
            }

            if (place(placePos)) {
                placed++;
            }

        }

        switch (switchMode.get()) {
            case SilentHotbar -> InvUtils.swapBack();
            case SilentSwap -> {
                if (didSilentSwap) {
                    InvUtils.quickSwap().fromId(selectedSlot).to(invSlot);
                }
            }
        }
    }

    private boolean place(BlockPos blockPos) {
        if (!BlockUtils.canPlace(blockPos, true)) {
            return false;
        }

        BlockPos neighbour;
        Direction dir = BlockUtils.getPlaceSide(blockPos);

        Vec3d hitPos = blockPos.toCenterPos();
        if (dir == null) {
            neighbour = blockPos;
        } else {
            neighbour = blockPos.offset(dir);
            hitPos = hitPos.add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5,
                    dir.getOffsetZ() * 0.5);
        }


        if (placeCooldowns.containsKey(blockPos)) {
            if (System.currentTimeMillis() - placeCooldowns.get(blockPos) < 50) {
                return false;
            }
        }

        placeCooldowns.put(blockPos, System.currentTimeMillis());

        Hand hand = Hand.MAIN_HAND;

        if (dir == null && grimBypass.get()) {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            new BlockPos(0, 0, 0), Direction.DOWN));

            hand = Hand.OFF_HAND;
        }

        int s = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

        mc.getNetworkHandler()
                .sendPacket(new PlayerInteractBlockC2SPacket(hand, new BlockHitResult(hitPos,
                        (dir == null ? Direction.DOWN : dir.getOpposite()), neighbour, false), s));

        if (dir == null && grimBypass.get()) {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            new BlockPos(0, 0, 0), Direction.DOWN));
        }

        return true;
    }

    public static Direction getPlaceOnDirection(BlockPos pos) {
        if (pos == null) {
            return null;
        }

        Direction best = null;
        if (MeteorClient.mc.world != null && MeteorClient.mc.player != null) {
            double cDist = -1;
            for (Direction dir : Direction.values()) {

                // Can't place on air lol
                if (MeteorClient.mc.world.getBlockState(pos.offset(dir)).isAir()) {
                    continue;
                }

                // Only accepts if closer than last accepted direction
                double dist = getDistanceForDir(pos, dir);
                if (dist >= 0 && (cDist < 0 || dist < cDist)) {
                    best = dir;
                    cDist = dist;
                }
            }
        }
        return best;
    }

    private static double getDistanceForDir(BlockPos pos, Direction dir) {
        if (MeteorClient.mc.player == null) {
            return 0.0;
        }

        Vec3d vec = new Vec3d(pos.getX() + dir.getOffsetX() / 2f,
                pos.getY() + dir.getOffsetY() / 2f, pos.getZ() + dir.getOffsetZ() / 2f);
        Vec3d dist = MeteorClient.mc.player.getEyePos().add(-vec.x, -vec.y, -vec.z);

        // Len squared for optimization
        return dist.lengthSquared();
    }

    public enum SwitchMode {
        SilentHotbar, SilentSwap
    }

    public enum AutoSelfTrapMode {
        None, Smart, Always
    }
}
