package minegame159.meteorclient.utils.player;

import meteordevelopment.orbit.EventHandler;
import minegame159.meteorclient.MeteorClient;
import minegame159.meteorclient.events.entity.player.SendMovementPacketsEvent;
import minegame159.meteorclient.events.world.TickEvent;
import minegame159.meteorclient.utils.entity.Target;
import minegame159.meteorclient.utils.misc.Pool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class Rotations {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Pool<Rotation> rotationPool = new Pool<>(Rotation::new);
    private static final List<Rotation> rotations = new ArrayList<>();
    private static float preYaw, prePitch;
    private static int i = 0;

    public static float serverYaw;
    public static float serverPitch;
    public static int rotationTimer;

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(Rotations.class);
    }

    public static void rotate(double yaw, double pitch, int priority, Runnable callback) {
        Rotation rotation = rotationPool.get();
        rotation.set(yaw, pitch, priority, callback);

        int i = 0;
        for (; i < rotations.size(); i++) {
            if (priority > rotations.get(i).priority) break;
        }

        rotations.add(i, rotation);
    }

    public static void rotate(double yaw, double pitch, Runnable callback) {
        rotate(yaw, pitch, 0, callback);
    }

    public static void rotate(double yaw, double pitch) {
        rotate(yaw, pitch, 0, null);
    }

    @EventHandler
    private static void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (!rotations.isEmpty() && mc.cameraEntity == mc.player) {
            Rotation rotation = rotations.get(i);

            preYaw = mc.player.yaw;
            prePitch = mc.player.pitch;

            mc.player.yaw = (float) rotation.yaw;
            mc.player.pitch = (float) rotation.pitch;

            setCamRotation(rotation.yaw, rotation.pitch);
            rotation.runCallback();

            rotationPool.free(rotation);
            i++;
        }
    }

    @EventHandler
    private static void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (!rotations.isEmpty()) {
            if (mc.cameraEntity == mc.player) {
                mc.player.yaw = preYaw;
                mc.player.pitch = prePitch;
            }

            for (; i < rotations.size(); i++) {
                Rotation rotation = rotations.get(i);

                setCamRotation(rotation.yaw, rotation.pitch);
                rotation.sendPacket();

                rotationPool.free(rotation);
            }

            rotations.clear();
            i = 0;
        }
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        rotationTimer++;
    }

    public static double getYaw(Entity entity) {
        return mc.player.yaw + MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(entity.getZ() - mc.player.getZ(), entity.getX() - mc.player.getX())) - 90f - mc.player.yaw);
    }

    public static double getPitch(Entity entity, Target target) {
        double y;
        if (target == Target.Head) y = entity.getEyeY();
        else if (target == Target.Body) y = entity.getY() + entity.getHeight() / 2;
        else y = entity.getY();

        double diffX = entity.getX() - mc.player.getX();
        double diffY = y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = entity.getZ() - mc.player.getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return mc.player.pitch + MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - mc.player.pitch);
    }
    public static double getPitch(Entity entity) { return getPitch(entity, Target.Body); }

    public static double getYaw(BlockPos pos) {
        return mc.player.yaw + MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(pos.getZ() + 0.5 - mc.player.getZ(), pos.getX() + 0.5 - mc.player.getX())) - 90f - mc.player.yaw);
    }

    public static double getPitch(BlockPos pos) {
        double diffX = pos.getX() + 0.5 - mc.player.getX();
        double diffY = pos.getY() + 0.5 - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = pos.getZ() + 0.5 - mc.player.getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return mc.player.pitch + MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - mc.player.pitch);
    }

    public static void setCamRotation(double yaw, double pitch) {
        serverYaw = (float) yaw;
        serverPitch = (float) pitch;
        rotationTimer = 0;
    }

    private static class Rotation {
        public double yaw, pitch;
        public int priority;
        public Runnable callback;

        public void set(double yaw, double pitch, int priority, Runnable callback) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.priority = priority;
            this.callback = callback;
        }

        public void sendPacket() {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookOnly((float) yaw, (float) pitch, mc.player.isOnGround()));
            runCallback();
        }

        public void runCallback() {
            if (callback != null) callback.run();
        }
    }
}
