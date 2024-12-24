package meteordevelopment.meteorclient.systems.managers;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.LookAtEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerJumpEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerTravelEvent;
import meteordevelopment.meteorclient.events.entity.player.RotateEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.entity.player.UpdatePlayerVelocity;
import meteordevelopment.meteorclient.events.input.KeyboardInputEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.config.AntiCheatConfig;
import meteordevelopment.meteorclient.systems.modules.movement.MovementFix;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.*;

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

    private static float renderPitch;
    private static float renderYawOffset;
    private static float prevPitch;
    private static float prevRenderYawOffset;
    private static float prevRotationYawHead;
    private static float rotationYawHead;

    public static boolean sendDisablerPacket = false;
    public static float lastActualYaw = 0f;

    private int ticksExisted;

    public static Vec3d targetVec = null;
    public static boolean lastGround;
    public double lastX = 0;
    public double lastY = 0;
    public double lastZ = 0;

    private boolean shouldFulfilRequest = false;
    private static RotationRequest request = new RotationRequest();

    private final AntiCheatConfig antiCheatConfig = AntiCheatConfig.get();

    public void snapAt(Vec3d target) {
        float[] angle = getRotation(target);

        if (antiCheatConfig.grimSnapRotation.get()) {
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.Full(lastX, lastY, lastZ, angle[0], angle[1], lastGround));
        } else {
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.LookAndOnGround(angle[0], angle[1], lastGround));
        }
    }

    public void snapAt(float yaw, float pitch) {
        if (antiCheatConfig.grimSnapRotation.get()) {
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.Full(lastX, lastY, lastZ, yaw, pitch, lastGround));
        } else {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, lastGround));
        }
    }

    public void requestRotation(Vec3d target, double priority) {
        float[] angle = getRotation(target);

        requestRotation(angle[0], angle[1], priority, null);
    }

    public void requestRotation(Vec3d target, double priority, Runnable callback) {
        float[] angle = getRotation(target);

        requestRotation(angle[0], angle[1], priority, callback);
    }

    public void requestRotation(float yaw, float pitch, double priority) {
        requestRotation(yaw, pitch, priority, null);
    }

    public void requestRotation(float yaw, float pitch, double priority, Runnable callback) {
        if (request.priority > priority && !request.fulfilled) {
            return;
        }

        request.fulfilled = false;
        request.yaw = yaw;
        request.pitch = pitch;
        request.priority = priority;
        request.callback = callback;
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

    public boolean lookingAt(Box box) {
        return lookingAt(lastYaw, lastPitch, box);
    }

    public boolean lookingAt(float yaw, float pitch, Box box) {
        if (raytraceCheck(mc.player.getEyePos(), yaw, pitch, box))
            return true;

        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLastRotation(RotateEvent event) {
        LookAtEvent lookAtEvent = new LookAtEvent();
        MeteorClient.EVENT_BUS.post(lookAtEvent);

        shouldFulfilRequest = false;
        if (request != null && !request.fulfilled && request.priority > lookAtEvent.priority) {
            event.setYaw(request.yaw);
            event.setPitch(request.pitch);

            shouldFulfilRequest = true;
            return;
        }

        if (lookAtEvent.getRotation()) {
            event.setYaw(lookAtEvent.getYaw());
            event.setPitch(lookAtEvent.getPitch());
        } else if (lookAtEvent.getTarget() != null) {
            float[] newAngle = getRotation(lookAtEvent.getTarget());
            event.setYaw(newAngle[0]);
            event.setPitch(newAngle[1]);
        }
    }

    @EventHandler(priority = -999)
    public void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || event.isCancelled())
            return;
        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesLook()) {
                lastYaw = packet.getYaw(lastYaw);
                if (sendDisablerPacket) {
                    sendDisablerPacket = false;
                    lastYaw = lastActualYaw;
                }
                lastPitch = packet.getPitch(lastPitch);

                setRenderRotation(lastYaw, lastPitch, false);
            }

            if (packet.changesPosition()) {
                lastX = packet.getX(lastX);
                lastY = packet.getY(lastY);
                lastZ = packet.getZ(lastZ);
            }

            lastGround = packet.isOnGround();
        }
    }

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

            if (packet.getFlags().contains(PositionFlag.X)) {
                lastX = lastX + packet.getX();
            } else {
                lastX = packet.getX();
            }

            if (packet.getFlags().contains(PositionFlag.Y)) {
                lastY = lastY + packet.getY();
            } else {
                lastY = packet.getY();
            }

            if (packet.getFlags().contains(PositionFlag.Z)) {
                lastZ = lastZ + packet.getZ();
            } else {
                lastZ = packet.getZ();
            }

            setRenderRotation(lastYaw, lastPitch, true);
        }
    }

    @EventHandler
    public void onUpdateWalkingPost(SendMovementPacketsEvent.Post event) {
        setRenderRotation(lastYaw, lastPitch, false);
    }

    @EventHandler
    public void onMovementPacket(SendMovementPacketsEvent.Rotation event) {
        if (!antiCheatConfig.tickSync.get()) {
            return;
        }

        if (shouldFulfilRequest && !request.fulfilled) {
            request.fulfilled = true;
            shouldFulfilRequest = false;
        }

        if (MovementFix.MOVE_FIX.isActive()) {
            event.yaw = nextYaw;
            event.pitch = nextPitch;
        } else {
            RotateEvent rotateEvent = new RotateEvent(event.yaw, event.pitch);
            MeteorClient.EVENT_BUS.post(rotateEvent);
            event.yaw = rotateEvent.getYaw();
            event.pitch = rotateEvent.getPitch();
        }

        if (antiCheatConfig.grimRotation.get()) {
            event.forceFull = true;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUpdatePlayerVelocity(UpdatePlayerVelocity event) {
        if (MovementFix.MOVE_FIX.isActive()
                && MovementFix.MOVE_FIX.updateMode.get() != MovementFix.UpdateMode.Mouse) {
            moveFixRotation();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreJump(PlayerJumpEvent.Pre e) {
        if (MovementFix.MOVE_FIX.isActive()
                && MovementFix.MOVE_FIX.updateMode.get() != MovementFix.UpdateMode.Mouse) {
            moveFixRotation();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTravel(PlayerTravelEvent.Pre e) {
        if (MovementFix.MOVE_FIX.isActive()
                && MovementFix.MOVE_FIX.updateMode.get() != MovementFix.UpdateMode.Mouse) {
            moveFixRotation();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKeyInput(KeyboardInputEvent e) {
        if (MovementFix.MOVE_FIX.isActive()
                && MovementFix.MOVE_FIX.updateMode.get() != MovementFix.UpdateMode.Mouse) {
            moveFixRotation();
        }

    }

    private void moveFixRotation() {
        if (MovementFix.setRot) {
            mc.player.setYaw(MovementFix.prevYaw);
            mc.player.setPitch(MovementFix.prevPitch);
        }
        RotateEvent rotateEvent = new RotateEvent(mc.player.getYaw(), mc.player.getPitch());
        MeteorClient.EVENT_BUS.post(rotateEvent);

        nextYaw = rotateEvent.getYaw();
        nextPitch = rotateEvent.getPitch();

        MovementFix.fixYaw = nextYaw;
        MovementFix.fixPitch = nextPitch;

        if (MovementFix.setRot) {
            mc.player.setYaw(MovementFix.fixYaw);
            mc.player.setPitch(MovementFix.fixPitch);
        }
    }

    public boolean raytraceCheck(Vec3d pos, double y, double p, Box box) {
        // Vector in direction of yaw and pitch
        Vec3d vec =
                new Vec3d(Math.cos(Math.toRadians(y + 90)) * Math.abs(Math.cos(Math.toRadians(p))),
                        -Math.sin(Math.toRadians(p)),
                        Math.sin(Math.toRadians(y + 90)) * Math.abs(Math.cos(Math.toRadians(p))));

        // Ray origin
        double rayX = pos.x;
        double rayY = pos.y;
        double rayZ = pos.z;

        // Ray direction
        double dirX = vec.x;
        double dirY = vec.y;
        double dirZ = vec.z;

        // Box bounds
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // Avoid division by zero by replacing 0 with a very small number
        double invDirX = (dirX != 0) ? 1.0 / dirX : 1e10;
        double invDirY = (dirY != 0) ? 1.0 / dirY : 1e10;
        double invDirZ = (dirZ != 0) ? 1.0 / dirZ : 1e10;

        // Calculate intersection t-values for x, y, z slabs
        double tMinX = (minX - rayX) * invDirX;
        double tMaxX = (maxX - rayX) * invDirX;
        if (tMinX > tMaxX) {
            double temp = tMinX;
            tMinX = tMaxX;
            tMaxX = temp;
        }

        double tMinY = (minY - rayY) * invDirY;
        double tMaxY = (maxY - rayY) * invDirY;
        if (tMinY > tMaxY) {
            double temp = tMinY;
            tMinY = tMaxY;
            tMaxY = temp;
        }

        double tMinZ = (minZ - rayZ) * invDirZ;
        double tMaxZ = (maxZ - rayZ) * invDirZ;
        if (tMinZ > tMaxZ) {
            double temp = tMinZ;
            tMinZ = tMaxZ;
            tMaxZ = temp;
        }

        // Find the intersection range by combining the slabs
        double tMin = Math.max(Math.max(tMinX, tMinY), tMinZ);
        double tMax = Math.min(Math.min(tMaxX, tMaxY), tMaxZ);

        // If tMax < 0, the ray intersects but the box is behind the ray
        // If tMin > tMax, the ray misses the box
        return tMax >= 0 && tMin <= tMax;
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

    public static class RotationRequest {
        public double priority;

        public float yaw;

        public float pitch;

        public boolean fulfilled = false;

        public Runnable callback = null;
    }
}
