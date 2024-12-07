/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.mixininterface.IExplosion;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.SoundBlocker;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(Explosion.class)
public abstract class ExplosionMixin implements IExplosion {
    @Shadow @Final @Mutable private World world;
    @Shadow @Final @Mutable @Nullable private Entity entity;

    @Shadow @Final @Mutable private double x;
    @Shadow @Final @Mutable private double y;
    @Shadow @Final @Mutable private double z;

    @Shadow @Final @Mutable private float power;
    @Shadow @Final @Mutable private boolean createFire;
    @Shadow @Final @Mutable private Explosion.DestructionType destructionType;

    @Override
    public void set(Vec3d pos, float power, boolean createFire) {
        this.world = mc.world;
        this.entity = null;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.power = power;
        this.createFire = createFire;
        this.destructionType = Explosion.DestructionType.DESTROY;
    }

    @Redirect(method = "affectWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;playSound(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZ)V"))
    private void redirect(World instance, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, boolean useDistance) {
        SoundBlocker blocker = Modules.get().get(SoundBlocker.class);

        if (blocker.isActive()) {
            instance.playSound(x, y, z, sound, category,
                    (float) (volume * blocker.getCrystalVolume()), pitch, useDistance);
        } else {
            instance.playSound(x, y, z, sound, category, volume, pitch, useDistance);
        }
    }
}