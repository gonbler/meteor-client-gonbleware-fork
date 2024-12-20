/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.EntityVelocityUpdateS2CPacketAccessor;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.managers.RotationManager;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class Velocity extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> knockback = sgGeneral.add(new BoolSetting.Builder()
        .name("knockback")
        .description("Modifies the amount of knockback you take from attacks.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> knockbackPhaseOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("knockback-phase-only")
        .description("Only disables knockback when phased into a wall.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> knockbackHorizontal = sgGeneral.add(new DoubleSetting.Builder()
        .name("knockback-horizontal")
        .description("How much horizontal knockback you will take.")
        .defaultValue(0)
        .sliderMax(1)
        .visible(knockback::get)
        .build()
    );

    public final Setting<Double> knockbackVertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("knockback-vertical")
        .description("How much vertical knockback you will take.")
        .defaultValue(0)
        .sliderMax(1)
        .visible(knockback::get)
        .build()
    );

    public final Setting<Boolean> explosions = sgGeneral.add(new BoolSetting.Builder()
        .name("explosions")
        .description("Modifies your knockback from explosions.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> explosionsHorizontal = sgGeneral.add(new DoubleSetting.Builder()
        .name("explosions-horizontal")
        .description("How much velocity you will take from explosions horizontally.")
        .defaultValue(0)
        .sliderMax(1)
        .visible(explosions::get)
        .build()
    );

    public final Setting<Double> explosionsVertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("explosions-vertical")
        .description("How much velocity you will take from explosions vertically.")
        .defaultValue(0)
        .sliderMax(1)
        .visible(explosions::get)
        .build()
    );

    public final Setting<Boolean> liquids = sgGeneral.add(new BoolSetting.Builder()
        .name("liquids")
        .description("Modifies the amount you are pushed by flowing liquids.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> liquidsHorizontal = sgGeneral.add(new DoubleSetting.Builder()
        .name("liquids-horizontal")
        .description("How much velocity you will take from liquids horizontally.")
        .defaultValue(0)
        .sliderMax(1)
        .visible(liquids::get)
        .build()
    );

    public final Setting<Double> liquidsVertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("liquids-vertical")
        .description("How much velocity you will take from liquids vertically.")
        .defaultValue(0)
        .sliderMax(1)
        .visible(liquids::get)
        .build()
    );

    public final Setting<Boolean> entityPush = sgGeneral.add(new BoolSetting.Builder()
        .name("entity-push")
        .description("Modifies the amount you are pushed by entities.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> entityPushAmount = sgGeneral.add(new DoubleSetting.Builder()
        .name("entity-push-amount")
        .description("How much you will be pushed.")
        .defaultValue(0)
        .sliderMax(1)
        .visible(entityPush::get)
        .build()
    );

    public final Setting<Boolean> blocks = sgGeneral.add(new BoolSetting.Builder()
        .name("blocks")
        .description("Prevents you from being pushed out of blocks.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> sinking = sgGeneral.add(new BoolSetting.Builder()
        .name("sinking")
        .description("Prevents you from sinking in liquids.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> fishing = sgGeneral.add(new BoolSetting.Builder()
        .name("fishing")
        .description("Prevents you from being pulled by fishing rods.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> livingEntityKnockback = sgGeneral.add(new BoolSetting.Builder()
        .name("living-entity-knockback")
        .description("Prevents you from being moved by knockback.")
        .defaultValue(true)
        .build()
    );

    public Velocity() {
        super(Categories.Movement, "velocity", "Prevents you from being moved by external forces.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!sinking.get()) return;
        if (mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed()) return;

        if ((mc.player.isTouchingWater() || mc.player.isInLava()) && mc.player.getVelocity().y < 0) {
            ((IVec3d) mc.player.getVelocity()).setY(0);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (knockback.get() && event.packet instanceof EntityVelocityUpdateS2CPacket packet
            && packet.getEntityId() == mc.player.getId()) {
            if (knockbackPhaseOnly.get()) {
                boolean isPhased = false;

                Box boundingBox = mc.player.getBoundingBox().shrink(0.05, 0.1, 0.05);
                double feetY = mc.player.getY();

                Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                        feetY + 0.1, boundingBox.maxZ);

                for (BlockPos pos : BlockPos.iterate((int) Math.floor(feetBox.minX),
                        (int) Math.floor(feetBox.minY), (int) Math.floor(feetBox.minZ),
                        (int) Math.floor(feetBox.maxX), (int) Math.floor(feetBox.maxY),
                        (int) Math.floor(feetBox.maxZ))) {
                    
                    if (mc.world.getBlockState(pos).isSolidBlock(mc.world, pos)) {
                        if (RotationManager.lastGround) {
                            isPhased = true;
                        }
                        break;
                    }
                }

                if (!isPhased) {
                    return;
                }
            } 

            double velX = (packet.getVelocityX() / 8000d - mc.player.getVelocity().x) * knockbackHorizontal.get();
            double velY = (packet.getVelocityY() / 8000d - mc.player.getVelocity().y) * knockbackVertical.get();
            double velZ = (packet.getVelocityZ() / 8000d - mc.player.getVelocity().z) * knockbackHorizontal.get();
            ((EntityVelocityUpdateS2CPacketAccessor) packet).setX((int) (velX * 8000 + mc.player.getVelocity().x * 8000));
            ((EntityVelocityUpdateS2CPacketAccessor) packet).setY((int) (velY * 8000 + mc.player.getVelocity().y * 8000));
            ((EntityVelocityUpdateS2CPacketAccessor) packet).setZ((int) (velZ * 8000 + mc.player.getVelocity().z * 8000));
        }
    }

    public double getHorizontal(Setting<Double> setting) {
        return isActive() ? setting.get() : 1;
    }

    public double getVertical(Setting<Double> setting) {
        return isActive() ? setting.get() : 1;
    }
}
