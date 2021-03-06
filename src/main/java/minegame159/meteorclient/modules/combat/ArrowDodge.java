package minegame159.meteorclient.modules.combat;

import com.google.common.collect.Sets;
import minegame159.meteorclient.settings.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;

import meteordevelopment.orbit.EventHandler;
import minegame159.meteorclient.events.world.TickEvent;

import minegame159.meteorclient.modules.Categories;
import minegame159.meteorclient.modules.Module;
import net.minecraft.util.shape.VoxelShapes;

import java.util.*;

public class ArrowDodge extends Module {
    private final List<Vec3d> possibleMoveDirections = Arrays.asList(
            new Vec3d(1, 0, 1), new Vec3d(0, 0, 1), new Vec3d(-1, 0, 1),
            new Vec3d(1, 0, 0), new Vec3d(-1, 0, 0),
            new Vec3d(1, 0, -1), new Vec3d(0, 0, -1), new Vec3d(-1, 0, -1)
    );

    private final SettingGroup sgDefault = settings.getDefaultGroup();
    private final SettingGroup sgMovement = settings.createGroup("Movement");

    private final Setting<Integer> arrowLookahead = sgDefault.add(new IntSetting.Builder()
        .name("arrow-lookahead")
        .description("How many steps into the future should be taken into consideration when deciding the direction")
        .defaultValue(500)
        .min(1)
        .max(750)
        .build()
    );

    private final Setting<MoveType> moveType = sgMovement.add(new EnumSetting.Builder<MoveType>()
        .name("move-type")
        .description("The way you are moved by this module")
        .defaultValue(MoveType.Client)
        .build()
    );

    private final Setting<Double> moveSpeed = sgMovement.add(new DoubleSetting.Builder()
            .name("move-speed")
            .description("How fast should you be when dodging arrow")
            .defaultValue(1)
            .min(0.01)
            .sliderMax(5)
            .build()
    );

    public ArrowDodge() {
        super(Categories.Combat, "arrow-dodge", "Tries to dodge arrows coming at you");
    }

    public enum MoveType {
        Client, Packet
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Box playerHitbox = mc.player.getBoundingBox();
        if (playerHitbox == null) return;
        playerHitbox = playerHitbox.expand(0.6);

        Double speed = moveSpeed.get();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ArrowEntity) || e.age > 50) continue;
            if (((ArrowEntity)e).getOwner() == mc.player) continue;

            List<Box> futureArrowHitboxes = new ArrayList<>();

            for (int i = 0; i < arrowLookahead.get(); i++) {
                Vec3d nextPos = e.getPos().add(e.getVelocity().multiply(i / 5));
                futureArrowHitboxes.add(new Box(
                        nextPos.subtract(e.getBoundingBox().getXLength() / 2, 0, e.getBoundingBox().getZLength() / 2),
                        nextPos.add(e.getBoundingBox().getXLength() / 2, e.getBoundingBox().getYLength(), e.getBoundingBox().getZLength() / 2)));
            }

            for (Box arrowHitbox: futureArrowHitboxes) {
                if (playerHitbox.intersects(arrowHitbox)) {
                    Collections.shuffle(possibleMoveDirections); //Make the direction unpredictable
                    boolean didMove = false;
                    for (Vec3d direction: possibleMoveDirections) {
                        Vec3d velocity = direction.multiply(speed);
                        boolean isValid = true;
                        for (Box futureArrowHitbox: futureArrowHitboxes) {
                            Box newPlayerPos = moveBox(playerHitbox,velocity);
                            if (futureArrowHitbox.intersects(newPlayerPos)) {
                                isValid = false;
                                break;
                            }
                            BlockPos blockPos = mc.player.getBlockPos().add(velocity.x,velocity.y,velocity.z);
                            if (mc.world.getBlockState(blockPos).getCollisionShape(mc.world,blockPos) != VoxelShapes.empty()) {
                                isValid = false;
                                break;
                            }
                        }
                        if (isValid) {
                            move(velocity);
                            didMove=true;
                            break;
                        }
                    }
                    if (!didMove) { //If didn't find a suitable position, run back
                        double yaw = Math.toRadians(e.yaw);
                        double pitch = Math.toRadians(e.pitch);
                        double velocityX = Math.sin(yaw) * Math.cos(pitch) * speed;
                        double velocityY = Math.sin(pitch) * speed;
                        double velocityZ = -Math.cos(yaw) * Math.cos(pitch) * speed;
                        move(new Vec3d(velocityX,velocityY,velocityZ));
                    }
                }
            }

        }
    }

    private void move(Vec3d vel) {
        MoveType mode = moveType.get();
        if (mode == MoveType.Client) {
            mc.player.setVelocity(vel);
        }
        else if (mode == MoveType.Packet) {
            Vec3d newPos = mc.player.getPos().add(vel);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionOnly(newPos.x,newPos.y, newPos.z, false));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionOnly(newPos.x,newPos.y - 0.01, newPos.z, true));
        }
    }

    private Box moveBox(Box box, Vec3d offset) {
        return new Box(new Vec3d(box.minX, box.minY, box.minZ).add(offset.x, offset.y, offset.z), new Vec3d(box.maxX, box.maxY, box.maxZ).add(offset.x, offset.y, offset.z));
    }
}
