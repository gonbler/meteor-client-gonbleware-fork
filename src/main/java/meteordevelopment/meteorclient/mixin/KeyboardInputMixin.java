/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.input.KeyboardInputEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.ElytraFakeFly;
import meteordevelopment.meteorclient.systems.modules.movement.Sneak;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void isPressed(boolean slowDown, float f, CallbackInfo ci) {
        if (Modules.get().get(Sneak.class).doVanilla()) sneaking = true;

        if (Modules.get().get(ElytraFakeFly.class).isFlying()) sneaking = false;
    }

    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/input/KeyboardInput;sneaking:Z", shift = At.Shift.AFTER), cancellable = true)
    private void onSneak(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        KeyboardInputEvent event = new KeyboardInputEvent();
        MeteorClient.EVENT_BUS.post(event);
        if (event.isCancelled()) ci.cancel();
    }
}
