/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.PlayerPositionLookS2CPacketAccessor;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;

public class NoRotate extends Module {
    public NoRotate() {
        super(Categories.Player, "no-rotate", "Attempts to block rotations sent from server to client.");
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            if (packet.getFlags().contains(PositionFlag.Y_ROT)) {
                ((PlayerPositionLookS2CPacketAccessor) packet).setYaw(0);
            } else {
                ((PlayerPositionLookS2CPacketAccessor) packet).setYaw(mc.player.getYaw());
            }
            if (packet.getFlags().contains(PositionFlag.X_ROT)) {
                ((PlayerPositionLookS2CPacketAccessor) packet).setPitch(0);
            } else {
                ((PlayerPositionLookS2CPacketAccessor) packet).setPitch(mc.player.getPitch());
            }
        }
    }
}
