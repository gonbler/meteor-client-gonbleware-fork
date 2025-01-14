/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class EXPThrower extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> throwsPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("throws-per-tick").description("Number of xp bottles to throw every tick.")
            .defaultValue(1).min(1).sliderMax(5).build());

    public EXPThrower() {
        super(Categories.Player, "exp-thrower",
                "Automatically throws XP bottles from your hotbar.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        FindItemResult result = InvUtils.find(Items.EXPERIENCE_BOTTLE);

        if (!result.found() || mc.player.isUsingItem())
            return;

        if (MeteorClient.SWAP.beginSwap(result, true)) {
            MeteorClient.ROTATION.requestRotation(mc.player.getYaw(), 90, 0);

            for (int i = 0; i < throwsPerTick.get(); i++) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }

            MeteorClient.SWAP.endSwap(true);
        }
    }
}
