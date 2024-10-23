package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ElytraSpeed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private boolean using;
    private double yaw;
    private double pitch;
    private Vec3d lastMovement;
    private boolean rubberband;

    private final Setting<Double> startSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("start-speed").description("Initial speed when you use a firework")
            .defaultValue(30).min(0).sliderMax(100).build());

    private final Setting<Double> accel =
            sgGeneral.add(new DoubleSetting.Builder().name("accel-speed")
                    .description("Acceleration").defaultValue(3).min(0).sliderMax(5).build());

    private final Setting<Double> maxSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("max-speed").description("Maximum speed you can go while flying")
            .defaultValue(100).min(0).sliderMax(250).build());

    public ElytraSpeed() {
        super(Categories.Movement, "elytra-speed",
                "Makes your elytra faster when you use a firework.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        using = false;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity firework) {
                if (firework.getOwner() != null && firework.getOwner().equals(mc.player)) {
                    using = true;
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (rubberband)
            return;

        yaw = Math.toRadians(mc.player.getYaw());
        pitch = Math.toRadians(mc.player.getPitch());
    }

    @Override
    public void onDeactivate() {}

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive())
            return;
        if (event.packet instanceof PlayerPositionLookS2CPacket lookS2CPacket) {
            rubberband = true;

            lastMovement = new Vec3d(0, 0, 0);
            yaw = Math.toRadians(lookS2CPacket.getYaw());
            pitch = Math.toRadians(lookS2CPacket.getPitch());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isActive())
            return;

        ElytraFly eFly = (ElytraFly) Modules.get().get(ElytraFly.class);

        if (!rubberband && (!using || !mc.player.isFallFlying() || eFly.isActive())) {
            lastMovement = event.movement;
            return;
        }

        Vec3d direction = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();

        Vec3d currentMovement = direction.multiply(lastMovement.length());

        Vec3d newMovement;

        if (rubberband) {
            newMovement = currentMovement;
        } else {
            if (lastMovement.length() < startSpeed.get() / 20.0) {
                newMovement = direction.multiply(startSpeed.get() / 20.0);
            } else {
                newMovement = currentMovement.add(direction.multiply(accel.get() / 20.0));
            }

            if (newMovement.length() > maxSpeed.get() / 20.0) {
                newMovement = newMovement.normalize().multiply(maxSpeed.get() / 20.0);
            }
        }

        double speed = lastMovement.length();
        double speedFactor = Math.max(0.1,
                Math.min(1.0, ((maxSpeed.get() * 2.5) / 20.0 - speed) / (((maxSpeed.get() * 2.5) / 20.0))));

        Vec3d lastDirection = lastMovement.normalize();
        Vec3d newDirection = newMovement.normalize();

        double dot = lastDirection.dotProduct(newDirection);
        dot = MathHelper.clamp(dot, -1.0, 1.0); // Clamp the dot product to avoid silliness
        double theta = Math.acos(dot) * speedFactor;

        Vec3d slerpedDirection;
        if (Math.abs(theta) < 0.001) {
            slerpedDirection = newDirection;
        } else {
            Vec3d relativeDirection =
                    newDirection.subtract(lastDirection.multiply(dot)).normalize();
            slerpedDirection = lastDirection.multiply(Math.cos(theta))
                    .add(relativeDirection.multiply(Math.sin(theta)));
        }

        // Adjust the new movement velocity based on the slerped direction
        if (lastMovement.length() < startSpeed.get()) {
            newMovement = slerpedDirection.multiply(newMovement.length());
        }

        // Apply velocity clamping as before
        if (newMovement.length() > maxSpeed.get() / 20.0) {
            newMovement = newMovement.normalize().multiply(maxSpeed.get() / 20.0);
        }

        mc.player.setVelocity(newMovement);
        ((IVec3d) event.movement).set(newMovement.x, newMovement.y, newMovement.z);
        lastMovement = newMovement;

        if (rubberband) {
            rubberband = false;
        }
    }
}
