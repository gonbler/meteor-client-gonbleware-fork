/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlastFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.entity.Hopper;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import static meteordevelopment.orbit.EventPriority.HIGHEST;

public class Offhand extends Module {
    private final SettingGroup sgTotem = settings.createGroup("Totem");
    private final SettingGroup sgCombat = settings.createGroup("Combat");

    // Totem
    private final Setting<Integer> totemOffhandHealth = sgTotem.add(new IntSetting.Builder()
            .name("offhand-totem-health").description("The health to force hold a totem at.")
            .defaultValue(10).range(0, 36).sliderMax(36).build());

    private final Setting<Boolean> mainHandTotem =
            sgTotem.add(new BoolSetting.Builder().name("main-hand-totem")
                    .description("Whether or not to hold a totem in your main hand.")
                    .defaultValue(true).build());

    private final Setting<Integer> mainHandTotemSlot =
            sgTotem.add(new IntSetting.Builder().name("main-hand-totem-slot")
                    .description("The slot in your hotbar to hold your main hand totem.")
                    .defaultValue(3).range(1, 9).visible(() -> mainHandTotem.get()).build());

    // Combat
    private final Setting<Boolean> swordGapple = sgCombat.add(new BoolSetting.Builder()
            .name("sword-gapple")
            .description("Lets you right click while holding a sword to eat a golden apple.")
            .defaultValue(true).build());

    public Offhand() {
        super(Categories.Combat, "offhand", "Allows you to hold specified items in your offhand.");
    }

    @Override
    public void onActivate() {

    }

    @EventHandler(priority = HIGHEST + 999)
    private void onTick(TickEvent.Pre event) {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)
                && !(mc.currentScreen instanceof InventoryScreen)
                && !(mc.currentScreen instanceof GameMenuScreen)) {
            return;
        }

        if (mainHandTotem.get()) {
            updateMainHandTotem();
        }

        updateOffhandSlot();
    }

    private void updateMainHandTotem() {
        FindItemResult totemResult = findTotem();

        if (!totemResult.found() || totemResult.isOffhand()) {
            return;
        }

        if (mc.player.getInventory().getStack(mainHandTotemSlot.get() - 1)
                .getItem() != Items.TOTEM_OF_UNDYING) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    SlotUtils.indexToId(totemResult.slot()), mainHandTotemSlot.get() - 1,
                    SlotActionType.SWAP, mc.player);
        }
    }

    private void updateOffhandSlot() {
        boolean isLowHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount()
                - PlayerUtils.possibleHealthReductions(true, true) <= totemOffhandHealth.get();

        boolean flying =
                true && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
                        && mc.player.isFallFlying();

        // If we're flying or low health, hold totem
        if (isLowHealth || flying) {
            moveTotemToOffhand();
        } else if (swordGapple.get() && mc.player.getMainHandStack().getItem() instanceof SwordItem
                && mc.options.useKey.isPressed()) {
            moveGappleToOffhand();
        } else {
            // Hold totem if we're not doing anything else
            moveTotemToOffhand();
        }
    }

    private void moveTotemToOffhand() {
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            return;
        }

        FindItemResult totemResult = findTotem();

        if (totemResult.isHotbar()) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, SlotUtils.OFFHAND,
                    totemResult.slot(), SlotActionType.SWAP, mc.player);

            updateMainHandTotem();
        } else if (totemResult.found()) {
            int selectedSlot = mc.player.getInventory().selectedSlot;

            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    totemResult.slot(), selectedSlot, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, SlotUtils.OFFHAND,
                    selectedSlot, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    totemResult.slot(), selectedSlot, SlotActionType.SWAP, mc.player);
        }
    }

    private void moveGappleToOffhand() {
        if (mc.player.getOffHandStack().getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
            return;
        }

        if (willInteractWithChestBlock()) {
            return;
        }

        FindItemResult inventoryGappleResult = InvUtils.find(Items.ENCHANTED_GOLDEN_APPLE);

        if (inventoryGappleResult.found()) {
            // We can instantly move it if it's in our hotbar
            if (inventoryGappleResult.isHotbar()) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                        SlotUtils.OFFHAND, inventoryGappleResult.slot(), SlotActionType.SWAP,
                        mc.player);
            } else {
                int selectedSlot = mc.player.getInventory().selectedSlot;

                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                        inventoryGappleResult.slot(), selectedSlot, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                        SlotUtils.OFFHAND, selectedSlot, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                        inventoryGappleResult.slot(), selectedSlot, SlotActionType.SWAP, mc.player);
            }
        }
    }

    private boolean willInteractWithChestBlock() {
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) mc.crosshairTarget;
            BlockState blockState = mc.world.getBlockState(blockHitResult.getBlockPos());
            Block block = blockState.getBlock();

            // TODO: Find a less shit way to do this
            if (block instanceof ShulkerBoxBlock || block instanceof AbstractChestBlock
                    || block instanceof EnderChestBlock || block instanceof TrappedChestBlock
                    || block instanceof FurnaceBlock || block instanceof AbstractFurnaceBlock
                    || block instanceof BlastFurnaceBlock || block instanceof DropperBlock
                    || block instanceof Hopper) {
                return true;
            }
        }
        return false;
    }

    private FindItemResult findTotem() {
        return InvUtils.find(x -> {
            if (x.getItem().equals(Items.TOTEM_OF_UNDYING)) {
                return true;
            }

            return false;
        }, 0, 35);
    }
}
