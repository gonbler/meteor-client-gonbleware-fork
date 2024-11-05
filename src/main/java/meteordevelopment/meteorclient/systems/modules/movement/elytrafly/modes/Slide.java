/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

/*
 * Credit to Luna (https://github.com/InLieuOfLuna) for making the original Elytra Recast mod
 * (https://github.com/InLieuOfLuna/elytra-recast)!
 */

package meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class Slide extends ElytraFlightMode {

    public Slide() {
        super(ElytraFlightModes.Slide);
    }

    boolean rubberbanded = false;

    int tickDelay = elytraFly.restartDelay.get();

    @Override
    public void onTick() {
        super.onTick();

        if (mc.options.jumpKey.isPressed() && !mc.player.isFallFlying())
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player,
                    ClientCommandC2SPacket.Mode.START_FALL_FLYING));

        // Make sure all the conditions are met (player has an elytra, isn't in water, etc)
        if (checkConditions(mc.player) && mc.player.isOnGround()) {
            double yaw = Math.toRadians(mc.player.getYaw());

            double speedFactor = Math.max(0.1, Math.min(1.0,
                    ((100 * elytraFly.slideAccel.get()) / 20.0 - mc.player.getVelocity().length())
                            / (((100 * elytraFly.slideAccel.get()) / 20.0))));

            Vec3d dir = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));

            mc.player.addVelocity(
                    dir.multiply(((elytraFly.slideMaxSpeed.get() / 2000.0) / speedFactor)));

            // Rubberbanding
            if (rubberbanded && elytraFly.restart.get()) {
                if (tickDelay > 0) {
                    tickDelay--;
                } else {
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player,
                            ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    rubberbanded = false;
                    tickDelay = elytraFly.restartDelay.get();
                }
            }
        }
    }

    @Override
    public void onPreTick() {
        super.onPreTick();
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            rubberbanded = true;
            mc.player.stopFallFlying();
        }
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ClientCommandC2SPacket
                && ((ClientCommandC2SPacket) event.packet).getMode().equals(
                        ClientCommandC2SPacket.Mode.START_FALL_FLYING)
                && !elytraFly.sprint.get()) {
            mc.player.setSprinting(true);
        }
    }

    public static boolean recastElytra(ClientPlayerEntity player) {
        if (checkConditions(player) && ignoreGround(player)) {
            player.networkHandler.sendPacket(new ClientCommandC2SPacket(player,
                    ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        } else
            return false;
    }

    public static boolean checkConditions(ClientPlayerEntity player) {
        ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
        return (!player.hasVehicle() && !player.isClimbing() && itemStack.isOf(Items.ELYTRA)
                && ElytraItem.isUsable(itemStack));
    }

    private static boolean ignoreGround(ClientPlayerEntity player) {
        if (!player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.LEVITATION)) {
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
                player.startFallFlying();
                return true;
            } else
                return false;
        } else
            return false;
    }

    @Override
    public void onActivate() {}

    @Override
    public void onDeactivate() {
        rubberbanded = false;
    }
}


