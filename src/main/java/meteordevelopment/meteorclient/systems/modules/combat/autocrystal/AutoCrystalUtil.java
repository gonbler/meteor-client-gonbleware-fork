package meteordevelopment.meteorclient.systems.modules.combat.autocrystal;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AutoCrystalUtil {
    public static BlockHitResult getPlaceBlockHitResult(BlockPos blockPos) {
        Direction dir = getPlaceOnDirection(blockPos);
        Vec3d pos = getPosForDir(blockPos, dir);

        return new BlockHitResult(pos, dir, blockPos, true);
    }

    private static Direction getPlaceOnDirection(BlockPos blockPos) {
        if (blockPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        Direction bestdir = null;
        double bestDist = -1;

        for (Direction dir : Direction.values()) {
            Vec3d pos = getPosForDir(blockPos, dir);
            double dist = mc.player.getEyePos().squaredDistanceTo(pos);

            if (dist >= 0 && (bestDist < 0 || dist < bestDist)) {
                bestdir = dir;
                bestDist = dist;
            }
        }

        return bestdir;
    }

    private static Vec3d getPosForDir(BlockPos blockPos, Direction dir) {
        return new Vec3d(blockPos.getX() + dir.getOffsetX() / 2f,
                blockPos.getY() + dir.getOffsetY() / 2f, blockPos.getZ() + dir.getOffsetZ() / 2f);
    }
}
