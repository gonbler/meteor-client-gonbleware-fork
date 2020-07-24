package minegame159.meteorclient.modules.combat;

//Updated by squidoodly 31/04/2020
//Updated by squidoodly 19/06/2020

import com.google.common.collect.Streams;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import minegame159.meteorclient.accountsfriends.FriendManager;
import minegame159.meteorclient.events.TickEvent;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.ToggleModule;
import minegame159.meteorclient.settings.*;
import minegame159.meteorclient.utils.DamageCalcUtils;
import minegame159.meteorclient.utils.InvUtils;
import minegame159.meteorclient.utils.Utils;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EnderCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BedItem;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.stream.Collectors;

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

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-switch")
            .description("Switches to crystals for you.")
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
            .description("Allow it to place cystals")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> antiWeakness = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-weakness")
            .description("Switches to tools when you have weakness")
            .defaultValue(true)
            .build()
    );

    public CrystalAura() {
        super(Category.Combat, "crystal-aura", "Places and breaks end crystals automatically");
    }

    @EventHandler
    private Listener<TickEvent> onTick = new Listener<>(event -> {
        if ((mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= minHealth.get() && mode.get() != Mode.suicide) return;
        if(place.get() && ((mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL && mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL) && !autoSwitch.get())) return;
        if(place.get()) {
            ListIterator<BlockPos> validBlocks = Objects.requireNonNull(findValidBlocks()).listIterator();
            Iterator<AbstractClientPlayerEntity> validEntities = mc.world.getPlayers().stream().filter(entityPlayer -> !FriendManager.INSTANCE.isTrusted(entityPlayer)).filter(entityPlayer -> !entityPlayer.getDisplayName().equals(mc.player.getDisplayName())).filter(entityPlayer -> Math.sqrt(mc.player.squaredDistanceTo(new Vec3d(entityPlayer.x, entityPlayer.y, entityPlayer.z))) <= 10).collect(Collectors.toList()).iterator();
            AbstractClientPlayerEntity target;
            if (validEntities.hasNext()) {
                target = validEntities.next();
            } else {
                return;
            }
            for (AbstractClientPlayerEntity i = null; validEntities.hasNext(); i = validEntities.next()) {
                if (i == null) continue;
                if (mc.player.distanceTo(i) < mc.player.distanceTo(target)) {
                    target = i;
                }
            }
            BlockPos bestBlock = mc.player.getBlockPos();
            for (BlockPos i = null; validBlocks.hasNext(); i = validBlocks.next()) {
                if (i == null) continue;
                Vec3d convert = new Vec3d(i.getX(), i.getY(), i.getZ()).add(0, 1, 0);
                if (mc.player.getHealth() + mc.player.getAbsorptionAmount() - DamageCalcUtils.resistanceReduction(mc.player, DamageCalcUtils.blastProtReduction(mc.player, DamageCalcUtils.armourCalc(mc.player, DamageCalcUtils.crystalDamage(mc.player, convert))))
                        < minHealth.get() && mode.get() != Mode.suicide) continue;
                double damage = DamageCalcUtils.resistanceReduction(target, DamageCalcUtils.blastProtReduction(target, DamageCalcUtils.armourCalc(target, DamageCalcUtils.crystalDamage(target, convert))));
                double selfDamage = DamageCalcUtils.resistanceReduction(mc.player, DamageCalcUtils.blastProtReduction(mc.player, DamageCalcUtils.armourCalc(mc.player, DamageCalcUtils.crystalDamage(mc.player, convert))));
                convert = new Vec3d(bestBlock.getX(), bestBlock.getY(), bestBlock.getZ()).add(0, 1, 0);
                if (damage > DamageCalcUtils.resistanceReduction(target, DamageCalcUtils.blastProtReduction(target, DamageCalcUtils.armourCalc(target, DamageCalcUtils.crystalDamage(target, convert))))
                        && (selfDamage < maxDamage.get() || mode.get() == Mode.suicide) && damage > minDamage.get()) {
                    bestBlock = i;
                    break;
                }
            }
            if(autoSwitch.get() && mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL){
                int slot = InvUtils.findItemWithCount(Items.END_CRYSTAL).slot;
                if(slot != -1 && slot < 9){
                    mc.player.inventory.selectedSlot = slot;
                }
            }
            if (!bestBlock.equals(mc.player.getBlockPos())) {
                Hand hand = Hand.MAIN_HAND;
                if (!(mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL) && mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) {
                    hand = Hand.OFF_HAND;
                } else if (!(mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL) && !(mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL)) return;
                mc.interactionManager.interactBlock(mc.player, mc.world, hand, new BlockHitResult(new Vec3d(bestBlock), Direction.UP, bestBlock, false));
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
        Streams.stream(mc.world.getEntities())
                .filter(entity -> entity instanceof EnderCrystalEntity)
                .filter(entity -> entity.distanceTo(mc.player) <= breakRange.get())
                .filter(Entity::isAlive)
                .filter(entity -> ignoreWalls.get() || mc.player.canSee(entity))
                .filter(entity -> !(breakMode.get() == Mode.safe) || (mc.player.getHealth() + mc.player.getAbsorptionAmount()
                        - DamageCalcUtils.resistanceReduction(mc.player, DamageCalcUtils.blastProtReduction(mc.player, DamageCalcUtils.armourCalc(mc.player, DamageCalcUtils.getDamageMultiplied(DamageCalcUtils.crystalDamage(mc.player, entity.getPos())))))
                        > minHealth.get() && DamageCalcUtils.resistanceReduction(mc.player, DamageCalcUtils.blastProtReduction(mc.player, DamageCalcUtils.armourCalc(mc.player, DamageCalcUtils.getDamageMultiplied(DamageCalcUtils.crystalDamage(mc.player, entity.getPos())))))
                        < maxDamage.get()))
                .min(Comparator.comparingDouble(o -> o.distanceTo(mc.player)))
                .ifPresent(entity -> {
                    int preSlot = mc.player.inventory.selectedSlot;
                    if(mc.player.getActiveStatusEffects().containsKey(StatusEffects.WEAKNESS)){
                        for(int i = 0; i < 9; i++){
                            if(mc.player.inventory.getInvStack(i).getItem() instanceof SwordItem || mc.player.inventory.getInvStack(i).getItem() instanceof AxeItem){
                                mc.player.inventory.selectedSlot = i;
                            }
                        }
                    }
                    double deltaX = entity.x - mc.player.x;
                    double deltaZ = entity.z - mc.player.z;
                    double deltaY = entity.y - (mc.player.y + mc.player.getEyeHeight(mc.player.getPose()));
                    double yawAngle = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;
                    double idk = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    double pitchAngle = -Math.toDegrees(Math.atan2(deltaY, idk));
                    PlayerMoveC2SPacket.LookOnly packet = new PlayerMoveC2SPacket.LookOnly(((float) yawAngle),(float) pitchAngle, mc.player.onGround);
                    mc.player.networkHandler.sendPacket(packet);
                    mc.interactionManager.attackEntity(mc.player, entity);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    mc.player.inventory.selectedSlot = preSlot;
                });
    }, EventPriority.HIGH);

    private List<BlockPos> findValidBlocks(){
        Iterator<BlockPos> allBlocks = getRange(mc.player.getBlockPos(), placeRange.get()).iterator();
        List<BlockPos> validBlocks = new ArrayList<>();
        for(BlockPos i = null; allBlocks.hasNext(); i = allBlocks.next()){
            if(i == null) continue;
            if((mc.world.getBlockState(i).getBlock() == Blocks.BEDROCK
                    || mc.world.getBlockState(i).getBlock() == Blocks.OBSIDIAN)
                    && (mc.world.getBlockState(i.up()).getBlock() == Blocks.AIR && mc.world.getEntities(null, new Box(i.up().getX(), i.up().getY(), i.up().getZ(), i.up().getX() + 1.0D, i.up().getY() + 2.0D, i.up().getZ() + 1.0D)).isEmpty())
                    && mc.world.getBlockState(i.up(2)).getBlock() == Blocks.AIR && mc.world.getEntities(null, new Box(i.up(2).getX(), i.up(2).getY(), i.up(2).getZ(), i.up(2).getX() + 1.0D, i.up(2).getY() + 2.0D, i.up(2).getZ() + 1.0D)).isEmpty()){
                validBlocks.add(i);
            }
        }
        return validBlocks;
    }

    private List<BlockPos> getRange(BlockPos player, double range){
        List<BlockPos> allBlocks = new ArrayList<>();
        for(double i = player.getX() - range; i < player.getX() + range; i++){
            for(double j = player.getZ() - range; j < player.getZ() + range; j++){
                for(int k = player.getY() - 3; k < player.getY() + 3; k++){
                    BlockPos x = new BlockPos(i, k, j);
                    allBlocks.add(x);
                }
            }
        }
        return allBlocks;
    }
}
