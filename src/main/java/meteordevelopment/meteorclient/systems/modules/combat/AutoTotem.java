/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AutoTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>().name("mode")
            .description("Determines when to hold a totem, strict will always hold.")
            .defaultValue(Mode.Smart).build());

    private final Setting<Integer> health = sgGeneral.add(new IntSetting.Builder().name("health")
            .description("The health to hold a totem at.").defaultValue(10).range(0, 36)
            .sliderMax(36).visible(() -> mode.get() == Mode.Smart).build());

    private final Setting<Boolean> elytra = sgGeneral.add(new BoolSetting.Builder().name("elytra")
            .description("Will always hold a totem when flying with elytra.").defaultValue(true)
            .visible(() -> mode.get() == Mode.Smart).build());

    private final Setting<Boolean> fall = sgGeneral.add(new BoolSetting.Builder().name("fall")
            .description("Will hold a totem when fall damage could kill you.").defaultValue(true)
            .visible(() -> mode.get() == Mode.Smart).build());

    private final Setting<Boolean> explosion =
            sgGeneral.add(new BoolSetting.Builder().name("explosion")
                    .description("Will hold a totem when explosion damage could kill you.")
                    .defaultValue(true).visible(() -> mode.get() == Mode.Smart).build());

    private final Setting<Boolean> antiTFail = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-totem-fail").description("Will swap to a totem in your hotbar if you pop")
            .defaultValue(true).build());

    private final Setting<Integer> antiTFailHotbarSlot =
            sgGeneral.add(new IntSetting.Builder().name("anti-totem-fail-hotbar-slot")
                    .description("Will swap to a totem in your hotbar if you pop").defaultValue(8)
                    .min(0).max(8).build());

    private final Setting<Boolean> antiTFailUseBackupSwapEat =
            sgGeneral.add(new BoolSetting.Builder().name("anti-totem-fail-use-backup-swap-eat")
                    .description("Uses a different method of swapping if you pop while eating")
                    .defaultValue(true).build());

    public AutoTotem() {
        super(Categories.Combat, "auto-totem", "Automatically equips a totem in your offhand.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
        int totems = result.count();

        FindItemResult inventoryResult = getInventoryTotemSlot();

        if (totems > 0 && antiTFail.get() && inventoryResult.found() && mc.player.getInventory()
                .getStack(antiTFailHotbarSlot.get()).getItem() != Items.TOTEM_OF_UNDYING) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    inventoryResult.slot(), antiTFailHotbarSlot.get(), SlotActionType.SWAP,
                    mc.player);
        }

        if (mode.get() == Mode.None || totems < 0) {
            return;
        }

        boolean isLowHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount()
                - PlayerUtils.possibleHealthReductions(explosion.get(), fall.get()) <= health.get();
        boolean flying = elytra.get()
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
                && mc.player.isFallFlying();

        boolean shouldOffhandTotem = false;

        if (mode.get() == Mode.Smart && (isLowHealth || flying)) {
            shouldOffhandTotem = true;
        } else if (mode.get() == Mode.Strict) {
            shouldOffhandTotem = true;
        }

        FindItemResult hotbarResult = InvUtils.find(x -> {
            if (x.getItem().equals(Items.TOTEM_OF_UNDYING)) {
                return true;
            }

            return false;
        }, 0, 8);

        if (shouldOffhandTotem && mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            if (antiTFail.get() && hotbarResult.found()) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                        SlotUtils.OFFHAND, hotbarResult.slot(), SlotActionType.SWAP, mc.player);
            } else {
                InvUtils.move().from(result.slot()).toOffhand();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket p))
            return;
        if (p.getStatus() != 35)
            return;

        Entity entity = p.getEntity(mc.world);
        if (entity == null || !(entity.equals(mc.player)))
            return;

        if (!antiTFail.get()) {
            return;
        }


        FindItemResult hotbarResult = InvUtils.find(x -> {
            if (x.getItem().equals(Items.TOTEM_OF_UNDYING)) {
                return true;
            }

            return false;
        }, 0, 8);

        if (antiTFailUseBackupSwapEat.get() && mc.player.isUsingItem()) {
            if (hotbarResult.found()) {
                int slot = mc.player.getInventory().getStack(antiTFailHotbarSlot.get())
                        .getItem() == Items.TOTEM_OF_UNDYING ? antiTFailHotbarSlot.get()
                                : hotbarResult.slot();

                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                        SlotUtils.OFFHAND, slot, SlotActionType.SWAP, mc.player);

                FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
                int totems = result.count();

                FindItemResult inventoryResult = getInventoryTotemSlot();

                if (totems > 0 && antiTFail.get() && inventoryResult.found()
                        && mc.player.getInventory().getStack(antiTFailHotbarSlot.get())
                                .getItem() != Items.TOTEM_OF_UNDYING) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                            inventoryResult.slot(), antiTFailHotbarSlot.get(), SlotActionType.SWAP,
                            mc.player);
                }
            }

            return;
        }

        if (hotbarResult.found()) {
            int slot = mc.player.getInventory().getStack(antiTFailHotbarSlot.get())
                    .getItem() == Items.TOTEM_OF_UNDYING ? antiTFailHotbarSlot.get()
                            : hotbarResult.slot();


            InvUtils.swap(slot, true);

            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            new BlockPos(0, 0, 0), Direction.DOWN));

            InvUtils.swapBack();

            FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
            int totems = result.count();

            FindItemResult inventoryResult = getInventoryTotemSlot();

            if (totems > 0 && antiTFail.get() && inventoryResult.found() && mc.player.getInventory()
                    .getStack(antiTFailHotbarSlot.get()).getItem() != Items.TOTEM_OF_UNDYING) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                        inventoryResult.slot(), antiTFailHotbarSlot.get(), SlotActionType.SWAP,
                        mc.player);
            }

            return;
        }

    }

    private FindItemResult getInventoryTotemSlot() {
        return InvUtils.find(x -> {
            if (x.getItem().equals(Items.TOTEM_OF_UNDYING)) {
                return true;
            }

            return false;
        }, 9, 35);
    }

    public enum Mode {
        Smart, Strict, None
    }
}
