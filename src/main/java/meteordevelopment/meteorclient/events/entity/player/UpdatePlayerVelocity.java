package meteordevelopment.meteorclient.events.entity.player;

import meteordevelopment.meteorclient.events.Cancellable;
import net.minecraft.util.math.Vec3d;

public class UpdatePlayerVelocity extends Cancellable {
    Vec3d movementInput;
    float speed;
    float yaw;
    Vec3d velocity;

    public UpdatePlayerVelocity(Vec3d movementInput, float speed, float yaw, Vec3d velocity) {
        this.movementInput = movementInput;
        this.speed = speed;
        this.yaw = yaw;
        this.velocity = velocity;
    }

    public Vec3d getMovementInput() {
        return this.movementInput;
    }

    public float getSpeed() {
        return this.speed;
    }

    public Vec3d getVelocity() {
        return this.velocity;
    }

    public void setVelocity(Vec3d velocity) {
        this.velocity = velocity;
    }
}
