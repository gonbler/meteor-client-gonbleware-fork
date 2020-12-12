/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2020 Meteor Development.
 */

package minegame159.meteorclient.mixin;

import minegame159.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerMoveC2SPacket.class)
public class PlayerMoveC2SPacketMixin implements IPlayerMoveC2SPacket {
    @Shadow protected double y;
    @Shadow protected boolean onGround;
    @Shadow protected float yaw;
    @Shadow protected float pitch;

    @Override
    public void setY(double y) {
        this.y = y;
    }

    @Override
    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    @Override
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    @Override
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }


}
