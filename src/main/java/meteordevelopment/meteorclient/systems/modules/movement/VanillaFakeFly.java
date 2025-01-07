package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class VanillaFakeFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public VanillaFakeFly() {
        super(Categories.Movement, "vanilla-elytra-fakefly", "Fakes your fly.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        PlayerUtils.silentSwapEquipElytra();

        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player,
                ClientCommandC2SPacket.Mode.START_FALL_FLYING));

        PlayerUtils.silentSwapEquipChestplate();
    }

    public boolean isFlying() {
        return isActive();
    }
}
