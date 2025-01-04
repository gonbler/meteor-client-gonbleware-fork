/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class ChestSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> swapBind = sgGeneral.add(new KeybindSetting.Builder()
            .name("swap-bind").description("Swaps on this key press.").build());

    private boolean keyUnpressed = false;

    public ChestSwap() {
        super(Categories.Player, "chest-swap",
                "Automatically swaps between a chestplate and an elytra.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        update();
    }

    private void update() {
        if (!swapBind.get().isPressed()) {
            keyUnpressed = true;
        }

        if (swapBind.get().isPressed() && keyUnpressed
                && !(mc.currentScreen instanceof ChatScreen)) {
            swap();
            keyUnpressed = false;
        }
    }

    public void swap() {
        Item currentItem = mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem();

        if (currentItem == Items.ELYTRA) {
            PlayerUtils.silentSwapEquipChestplate();
        } else if (currentItem instanceof ArmorItem
                && ((ArmorItem) currentItem).getSlotType() == EquipmentSlot.CHEST) {
            PlayerUtils.silentSwapEquipElytra();
        } else {
            if (!PlayerUtils.silentSwapEquipChestplate())
                PlayerUtils.silentSwapEquipElytra();
        }
    }
}
