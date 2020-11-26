/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2020 Meteor Development.
 */

package minegame159.meteorclient.modules.combat;

import com.google.common.collect.Streams;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import minegame159.meteorclient.events.EntityRemovedEvent;
import minegame159.meteorclient.events.PostTickEvent;
import minegame159.meteorclient.events.RenderEvent;
import minegame159.meteorclient.friends.FriendManager;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.ToggleModule;
import minegame159.meteorclient.rendering.ShapeBuilder;
import minegame159.meteorclient.settings.*;
import minegame159.meteorclient.utils.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.*;

public class CrystalAura extends ToggleModule {
    public enum Mode{
        safe,
        suicide
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("place-range")
            .description("The distance in a single direction the crystals get placed.")
            .defaultValue(3)
            .min(0)
            .sliderMax(5)
            .build()
    );

    private final Setting<Double> breakRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("break-range")
            .description("The distance in a single direction the crystals get broken.")
            .defaultValue(3)
            .min(0)
            .sliderMax(5)
            .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("place-mode")
            .description("The way crystals are placed")
            .defaultValue(Mode.safe)
            .build()
    );

    private final Setting<Mode> breakMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("break-mode")
            .description("The way crystals are broken.")
            .defaultValue(Mode.safe)
            .build()
    );

    private final Setting<List<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Entities to attack.")
            .defaultValue(getDefault())
            .onlyAttackable()
            .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-switch")
            .description("Switches to crystals for you.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> spoofChange = sgGeneral.add(new BoolSetting.Builder()
            .name("spoof-change")
            .description("Spoofs item change to crystal.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> minDamage = sgPlace.add(new DoubleSetting.Builder()
            .name("min-damage")
            .description("The minimum damage the crystal will place")
            .defaultValue(5.5)
            .build()
    );

    private final Setting<Double> maxDamage = sgPlace.add(new DoubleSetting.Builder()
            .name("max-damage")
            .description("The maximum self-damage allowed")
            .defaultValue(3)
            .build()
    );

    private final Setting<Boolean> strict = sgPlace.add(new BoolSetting.Builder()
            .name("strict")
            .description("Helps compatibility with some servers.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> minHealth = sgPlace.add(new DoubleSetting.Builder()
            .name("min-health")
            .description("The minimum health you have to be for it to place")
            .defaultValue(15)
            .build()
    );

    private final Setting<Boolean> ignoreWalls = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-walls")
            .description("Attack through walls")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> place = sgGeneral.add(new BoolSetting.Builder()
            .name("place")
            .description("Allow it to place crystals")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Delay ticks between placements.")
            .defaultValue(2)
            .min(0)
            .sliderMax(10)
            .build()
    );

    private final Setting<Boolean> smartDelay = sgGeneral.add(new BoolSetting.Builder()
            .name("smart-delay")
            .description("Reduces crystal consumption when doing large amounts of damage.(Can tank performance on lower end PCs)")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> surroundBreak = sgGeneral.add(new BoolSetting.Builder()
            .name("surround-break")
            .description("Places a crystal next to a surrounded player and keeps it there so they can't surround again.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> surroundHold = sgGeneral.add(new BoolSetting.Builder()
            .name("surround-hold")
            .description("Places a crystal next to a player so they can't surround.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> facePlace = sgGeneral.add(new BoolSetting.Builder()
            .name("face-place")
            .description("Will face place when target is below a certain health or their armour is low.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> facePlaceHealth = sgGeneral.add(new DoubleSetting.Builder()
            .name("face-place-health")
            .description("The health required to face place")
            .defaultValue(5)
            .min(1)
            .max(20)
            .build()
    );

    private final Setting<Double> facePlaceDurability = sgGeneral.add(new DoubleSetting.Builder()
            .name("face-place-durability")
            .description("The durability required to face place (in percent)")
            .defaultValue(2)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build()
    );

    private final Setting<Boolean> spamFacePlace = sgGeneral.add(new BoolSetting.Builder()
            .name("spam-face-place")
            .description("Places faster when someone is below the face place health (requires smart delay)")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> healthDifference = sgGeneral.add(new DoubleSetting.Builder()
            .name("damage-increase")
            .description("The damage increase for smart delay to work.")
            .defaultValue(5)
            .min(0)
            .max(20)
            .build()
    );

    private final Setting<Boolean> antiWeakness = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-weakness")
            .description("Switches to tools when you have weakness")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> noSwing = sgGeneral.add(new BoolSetting.Builder()
            .name("no-swing")
            .description("Stops your hand swinging.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
            .name("render")
            .description("Render a box where it is placing a crystal.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Color> renderColor = sgGeneral.add(new ColorSetting.Builder()
            .name("render-color")
            .description("Render color.")
            .defaultValue(new Color(25, 225, 225))
            .build()
    );

    private final Color sideColor = new Color();

    public CrystalAura() {
        super(Category.Combat, "crystal-aura", "Places and breaks end crystals automatically");
    }

    private int preSlot;
    private int delayLeft = delay.get();
    private Vec3d bestBlock;
    private double bestDamage;
    private BlockPos playerPos;
    private Vec3d pos;
    private double lastDamage = 0;
    private boolean shouldFacePlace = false;
    private boolean shouldPlace = false;
    private EndCrystalEntity heldCrystal = null;
    private LivingEntity target;
    private boolean locked = false;

    private final Pool<RenderBlock> renderBlockPool = new Pool<>(RenderBlock::new);
    private final List<RenderBlock> renderBlocks = new ArrayList<>();

    @Override
    public void onActivate() {
        preSlot = -1;
    }

    @Override
    public void onDeactivate() {
        assert mc.player != null;
        if (preSlot != -1) mc.player.inventory.selectedSlot = preSlot;
        for (RenderBlock renderBlock : renderBlocks) {
            renderBlockPool.free(renderBlock);
        }
        renderBlocks.clear();
    }

    @EventHandler
    private final Listener<EntityRemovedEvent> onEntityRemoved = new Listener<>(event -> {
        if (heldCrystal == null) return;
        if (event.entity.getBlockPos().equals(heldCrystal.getBlockPos())) {
            heldCrystal = null;
            locked = false;
        }
    });

    @EventHandler
    private final Listener<PostTickEvent> onTick = new Listener<>(event -> {
        assert mc.player != null;
        assert mc.world != null;
        for (Iterator<RenderBlock> it = renderBlocks.iterator(); it.hasNext();) {
            RenderBlock renderBlock = it.next();

            if (renderBlock.shouldRemove()) {
                it.remove();
                renderBlockPool.free(renderBlock);
            }
        }

        delayLeft --;
        if (!surroundBreak.get() && locked){
            heldCrystal = null;
            locked = false;
        }
        if (heldCrystal != null && mc.player.distanceTo(heldCrystal) > breakRange.get()) {
            heldCrystal = null;
            locked = false;
        }
        boolean isThere = false;
        if (heldCrystal != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof EndCrystalEntity)) continue;
                if (heldCrystal != null && entity.getBlockPos().equals(heldCrystal.getBlockPos())) {
                    isThere = true;
                    break;
                }
            }
            if (!isThere){
                heldCrystal = null;
                locked = false;
            }
        }
        shouldFacePlace = false;
        if (getTotalHealth(mc.player) <= minHealth.get() && mode.get() != Mode.suicide) return;
        if (target != null && heldCrystal != null && delayLeft <= 0 && mc.world.raycast(new RaycastContext(target.getPos(), heldCrystal.getPos(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, target)).getType()
                == HitResult.Type.MISS) locked = false;
        if (heldCrystal == null) locked = false;
        if (locked) return;
        hitCrystal();
        if (!smartDelay.get() && delayLeft > 0) return;
        if (!autoSwitch.get() && mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL && mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) return;
        if (place.get()) {
            findTarget();
            if (target == null) return;
            if (surroundHold.get() && heldCrystal == null){
                bestBlock = findOpen(target);
                if (bestBlock != null){
                    if (autoSwitch.get()) doSwitch();
                    bestDamage = DamageCalcUtils.crystalDamage(target, bestBlock.add(0, 1, 0));
                    heldCrystal = new EndCrystalEntity(mc.world, bestBlock.x, bestBlock.y + 1, bestBlock.z);
                    locked = true;
                    if (!smartDelay.get()) {
                        delayLeft = delay.get();
                    }else if (smartDelay.get()) {
                        lastDamage = bestDamage;
                        if (delayLeft <= 0) delayLeft = 10;
                    }
                    placeBlock(bestBlock, getHand());
                    return;
                }
            }
            findValidBlocks(target);
            if (bestBlock == null) return;
            if (facePlace.get() && Math.sqrt(target.squaredDistanceTo(bestBlock)) <= 2) {
                if (target.getHealth() + target.getAbsorptionAmount() < facePlaceHealth.get()) {
                    shouldFacePlace = true;
                } else {
                    Iterator<ItemStack> armourItems = target.getArmorItems().iterator();
                    for (ItemStack itemStack = null; armourItems.hasNext(); itemStack = armourItems.next()){
                        if (itemStack == null) continue;
                        if (!itemStack.isEmpty() && (((double)(itemStack.getMaxDamage() - itemStack.getDamage()) / itemStack.getMaxDamage()) * 100) <= facePlaceDurability.get()){
                            shouldFacePlace = true;
                        }
                    }
                }
            }
            if (bestBlock != null && (bestDamage >= minDamage.get() || shouldFacePlace || shouldPlace)) {
                if (autoSwitch.get()) doSwitch();
                if (!smartDelay.get()) {
                    delayLeft = delay.get();
                    placeBlock(bestBlock, getHand());
                }else if (smartDelay.get() && (delayLeft <= 0 || bestDamage - lastDamage > healthDifference.get()
                        || (spamFacePlace.get() && shouldFacePlace))) {
                    lastDamage = bestDamage;
                    placeBlock(bestBlock, getHand());
                    if (delayLeft <= 0) delayLeft = 10;
                }
            }
            if (spoofChange.get() && preSlot != mc.player.inventory.selectedSlot && preSlot != -1)
                mc.player.inventory.selectedSlot = preSlot;
        }
    }, EventPriority.HIGH);

    @EventHandler
    private final Listener<RenderEvent> onRender = new Listener<>(event -> {
        if (render.get()) {
            sideColor.set(renderColor.get());
            sideColor.a = 45;

            for (RenderBlock renderBlock : renderBlocks) {
                renderBlock.render();
            }
        }
    });

    private void hitCrystal(){
        assert mc.player != null;
        assert mc.world != null;
        assert mc.interactionManager != null;
        Streams.stream(mc.world.getEntities())
                .filter(entity -> entity instanceof EndCrystalEntity)
                .filter(entity -> entity.distanceTo(mc.player) <= breakRange.get())
                .filter(Entity::isAlive)
                .filter(entity -> shouldBreak((EndCrystalEntity) entity))
                .filter(entity -> ignoreWalls.get() || mc.player.canSee(entity))
                .filter(entity -> !(breakMode.get() == Mode.safe)
                        || (getTotalHealth(mc.player) - DamageCalcUtils.crystalDamage(mc.player, entity.getPos()) > minHealth.get()
                        && DamageCalcUtils.crystalDamage(mc.player, entity.getPos()) < maxDamage.get()))
                .min(Comparator.comparingDouble(o -> o.distanceTo(mc.player)))
                .ifPresent(entity -> {
                    int preSlot = mc.player.inventory.selectedSlot;
                    if (mc.player.getActiveStatusEffects().containsKey(StatusEffects.WEAKNESS) && antiWeakness.get()) {
                        for (int i = 0; i < 9; i++) {
                            if (mc.player.inventory.getStack(i).getItem() instanceof SwordItem || mc.player.inventory.getStack(i).getItem() instanceof AxeItem) {
                                mc.player.inventory.selectedSlot = i;
                                break;
                            }
                        }
                    }

                    Vec3d vec1 = entity.getPos();
                    PlayerMoveC2SPacket.LookOnly packet = new PlayerMoveC2SPacket.LookOnly(Utils.getNeededYaw(vec1), Utils.getNeededPitch(vec1), mc.player.isOnGround());
                    mc.player.networkHandler.sendPacket(packet);

                    mc.interactionManager.attackEntity(mc.player, entity);
                    if (!noSwing.get()) mc.player.swingHand(getHand());
                    mc.player.inventory.selectedSlot = preSlot;
                    if (heldCrystal != null && entity.getBlockPos().equals(heldCrystal.getBlockPos())) {
                        heldCrystal = null;
                        locked = false;
                    }
                });
    }

    private void findTarget(){
        Optional<LivingEntity> livingEntity = Streams.stream(mc.world.getEntities())
                .filter(Entity::isAlive)
                .filter(entity -> entity != mc.player)
                .filter(entity -> !(entity instanceof PlayerEntity) || FriendManager.INSTANCE.attack((PlayerEntity) entity))
                .filter(entity -> entity instanceof LivingEntity)
                .filter(entity -> entities.get().contains(entity.getType()))
                .min(Comparator.comparingDouble(o -> o.distanceTo(mc.player)))
                .filter(entity -> entity.distanceTo(mc.player) <= breakRange.get() * 2)
                .map(entity -> (LivingEntity) entity);
        if (!livingEntity.isPresent()) {
            target = null;
            return;
        }
        target = livingEntity.get();
    }

    private void doSwitch(){
        assert mc.player != null;
        if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL || mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) {
            int slot = InvUtils.findItemWithCount(Items.END_CRYSTAL).slot;
            if (slot != -1 && slot < 9) {
                preSlot = mc.player.inventory.selectedSlot;
                mc.player.inventory.selectedSlot = slot;
            }
        }
    }

    private void placeBlock(Vec3d block, Hand hand){
        assert mc.player != null;
        assert mc.interactionManager != null;
        float yaw = mc.player.yaw;
        float pitch = mc.player.pitch;
        Vec3d vec1 = block.add(0.5, 0.5, 0.5);
        PlayerMoveC2SPacket.LookOnly packet = new PlayerMoveC2SPacket.LookOnly(Utils.getNeededYaw(vec1), Utils.getNeededPitch(vec1), mc.player.isOnGround());
        mc.player.networkHandler.sendPacket(packet);

        mc.interactionManager.interactBlock(mc.player, mc.world, hand, new BlockHitResult(block, Direction.UP, new BlockPos(block), false));
        if (!noSwing.get()) mc.player.swingHand(hand);
        packet = new PlayerMoveC2SPacket.LookOnly(yaw, pitch, mc.player.isOnGround());
        mc.player.networkHandler.sendPacket(packet);
        mc.player.yaw = yaw;
        mc.player.pitch = pitch;

        if (render.get()) {
            RenderBlock renderBlock = renderBlockPool.get();
            renderBlock.reset(block);
            renderBlocks.add(renderBlock);
        }
    }

    private void findValidBlocks(LivingEntity target){
        assert mc.player != null;
        assert mc.world != null;
        bestBlock = null;
        bestDamage = 0;
        playerPos = mc.player.getBlockPos();
        boolean valid = isSurrounded(target);
        for(double i = playerPos.getX() - placeRange.get(); i < playerPos.getX() + placeRange.get(); i++){
            for(double j = playerPos.getZ() - placeRange.get(); j < playerPos.getZ() + placeRange.get(); j++){
                for(double k = playerPos.getY() - 3; k < playerPos.getY() + 3; k++){
                    pos = new Vec3d(i, k, j);
                    if (bestBlock == null) bestBlock = pos;
                    if(isValid(pos) && (DamageCalcUtils.crystalDamage(mc.player, new Vec3d(Math.floor(pos.x), Math.floor(pos.y), Math.floor(pos.z)).add(0.5, 1, 0.5)) < maxDamage.get()
                            || mode.get() == Mode.suicide)){
                        if (!strict.get() || isEmpty(new BlockPos(pos.add(0, 2, 0)))) {
                            if (surroundBreak.get() && heldCrystal == null && valid
                                    && target.getBlockPos().getY() == k + 1
                                    && (Math.pow((target.getBlockPos().getX() - i), 2) + Math.pow((target.getBlockPos().getZ() - j), 2)) == 4d
                                    && mc.world.raycast(new RaycastContext(target.getPos(), pos.add(0, 1, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, target)).getType()
                                    != HitResult.Type.MISS){
                                bestBlock = new Vec3d(Math.floor(pos.x), Math.floor(pos.y), Math.floor(pos.z));
                                bestDamage = DamageCalcUtils.crystalDamage(target, bestBlock.add(0.5, 1, 0.5));
                                shouldPlace = true;
                                heldCrystal = new EndCrystalEntity(mc.world, Math.floor(i), Math.floor(k) + 1, Math.floor(j));
                                locked = true;
                                return;
                            }
                            if (bestDamage < DamageCalcUtils.crystalDamage(target, new Vec3d(Math.floor(pos.x), Math.floor(pos.y), Math.floor(pos.z)).add(0.5, 1, 0.5))) {
                                bestBlock = new Vec3d(Math.floor(pos.x), Math.floor(pos.y), Math.floor(pos.z));
                                bestDamage = DamageCalcUtils.crystalDamage(target, bestBlock.add(0.5, 1, 0.5));
                            }
                        }
                    }
                }
            }
        }
        if (DamageCalcUtils.crystalDamage(target, bestBlock.add(0.5, 1, 0.5)) < minDamage.get()) bestBlock = null;
    }

    private Vec3d findOpen(LivingEntity target){
        assert mc.player != null;
        int x = 0;
        int z = 0;
        if (isValid(target.getPos().add(1, -1, 0))
                && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(new Vec3i(target.getBlockPos().getX() + 1, target.getBlockPos().getY(), target.getBlockPos().getZ()))) < placeRange.get()){
            x = 1;
        } else if (isValid(target.getPos().add(-1, -1, 0))
                && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(new Vec3i(target.getBlockPos().getX() -1, target.getBlockPos().getY(), target.getBlockPos().getZ()))) < placeRange.get()){
            x = -1;
        } else if (isValid(target.getPos().add(0, -1, -1))
                && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(new Vec3i(target.getBlockPos().getX(), target.getBlockPos().getY(), target.getBlockPos().getZ() + 1))) < placeRange.get()){
            z = 1;
        } else if (isValid(target.getPos().add(0, -1, -1))
                && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(new Vec3i(target.getBlockPos().getX(), target.getBlockPos().getY(), target.getBlockPos().getZ() - 1))) < placeRange.get()){
            z = -1;
        }
        if (x != 0 || z != 0) {
            return new Vec3d(target.getBlockPos().getX() + 0.5 + x, target.getBlockPos().getY() - 1, target.getBlockPos().getZ() + 0.5 + z);
        }
        return null;
    }

    private boolean isValid(Vec3d vec3d){
        assert mc.world != null;
        return (mc.world.getBlockState(new BlockPos(vec3d)).getBlock() == Blocks.BEDROCK
                || mc.world.getBlockState(new BlockPos(vec3d)).getBlock() == Blocks.OBSIDIAN)
                && isEmpty(new BlockPos(vec3d.add(0, 1, 0)));
    }

    private float getTotalHealth(PlayerEntity target) {
        return target.getHealth() + target.getAbsorptionAmount();
    }

    private boolean isEmpty(BlockPos pos) {
        assert mc.world != null;
        return mc.world.isAir(pos) && mc.world.getOtherEntities(null, new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D, pos.getY() + 2.0D, pos.getZ() + 1.0D)).isEmpty();
    }

    private class RenderBlock {
        private int x, y, z;
        private int timer;

        public void reset(Vec3d pos) {
            x = MathHelper.floor(pos.getX());
            y = MathHelper.floor(pos.getY());
            z = MathHelper.floor(pos.getZ());
            timer = 4;
        }

        public boolean shouldRemove() {
            if (timer <= 0) return true;
            timer--;
            return false;
        }

        public void render() {
            ShapeBuilder.boxSides(x, y, z, x+1, y+1, z+1, sideColor);
            ShapeBuilder.boxEdges(x, y, z, x+1, y+1, z+1, renderColor.get());
        }
    }

    private List<EntityType<?>> getDefault(){
        List<EntityType<?>> list = new ArrayList<>();
        list.add(EntityType.PLAYER);
        return list;
    }

    private boolean shouldBreak(EndCrystalEntity entity){
        return heldCrystal == null || !heldCrystal.getBlockPos().equals(entity.getBlockPos()) || mc.world.raycast(new RaycastContext(target.getPos(), heldCrystal.getPos(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, target)).getType()
                == HitResult.Type.MISS;
    }

    private boolean isSurrounded(LivingEntity target){
        assert mc.world != null;
        return !mc.world.getBlockState(target.getBlockPos().add(1, 0, 0)).isAir()
                && !mc.world.getBlockState(target.getBlockPos().add(-1, 0, 0)).isAir()
                && !mc.world.getBlockState(target.getBlockPos().add(0, 0, 1)).isAir() &&
                !mc.world.getBlockState(target.getBlockPos().add(0, 0, -1)).isAir();
    }

    public Hand getHand() {
        Hand hand = Hand.MAIN_HAND;
        if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL && mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) {
            hand = Hand.OFF_HAND;
        }
        return hand;
    }
}