package meteordevelopment.meteorclient.events.entity;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;

public class VehicleMoveEvent {
    private static final VehicleMoveEvent INSTANCE = new VehicleMoveEvent();

    public Entity entity;
    public VehicleMoveC2SPacket packet;

    public static VehicleMoveEvent get(VehicleMoveC2SPacket packet, Entity entity) {
        INSTANCE.entity = entity;
        INSTANCE.packet = packet;
        return INSTANCE;
    }
}
