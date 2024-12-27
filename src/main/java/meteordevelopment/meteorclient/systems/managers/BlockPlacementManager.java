package meteordevelopment.meteorclient.systems.managers;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.config.AntiCheatConfig;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockPlacementManager {
    public BlockPlacementManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }


    private final AntiCheatConfig antiCheatConfig = AntiCheatConfig.get();

    private final Map<BlockPos, Long> placeCooldowns = new HashMap<>();

    private long endPlaceCooldown = 0;
    private int placesThisPlacement = 0;

    private boolean locked = false;

    private int silentInvSlot;
    private int selectedSlot;
    private boolean didSilentSwap;

    public boolean beginPlacement(List<BlockPos> positions, Item item) {
        if (switch (antiCheatConfig.blockPlaceItemSwapMode.get()) {
            case SilentHotbar -> !InvUtils.findInHotbar(item).found();
            case SilentSwap -> !InvUtils.find(item).found();
            case None -> mc.player.getMainHandStack().getItem() != item;
        }) {
            return false;
        }

        if (System.currentTimeMillis() < endPlaceCooldown) {
            return false;
        }

        // Lock placements until the current placement ends
        if (locked) {
            return false;
        }

        if (positions.isEmpty()) {
            return false;
        }

        locked = true;
        silentInvSlot = InvUtils.find(item).slot();
        selectedSlot = mc.player.getInventory().selectedSlot;
        didSilentSwap = false;

        boolean inHotbar = InvUtils.findInHotbar(item).found();

        switch (antiCheatConfig.blockPlaceItemSwapMode.get()) {
            case SilentHotbar -> {
                InvUtils.swap(InvUtils.findInHotbar(item).slot(), true);
            }
            case SilentSwap -> {
                // If the block is in our hotbar, don't SilentSwap, just SilentHotbar
                if (inHotbar) {
                    InvUtils.swap(InvUtils.findInHotbar(item).slot(), true);
                } else if (silentInvSlot != mc.player.getInventory().selectedSlot) {
                    InvUtils.quickSwap().fromId(selectedSlot).to(silentInvSlot);
                    didSilentSwap = true;
                }
            }
            case None -> {
                // Fall
            }
        }

        placesThisPlacement = 0;

        return true;
    }

    public boolean placeBlock(BlockPos blockPos) {
        long currentTime = System.currentTimeMillis();

        if (placesThisPlacement > 9) {
            return false;
        }

        if (placeCooldowns.values().stream().filter(x -> currentTime - x <= 1000)
                .count() >= antiCheatConfig.blocksPerSecondCap.get()) {
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

        placesThisPlacement++;

        return true;
    }

    public void endPlacement() {
        if (!locked) {
            return;
        }

        locked = false;

        if (placesThisPlacement > 2) {
            endPlaceCooldown = System.currentTimeMillis() + placesThisPlacement * 37;
        }

        switch (antiCheatConfig.blockPlaceItemSwapMode.get()) {
            case SilentHotbar -> InvUtils.swapBack();
            case SilentSwap -> {
                // If the block is in our hotbar, don't SilentSwap, just SilentHotbar
                if (didSilentSwap) {
                    InvUtils.quickSwap().fromId(selectedSlot).to(silentInvSlot);
                } else {
                    InvUtils.swapBack();
                }
            }
            case None -> {
                // Fall
            }
        }
    }

    public Stream<BlockPos> filterCanPlace(Stream<BlockPos> positions) {
        return positions.filter(blockPos -> {
            // Air place check
            if (!antiCheatConfig.blockPlaceAirPlace.get()
                    && getPlaceOnDirection(blockPos) == null) {
                return false;
            }

            // Replaceable check
            if (!mc.world.getBlockState(blockPos).isReplaceable()) {
                return false;
            }

            // Height check
            if (!World.isValid(blockPos)) {
                return false;
            }

            // Entity check
            if (!mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), blockPos,
                    ShapeContext.absent())) {
                return false;
            }

            return true;
        });
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

    public enum ItemSwapMode {
        None, SilentHotbar, SilentSwap
    }
}
