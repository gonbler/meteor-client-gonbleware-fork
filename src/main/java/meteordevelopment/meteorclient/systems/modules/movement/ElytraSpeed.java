package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ElytraSpeed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private boolean using;
    private double yaw;
    private double pitch;
    private Vec3d lastMovement;
    private boolean rubberband;

    private final Setting<Double> startSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("start-speed")
            .description("Initial velocity of elytra")
            .defaultValue(20)
            .min(0)
            .sliderMax(100)
            .build());

    private final Setting<Double> accel = sgGeneral.add(new DoubleSetting.Builder()
            .name("accel-speed")
            .description("Acceleration")
            .defaultValue(8.5)
            .min(0)
            .sliderMax(30)
            .build());

    private final Setting<Double> maxSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("max-speed")
            .description("Initial velocity of elytra")
            .defaultValue(230)
            .min(0)
            .sliderMax(600)
            .build());

    public ElytraSpeed() {
        super(Categories.Movement, "elytra-speed", "Makes your elytra faster.");
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
        yaw = Math.toRadians(mc.player.getYaw());
        pitch = Math.toRadians(mc.player.getPitch());
    }

    @Override
    public void onDeactivate() {
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) return;
        if (event.packet instanceof PlayerPositionLookS2CPacket lookS2CPacket) {
            rubberband = true;

            lastMovement = new Vec3d(lookS2CPacket.getX(), lookS2CPacket.getY(), lookS2CPacket.getZ());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isActive()) return;
        if (!rubberband && (!using || !mc.player.isFallFlying())) {
            lastMovement = event.movement;
            return;
        }

        if (rubberband) {
            rubberband = false;
        }

        Vec3d direction = new Vec3d(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();
        
        Vec3d calcVel = direction.multiply(lastMovement.length());

        ((IVec3d) event.movement).set(calcVel.x, calcVel.y, calcVel.z);
        Vec3d currentMovement = event.movement;

        Vec3d newMovement;

        if (lastMovement.length() < startSpeed.get() / 20.0) {
            newMovement = direction.multiply(startSpeed.get() / 20.0);
        } else {
            newMovement = currentMovement.add(direction.multiply(accel.get() / 20.0));
        }

        if (newMovement.length() > maxSpeed.get() / 20.0) {
            newMovement = newMovement.normalize().multiply(maxSpeed.get() / 20.0);
        }

        //mc.player.setVelocity(lastMovement.subtract(newMovement));
        ((IVec3d) event.movement).set(newMovement.x, newMovement.y, newMovement.z);
        lastMovement = newMovement;
    }
}
