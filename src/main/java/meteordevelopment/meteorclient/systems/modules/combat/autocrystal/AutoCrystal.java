package meteordevelopment.meteorclient.systems.modules.combat.autocrystal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.PlayerDeathEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IBox;
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
import meteordevelopment.meteorclient.systems.modules.combat.AutoMine;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
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
import net.minecraft.util.math.Vec3d;

public class AutoCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgFacePlace = settings.createGroup("Face Place");
    private final SettingGroup sgBreak = settings.createGroup("Break");

    private final SettingGroup sgRotate = settings.createGroup("Rotate");
    private final SettingGroup sgSwing = settings.createGroup("Swing");

    private final SettingGroup sgRange = settings.createGroup("Range");

    // Render settings and etc are handled by the AutoCrystalRenderer class
    private final AutoCrystalRenderer renderer = new AutoCrystalRenderer(this);

    // -- General -- //
    private final Setting<Boolean> placeCrystals = sgGeneral.add(new BoolSetting.Builder()
            .name("place").description("Places crystals.").defaultValue(true).build());

    private final Setting<Boolean> pauseEatPlace =
            sgGeneral.add(new BoolSetting.Builder().name("pause-eat-place")
                    .description("Pauses placing when eating").defaultValue(true).build());

    private final Setting<Boolean> breakCrystals = sgGeneral.add(new BoolSetting.Builder()
            .name("break").description("Breaks crystals.").defaultValue(true).build());

    private final Setting<Boolean> pauseEatBreak =
            sgGeneral.add(new BoolSetting.Builder().name("pause-eat-break")
                    .description("Pauses placing when breaking").defaultValue(false).build());

    private final Setting<Boolean> ignoreNakeds =
            sgGeneral.add(new BoolSetting.Builder().name("ignore-nakeds")
                    .description("Ignore players with no items.").defaultValue(true).build());

    // -- Place -- //
    private final Setting<Double> placeSpeedLimit =
            sgPlace.add(new DoubleSetting.Builder().name("place-speed-limit")
                    .description("Maximum number of crystals to place every second.")
                    .defaultValue(40).min(0).sliderRange(0, 40).build());

    private final Setting<Double> minPlace = sgPlace.add(new DoubleSetting.Builder()
            .name("min-place").description("Minimum enemy damage to place.").defaultValue(8).min(0)
            .sliderRange(0, 20).build());

    private final Setting<Double> maxPlace = sgPlace.add(
            new DoubleSetting.Builder().name("max-place").description("Max self damage to place.")
                    .defaultValue(20).min(0).sliderRange(0, 20).build());

    private final Setting<Boolean> antiSurroundPlace = sgPlace.add(new BoolSetting.Builder()
            .name("anti-surround")
            .description(
                    "Ignores auto-mine blocks from calculations to place outside of their surround.")
            .defaultValue(true).build());

    private final Setting<Double> placeDelay = sgPlace.add(new DoubleSetting.Builder()
            .name("place-delay")
            .description("The number of seconds to wait to retry placing a crystal at a position.")
            .defaultValue(0.05).min(0).sliderMax(0.6).build());

    // -- Face Place -- //
    private final Setting<Boolean> facePlaceMissingArmor =
            sgFacePlace.add(new BoolSetting.Builder().name("face-place-missing-armor")
                    .description("Face places on missing armor").defaultValue(true).build());

    private final Setting<Keybind> forceFacePlaceKeybind =
            sgFacePlace.add(new KeybindSetting.Builder().name("force-face-place")
                    .description("Keybind to force face place").build());

    private final Setting<Boolean> slowPlace = sgFacePlace.add(new BoolSetting.Builder()
            .name("slow-place").description("Slowly places crystals at lower damages.")
            .defaultValue(true).build());

    private final Setting<Double> slowPlaceMinDamage = sgFacePlace.add(new DoubleSetting.Builder()
            .name("slow-place-min-place").description("Minimum damage to slow place.")
            .defaultValue(4).min(0).sliderRange(0, 20).visible(() -> slowPlace.get()).build());

    private final Setting<Double> slowPlaceMaxDamage = sgFacePlace.add(new DoubleSetting.Builder()
            .name("slow-place-max-place").description("Maximum damage to slow place.")
            .defaultValue(8).min(0).sliderRange(0, 20).visible(() -> slowPlace.get()).build());

    private final Setting<Double> slowPlaceSpeed = sgFacePlace.add(new DoubleSetting.Builder()
            .name("slow-place-speed").description("Speed at which to slow place.").defaultValue(2)
            .min(0).sliderRange(0, 20).visible(() -> slowPlace.get()).build());

    // -- Break -- //
    private final Setting<Double> breakSpeedLimit =
            sgBreak.add(new DoubleSetting.Builder().name("break-speed-limit")
                    .description("Maximum number of crystals to break every second.")
                    .defaultValue(60).min(0).sliderRange(0, 60).build());

    private final Setting<Boolean> packetBreak = sgBreak.add(new BoolSetting.Builder()
            .name("packet-break").description("Breaks when the crystal packet arrives")
            .defaultValue(true).build());

    private final Setting<Double> minBreak = sgBreak.add(new DoubleSetting.Builder()
            .name("min-break").description("Minimum enemy damage to break.").defaultValue(3).min(0)
            .sliderRange(0, 20).build());

    private final Setting<Double> maxBreak = sgBreak.add(
            new DoubleSetting.Builder().name("max-break").description("Max self damage to break.")
                    .defaultValue(20).min(0).sliderRange(0, 20).build());

    private final Setting<Double> breakDelay =
            sgBreak.add(new DoubleSetting.Builder().name("break-delay")
                    .description("The number of seconds to wait to retry breaking a crystal.")
                    .defaultValue(0.05).min(0).sliderMax(0.6).build());
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
    public final Setting<Double> placeRange = sgRange.add(new DoubleSetting.Builder()
            .name("place-range").description("Maximum distance to place crystals for")
            .defaultValue(4.0).build());

    private final Setting<Double> breakRange = sgRange.add(new DoubleSetting.Builder()
            .name("break-range").description("Maximum distance to break crystals for")
            .defaultValue(4.0).build());

    public final List<Entity> forceBreakCrystals = new ArrayList<>();

    private final Pool<PlacePosition> placePositionPool = new Pool<>(PlacePosition::new);
    private final List<PlacePosition> _placePositions = new ArrayList<>();
    private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();
    private final BlockPos.Mutable downMutablePos = new BlockPos.Mutable();

    private final IntSet explodedCrystals = new IntOpenHashSet();
    private final Map<Integer, Long> crystalBreakDelays = new HashMap<>();
    private final Map<BlockPos, Long> crystalPlaceDelays = new HashMap<>();

    public final List<Boolean> cachedValidSpots = new ArrayList<>();

    private final Set<UUID> deadPlayers = new HashSet<>();

    private long lastPlaceTimeMS = 0;
    private long lastBreakTimeMS = 0;

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

        deadPlayers.clear();

        renderer.onActivate();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        synchronized (deadPlayers) {
            deadPlayers.removeIf(uuid -> {
                PlayerEntity entity = mc.world.getPlayerByUuid(uuid);

                if (entity == null || entity.isDead()) {
                    return false;
                }

                return true;
            });
        }
    }

    private void update() {
        if (mc.player == null || mc.world == null || mc.world.getPlayers().isEmpty())
            return;

        if (autoMine == null) {
            autoMine = Modules.get().get(AutoMine.class);
        }

        for (PlacePosition p : _placePositions)
            placePositionPool.free(p);

        _placePositions.clear();

        PlacePosition bestPlacePos = null;
        synchronized (deadPlayers) {
            if (placeCrystals.get() && !(pauseEatPlace.get() && mc.player.isUsingItem())) {
                cachedValidPlaceSpots();

                for (PlayerEntity player : mc.world.getPlayers()) {
                    if (player == mc.player) {
                        continue;
                    }

                    if (deadPlayers.contains(player.getUuid())) {
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

                    if (player.squaredDistanceTo(mc.player.getEyePos()) > 12 * 12) {
                        continue;
                    }

                    PlacePosition testPos = findBestPlacePosition(player);

                    if (testPos != null
                            && (bestPlacePos == null || testPos.damage > bestPlacePos.damage)) {
                        bestPlacePos = testPos;
                    }
                }

                long currentTime = System.currentTimeMillis();

                if (bestPlacePos != null && placeSpeedCheck(bestPlacePos.isSlowPlace)) {
                    if (placeCrystal(bestPlacePos.blockPos.down())) {
                        lastPlaceTimeMS = currentTime;
                    }
                }
            }

            if (breakCrystals.get() && !(pauseEatBreak.get() && mc.player.isUsingItem())) {
                for (Entity entity : mc.world.getEntities()) {
                    if (!(entity instanceof EndCrystalEntity))
                        continue;

                    if (!inBreakRange(entity.getPos())) {
                        continue;
                    }

                    if (!shouldBreakCrystal(entity)) {
                        continue;
                    }

                    if (!breakSpeedCheck()) {
                        break;
                    }

                    if (!breakCrystal(entity) && rotateBreak.get()
                            && !MeteorClient.ROTATION.lookingAt(entity.getBoundingBox())) {
                        break;
                    }
                }
            }
        }
    }

    public boolean placeCrystal(BlockPos blockPos) {
        if (blockPos == null || mc.player == null) {
            return false;
        }

        BlockPos crystaBlockPos = blockPos.up();

        Box box = new Box(crystaBlockPos.getX(), crystaBlockPos.getY(), crystaBlockPos.getZ(),
                crystaBlockPos.getX() + 1, crystaBlockPos.getY() + 2, crystaBlockPos.getZ() + 1);

        if (intersectsWithEntities(box)) {
            return false;
        }

        FindItemResult result = InvUtils.find(Items.END_CRYSTAL);

        if (!result.found()) {
            return false;
        }

        if (rotatePlace.get()) {
            MeteorClient.ROTATION.requestRotation(blockPos.toCenterPos(), 10);

            if (!MeteorClient.ROTATION.lookingAt(new Box(blockPos))) {
                return false;
            }
        }

        long currentTime = System.currentTimeMillis();

        if (crystalPlaceDelays.containsKey(blockPos)) {
            if ((currentTime - crystalPlaceDelays.get(blockPos)) / 1000.0 < placeDelay.get()) {
                return false;
            }
        }

        if (!MeteorClient.SWAP.beginSwap(result, true)) {
            return false;
        }

        crystalPlaceDelays.put(blockPos, currentTime);
        renderer.onPlaceCrystal(blockPos);

        BlockHitResult calculatedHitResult = AutoCrystalUtil.getPlaceBlockHitResult(blockPos);

        Hand hand = Hand.MAIN_HAND;

        int s = mc.world.getPendingUpdateManager().incrementSequence().getSequence();
        mc.player.networkHandler
                .sendPacket(new PlayerInteractBlockC2SPacket(hand, calculatedHitResult, s));

        if (placeSwingMode.get() == SwingMode.Client)
            mc.player.swingHand(hand);

        if (placeSwingMode.get() == SwingMode.Packet)
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));

        MeteorClient.SWAP.endSwap(true);

        return true;
    }

    public boolean breakCrystal(Entity entity) {
        if (mc.player == null) {
            return false;
        }

        if (rotateBreak.get()) {
            MeteorClient.ROTATION.requestRotation(entity.getPos(), 10);

            if (!MeteorClient.ROTATION.lookingAt(entity.getBoundingBox())) {
                return false;
            }
        }

        long currentTime = System.currentTimeMillis();

        if (crystalBreakDelays.containsKey(entity.getId())) {
            if ((currentTime - crystalBreakDelays.get(entity.getId())) / 1000.0 < breakDelay
                    .get()) {
                return false;
            }
        }

        crystalBreakDelays.put(entity.getId(), currentTime);

        renderer.onBreakCrystal(entity);

        PlayerInteractEntityC2SPacket packet =
                PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking());

        mc.getNetworkHandler().sendPacket(packet);

        if (breakSwingMode.get() == SwingMode.Client)
            mc.player.swingHand(Hand.MAIN_HAND);

        if (breakSwingMode.get() == SwingMode.Packet)
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        explodedCrystals.add(entity.getId());

        lastBreakTimeMS = System.currentTimeMillis();

        return true;
    }

    private Set<BlockPos> _calcIgnoreSet = new HashSet<>();

    private PlacePosition findBestPlacePosition(PlayerEntity target) {
        // Optimization to not spam allocs because java sucks
        PlacePosition bestPos = placePositionPool.get();
        _placePositions.add(bestPos);

        bestPos.damage = 0.0;
        bestPos.blockPos = null;
        bestPos.isSlowPlace = false;

        int r = (int) Math.floor(placeRange.get());

        BlockPos eyePos = BlockPos.ofFloored(mc.player.getEyePos());
        int ex = eyePos.getX();
        int ey = eyePos.getY();
        int ez = eyePos.getZ();
        // BlockState obstate = Blocks.OBSIDIAN.getDefaultState();
        // boolean airPlace = allowAirPlace.get().isPressed();

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
                    if (!cachedValidSpots
                            .get((x + r) * ((2 * r) * (2 * r)) + (y + r) * (2 * r) + (z + r))) {
                        continue;
                    }

                    BlockPos pos = mutablePos.set(ex + x, ey + y, ez + z);

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

                    boolean shouldSet = targetDamage >= (shouldFacePlace ? 1.0 : minPlace.get())
                            && targetDamage > bestPos.damage;
                    boolean isSlowPlace = false;

                    if (slowPlace.get() && targetDamage > bestPos.damage) {
                        if (targetDamage <= slowPlaceMaxDamage.get()
                                && targetDamage >= slowPlaceMinDamage.get()) {
                            shouldSet = true;
                            isSlowPlace = true;
                        }
                    }

                    if (shouldSet) {
                        bestPos.blockPos = pos.toImmutable();
                        bestPos.damage = targetDamage;
                        bestPos.isSlowPlace = isSlowPlace;

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

    private void cachedValidPlaceSpots() {
        int r = (int) Math.floor(placeRange.get());

        BlockPos eyePos = BlockPos.ofFloored(mc.player.getEyePos());
        int ex = eyePos.getX();
        int ey = eyePos.getY();
        int ez = eyePos.getZ();
        Box box = new Box(0, 0, 0, 0, 0, 0);

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

        // Reset the list
        cachedValidSpots.clear();
        while (cachedValidSpots.size() < (2 * r + 1) * (2 * r + 1) * (2 * r + 1)) {
            cachedValidSpots.add(false);
        }

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = mutablePos.set(ex + x, ey + y, ez + z);
                    BlockState state = mc.world.getBlockState(pos);

                    // Check if there's an air block to place the crystal in
                    if (!state.isAir()) {
                        continue;
                    }

                    BlockPos downPos = downMutablePos.set(ex + x, ey + y - 1, ez + z);
                    BlockState downState = mc.world.getBlockState(downPos);
                    Block downBlock = downState.getBlock();

                    // We can only place on obsidian and bedrock
                    if (downState.isAir()
                            || (downBlock != Blocks.OBSIDIAN && downBlock != Blocks.BEDROCK)) {
                        continue;
                    }

                    // Range check
                    if (!inPlaceRange(downPos)) {
                        continue;
                    }

                    // Check if the crystal intersects with any players/crystals/whatever
                    ((IBox) box).set(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1,
                            pos.getY() + 2, pos.getZ() + 1);

                    if (intersectsWithEntities(box)) {
                        continue;
                    }

                    double selfDamage =
                            DamageUtils.newCrystalDamage(mc.player, mc.player.getBoundingBox(),
                                    new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                                    _calcIgnoreSet);

                    if (selfDamage > maxPlace.get()) {
                        continue;
                    }

                    cachedValidSpots
                            .set((x + r) * ((2 * r) * (2 * r)) + (y + r) * (2 * r) + (z + r), true);
                }
            }
        }
    }

    public void preplaceCrystal(BlockPos crystalBlockPos, boolean snapAt) {
        BlockPos blockPos = crystalBlockPos.down();

        crystalPlaceDelays.remove(blockPos);

        Box box = new Box(crystalBlockPos.getX(), crystalBlockPos.getY(), crystalBlockPos.getZ(),
                crystalBlockPos.getX() + 1, crystalBlockPos.getY() + 2, crystalBlockPos.getZ() + 1);

        if (intersectsWithEntities(box)) {
            return;
        }

        // Also don't snap if we're already looking there
        if (rotatePlace.get() && snapAt
                && !MeteorClient.ROTATION.lookingAt(new Box(crystalBlockPos))) {
            MeteorClient.ROTATION.snapAt(crystalBlockPos.toCenterPos());
        }

        placeCrystal(blockPos);
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

        double selfDamage = DamageUtils.newCrystalDamage(mc.player, mc.player.getBoundingBox(),
                entity.getPos(), null);

        if (selfDamage > maxBreak.get()) {
            return false;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                continue;
            }

            if (deadPlayers.contains(player.getUuid())) {
                continue;
            }

            if (player.isDead()) {
                continue;
            }

            if (Friends.get().isFriend(player)) {
                continue;
            }

            if (player.squaredDistanceTo(mc.player.getEyePos()) > 14 * 14) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onEntity(EntityAddedEvent event) {
        Entity entity = event.entity;
        if (!(entity instanceof EndCrystalEntity)) {
            return;
        }

        BlockPos blockPos = entity.getBlockPos().down();
        if (crystalPlaceDelays.containsKey(blockPos)) {
            crystalPlaceDelays.remove(blockPos);
        }

        if (breakCrystals.get() && packetBreak.get()) {
            if (!(entity instanceof EndCrystalEntity))
                return;

            if (!inBreakRange(entity.getPos())) {
                return;
            }

            if (!shouldBreakCrystal(entity)) {
                return;
            }

            if (!breakSpeedCheck()) {
                return;
            }

            breakCrystal(entity);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onRender3D(Render3DEvent event) {
        if (!isActive())
            return;

        update();

        renderer.onRender3D(event);
    }

    @EventHandler
    private void onPlayerDeath(PlayerDeathEvent.Death event) {
        if (event.getPlayer() == null || event.getPlayer() == mc.player)
            return;

        synchronized (deadPlayers) {
            deadPlayers.add(event.getPlayer().getUuid());
        }
    }

    private boolean intersectsWithEntities(Box box) {
        return EntityUtils.intersectsWithEntity(box,
                entity -> !entity.isSpectator() && !explodedCrystals.contains(entity.getId()));
    }

    private boolean breakSpeedCheck() {
        long currentTime = System.currentTimeMillis();

        return breakSpeedLimit.get() == 0 || ((double) (currentTime - lastBreakTimeMS))
                / 1000.0 > 1.0 / breakSpeedLimit.get();
    }

    private boolean placeSpeedCheck(boolean slowPlace) {
        long currentTime = System.currentTimeMillis();

        double placeSpeed = slowPlace ? slowPlaceSpeed.get() : placeSpeedLimit.get();

        return placeSpeed == 0
                || ((double) (currentTime - lastPlaceTimeMS)) / 1000.0 > 1.0 / placeSpeed;
    }

    @Override
    public String getInfoString() {
        long currentTime = System.currentTimeMillis();
        return String.format("%d",
                crystalBreakDelays.values().stream().filter(x -> currentTime - x <= 1000).count());
    }

    private class PlacePosition {
        public BlockPos blockPos;

        public double damage = 0.0;

        public boolean isSlowPlace = false;
    }

    public enum SwingMode {
        Packet, Client, None
    }
}
