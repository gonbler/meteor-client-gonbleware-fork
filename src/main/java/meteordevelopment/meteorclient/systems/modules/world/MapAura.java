package meteordevelopment.meteorclient.systems.modules.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class MapAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> placeRange = sgGeneral.add(
            new DoubleSetting.Builder().name("place-range").description("How far you can reach")
                    .defaultValue(4).min(0).sliderMax(6).build());

    private final Setting<Double> placeDelay =
            sgGeneral.add(new DoubleSetting.Builder().name("place-delay")
                    .description("How many seconds to wait between placing in the same spot")
                    .defaultValue(0.2).min(0).sliderMax(2).build());

    private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();

    private final Map<BlockPos, Long> timeOfLastPlace = new HashMap<>();
    private final Map<Integer, Long> timeOfLastMapInteract = new HashMap<>();

    public MapAura() {
        super(Categories.World, "map-aura", "Places maps and item frames on every surface");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        FindItemResult itemFrameResult = InvUtils.findInHotbar(Items.ITEM_FRAME);

        if (itemFrameResult.found()) {
            InvUtils.swap(itemFrameResult.slot(), true);
            placeNextItemFrame();
            InvUtils.swapBack();
        }

        FindItemResult mapItemResult = InvUtils.findInHotbar(item -> {
            if (item.getItem() instanceof FilledMapItem && item.getCount() > 1) {
                return true;
            }

            return false;
        });

        if (mapItemResult.found()) {
            InvUtils.swap(mapItemResult.slot(), true);
            placeNextMap();
            InvUtils.swapBack();
        }
    }

    private boolean placeNextItemFrame() {
        long currentTime = System.currentTimeMillis();

        int r = (int) Math.floor(placeRange.get());

        BlockPos eyePos = BlockPos.ofFloored(mc.player.getEyePos());
        int ex = eyePos.getX();
        int ey = eyePos.getY();
        int ez = eyePos.getZ();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos blockPos = mutablePos.set(ex + x, ey + y, ez + z);

                    BlockState state = mc.world.getBlockState(blockPos);

                    // Check if the current block can have a map placed on
                    if (state.isAir()) {
                        continue;
                    }

                    if (timeOfLastPlace.containsKey(blockPos)) {
                        if (((double) currentTime - (double) timeOfLastPlace.get(blockPos))
                                / 1000.0 < placeDelay.get()) {
                            continue;
                        }
                    }

                    for (Direction dir : Direction.values()) {
                        BlockPos neighbour = blockPos.offset(dir);

                        if (!mc.world.getBlockState(neighbour).isAir()) {
                            continue;
                        }

                        if (!World.isValid(neighbour) || neighbour.getY() < -64) {
                            continue;
                        }

                        final Vec3d hitPos = blockPos.toCenterPos().add(dir.getOffsetX() * 0.5,
                                dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);

                        List<ItemFrameEntity> entities = mc.world.getEntitiesByType(
                                TypeFilter.instanceOf(ItemFrameEntity.class),
                                Box.of(hitPos, 0.1, 0.1, 0.1), (entity) -> {
                                    return true;
                                });

                        if (entities.isEmpty()) {
                            mc.getNetworkHandler()
                                    .sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
                                            new BlockHitResult(hitPos, dir, blockPos, false),
                                            mc.world.getPendingUpdateManager().incrementSequence()
                                                    .getSequence()));

                            timeOfLastPlace.put(blockPos, currentTime);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean placeNextMap() {
        List<ItemFrameEntity> entities = mc.world.getEntitiesByType(
                TypeFilter.instanceOf(ItemFrameEntity.class), Box.of(mc.player.getEyePos(),
                        placeRange.get() * 2, placeRange.get() * 2, placeRange.get() * 2),
                this::checkEntity);


        if (entities.isEmpty()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        ItemFrameEntity entity = entities.getFirst();

        MeteorClient.ROTATION.requestRotation(
                getClosestPointOnBox(entity.getBoundingBox(), mc.player.getEyePos()), 5);

        if (!MeteorClient.ROTATION.lookingAt(entity.getBoundingBox())) {
            return false;
        }

        if (timeOfLastMapInteract.containsKey(entity.getId())) {
            if (((double) currentTime - (double) timeOfLastMapInteract.get(entity.getId()))
                    / 1000.0 < placeDelay.get()) {
                return false;
            }
        }


        EntityHitResult entityHitResult = new EntityHitResult(entity, getClosestPointOnBox(entity.getBoundingBox(), mc.player.getEyePos()));
        
        ActionResult actionResult = mc.interactionManager.interactEntityAtLocation(mc.player,
                entity, entityHitResult, Hand.MAIN_HAND);

        if (!actionResult.isAccepted()) {
            actionResult = mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);
        }
        
        if (actionResult.isAccepted() && actionResult.shouldSwingHand()) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        info("Placed map");

        timeOfLastMapInteract.put(entity.getId(), currentTime);

        return true;
    }

    private boolean checkEntity(Entity entity) {
        if (entity instanceof ItemFrameEntity itemFrame) {
            if (!getClosestPointOnBox(entity.getBoundingBox(), mc.player.getEyePos())
                    .isWithinRangeOf(mc.player.getEyePos(), placeRange.get(), placeRange.get())) {
                return false;
            }

            if (itemFrame.getHeldItemStack() == null || itemFrame.getHeldItemStack().isEmpty()) {
                return true;
            }

            return false;
        } else {
            // Sanity check for not being an item frame
            return false;
        }
    }

    public Vec3d getClosestPointOnBox(Box box, Vec3d point) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return new Vec3d(x, y, z);
    }
}
