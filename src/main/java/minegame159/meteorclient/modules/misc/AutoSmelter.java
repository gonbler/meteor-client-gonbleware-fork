/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2020 Meteor Development.
 */

package minegame159.meteorclient.modules.misc;

import minegame159.meteorclient.mixininterface.IAbstractFurnaceScreenHandler;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.Module;
import minegame159.meteorclient.utils.player.Chat;
import minegame159.meteorclient.utils.player.InvUtils;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class AutoSmelter extends Module {
    private int step;
    private boolean first;
    private int timer;
    private boolean waitingForItemsToSmelt;

    public AutoSmelter() {
        super(Category.Misc, "auto-smelter", "Automatically smelts all items in your inventory that can be smelted.");
    }

    @Override
    public void onActivate() {
        first = true;
        waitingForItemsToSmelt = false;
    }

    public void onFurnaceClose() {
        first = true;
        waitingForItemsToSmelt = false;
    }

    public void tick(AbstractFurnaceScreenHandler c) {
        timer++;

        // When the furnace is opened.
        if (!first) {
            first = true;

            step = 0;
            timer = 0;
        }

        // Check for fuel.
        if (checkFuel(c)) return;

        // Wait for the smelting to be complete.
        if (c.getCookProgress() != 0 || timer < 5) return;

        if (step == 0) {
            // Take the smelted results.
            if (takeResults(c)) return;

            step++;
            timer = 0;
        } else if (step == 1) {
            // Wait for the items to smelt.
            if (waitingForItemsToSmelt) {
                if (c.slots.get(0).getStack().isEmpty()) {
                    step = 0;
                    timer = 0;
                    waitingForItemsToSmelt = false;
                }
                return;
            }

            // Insert items.
            if (insertItems(c)) return;

            waitingForItemsToSmelt = true;
        }
    }

    private boolean insertItems(AbstractFurnaceScreenHandler c) {
        if (!c.slots.get(0).getStack().isEmpty()) return true;

        int slot = -1;

        for (int i = 3; i < c.slots.size(); i++) {
            if (((IAbstractFurnaceScreenHandler) c).isSmeltableI(c.slots.get(i).getStack())) {
                slot = i;
                break;
            }
        }

        if (slot == -1) {
            Chat.warning(this, "You do not have any items in your inventory that can be smelted... disabling.");
            toggle();
            return true;
        }

        InvUtils.clickSlot(slot, 0, SlotActionType.PICKUP);
        InvUtils.clickSlot(0, 0, SlotActionType.PICKUP);

        return false;
    }

    private boolean checkFuel(AbstractFurnaceScreenHandler c) {
        if (c.getFuelProgress() <= 1 && !((IAbstractFurnaceScreenHandler) c).isFuelI(c.slots.get(1).getStack())) {
            if (!c.slots.get(1).getStack().isEmpty()) {
                InvUtils.clickSlot(1, 0, SlotActionType.QUICK_MOVE);

                if (!c.slots.get(1).getStack().isEmpty()) {
                    Chat.warning(this, "Your inventory is currently full... disabling.");
                    toggle();
                    return true;
                }
            }

            int slot = -1;
            for (int i = 3; i < c.slots.size(); i++) {
                if (((IAbstractFurnaceScreenHandler) c).isFuelI(c.slots.get(i).getStack())) {
                    slot = i;
                    break;
                }
            }

            if (slot == -1) {
                Chat.warning(this, "You do not have any fuel in your inventory... disabling.");
                toggle();
                return true;
            }

            InvUtils.clickSlot(slot, 0, SlotActionType.PICKUP);
            InvUtils.clickSlot(1, 0, SlotActionType.PICKUP);
        }

        return false;
    }

    private boolean takeResults(AbstractFurnaceScreenHandler c) {
        InvUtils.clickSlot(2, 0, SlotActionType.QUICK_MOVE);

        if (!c.slots.get(2).getStack().isEmpty()) {
            Chat.warning(this, "Your inventory is full... disabling.");
            toggle();
            return true;
        }

        return false;
    }
}
