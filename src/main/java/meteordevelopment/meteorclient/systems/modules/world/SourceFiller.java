/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import meteordevelopment.meteorclient.systems.modules.combat.AutoCrystal;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class SourceFiller extends Module {
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

    private final Setting<Double> placeTime =
            sgGeneral.add(new DoubleSetting.Builder().name("place-time")
                    .description("Time between places").defaultValue(0.06).min(0).max(0.5).build());

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

    public SourceFiller() {
        super(Categories.World, "source-filler",
                "Places blocks in water and lava source blocks around you.");
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
        for (BlockPos pos : placePoses) {
            event.renderer.box(pos, normalSideColor.get(), normalLineColor.get(), shapeMode.get(),
                    0);
        }
    }

    private void update() {
        placePoses.clear();

        int r = 5;

        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastPlaceTimeMS) / 1000.0 > placeTime.get()) {
            lastPlaceTimeMS = currentTime;
        } else {
            return;
        }

        if (pauseEat.get() && mc.player.isUsingItem()) {
            return;
        }

        BlockPos eyePos = BlockPos.ofFloored(mc.player.getEyePos());
        for (int y = r; y > -r; y--) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = eyePos.add(x, y, z);

                    if (placePoses.size() >= 2) {
                        continue;
                    }

                    if (!pos.toCenterPos().isInRange(eyePos.toCenterPos(), 5.0)) {
                        continue;
                    }

                    if (isWaterOrLavaSource(pos)) {
                        placePoses.add(pos);
                    }
                }
            }
        }

        Iterator<BlockPos> iterator = placePoses.iterator();

        boolean needSwapBack = false;
        int placed = 0;
        while (placed < places.get() && iterator.hasNext()) {
            BlockPos placePos = iterator.next();

            if (!BlockUtils.canPlace(placePos, true)) {
                continue;
            }

            FindItemResult result = InvUtils.findInHotbar(Items.NETHERRACK);

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
        /*
         * for (Direction test : Direction.values()) { Direction placeOnDir =
         * AutoCrystal.getPlaceOnDirection(blockPos.offset(test)); if (placeOnDir != null &&
         * blockPos.offset(test).offset(placeOnDir).equals(blockPos)) { dir = placeOnDir; break; } }
         */

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

    public boolean isWaterOrLavaSource(BlockPos pos) {
        BlockState blockState = mc.world.getBlockState(pos);

        return (blockState.getFluidState().getFluid().matchesType(Fluids.LAVA)
                || blockState.getFluidState().getFluid().matchesType(Fluids.WATER))
                && blockState.getFluidState().getLevel() == 8;
    }

}
