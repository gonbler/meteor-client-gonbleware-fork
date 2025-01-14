package meteordevelopment.meteorclient.systems.managers;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.config.AntiCheatConfig;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BlockPlacementManager {
    public BlockPlacementManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }


    private final AntiCheatConfig antiCheatConfig = AntiCheatConfig.get();

    private final Map<BlockPos, Long> placeCooldowns = new ConcurrentHashMap<>();

    private long endPlaceCooldown = 0;
    private int placesThisTick = 0;

    private boolean locked = false;

    public boolean beginPlacement(BlockPos position, BlockState state, Item item) {
        if (System.currentTimeMillis() < endPlaceCooldown) {
            return false;
        }

        // Lock placements until the current placement ends
        if (locked) {
            return false;
        }

        if (!checkPlacement(item, position, state)) {
            return false;
        }

        if (!MeteorClient.SWAP.beginSwap(item, true)) {
            return false;
        }

        locked = true;

        return true;
    }

    public boolean beginPlacement(List<BlockPos> positions, Item item) {

        if (System.currentTimeMillis() < endPlaceCooldown) {
            return false;
        }

        // Lock placements until the current placement ends
        if (locked) {
            return false;
        }

        if (positions.stream().filter(x -> checkPlacement(item, x)).findAny().isEmpty()) {
            return false;
        }

        if (!MeteorClient.SWAP.beginSwap(item, true)) {
            return false;
        }

        locked = true;
        return true;
    }

    public boolean placeBlock(Item item, BlockPos blockPos) {
        return placeBlock(item, blockPos, mc.world.getBlockState(blockPos));
    }

    public boolean placeBlock(Item item, BlockPos blockPos, BlockState state) {
        long currentTime = System.currentTimeMillis();

        if (placesThisTick > 9) {
            return false;
        }

        if (placeCooldowns.values().stream().filter(x -> currentTime - x <= 1000)
                .count() >= antiCheatConfig.blocksPerSecondCap.get()) {
            return false;
        }

        if (!checkPlacement(item, blockPos, state)) {
            return false;
        }

        BlockPos neighbour;
        Direction dir = null;

        if (!antiCheatConfig.forceAirPlace.get()) {
            dir = BlockUtils.getPlaceSide(blockPos);
        }

        Vec3d hitPos = blockPos.toCenterPos();
        if (dir == null) {
            neighbour = blockPos;
        } else {
            neighbour = blockPos.offset(dir);
            hitPos = hitPos.add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5,
                    dir.getOffsetZ() * 0.5);

            if (antiCheatConfig.blockRotatePlace.get()) {
                MeteorClient.ROTATION.snapAt(hitPos);
            }
        }

        if (placeCooldowns.containsKey(blockPos)) {
            if (currentTime - placeCooldowns
                    .get(blockPos) < (antiCheatConfig.blockPlacePerBlockCooldown.get() * 1000.0)) {
                return false;
            }
        }

        placeCooldowns.put(blockPos, currentTime);

        Hand placeHand = Hand.MAIN_HAND;
        if (dir == null && antiCheatConfig.blockPlaceAirPlace.get()) {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN,
                            Direction.DOWN));

            placeHand = Hand.OFF_HAND;
        }

        mc.getNetworkHandler()
                .sendPacket(new PlayerInteractBlockC2SPacket(placeHand, new BlockHitResult(hitPos,
                        (dir == null ? Direction.DOWN : dir.getOpposite()), neighbour, false),
                        mc.world.getPendingUpdateManager().incrementSequence().getSequence()));

        if (dir == null && antiCheatConfig.blockPlaceAirPlace.get()) {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN,
                            Direction.DOWN));
        }

        placesThisTick++;

        return true;
    }

    public boolean checkPlacement(Item item, BlockPos blockPos) {
        return checkPlacement(item, blockPos, mc.world.getBlockState(blockPos));
    }

    public boolean checkPlacement(Item item, BlockPos blockPos, BlockState state) {
        if (!antiCheatConfig.blockPlaceAirPlace.get() && getPlaceOnDirection(blockPos) == null) {
            return false;
        }

        // Replaceable check
        if (!state.isReplaceable()) {
            return false;
        }

        // Height check
        if (!World.isValid(blockPos)) {
            return false;
        }

        // Entity check
        if (!mc.world.canPlace(Block.getBlockFromItem(item).getDefaultState(), blockPos,
                ShapeContext.absent())) {
            return false;
        }

        return true;
    }

    public void endPlacement() {
        if (!locked) {
            return;
        }

        locked = false;

        MeteorClient.SWAP.endSwap(true);
    }

    // Decently high priority?
    @EventHandler(priority = 100)
    private void onPostTick(TickEvent.Pre pre) {
        if (placesThisTick > 2) {
            endPlaceCooldown = System.currentTimeMillis() + placesThisTick * 38;
        }

        placesThisTick = 0;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            if (placeCooldowns.containsKey(packet.getPos()) && !packet.getState().isAir()) {
                placeCooldowns.remove(packet.getPos());
            }
        }
    }

    public static Direction getPlaceOnDirection(BlockPos pos) {
        if (pos == null) {
            return null;
        }

        Direction best = null;
        if (MeteorClient.mc.world != null && MeteorClient.mc.player != null) {
            double cDist = -1;
            for (Direction dir : Direction.values()) {

                // Can't place on air lol
                if (MeteorClient.mc.world.getBlockState(pos.offset(dir)).isAir()) {
                    continue;
                }

                // Only accepts if closer than last accepted direction
                double dist = getDistanceForDir(pos, dir);
                if (dist >= 0 && (cDist < 0 || dist < cDist)) {
                    best = dir;
                    cDist = dist;
                }
            }
        }
        return best;
    }

    private static double getDistanceForDir(BlockPos pos, Direction dir) {
        if (MeteorClient.mc.player == null) {
            return 0.0;
        }

        Vec3d vec = new Vec3d(pos.getX() + dir.getOffsetX() / 2f,
                pos.getY() + dir.getOffsetY() / 2f, pos.getZ() + dir.getOffsetZ() / 2f);
        Vec3d dist = MeteorClient.mc.player.getEyePos().add(-vec.x, -vec.y, -vec.z);

        // Len squared for optimization
        return dist.lengthSquared();
    }
}
