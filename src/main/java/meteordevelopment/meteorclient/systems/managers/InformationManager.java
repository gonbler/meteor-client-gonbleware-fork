package meteordevelopment.meteorclient.systems.managers;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import java.util.UUID;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class InformationManager {
    public InformationManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    private final Object2IntMap<UUID> totemPopMap = new Object2IntOpenHashMap<>();

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;
        
        switch (event.packet) {
            case EntityStatusS2CPacket packet when packet.getStatus() == 35
                    && packet.getEntity(mc.world) instanceof PlayerEntity entity -> {
                if ((entity.equals(mc.player)))
                    return;

                synchronized (totemPopMap) {
                    int pops = totemPopMap.getOrDefault(entity.getUuid(), 0);
                    totemPopMap.put(entity.getUuid(), ++pops);
                }
            }

            default -> {
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        synchronized (totemPopMap) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (!totemPopMap.containsKey(player.getUuid()))
                    continue;

                if (player.deathTime > 0 || player.getHealth() <= 0) {
                    totemPopMap.removeInt(player.getUuid());
                }
            }
        }
    }

    public int getPops(Entity entity) {
        return totemPopMap.getOrDefault(entity.getUuid(), 0);
    }
}
