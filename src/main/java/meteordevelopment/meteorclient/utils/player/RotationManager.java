package meteordevelopment.meteorclient.utils.player;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.LookAtEvent;
import meteordevelopment.meteorclient.events.entity.player.RotateEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RotationManager {
    public RotationManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public float nextYaw;
    public float nextPitch;
    public float rotationYaw = 0;
    public float rotationPitch = 0;
    public float lastYaw = 0;
    public float lastPitch = 0;
    public static final Timer ROTATE_TIMER = new Timer();
    public static Vec3d directionVec = null;
    public static boolean lastGround;

    private boolean sentRotationPacketThisMove = false;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        /*
         * if (!ROTATE_TIMER.passed(50)) { SendMovementPacketsEvent.Packet packet = new
         * SendMovementPacketsEvent.Packet(new PlayerMoveC2SPacket.LookAndOnGround(
         * mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
         * onMovementPacket(packet);
         * 
         * if (packet.packet != null) { mc.getNetworkHandler().sendPacket(packet.packet); } }
         */
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMovePre(SendMovementPacketsEvent.Pre event) {
        sentRotationPacketThisMove = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMovePost(SendMovementPacketsEvent.Post event) {
        if (!sentRotationPacketThisMove) {
            RotateEvent event1 = new RotateEvent(mc.player.getYaw(), mc.player.getPitch());
            MeteorClient.EVENT_BUS.post(event1);

            if (!event1.isModified()) {
                return;
            }

            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    event1.getYaw(), event1.getPitch(), mc.player.isOnGround()));

            rotationYaw = event1.getYaw();
            rotationPitch = event1.getPitch();
        }
    }

    public void snapBack() {
        mc.getNetworkHandler()
                .sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(),
                        mc.player.getZ(), rotationYaw, rotationPitch, mc.player.isOnGround()));
    }

    public void lookAt(Vec3d directionVec) {
        rotationTo(directionVec);
        snapAt(directionVec);
    }

    public void lookAt(BlockPos pos, Direction side) {
        final Vec3d hitVec = pos.toCenterPos().add(new Vec3d(side.getVector().getX() * 0.5,
                side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5));
        lookAt(hitVec);
    }

    public void snapAt(float yaw, float pitch) {
        snapAt(yaw, pitch, false);
    }

    public void snapAt(float yaw, float pitch, boolean send) {
        setRenderRotation(yaw, pitch, true);
        if (send) {
            SendMovementPacketsEvent.Packet packet = new SendMovementPacketsEvent.Packet(
                    new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround()));
            onMovementPacket(packet);

            if (packet.packet != null) {
                mc.getNetworkHandler().sendPacket(packet.packet);
            }
        }
    }

    public void snapAt(Vec3d directionVec) {
        snapAt(directionVec, false);
    }

    public void snapAt(Vec3d directionVec, boolean send) {
        float[] angle = getRotation(directionVec);
        snapAt(angle[0], angle[1], send);
    }

    public float[] getRotation(Vec3d eyesPos, Vec3d vec) {
        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        return new float[] {MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch)};
    }

    public float[] getRotation(Vec3d vec) {
        Vec3d eyesPos = mc.player.getEyePos();
        return getRotation(eyesPos, vec);
    }

    public void rotationTo(Vec3d vec3d) {
        ROTATE_TIMER.reset();
        directionVec = vec3d;
    }

    public boolean inFov(Vec3d directionVec, float fov) {
        float[] angle = getRotation(new Vec3d(mc.player.getX(),
                mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ()),
                directionVec);
        return inFov(angle[0], angle[1], fov);
    }

    public boolean inFov(float yaw, float pitch, float fov) {
        return MathHelper.angleBetween(yaw, rotationYaw) + Math.abs(pitch - rotationPitch) <= fov;
    }

    @EventHandler
    public void onMovementPacket(SendMovementPacketsEvent.Packet event) {
        RotateEvent event1 = new RotateEvent(event.packet.getYaw(mc.player.getYaw()),
                event.packet.getPitch(mc.player.getPitch()));
        MeteorClient.EVENT_BUS.post(event1);

        if (!event1.isModified()) {
            return;
        }

        sentRotationPacketThisMove = true;

        if (event.packet.changesLook()) {
            if (event.packet instanceof PlayerMoveC2SPacket.Full) {
                event.packet = new PlayerMoveC2SPacket.Full(event.packet.getX(0),
                        event.packet.getY(0), event.packet.getZ(0), event1.getYaw(),
                        event1.getPitch(), event.packet.isOnGround());
            } else if (event.packet instanceof PlayerMoveC2SPacket.LookAndOnGround) {
                event.packet = new PlayerMoveC2SPacket.LookAndOnGround(event1.getYaw(),
                        event1.getPitch(), event.packet.isOnGround());
            }
        } else {
            if (event.packet instanceof PlayerMoveC2SPacket.PositionAndOnGround) {
                event.packet = new PlayerMoveC2SPacket.Full(event.packet.getX(0),
                        event.packet.getY(0), event.packet.getZ(0), event1.getYaw(),
                        event1.getPitch(), event.packet.isOnGround());
            } else {
                event.packet = new PlayerMoveC2SPacket.LookAndOnGround(event1.getYaw(),
                        event1.getPitch(), event.packet.isOnGround());
            }
        }

        rotationYaw = event1.getYaw();
        rotationPitch = event1.getPitch();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void update(SendMovementPacketsEvent.Pre event) {
        /*
         * if (MoveFix.INSTANCE.isOn() &&
         * !MoveFix.INSTANCE.updateMode.is(MoveFix.UpdateMode.UpdateMouse) &&
         * !BaritoneModule.isActive()) { if (event.isPost()) { updateNext(); } }
         */
    }

    /*
     * @EventHandler(priority = EventPriority.LOWEST) public void update(MouseUpdateEvent event) {
     * if (mc.player != null && MoveFix.INSTANCE.isOn() &&
     * !MoveFix.INSTANCE.updateMode.is(MoveFix.UpdateMode.MovementPacket) &&
     * !BaritoneModule.isActive()) { updateNext(); } }
     */

    private void updateNext() {
        RotateEvent rotateEvent = new RotateEvent(mc.player.getYaw(), mc.player.getPitch());
        MeteorClient.EVENT_BUS.post(rotateEvent);
        if (rotateEvent.isModified()) {
            nextYaw = rotateEvent.getYaw();
            nextPitch = rotateEvent.getPitch();
        } else {
            float[] newAngle =
                    injectStep(new float[] {rotateEvent.getYaw(), rotateEvent.getPitch()}, 180f);
            nextYaw = newAngle[0];
            nextPitch = newAngle[1];
        }
        // MoveFix.fixRotation = nextYaw;
        // MoveFix.fixPitch = nextPitch;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLastRotation(RotateEvent event) {
        LookAtEvent lookAtEvent = new LookAtEvent();
        MeteorClient.EVENT_BUS.post(lookAtEvent);
        if (lookAtEvent.getRotation()) {
            float[] newAngle =
                    injectStep(new float[] {lookAtEvent.getYaw(), lookAtEvent.getPitch()},
                            lookAtEvent.getSpeed());
            event.setYaw(newAngle[0]);
            event.setPitch(newAngle[1]);
        } else if (lookAtEvent.getTarget() != null) {
            float[] newAngle = injectStep(lookAtEvent.getTarget(), lookAtEvent.getSpeed());
            event.setYaw(newAngle[0]);
            event.setPitch(newAngle[1]);
            // } else if (!event.isModified() && AntiCheat.INSTANCE.look.getValue()) {
            // if (directionVec != null && !ROTATE_TIMER
            // .passed((long) (AntiCheat.INSTANCE.rotateTime.getValue() * 1000))) {
            // float[] newAngle =
            // injectStep(directionVec, AntiCheat.INSTANCE.steps.getValueFloat());
            // event.setYaw(newAngle[0]);
            // event.setPitch(newAngle[1]);
            // }
        }
    }

    public float[] injectStep(Vec3d vec, float steps) {
        float currentYaw = /* AntiCheat.INSTANCE.forceSync.getValue() ? lastYaw : */ rotationYaw;
        float currentPitch =
                /* AntiCheat.INSTANCE.forceSync.getValue() ? lastPitch : */ rotationPitch;

        float yawDelta = MathHelper.wrapDegrees((float) MathHelper.wrapDegrees(
                Math.toDegrees(Math.atan2(vec.z - mc.player.getZ(), (vec.x - mc.player.getX())))
                        - 90)
                - currentYaw);
        float pitchDelta = ((float) (-Math.toDegrees(Math.atan2(
                vec.y - (mc.player.getPos().y + mc.player.getEyeHeight(mc.player.getPose())),
                Math.sqrt(Math.pow((vec.x - mc.player.getX()), 2)
                        + Math.pow(vec.z - mc.player.getZ(), 2)))))
                - currentPitch);

        float angleToRad = (float) Math.toRadians(27 * (mc.player.age % 30));
        yawDelta = (float) (yawDelta + Math.sin(angleToRad) * 3) + 0f;
        pitchDelta = pitchDelta + 0f;

        if (yawDelta > 180)
            yawDelta = yawDelta - 180;

        float yawStepVal = 180 * steps;

        float clampedYawDelta = MathHelper.clamp(MathHelper.abs(yawDelta), -yawStepVal, yawStepVal);
        float clampedPitchDelta = MathHelper.clamp(pitchDelta, -45, 45);

        float newYaw = currentYaw + (yawDelta > 0 ? clampedYawDelta : -clampedYawDelta);
        float newPitch = MathHelper.clamp(currentPitch + clampedPitchDelta, -90.0F, 90.0F);

        double gcdFix =
                (Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0)) * 1.2;

        return new float[] {(float) (newYaw - (newYaw - currentYaw) % gcdFix),
                (float) (newPitch - (newPitch - currentPitch) % gcdFix)};
    }

    public float[] injectStep(float[] angle, float steps) {
        if (steps < 0.01f)
            steps = 0.01f;
        if (steps > 1)
            steps = 1;
        if (steps < 1 && angle != null) {
            float packetYaw = /* AntiCheat.INSTANCE.forceSync.getValue() ? lastYaw : */ rotationYaw;
            float diff = MathHelper.angleBetween(angle[0], packetYaw);
            if (Math.abs(diff) > 180 * steps) {
                angle[0] = (packetYaw + (diff * ((180 * steps) / Math.abs(diff))));
            }
            float packetPitch =
                    /* AntiCheat.INSTANCE.forceSync.getValue() ? lastPitch : */ rotationPitch;
            diff = angle[1] - packetPitch;
            if (Math.abs(diff) > 90 * steps) {
                angle[1] = (packetPitch + (diff * ((90 * steps) / Math.abs(diff))));
            }
        }
        return new float[] {angle[0], angle[1]};
    }

    @EventHandler(priority = -999)
    public void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || event.isCancelled())
            return;
        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesLook()) {
                lastYaw = packet.getYaw(lastYaw);
                lastPitch = packet.getPitch(lastPitch);
                setRenderRotation(lastYaw, lastPitch, false);
            }
            lastGround = packet.isOnGround();
        }
    }

    private static float renderPitch;
    private static float renderYawOffset;
    private static float prevPitch;
    private static float prevRenderYawOffset;
    private static float prevRotationYawHead;
    private static float rotationYawHead;
    private int ticksExisted;

    @EventHandler(priority = EventPriority.HIGH)
    public void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null)
            return;
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            if (packet.getFlags().contains(PositionFlag.X_ROT)) {
                lastYaw = lastYaw + packet.getYaw();
            } else {
                lastYaw = packet.getYaw();
            }

            if (packet.getFlags().contains(PositionFlag.Y_ROT)) {
                lastPitch = lastPitch + packet.getPitch();
            } else {
                lastPitch = packet.getPitch();
            }
            setRenderRotation(lastYaw, lastPitch, true);
        }
    }

    @EventHandler
    public void onUpdateWalkingPost(SendMovementPacketsEvent.Post event) {
        setRenderRotation(lastYaw, lastPitch, false);
    }

    public void setRenderRotation(float yaw, float pitch, boolean force) {
        if (mc.player == null)
            return;
        if (mc.player.age == ticksExisted && !force) {
            return;
        }

        ticksExisted = mc.player.age;
        prevPitch = renderPitch;

        prevRenderYawOffset = renderYawOffset;
        renderYawOffset = getRenderYawOffset(yaw, prevRenderYawOffset);

        prevRotationYawHead = rotationYawHead;
        rotationYawHead = yaw;

        renderPitch = pitch;
    }

    public static float getRenderPitch() {
        return renderPitch;
    }

    public static float getRotationYawHead() {
        return rotationYawHead;
    }

    public static float getRenderYawOffset() {
        return renderYawOffset;
    }

    public static float getPrevPitch() {
        return prevPitch;
    }

    public static float getPrevRotationYawHead() {
        return prevRotationYawHead;
    }

    public static float getPrevRenderYawOffset() {
        return prevRenderYawOffset;
    }

    private float getRenderYawOffset(float yaw, float offsetIn) {
        float result = offsetIn;
        float offset;

        double xDif = mc.player.getX() - mc.player.prevX;
        double zDif = mc.player.getZ() - mc.player.prevZ;

        if (xDif * xDif + zDif * zDif > 0.0025000002f) {
            offset = (float) MathHelper.atan2(zDif, xDif) * 57.295776f - 90.0f;
            float wrap = MathHelper.abs(MathHelper.wrapDegrees(yaw) - offset);
            if (95.0F < wrap && wrap < 265.0F) {
                result = offset - 180.0F;
            } else {
                result = offset;
            }
        }

        if (mc.player.handSwingProgress > 0.0F) {
            result = yaw;
        }

        result = offsetIn + MathHelper.wrapDegrees(result - offsetIn) * 0.3f;
        offset = MathHelper.wrapDegrees(yaw - result);

        if (offset < -75.0f) {
            offset = -75.0f;
        } else if (offset >= 75.0f) {
            offset = 75.0f;
        }

        result = yaw - offset;
        if (offset * offset > 2500.0f) {
            result += offset * 0.2f;
        }

        return result;
    }
}
