package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;

public class PacketSaver extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> grimRubberbandResponse =
            sgGeneral.add(new BoolSetting.Builder().name("ignore-grim-rubberband")
                    .description("Stops the client from responding to Grim rubberband packets")
                    .defaultValue(true).build());

    public PacketSaver() {
        super(Categories.Misc, "packet-saver",
                "Stops the client from sending unnecessary packets. Helps with packet kicks.");
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onPacketSend(PacketEvent.Send event) {
        if (grimRubberbandResponse.get()
                && event.packet instanceof TeleportConfirmC2SPacket packet
                && packet.getTeleportId() < 0) {
            event.cancel();
        }
    }
}
