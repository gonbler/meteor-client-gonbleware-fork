package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

public class GrimDisabler extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<HorizontalDisablerMode> horizontalDisblerMode = sgGeneral
            .add(new EnumSetting.Builder<HorizontalDisablerMode>().name("horizontal-disabler-mode")
                    .description("Determines mode of disabler for horizontal movement")
                    .defaultValue(HorizontalDisablerMode.YawOverflow).build());

    /*private final Setting<Boolean> horizontalDisablerElytraFly =
            sgGeneral.add(new BoolSetting.Builder().name("horizontal-disabler-elytra-fly")
                    .description("Determines if the horizontal lets you elytra fly")
                    .defaultValue(true)
                    .visible(() -> horizontalDisblerMode.get() != HorizontalDisablerMode.None)
                    .build());*/

    private boolean fallFlyingBoostState = false;

    public GrimDisabler() {
        super(Categories.Movement, "grim-disabler",
                "Disables the Grim anti-cheat. Allows use of modules such as Speed and ClickTp");
    }

    @EventHandler
    public void onPreMove(SendMovementPacketsEvent.Pre event) {
        // WIP Elytra
        /*if (horizontalDisablerActive.get() && horizontalDisablerElytraFly.get()) {
            boolean wearingElytra = false;
            if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
                wearingElytra = true;
            }

            if (wearingElytra) {
                stopFallFlying();
                fallFlyingBoostState = false;
            } else {
                startFallFlying();
                fallFlyingBoostState = true;
            }
        }*/
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // TODO ?
    }

    public boolean isInElytraFlyState() {
        return isActive() && fallFlyingBoostState;
    }

    public boolean shouldSetYawOverflowRotation() {
        return isActive() && horizontalDisblerMode.get() == HorizontalDisablerMode.YawOverflow/*  && !isInElytraFlyState()*/;
    }

    private void stopFallFlying() {
        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
            return;
        }

        // Unequip and requipt elytra
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0,
                SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0,
                SlotActionType.PICKUP, mc.player);
    }

    private void startFallFlying() {
        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
            return;
        }

        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player,
                ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    @Override
    public String getInfoString() {
        if (horizontalDisblerMode.get() == HorizontalDisablerMode.None) {
            return "";
        }

        return String.format("%s", horizontalDisblerMode.get().toString());
    }

    public enum HorizontalDisablerMode {
        None, YawOverflow
    }
}
