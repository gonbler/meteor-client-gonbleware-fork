package meteordevelopment.meteorclient.systems.modules.combat;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.player.LookAtEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.EntityTrackingSectionAccessor;
import meteordevelopment.meteorclient.mixin.SectionedEntityCacheAccessor;
import meteordevelopment.meteorclient.mixin.SimpleEntityLookupAccessor;
import meteordevelopment.meteorclient.mixin.WorldAccessor;
import meteordevelopment.meteorclient.mixininterface.IBox;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.misc.text.MeteorClickEvent;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.Timer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.particle.BlockMarkerParticle;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import net.minecraft.world.entity.SimpleEntityLookup;

public class AutoCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgBreak = settings.createGroup("Break");

    private final SettingGroup sgSwitch = settings.createGroup("Switch");

    private final SettingGroup sgRotate = settings.createGroup("Rotate");
    private final SettingGroup sgSwing = settings.createGroup("Swing");

    private final SettingGroup sgRange = settings.createGroup("Range");

    // -- General -- //
    private final Setting<Boolean> placeCrystals = sgGeneral.add(new BoolSetting.Builder()
            .name("place").description("Places crystals.").defaultValue(true).build());

    private final Setting<Boolean> breakCrystals = sgGeneral.add(new BoolSetting.Builder()
            .name("break").description("Breaks crystals.").defaultValue(true).build());

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-eat").description("Pauses when eating").defaultValue(true).build());

    private final Setting<Boolean> ignoreNakeds =
            sgGeneral.add(new BoolSetting.Builder().name("ignore-nakeds")
                    .description("Ignore players with no items.").defaultValue(true).build());

    // -- Place -- //
    private final Setting<Double> placeSpeedLimit =
            sgPlace.add(new DoubleSetting.Builder().name("place-speed-limit")
                    .description("Maximum number of crystals to place every second. 0 = unlimited")
                    .defaultValue(0).min(0).sliderRange(0, 20).build());

    private final Setting<Double> minPlace = sgPlace.add(new DoubleSetting.Builder()
            .name("min-place").description("Minimum enemy damage to place.").defaultValue(7).min(0)
            .sliderRange(0, 20).build());

    private final Setting<Double> maxPlace = sgPlace.add(
            new DoubleSetting.Builder().name("max-place").description("Max self damage to place.")
                    .defaultValue(15).min(0).sliderRange(0, 20).build());

    // -- Break -- //
    private final Setting<Double> breakSpeedLimit =
            sgBreak.add(new DoubleSetting.Builder().name("break-speed-limit")
                    .description("Maximum number of crystals to break every second. 0 = unlimited")
                    .defaultValue(0).min(0).sliderRange(0, 20).build());

    private final Setting<Boolean> packetBreak = sgBreak.add(new BoolSetting.Builder()
            .name("packet-break").description("Breaks when the crystal packet arrives")
            .defaultValue(true).build());

    private final Setting<Double> minBreak = sgPlace.add(new DoubleSetting.Builder()
            .name("min-break").description("Minimum enemy damage to break.").defaultValue(7).min(0)
            .sliderRange(0, 20).build());

    private final Setting<Double> maxBreak = sgPlace.add(
            new DoubleSetting.Builder().name("max-break").description("Max self damage to break.")
                    .defaultValue(15).min(0).sliderRange(0, 20).build());

    // -- Switch -- //
    private final Setting<SwitchMode> switchMode =
            sgSwitch.add(new EnumSetting.Builder<SwitchMode>().name("switch-mode")
                    .description("Mode for switching to crystal in main hand.")
                    .defaultValue(SwitchMode.Silent).build());

    // -- Rotate -- //
    private final Setting<Boolean> rotatePlace =
            sgRotate.add(new BoolSetting.Builder().name("rotate-place")
                    .description("Rotates server-side towards the crystals when placed.")
                    .defaultValue(false).build());

    private final Setting<Double> rotatePlaceTime =
            sgRotate.add(new DoubleSetting.Builder().name("rotate-place-time")
                    .description("Amount of time to rotate for.").defaultValue(0.05).build());

    private final Setting<Boolean> rotateBreak =
            sgRotate.add(new BoolSetting.Builder().name("rotate-break")
                    .description("Rotates server-side towards the crystals when broken.")
                    .defaultValue(true).build());

    private final Setting<Double> rotateBreakTime =
            sgRotate.add(new DoubleSetting.Builder().name("rotate-break-time")
                    .description("Amount of time to rotate for.").defaultValue(0.05).build());

    // -- Swing -- //
    private final Setting<SwingMode> breakSwingMode =
            sgSwing.add(new EnumSetting.Builder<SwingMode>().name("break-swing-mode")
                    .description("Mode for swinging your hand when breaking")
                    .defaultValue(SwingMode.None).build());

    private final Setting<SwingMode> placeSwingMode =
            sgSwing.add(new EnumSetting.Builder<SwingMode>().name("place-swing-mode")
                    .description("Mode for swinging your hand when placing")
                    .defaultValue(SwingMode.None).build());

    // -- Range -- //
    private final Setting<Double> placeRange = sgRange.add(new DoubleSetting.Builder()
            .name("place-range").description("Maximum distance to place crystals for")
            .defaultValue(4.0).build());

    private final Setting<Double> breakRange = sgRange.add(new DoubleSetting.Builder()
            .name("break-range").description("Maximum distance to break crystals for")
            .defaultValue(4.0).build());

    private final Pool<PlacePosition> placePositionPool = new Pool<>(PlacePosition::new);
    private final List<PlacePosition> _placePositions = new ArrayList<>();

    private final IntSet explodedCrystals = new IntOpenHashSet();

    private Entity explodeEntity = null;

    private long lastPlaceTimeMS = 0;
    private long lastBreakTimeMS = 0;

    private Vec3d rotatePos = null;

    private BlockPos renderPos = null;
    private Timer renderTimer = new Timer();

    private boolean isExplodeEntity = false;

    public AutoCrystal() {
        super(Categories.Combat, "auto-crystal", "Automatically places and attacks crystals.");
    }

    @Override
    public void onActivate() {
        explodedCrystals.clear();
    }


    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (rotatePos != null && !MeteorClient.ROTATION.inFov(rotatePos, 20)) {
            // MeteorClient.ROTATION.snapAt(rotatePos, true);
        }

        if (mc.player.isSprinting() && rotatePos != null && !MeteorClient.ROTATION
                .inFov(mc.player.getYaw(), MeteorClient.ROTATION.rotationPitch, 10)) {
            mc.player.setSprinting(false);
        }

        isExplodeEntity = false;
    }

    private void update(Render3DEvent event) {
        if (mc.player == null || mc.world == null || mc.world.getPlayers().isEmpty())
            return;

        explodeEntity = null;

        for (PlacePosition p : _placePositions)
            placePositionPool.free(p);

        _placePositions.clear();

        if (pauseEat.get() && mc.player.isUsingItem()
                && (mc.player.getInventory().getMainHandStack().getItem().getComponents()
                        .get(DataComponentTypes.FOOD) != null
                        || mc.player.getInventory().getStack(PlayerInventory.OFF_HAND_SLOT)
                                .getItem().getComponents().get(DataComponentTypes.FOOD) != null)) {
            return;
        }

        if (placeCrystals.get()) {
            PlacePosition bestPlacePos = null;
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) {
                    continue;
                }

                if (Friends.get().isFriend(player)) {
                    continue;
                }

                PlacePosition testPos = findBestPlacePosition(player);

                if (testPos != null
                        && (bestPlacePos == null || testPos.damage > bestPlacePos.damage)) {
                    bestPlacePos = testPos;
                }
            }

            long currentTime = System.currentTimeMillis();

            if (bestPlacePos != null && ((double) (currentTime - lastPlaceTimeMS)) / 1000.0 > 1.0
                    / placeSpeedLimit.get()) {
                if (placeCrystal(bestPlacePos.blockPos.down(), bestPlacePos.placeDirection)) {
                    lastPlaceTimeMS = currentTime;

                    renderPos = bestPlacePos.blockPos.down();
                    renderTimer.reset();
                }
            }
        }

        if (breakCrystals.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof EndCrystalEntity))
                    continue;

                if (!inBreakRange(entity.getPos())) {
                    continue;
                }

                if (!shouldBreakCrystal(entity)) {
                    continue;
                }

                isExplodeEntity = true;

                long currentTime = System.currentTimeMillis();

                boolean speedCheck = ((double) (currentTime - lastBreakTimeMS)) / 1000.0 > 1.0
                        / breakSpeedLimit.get();

                if (!speedCheck) {
                    break;
                }

                if (breakCrystal(entity)) {
                    explodedCrystals.add(entity.getId());

                    lastBreakTimeMS = System.currentTimeMillis();
                }
            }

            if (!isExplodeEntity && rotateBreak.get()) {
                rotatePos = null;
            }
        }
    }

    public boolean placeCrystal(BlockPos pos, Direction dir) {
        if (pos == null || mc.player == null) {
            return false;
        }

        FindItemResult result = InvUtils.findInHotbar(Items.END_CRYSTAL);

        if (!result.found()) {
            return false;
        }

        if (rotatePlace.get()) {
            MeteorClient.ROTATION.lookAt(pos.toCenterPos().add(0, 0.5, 0));

            rotatePos = pos.toCenterPos().add(0, 0.5, 0);

            if (!MeteorClient.ROTATION.inFov(pos.toCenterPos().add(0, 0.5, 0), 20)) {
                return false;
            }
        }

        switch (switchMode.get()) {
            case Silent -> InvUtils.swap(result.slot(), true);
        }

        Hand hand = Hand.MAIN_HAND;

        Vec3d eyes = mc.player.getEyePos();
        boolean inside = eyes.x > pos.getX() && eyes.x < pos.getX() + 1 && eyes.y > pos.getY()
                && eyes.y < pos.getY() + 1 && eyes.z > pos.getZ() && eyes.z < pos.getZ() + 1;

        int s = mc.world.getPendingUpdateManager().incrementSequence().getSequence();
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(hand,
                new BlockHitResult(pos.toCenterPos(), dir, pos, inside), s));

        if (placeSwingMode.get().client())
            mc.player.swingHand(hand);

        if (placeSwingMode.get().packet())
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));

        switch (switchMode.get()) {
            case Silent -> InvUtils.swapBack();
        }

        return true;
    }

    public boolean breakCrystal(Entity entity) {
        if (mc.player == null) {
            return false;
        }

        if (rotateBreak.get()) {
            MeteorClient.ROTATION.lookAt(entity.getPos());

            rotatePos = entity.getPos();

            if (!MeteorClient.ROTATION.inFov(entity.getPos(), 20)) {
                return false;
            }
        }

        PlayerInteractEntityC2SPacket packet =
                PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking());

        mc.getNetworkHandler().sendPacket(packet);

        if (breakSwingMode.get().client())
            mc.player.swingHand(Hand.MAIN_HAND);

        if (breakSwingMode.get().packet())
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        isExplodeEntity = true;

        return true;
    }

    private PlacePosition findBestPlacePosition(PlayerEntity target) {
        PlacePosition bestPos = new PlacePosition();
        bestPos.damage = 0.0;
        bestPos.selfDamage = 0.0;
        bestPos.placeDirection = null;
        bestPos.blockPos = null;

        // DamageUtils.crystalDamage(target, );
        int r = (int) Math.floor(placeRange.get());

        BlockPos eyePos = BlockPos.ofFloored(mc.player.getEyePos());
        // BlockState obstate = Blocks.OBSIDIAN.getDefaultState();
        // boolean airPlace = allowAirPlace.get().isPressed();
        Box box = new Box(0, 0, 0, 0, 0, 0);

        boolean set = false;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = eyePos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    // Check if there's an air block to place the crystal in
                    if (!state.isAir()) {
                        continue;
                    }

                    BlockPos downPos = pos.down();
                    BlockState downState = mc.world.getBlockState(downPos);
                    Block downBlock = downState.getBlock();

                    // We can only place on obsidian and bedrock
                    if (downState.isAir()
                            || (downBlock != Blocks.OBSIDIAN && downBlock != Blocks.BEDROCK)) {
                        continue;
                    }

                    Direction dir = getPlaceOnDirection(downPos);

                    if (dir == null) {
                        continue;
                    }

                    // Range check
                    if (!inPlaceRange(downPos) || !inBreakRange(
                            new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
                        continue;
                    }

                    // Check if the crystal intersects with any players/crystals/whatever
                    ((IBox) box).set(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1,
                            pos.getY() + 2, pos.getZ() + 1);

                    if (intersectsWithEntities(box))
                        continue;

                    double selfDamage = DamageUtils.newCrystalDamage(mc.player,
                            mc.player.getBoundingBox(),
                            new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), null);

                    if (selfDamage > maxPlace.get()) {
                        continue;
                    }

                    double targetDamage = DamageUtils.newCrystalDamage(target,
                            target.getBoundingBox(),
                            new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), null);

                    if (targetDamage >= minPlace.get() && targetDamage > bestPos.damage) {
                        bestPos.blockPos = pos;
                        bestPos.placeDirection = dir;
                        bestPos.damage = targetDamage;
                        bestPos.selfDamage = selfDamage;

                        set = true;
                    }
                }
            }
        }

        // _placePositions.add(bestPos);

        // Return null if we never actually found a good position
        if (set) {
            return bestPos;
        } else {
            return null;
        }
    }

    public boolean inPlaceRange(BlockPos blockPos) {
        Vec3d from = mc.player.getEyePos();

        return blockPos.toCenterPos().distanceTo(from) <= placeRange.get();
    }

    public boolean inBreakRange(Vec3d pos) {
        Vec3d from = mc.player.getEyePos();

        return pos.distanceTo(from) <= placeRange.get();
    }

    public boolean shouldBreakCrystal(Entity entity) {
        boolean damageCheck = false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                continue;
            }

            if (Friends.get().isFriend(player)) {
                continue;
            }

            double selfDamage = DamageUtils.newCrystalDamage(mc.player, mc.player.getBoundingBox(),
                    entity.getPos(), null);

            if (selfDamage > maxPlace.get()) {
                continue;
            }

            double targetDamage = DamageUtils.newCrystalDamage(player, player.getBoundingBox(),
                    entity.getPos(), null);

            if (targetDamage >= minBreak.get()) {
                damageCheck = true;

                break;
            }
        }

        if (!damageCheck) {
            return false;
        }

        return true;
    }

    @EventHandler()
    public void onRotate(LookAtEvent event) {
        if (rotatePos != null) {
            event.setTarget(rotatePos, 180f, 10f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onEntity(EntityAddedEvent event) {
        if (packetBreak.get()) {
            Entity entity = event.entity;
            if (!(entity instanceof EndCrystalEntity)) {
                return;
            }

            explodeEntity = entity;

            boolean breakCrystal = false;

            if (breakCrystals.get()) {
                if (!(entity instanceof EndCrystalEntity))
                    return;

                if (!inBreakRange(entity.getPos())) {
                    return;
                }

                if (!shouldBreakCrystal(entity)) {
                    return;
                }

                isExplodeEntity = true;

                long currentTime = System.currentTimeMillis();

                boolean speedCheck = ((double) (currentTime - lastBreakTimeMS)) / 1000.0 > 1.0
                        / breakSpeedLimit.get();

                if (!speedCheck) {
                    return;
                }

                if (breakCrystal(entity)) {
                    explodedCrystals.add(entity.getId());

                    lastBreakTimeMS = System.currentTimeMillis();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onRender3D(Render3DEvent event) {
        if (!isActive())
            return;

        update(event);

        if (renderPos != null && !renderTimer.passedTicks(3)) {
            event.renderer.box(renderPos, Color.RED.a(30), Color.RED.a(30), ShapeMode.Both, 0);
        }
    }

    private boolean intersectsWithEntities(Box box) {
        return intersectsWithEntity(box,
                entity -> !entity.isSpectator() && !explodedCrystals.contains(entity.getId()));
    }

    public boolean intersectsWithEntity(Box box, Predicate<Entity> predicate) {
        EntityLookup<Entity> entityLookup = ((WorldAccessor) mc.world).getEntityLookup();

        // Fast implementation using SimpleEntityLookup that returns on the first intersecting
        // entity
        if (entityLookup instanceof SimpleEntityLookup<Entity> simpleEntityLookup) {
            SectionedEntityCache<Entity> cache =
                    ((SimpleEntityLookupAccessor) simpleEntityLookup).getCache();
            LongSortedSet trackedPositions =
                    ((SectionedEntityCacheAccessor) cache).getTrackedPositions();
            Long2ObjectMap<EntityTrackingSection<Entity>> trackingSections =
                    ((SectionedEntityCacheAccessor) cache).getTrackingSections();

            int i = ChunkSectionPos.getSectionCoord(box.minX - 2);
            int j = ChunkSectionPos.getSectionCoord(box.minY - 2);
            int k = ChunkSectionPos.getSectionCoord(box.minZ - 2);
            int l = ChunkSectionPos.getSectionCoord(box.maxX + 2);
            int m = ChunkSectionPos.getSectionCoord(box.maxY + 2);
            int n = ChunkSectionPos.getSectionCoord(box.maxZ + 2);

            for (int o = i; o <= l; o++) {
                long p = ChunkSectionPos.asLong(o, 0, 0);
                long q = ChunkSectionPos.asLong(o, -1, -1);
                LongBidirectionalIterator longIterator =
                        trackedPositions.subSet(p, q + 1).iterator();

                while (longIterator.hasNext()) {
                    long r = longIterator.nextLong();
                    int s = ChunkSectionPos.unpackY(r);
                    int t = ChunkSectionPos.unpackZ(r);

                    if (s >= j && s <= m && t >= k && t <= n) {
                        EntityTrackingSection<Entity> entityTrackingSection =
                                trackingSections.get(r);

                        if (entityTrackingSection != null
                                && entityTrackingSection.getStatus().shouldTrack()) {
                            for (Entity entity : ((EntityTrackingSectionAccessor) entityTrackingSection)
                                    .<Entity>getCollection()) {
                                if (entity.getBoundingBox().intersects(box)
                                        && predicate.test(entity))
                                    return true;
                            }
                        }
                    }
                }
            }

            return false;
        }
        // Slow implementation that loops every entity if for some reason the EntityLookup
        // implementation is changed
        AtomicBoolean found = new AtomicBoolean(false);

        entityLookup.forEachIntersects(box, entity -> {
            if (!found.get() && predicate.test(entity))
                found.set(true);
        });

        return found.get();
    }

    public Direction getPlaceOnDirection(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        Direction best = null;
        if (mc.world != null && mc.player != null) {
            double cDist = -1;
            for (Direction dir : Direction.values()) {

                // Strict dir check (checks if face isnt on opposite side of the block to player)
                if (!getStrictDirection(pos, dir)) {
                    continue;
                }

                // Only accepts if closer than last accepted direction
                double dist = directionDir(pos, dir);
                if (dist >= 0 && (cDist < 0 || dist < cDist)) {
                    best = dir;
                    cDist = dist;
                }
            }
        }
        return best;
    }

    public boolean getStrictDirection(BlockPos pos, Direction dir) {
        return switch (dir) {
            case DOWN -> mc.player.getEyePos().y <= pos.getY() + 0.5;
            case UP -> mc.player.getEyePos().y >= pos.getY() + 0.5;
            case NORTH -> mc.player.getZ() < pos.getZ();
            case SOUTH -> mc.player.getZ() >= pos.getZ() + 1;
            case WEST -> mc.player.getX() < pos.getX();
            case EAST -> mc.player.getX() >= pos.getX() + 1;
        };
    }

    private double directionDir(BlockPos pos, Direction dir) {
        if (mc.player == null) {
            return 0;
        }

        Vec3d vec = new Vec3d(pos.getX() + dir.getOffsetX() / 2f,
                pos.getY() + dir.getOffsetY() / 2f, pos.getZ() + dir.getOffsetZ() / 2f);
        Vec3d dist = mc.player.getEyePos().add(-vec.x, -vec.y, -vec.z);

        return Math.sqrt(dist.x * dist.x + dist.y * dist.y + dist.z * dist.z);
    }

    private class PlacePosition {
        public BlockPos blockPos;

        public Direction placeDirection;

        public double damage = 0.0;

        public double selfDamage = 0.0;
    }

    private enum SwitchMode {
        None, Silent
    }

    public enum SwingMode {
        Both, Packet, Client, None;

        public boolean packet() {
            return this == Packet || this == Both;
        }

        public boolean client() {
            return this == Client || this == Both;
        }
    }
}
