package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import de.florianmichael.viafabricplus.settings.base.BooleanSetting;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.player.LookAtEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.EntityTrackingSectionAccessor;
import meteordevelopment.meteorclient.mixin.SectionedEntityCacheAccessor;
import meteordevelopment.meteorclient.mixin.SimpleEntityLookupAccessor;
import meteordevelopment.meteorclient.mixin.WorldAccessor;
import meteordevelopment.meteorclient.mixininterface.IBox;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Timer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import net.minecraft.world.entity.SimpleEntityLookup;

public class AutoCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgFacePlace = settings.createGroup("Face Place");
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

    private final Setting<Boolean> antiSurroundPlace = sgPlace.add(new BoolSetting.Builder()
            .name("anti-surround")
            .description(
                    "Ignores auto-mine blocks from calculations to place outside of their surround.")
            .defaultValue(true).build());

    // -- Face Place -- //
    private final Setting<Boolean> facePlaceMissingArmor =
            sgFacePlace.add(new BoolSetting.Builder().name("face-place-missing-armor")
                    .description("Face places on missing armor").defaultValue(true).build());

    private final Setting<Keybind> forceFacePlaceKeybind =
            sgFacePlace.add(new KeybindSetting.Builder().name("force-face-place")
                    .description("Keybind to force face place").build());

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

    private final Setting<Boolean> rotateBreak =
            sgRotate.add(new BoolSetting.Builder().name("rotate-break")
                    .description("Rotates server-side towards the crystals when broken.")
                    .defaultValue(true).build());

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
    private final Map<Integer, Long> crystalBreakDelays = new HashMap<>();
    private final Map<BlockPos, Long> crystalPlaceDelays = new HashMap<>();

    private long lastPlaceTimeMS = 0;
    private long lastBreakTimeMS = 0;

    private Vec3d rotatePos = null;

    private BlockPos renderPos = null;
    private Timer renderTimer = new Timer();

    private boolean isExplodeEntity = false;

    private AutoMine autoMine;

    public AutoCrystal() {
        super(Categories.Combat, "auto-crystal", "Automatically places and attacks crystals.");
    }

    @Override
    public void onActivate() {
        if (autoMine == null) {
            autoMine = Modules.get().get(AutoMine.class);
        }

        explodedCrystals.clear();
        crystalBreakDelays.clear();
        crystalPlaceDelays.clear();
    }


    @EventHandler
    private void onTick(TickEvent.Pre event) {

        // Move fix
        if (mc.player.isSprinting() && rotatePos != null && !MeteorClient.ROTATION
                .inFov(mc.player.getYaw(), MeteorClient.ROTATION.rotationPitch, 10)) {
            mc.player.setSprinting(false);
        }

        isExplodeEntity = false;
    }

    private void update(Render3DEvent event) {
        if (mc.player == null || mc.world == null || mc.world.getPlayers().isEmpty())
            return;

        if (autoMine == null) {
            autoMine = Modules.get().get(AutoMine.class);
        }

        for (PlacePosition p : _placePositions)
            placePositionPool.free(p);

        _placePositions.clear();

        if (pauseEat.get() && mc.player.isUsingItem()) {
            return;
        }

        PlacePosition bestPlacePos = null;
        if (placeCrystals.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) {
                    continue;
                }

                if (Friends.get().isFriend(player)) {
                    continue;
                }

                if (player.isDead()) {
                    continue;
                }

                if (ignoreNakeds.get()) {
                    if (player.getInventory().armor.get(0).isEmpty()
                            && player.getInventory().armor.get(1).isEmpty()
                            && player.getInventory().armor.get(2).isEmpty()
                            && player.getInventory().armor.get(3).isEmpty())
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
        }

        boolean rotateBreakThisUpdate = isExplodeEntity && rotateBreak.get();
        boolean rotatePlaceThisUpdate = bestPlacePos != null && rotatePlace.get();
        if (!rotateBreakThisUpdate && !rotatePlaceThisUpdate) {
            rotatePos = null;
        }
    }

    public boolean placeCrystal(BlockPos pos, Direction dir) {
        if (pos == null || mc.player == null) {
            return false;
        }

        BlockPos crystaBlockPos = pos.up();

        Box box = new Box(crystaBlockPos.getX(), crystaBlockPos.getY(), crystaBlockPos.getZ(),
                crystaBlockPos.getX() + 1, crystaBlockPos.getY() + 2, crystaBlockPos.getZ() + 1);

        if (intersectsWithEntity(box,
                entity -> !entity.isSpectator() && !explodedCrystals.contains(entity.getId()))) {
            return false;
        }

        FindItemResult result = InvUtils.findInHotbar(Items.END_CRYSTAL);

        if (!result.found()) {
            return false;
        }

        if (rotatePlace.get()) {
            MeteorClient.ROTATION.lookAt(pos.toCenterPos().add(0, 0.5, 0));

            rotatePos = pos.toCenterPos().add(0, 0.5, 0);

            if (!MeteorClient.ROTATION.lookingAt(rotatePos, new Box(pos))) {
                return false;
            }
        }

        if (crystalPlaceDelays.containsKey(pos)) {
            if (System.currentTimeMillis() - crystalPlaceDelays.get(pos) < 50) {
                return false;
            }
        }

        crystalPlaceDelays.put(pos, System.currentTimeMillis());

        switch (switchMode.get()) {
            case Silent -> InvUtils.swap(result.slot(), true);
            case None -> {
                if (mc.player.getInventory().getMainHandStack().getItem() != Items.END_CRYSTAL) {
                    return false;
                }
            }
        }

        renderPos = pos;
        renderTimer.reset();

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
            case None -> {
                if (mc.player.getInventory().getMainHandStack().getItem() != Items.END_CRYSTAL) {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean breakCrystal(Entity entity) {
        return breakCrystal(entity, false);
    }

    public boolean breakCrystal(Entity entity, boolean overrideRotate) {
        if (mc.player == null) {
            return false;
        }

        if (!overrideRotate && rotateBreak.get()) {
            MeteorClient.ROTATION.lookAt(entity.getPos());

            rotatePos = entity.getPos();

            if (!MeteorClient.ROTATION.lookingAt(entity.getPos(), entity.getBoundingBox())) {
                return false;
            }
        }

        if (crystalBreakDelays.containsKey(entity.getId())) {
            if (System.currentTimeMillis() - crystalBreakDelays.get(entity.getId()) < 50) {
                return false;
            }
        }

        crystalBreakDelays.put(entity.getId(), System.currentTimeMillis());

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

    private Set<BlockPos> _calcIgnoreSet = new HashSet<>();

    private PlacePosition findBestPlacePosition(PlayerEntity target) {
        // Optimization to not spam allocs because java sucks
        PlacePosition bestPos = placePositionPool.get();
        _placePositions.add(bestPos);

        bestPos.damage = 0.0;
        bestPos.selfDamage = 0.0;
        bestPos.placeDirection = null;
        bestPos.blockPos = null;

        int r = (int) Math.floor(placeRange.get());

        BlockPos eyePos = BlockPos.ofFloored(mc.player.getEyePos());
        // BlockState obstate = Blocks.OBSIDIAN.getDefaultState();
        // boolean airPlace = allowAirPlace.get().isPressed();
        Box box = new Box(0, 0, 0, 0, 0, 0);

        boolean set = false;

        _calcIgnoreSet.clear();
        if (antiSurroundPlace.get()) {
            SilentMine silentMine = Modules.get().get(SilentMine.class);
            if (silentMine.isActive()) {
                if (silentMine.getDelayedDestroyBlockPos() != null) {
                    _calcIgnoreSet.add(silentMine.getDelayedDestroyBlockPos());
                }

                if (silentMine.getRebreakBlockPos() != null) {
                    _calcIgnoreSet.add(silentMine.getRebreakBlockPos());
                }
            }
        }

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

                    Direction dir = Direction.UP;

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

                    double selfDamage =
                            DamageUtils.newCrystalDamage(mc.player, mc.player.getBoundingBox(),
                                    new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                                    _calcIgnoreSet);

                    if (selfDamage > maxPlace.get()) {
                        continue;
                    }

                    double targetDamage =
                            DamageUtils.newCrystalDamage(target, target.getBoundingBox(),
                                    new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                                    _calcIgnoreSet);

                    boolean shouldFacePlace = false;

                    if (facePlaceMissingArmor.get()) {
                        if (target.getInventory().armor.get(0).isEmpty()
                                || target.getInventory().armor.get(1).isEmpty()
                                || target.getInventory().armor.get(2).isEmpty()
                                || target.getInventory().armor.get(3).isEmpty()) {
                            shouldFacePlace = true;
                        }
                    }

                    if (forceFacePlaceKeybind.get().isPressed()) {
                        shouldFacePlace = true;
                    }

                    if (targetDamage >= (shouldFacePlace ? 1.0 : minPlace.get())
                            && targetDamage > bestPos.damage) {
                        bestPos.blockPos = pos;
                        bestPos.placeDirection = dir;
                        bestPos.damage = targetDamage;
                        bestPos.selfDamage = selfDamage;

                        set = true;
                    }
                }
            }
        }

        // Return null if we never actually found a good position
        if (set) {
            return bestPos;
        } else {
            return null;
        }
    }

    private Set<BlockPos> _preplaceSet = new HashSet<>();

    public void preplaceCrystal(BlockPos pos) {
        BlockPos downPos = pos.down();
        BlockState downState = mc.world.getBlockState(downPos);
        Block downBlock = downState.getBlock();

        // We can only place on obsidian and bedrock
        if (downState.isAir() || (downBlock != Blocks.OBSIDIAN && downBlock != Blocks.BEDROCK)) {
            return;
        }

        Direction dir = getPlaceOnDirection(downPos);

        if (dir == null) {
            return;
        }

        // Range check
        if (!inPlaceRange(downPos)
                || !inBreakRange(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
            return;
        }

        // Check if the crystal intersects with any players/crystals/whatever
        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2,
                pos.getZ() + 1);

        if (intersectsWithEntities(box))
            return;

        _preplaceSet.clear();
        _preplaceSet.add(pos);

        double selfDamage = DamageUtils.newCrystalDamage(mc.player, mc.player.getBoundingBox(),
                new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), _preplaceSet);

        if (selfDamage > maxPlace.get()) {
            return;
        }

        placeCrystal(downPos, dir);
    }

    public boolean inPlaceRange(BlockPos blockPos) {
        Vec3d from = mc.player.getEyePos();

        return blockPos.toCenterPos().distanceTo(from) <= placeRange.get();
    }

    public boolean inBreakRange(Vec3d pos) {
        Vec3d from = mc.player.getEyePos();

        return pos.distanceTo(from) <= breakRange.get();
    }

    public boolean shouldBreakCrystal(Entity entity) {
        boolean damageCheck = false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                continue;
            }

            if (player.isDead()) {
                continue;
            }

            if (Friends.get().isFriend(player)) {
                continue;
            }

            double selfDamage = DamageUtils.newCrystalDamage(mc.player, mc.player.getBoundingBox(),
                    entity.getPos(), null);

            if (selfDamage > maxBreak.get()) {
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
            event.setTarget(rotatePos, 10f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onEntity(EntityAddedEvent event) {
        if (packetBreak.get()) {
            Entity entity = event.entity;
            if (!(entity instanceof EndCrystalEntity)) {
                return;
            }

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

        // Basic renderer
        if (renderPos != null && !renderTimer.passedTicks(3)) {
            event.renderer.box(renderPos, Color.RED.a(30), Color.RED.a(30), ShapeMode.Both, 0);
        }
    }

    private boolean intersectsWithEntities(Box box) {
        return intersectsWithEntity(box, entity -> !entity.isSpectator() && !explodedCrystals.contains(entity.getId()));
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
