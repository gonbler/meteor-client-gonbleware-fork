/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public class AutoWeb extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-eat").description("Pauses while eating.").defaultValue(true).build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("target-range").description("The maximum distance to target players.")
            .defaultValue(5).range(0, 5).sliderMax(5).build());

    private final Setting<SortPriority> priority =
            sgGeneral.add(new EnumSetting.Builder<SortPriority>().name("target-priority")
                    .description("How to filter targets within range.")
                    .defaultValue(SortPriority.LowestDistance).build());

    private final Setting<Boolean> placeHead = sgGeneral.add(new BoolSetting.Builder()
            .name("place-head").description("Places webs in the target's upper hitbox.")
            .defaultValue(true).build());

    private final Setting<Boolean> placeFeet = sgGeneral.add(new BoolSetting.Builder()
            .name("place-feet").description("Places webs in the target's lower hitbox.")
            .defaultValue(false).build());

    private final Setting<Boolean> placeCrawling = sgGeneral.add(new BoolSetting.Builder()
            .name("place-crawling").description("Places webs in the taget's lower hitbox when they're swimming.")
            .defaultValue(true).build());

    private PlayerEntity target = null;

    public AutoWeb() {
        super(Categories.Combat, "auto-web", "Automatically places webs on other players.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (TargetUtils.isBadTarget(target, range.get())) {
            target = TargetUtils.getPlayerTarget(range.get(), priority.get());
            if (TargetUtils.isBadTarget(target, range.get()))
                return;
        }

        List<BlockPos> placePoses = new ArrayList<>();

        if (placeHead.get()) {
            placePoses.add(target.getBlockPos().up());
        }

        if (placeFeet.get() || (placeCrawling.get() && target.isCrawling())) {
            if (!PlayerUtils.isPlayerPhased(target)) {
                placePoses.add(target.getBlockPos());
            }
        }

        if (pauseEat.get() && mc.player.isUsingItem()) {
            return;
        }

        if (!MeteorClient.BLOCK.beginPlacement(placePoses, Items.COBWEB)) {
            return;
        }

        placePoses.forEach(blockPos -> {
            MeteorClient.BLOCK.placeBlock(Items.COBWEB, blockPos);
        });

        MeteorClient.BLOCK.endPlacement();
    }
}
