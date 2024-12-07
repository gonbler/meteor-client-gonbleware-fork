/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.DamageEvent;
import meteordevelopment.meteorclient.events.entity.DropItemsEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerJumpEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerTickMovementEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.systems.managers.RotationManager;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoCrystal;
import meteordevelopment.meteorclient.systems.modules.movement.*;
import meteordevelopment.meteorclient.systems.modules.player.Portals;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ClientPlayerTickable;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
    @Shadow
    public Input input;

    @Shadow
    @Final
    public ClientPlayNetworkHandler networkHandler;

    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void onDropSelectedItem(boolean dropEntireStack, CallbackInfoReturnable<Boolean> info) {
        if (MeteorClient.EVENT_BUS.post(DropItemsEvent.get(getMainHandStack())).isCancelled())
            info.setReturnValue(false);
    }

    @Redirect(method = "tickNausea", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;"))
    private Screen updateNauseaGetCurrentScreenProxy(MinecraftClient client) {
        if (Modules.get().isActive(Portals.class))
            return null;
        return client.currentScreen;
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean redirectUsingItem(boolean isUsingItem) {
        if (Modules.get().get(NoSlow.class).items())
            return false;
        return isUsingItem;
    }

    @Inject(method = "isSneaking", at = @At("HEAD"), cancellable = true)
    private void onIsSneaking(CallbackInfoReturnable<Boolean> info) {
        if (Modules.get().get(Scaffold.class).scaffolding())
            info.setReturnValue(false);
        if (Modules.get().get(Flight.class).noSneak())
            info.setReturnValue(false);
    }

    @Inject(method = "shouldSlowDown", at = @At("HEAD"), cancellable = true)
    private void onShouldSlowDown(CallbackInfoReturnable<Boolean> info) {
        if (Modules.get().get(NoSlow.class).sneaking()) {
            info.setReturnValue(isCrawling());
        }

        if (isCrawling() && Modules.get().get(NoSlow.class).crawling()) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double d, CallbackInfo info) {
        Velocity velocity = Modules.get().get(Velocity.class);
        if (velocity.isActive() && velocity.blocks.get()) {
            info.cancel();
        }
    }

    @Inject(method = "damage", at = @At("HEAD"))
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        if (Utils.canUpdate() && getWorld().isClient && canTakeDamage())
            MeteorClient.EVENT_BUS.post(DamageEvent.get(this, source));
    }

    @ModifyExpressionValue(method = "canSprint",
            at = @At(value = "CONSTANT", args = "floatValue=6.0f"))
    private float onHunger(float constant) {
        if (Modules.get().get(NoSlow.class).hunger())
            return -1;
        return constant;
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSneaking()Z"))
    private boolean isSneaking(boolean sneaking) {
        return Modules.get().get(Sneak.class).doPacket()
                || Modules.get().get(NoSlow.class).airStrict() || sneaking;
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void preTickMovement(CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(PlayerTickMovementEvent.get());
    }

    // Sprint

    @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isWalking()Z"))
    private boolean modifyIsWalking(boolean original) {
        if (!Modules.get().get(Sprint.class).rageSprint())
            return original;

        float forwards = Math.abs(input.movementSideways);
        float sideways = Math.abs(input.movementForward);

        return (isSubmergedInWater() ? (forwards > 1.0E-5F || sideways > 1.0E-5F)
                : (forwards > 0.8 || sideways > 0.8));
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
    private boolean modifyMovement(boolean original) {
        if (!Modules.get().get(Sprint.class).rageSprint())
            return original;

        return Math.abs(input.movementSideways) > 1.0E-5F
                || Math.abs(input.movementForward) > 1.0E-5F;
    }

    @WrapWithCondition(method = "tickMovement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;setSprinting(Z)V",
                    ordinal = 3))
    private boolean wrapSetSprinting(ClientPlayerEntity instance, boolean b) {
        return !Modules.get().get(Sprint.class).rageSprint();
    }

    // Rotations
    @Shadow
	private void sendSprintingPacket() {}

	@Shadow
	@Final
	private List<ClientPlayerTickable> tickables;
    
	@Shadow
	private boolean autoJumpEnabled;

	@Shadow
	private double lastX;

	@Shadow
	private double lastBaseY;

	@Shadow
	private double lastZ;

	@Shadow
	private float lastYaw;

	@Shadow
	private float lastPitch;

	@Shadow
	private boolean lastOnGround;

	@Shadow
	private boolean lastSneaking;

	@Shadow
	private int ticksSinceLastPositionPacketSent;

	@Shadow
	private void sendMovementPackets() {
	}

	@Shadow
	protected boolean isCamera() {
		return false;
	}

	@Shadow
	public abstract float getPitch(float tickDelta);

    @Inject(method = "sendMovementPackets", at = {@At("HEAD")}, cancellable = true)
    private void sendMovementPacketsHook(CallbackInfo ci) {
        ci.cancel();

        SendMovementPacketsEvent.Pre updateEvent = new SendMovementPacketsEvent.Pre();
        MeteorClient.EVENT_BUS.post(updateEvent);

        this.sendSprintingPacket();

        if (this.isSneaking() != this.lastSneaking) {
            ClientCommandC2SPacket.Mode mode = this.isSneaking() ? ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY
                    : ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY;
            this.networkHandler.sendPacket(new ClientCommandC2SPacket(this, mode));
            this.lastSneaking = this.isSneaking();
        }

        if (this.isCamera()) {
            double d = this.getX() - this.lastX;
            double e = this.getY() - this.lastBaseY;
            double f = this.getZ() - this.lastZ;

            float yaw = this.getYaw();
            float pitch = this.getPitch();

            SendMovementPacketsEvent.Rotation movementPacketsEvent = new SendMovementPacketsEvent.Rotation(yaw, pitch);
            MeteorClient.EVENT_BUS.post(movementPacketsEvent);

            yaw = movementPacketsEvent.yaw;
            pitch = movementPacketsEvent.pitch;
            MeteorClient.ROTATION.rotationYaw = yaw;
            MeteorClient.ROTATION.rotationPitch = pitch;

            double deltaYaw = yaw - MeteorClient.ROTATION.lastYaw;
            double deltaPitch = pitch - MeteorClient.ROTATION.lastPitch;

            this.ticksSinceLastPositionPacketSent++;

            boolean positionChanged = MathHelper.squaredMagnitude(d, e, f) > MathHelper.square(2.0E-4) || this.ticksSinceLastPositionPacketSent >= 20;
            boolean rotationChanged = (deltaYaw != 0.0 || deltaPitch != 0.0);
            
            if (this.hasVehicle()) {
                Vec3d vec3d = this.getVelocity();
                this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(vec3d.x, -999.0,
                        vec3d.z, yaw, pitch, this.isOnGround()));
                positionChanged = false;
            } else if (positionChanged && rotationChanged) {
                this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(this.getX(),
                        this.getY(), this.getZ(), yaw, pitch, this.isOnGround()));
            } else if (positionChanged) {
                this.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        this.getX(), this.getY(), this.getZ(), this.isOnGround()));
            } else if (rotationChanged) {
                this.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, this.isOnGround()));
            } else if (this.lastOnGround != this.isOnGround()) {
                this.networkHandler
                        .sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(this.isOnGround()));
            }

            if (positionChanged) {
                this.lastX = this.getX();
                this.lastBaseY = this.getY();
                this.lastZ = this.getZ();
                this.ticksSinceLastPositionPacketSent = 0;
            }

            if (rotationChanged) {
                this.lastYaw = yaw;
                this.lastPitch = pitch;
            }

            this.lastOnGround = this.isOnGround();
            this.autoJumpEnabled = mc.options.getAutoJump().getValue();
        }

        MeteorClient.EVENT_BUS.post(new SendMovementPacketsEvent.Post());
    }

    /*@Inject(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasVehicle()Z",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void tickHook(CallbackInfo ci) {
        
        ci.cancel();

            if (this.hasVehicle()) {
                UpdateWalkingPlayerEvent updateEvent =
                        new UpdateWalkingPlayerEvent(Event.Stage.Pre);
                Alien.EVENT_BUS.post(updateEvent);
                float yaw = this.getYaw();
                float pitch = this.getPitch();
                MovementPacketsEvent movementPacketsEvent = new MovementPacketsEvent(yaw, pitch);
                Alien.EVENT_BUS.post(movementPacketsEvent);
                yaw = movementPacketsEvent.getYaw();
                pitch = movementPacketsEvent.getPitch();
                Alien.ROTATION.rotationYaw = yaw;
                Alien.ROTATION.rotationPitch = pitch;

                this.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, this.isOnGround()));
                Alien.EVENT_BUS.post(new UpdateWalkingPlayerEvent(Event.Stage.Post));
                this.networkHandler.sendPacket(new PlayerInputC2SPacket(this.sidewaysSpeed,
                        this.forwardSpeed, this.input.jumping, this.input.sneaking));
                Entity entity = this.getRootVehicle();
                if (entity != this && entity.isLogicalSideForUpdatingMovement()) {
                    this.networkHandler.sendPacket(new VehicleMoveC2SPacket(entity));
                    this.sendSprintingPacket();
                }
            } else {
                this.sendMovementPackets();
            }

            for (ClientPlayerTickable clientPlayerTickable : this.tickables) {
                clientPlayerTickable.tick();
            }
    }*/

    /*
     * @Inject(method = "sendMovementPackets", at = @At("HEAD")) private void
     * onSendMovementPacketsHead(CallbackInfo info) {
     * MeteorClient.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get()); }
     * 
     * @Inject(method = "tick", at = @At(value = "INVOKE", target =
     * "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V",
     * ordinal = 0)) private void onTickHasVehicleBeforeSendPackets(CallbackInfo info) {
     * MeteorClient.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get()); }
     * 
     * @Inject(method = "sendMovementPackets", at = @At("TAIL")) private void
     * onSendMovementPacketsTail(CallbackInfo info) {
     * MeteorClient.EVENT_BUS.post(SendMovementPacketsEvent.Post.get()); }
     * 
     * @Inject(method = "tick", at = @At(value = "INVOKE", target =
     * "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V",
     * ordinal = 1, shift = At.Shift.AFTER)) private void
     * onTickHasVehicleAfterSendPackets(CallbackInfo info) {
     * MeteorClient.EVENT_BUS.post(SendMovementPacketsEvent.Post.get()); }
     * 
     * 
     * @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target =
     * "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V",
     * ordinal = 2)) private void sendPacketFull(ClientPlayNetworkHandler instance, Packet<?>
     * packet) { networkHandler.sendPacket(MeteorClient.EVENT_BUS.post(new
     * SendMovementPacketsEvent.Packet((PlayerMoveC2SPacket.Full) packet)).packet); }
     * 
     * @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target =
     * "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V",
     * ordinal = 3)) private void sendPacketPosGround(ClientPlayNetworkHandler instance, Packet<?>
     * packet) { networkHandler.sendPacket(MeteorClient.EVENT_BUS.post(new
     * SendMovementPacketsEvent.Packet((PlayerMoveC2SPacket.PositionAndOnGround) packet)).packet); }
     * 
     * @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target =
     * "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V",
     * ordinal = 4)) private void sendPacketLookGround(ClientPlayNetworkHandler instance, Packet<?>
     * packet) { PlayerMoveC2SPacket toSend = MeteorClient.EVENT_BUS.post(new
     * SendMovementPacketsEvent.Packet((PlayerMoveC2SPacket.LookAndOnGround) packet)).packet;
     * 
     * if (toSend != null) { networkHandler.sendPacket(toSend); } }
     * 
     * @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target =
     * "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V",
     * ordinal = 5)) private void sendPacketGround(ClientPlayNetworkHandler instance, Packet<?>
     * packet) { networkHandler.sendPacket(MeteorClient.EVENT_BUS.post(new
     * SendMovementPacketsEvent.Packet((PlayerMoveC2SPacket.OnGroundOnly) packet)).packet); }
     */


}
