package meteordevelopment.meteorclient.systems.managers;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import java.util.UUID;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.PlayerDeathEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.PlayerJoinLeaveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;

public class InformationManager {
    public InformationManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    private final Object2IntMap<UUID> totemPopMap = new Object2IntOpenHashMap<>();

    // Check for if the PlayerListS2C packet is the initial join packet
    private boolean isLoginPacket = true;

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null)
            return;

        switch (event.packet) {
            case EntityStatusS2CPacket packet when packet.getStatus() == 35
                    && packet.getEntity(mc.world) instanceof PlayerEntity entity -> {

                int pops = 0;

                synchronized (totemPopMap) {
                    pops = totemPopMap.getOrDefault(entity.getUuid(), 0);
                    totemPopMap.put(entity.getUuid(), ++pops);
                }

                MeteorClient.EVENT_BUS.post(PlayerDeathEvent.TotemPop.get(entity, pops));
            }

            case PlayerListS2CPacket packet -> {
                if (isLoginPacket) {
                    isLoginPacket = false;
                    return;
                }

                if (packet.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
                    for (PlayerListS2CPacket.Entry entry : packet.getPlayerAdditionEntries()) {
                        MeteorClient.EVENT_BUS.post(PlayerJoinLeaveEvent.Join.get(entry));
                    }
                }
            }

            case PlayerRemoveS2CPacket packet -> {
                if (mc.getNetworkHandler() == null) return;

                for (UUID uuid : packet.profileIds()) {
                    PlayerListEntry toRemove = mc.getNetworkHandler().getPlayerListEntry(uuid);
                    if (toRemove == null)
                        continue;

                    MeteorClient.EVENT_BUS.post(PlayerJoinLeaveEvent.Leave.get(toRemove));
                }
            }

            case EntityStatusS2CPacket packet when packet.getStatus() == 3
                    && packet.getEntity(mc.world) instanceof PlayerEntity entity -> {

                int pops = 0;
                if (totemPopMap.containsKey(entity.getUuid())) {
                    pops = totemPopMap.removeInt(entity.getUuid());
                }

                MeteorClient.EVENT_BUS.post(PlayerDeathEvent.Death.get(entity, pops));
            }

            default -> {
            }
        }
    }

    @EventHandler
    private void onGameLeave(GameLeftEvent event) {
        isLoginPacket = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null)
            return;
    }

    public int getPops(Entity entity) {
        return totemPopMap.getOrDefault(entity.getUuid(), 0);
    }
}
