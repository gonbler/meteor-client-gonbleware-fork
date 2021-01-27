/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package minegame159.meteorclient.modules.player;

//Created by squidooly 16/07/2020

import meteordevelopment.orbit.EventHandler;
import minegame159.meteorclient.events.world.TickEvent;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.Module;
import minegame159.meteorclient.settings.BoolSetting;
import minegame159.meteorclient.settings.Setting;
import minegame159.meteorclient.settings.SettingGroup;
import minegame159.meteorclient.utils.player.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SkeletonHorseEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;

public class AutoMount extends Module {
    public AutoMount(){super(Category.Player, "auto-mount", "Automatically mounts entities.");}

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAnimals = settings.createGroup("Animals");

    // General

    private final Setting<Boolean> checkSaddle = sgGeneral.add(new BoolSetting.Builder()
            .name("check-saddle")
            .description("Checks if the entity contains a saddle before mounting.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Faces the entity you mount.")
            .defaultValue(true)
            .build()
    );

    // Animals

    private final Setting<Boolean> donkeys  = sgAnimals.add(new BoolSetting.Builder()
            .name("donkey")
            .description("Donkey")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> skeletonHorse = sgAnimals.add(new BoolSetting.Builder()
            .name("skeleton-horse")
            .description("Skeleton Horse")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> llamas  = sgAnimals.add(new BoolSetting.Builder()
            .name("llama")
            .description("Llama")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> boats  = sgAnimals.add(new BoolSetting.Builder()
            .name("boat")
            .description("Boat")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> minecarts  = sgAnimals.add(new BoolSetting.Builder()
            .name("minecart")
            .description("Minecart")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> horses  = sgGeneral.add(new BoolSetting.Builder()
            .name("horse")
            .description("Horse")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> pigs  = sgAnimals.add(new BoolSetting.Builder()
            .name("pig")
            .description("Pig")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> mules  = sgAnimals.add(new BoolSetting.Builder()
            .name("mule")
            .description("Mule")
            .defaultValue(false)
            .build()
    );

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player.hasVehicle()) return;
        for (Entity entity : mc.world.getEntities()){
            if (mc.player.distanceTo(entity) > 4) continue;

            if (mc.player.getMainHandStack().getItem() instanceof SpawnEggItem) return;
            if (donkeys.get() && entity instanceof DonkeyEntity && (!checkSaddle.get() || ((DonkeyEntity) entity).isSaddled())) {
                if (rotate.get()) RotationUtils.packetRotate(entity);
                mc.player.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(entity, Hand.MAIN_HAND, mc.player.isSneaking()));
            } else if (llamas.get() && entity instanceof LlamaEntity) {
                if (rotate.get()) RotationUtils.packetRotate(entity);
                mc.player.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(entity, Hand.MAIN_HAND, mc.player.isSneaking()));
            } else if (boats.get() && entity instanceof BoatEntity) {
                if (rotate.get()) RotationUtils.packetRotate(entity);
                mc.player.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(entity, Hand.MAIN_HAND, mc.player.isSneaking()));
            } else if (minecarts.get() && entity instanceof MinecartEntity) {
                if (rotate.get()) RotationUtils.packetRotate(entity);
                mc.player.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(entity, Hand.MAIN_HAND, mc.player.isSneaking()));
            } else if (horses.get() && entity instanceof HorseEntity && (!checkSaddle.get() || ((HorseEntity) entity).isSaddled())) {
                if (rotate.get()) RotationUtils.packetRotate(entity);
                mc.player.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(entity, Hand.MAIN_HAND, mc.player.isSneaking()));
            } else if (pigs.get() && entity instanceof PigEntity && ((PigEntity) entity).isSaddled()) {
                if (rotate.get()) RotationUtils.packetRotate(entity);
                mc.player.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(entity, Hand.MAIN_HAND, mc.player.isSneaking()));
            } else if (mules.get() && entity instanceof MuleEntity && (!checkSaddle.get() || ((MuleEntity) entity).isSaddled())) {
                if (rotate.get()) RotationUtils.packetRotate(entity);
                mc.player.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(entity, Hand.MAIN_HAND, mc.player.isSneaking()));
            } else if (skeletonHorse.get() && entity instanceof SkeletonHorseEntity && (!checkSaddle.get() || ((SkeletonHorseEntity) entity).isSaddled())) {
                if (rotate.get()) RotationUtils.packetRotate(entity);
                mc.player.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(entity, Hand.MAIN_HAND, mc.player.isSneaking()));
            }
        }
    }
}
