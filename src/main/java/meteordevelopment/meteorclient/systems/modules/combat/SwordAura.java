package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.Set;
import org.apache.commons.lang3.mutable.MutableDouble;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SwordAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range")
            .description("The maximum range the entity can be to attack it.").defaultValue(2.85)
            .min(0).sliderMax(6).build());

    private final Setting<Boolean> silentSwap =
            sgGeneral.add(new BoolSetting.Builder().name("silent-swap")
                    .description("Whether or not to silently switch to your sword to attack")
                    .defaultValue(true).build());

    private final Setting<Boolean> silentSwapOverrideDelay = sgGeneral.add(new BoolSetting.Builder()
            .name("silent-swap-override-delay")
            .description(
                    "Whether or not to use the held items delay when attacking with silent swap")
            .defaultValue(true).visible(() -> silentSwap.get()).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("rotate")
            .description("Whether or not to rotate to the entity to attack it.").build());

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(
            new EntityTypeListSetting.Builder().name("entities").description("Entities to attack.")
                    .onlyAttackable().defaultValue(EntityType.PLAYER).build());

    private final Setting<SortPriority> priority =
            sgGeneral.add(new EnumSetting.Builder<SortPriority>().name("priority")
                    .description("How to filter targets within range.")
                    .defaultValue(SortPriority.ClosestAngle).build());

    private final Setting<Boolean> ignorePassive =
            sgGeneral.add(new BoolSetting.Builder().name("ignore-passive")
                    .description("Does not attack passive mobs.").defaultValue(false).build());

    private final Setting<Boolean> pauseOnEat = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-on-eat").description("Does not attack while using an item.")
            .defaultValue(true).build());

    private final Setting<Boolean> pauseInAir = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-in-air").description("Does not attack while jumping or falling")
            .defaultValue(true).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render")
            .description("Whether or not to render attacks").defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).visible(() -> render.get()).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").description("The side color of the rendering.")
            .defaultValue(new SettingColor(160, 0, 225, 35)).visible(() -> shapeMode.get().sides())
            .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").description("The line color of the rendering.")
            .defaultValue(new SettingColor(255, 255, 255, 50))
            .visible(() -> render.get() && shapeMode.get().lines()).build());

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
            .name("fade-time").description("How long to fade the bounding box render.").min(0)
            .sliderMax(2.0).defaultValue(0.8).build());

    private long lastAttackTime = 0;
    private Entity target = null;
    private Entity lastAttackedEntity = null;

    public SwordAura() {
        super(Categories.Combat, "sword-aura", "Automatically attacks entities with your sword");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        target = null;

        if (mc.player.isDead() || mc.player.isSpectator()) {
            return;
        }

        if (pauseOnEat.get() && mc.player.isUsingItem()
                && mc.player.getActiveHand() == Hand.MAIN_HAND) {
            return;
        }

        if (pauseInAir.get() && !mc.player.isOnGround()) {
            return;
        }

        Item mainHandItem = mc.player.getInventory().getMainHandStack().getItem();

        if (!silentSwap.get() && mainHandItem != Items.DIAMOND_SWORD
                && mainHandItem != Items.NETHERITE_SWORD) {
            return;
        }

        FindItemResult result = InvUtils.findInHotbar(Items.NETHERITE_SWORD);
        if (!result.found()) {
            result = InvUtils.findInHotbar(Items.DIAMOND_SWORD);
        }

        if (!result.found()) {
            return;
        }

        target = TargetUtils.get(entity -> {
            if (entity.equals(mc.player) || entity.equals(mc.cameraEntity))
                return false;

            if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead())
                    || !entity.isAlive())
                return false;

            Box hitbox = entity.getBoundingBox();
            Vec3d closestPointOnBoundingBox = getClosestPointOnBox(hitbox, mc.player.getEyePos());
            if (!closestPointOnBoundingBox.isWithinRangeOf(mc.player.getEyePos(), range.get(),
                    range.get()))
                return false;

            if (!entities.get().contains(entity.getType()))
                return false;

            if (ignorePassive.get()) {
                if (entity instanceof EndermanEntity enderman && !enderman.isAngry())
                    return false;
                if (entity instanceof ZombifiedPiglinEntity piglin && !piglin.isAttacking())
                    return false;
                if (entity instanceof WolfEntity wolf && !wolf.isAttacking())
                    return false;
            }

            if (entity instanceof PlayerEntity player) {
                if (player.isCreative())
                    return false;
                if (!Friends.get().shouldAttack(player))
                    return false;
            }

            return true;
        }, priority.get());

        if (target == null) {
            return;
        }

        if (!target.isAlive())
            return;
        
        int delayCheckSlot = result.slot();

        if (silentSwap.get() && silentSwapOverrideDelay.get()) {
            delayCheckSlot = mc.player.getInventory().selectedSlot;
        }

        if (delayCheck(delayCheckSlot)) {
            if (rotate.get()) {
                MeteorClient.ROTATION.requestRotation(
                        getClosestPointOnBox(target.getBoundingBox(), mc.player.getEyePos()), 9);

                if (!MeteorClient.ROTATION.lookingAt(target.getBoundingBox())) {
                    return;
                }
            }

            if (silentSwap.get()) {
                InvUtils.swap(result.slot(), true);
            }

            attack();

            if (silentSwap.get()) {
                InvUtils.swapBack();
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || lastAttackedEntity == null) {
            return;
        }

        double secondsSinceAttack = (System.currentTimeMillis() - lastAttackTime) / 1000.0;

        if (secondsSinceAttack > fadeTime.get()) {
            return;
        }

        double alpha = 1 - (secondsSinceAttack / fadeTime.get());

        // Bounding box interpolation
        double x = MathHelper.lerp(event.tickDelta, lastAttackedEntity.lastRenderX,
                lastAttackedEntity.getX()) - lastAttackedEntity.getX();
        double y = MathHelper.lerp(event.tickDelta, lastAttackedEntity.lastRenderY,
                lastAttackedEntity.getY()) - lastAttackedEntity.getY();
        double z = MathHelper.lerp(event.tickDelta, lastAttackedEntity.lastRenderZ,
                lastAttackedEntity.getZ()) - lastAttackedEntity.getZ();

        Box box = lastAttackedEntity.getBoundingBox();

        event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY,
                z + box.maxZ, sideColor.get().copy().a((int) (sideColor.get().a * alpha)),
                lineColor.get().copy().a((int) (lineColor.get().a * alpha)), shapeMode.get(), 0);
    }

    public void attack() {
        mc.getNetworkHandler()
                .sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        mc.player.swingHand(Hand.MAIN_HAND);

        lastAttackedEntity = target;
        lastAttackTime = System.currentTimeMillis();
    }

    private boolean delayCheck(int slot) {
        PlayerInventory inventory = mc.player.getInventory();
        ItemStack itemStack = inventory.getStack(slot);

        MutableDouble attackSpeed = new MutableDouble(
                mc.player.getAttributeBaseValue(EntityAttributes.GENERIC_ATTACK_SPEED));

        AttributeModifiersComponent attributeModifiers =
                itemStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attributeModifiers != null) {
            attributeModifiers.applyModifiers(EquipmentSlot.MAINHAND, (entry, modifier) -> {
                if (entry == EntityAttributes.GENERIC_ATTACK_SPEED) {
                    attackSpeed.add(modifier.value());
                }
            });
        }

        double attackCooldownTicks = 1.0 / attackSpeed.getValue() * 20.0;

        long currentTime = System.currentTimeMillis();

        // 50 ms in a tick
        if ((currentTime - lastAttackTime) / 50.0 > attackCooldownTicks) {
            return true;
        }

        return false;
    }

    public Vec3d getClosestPointOnBox(Box box, Vec3d point) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return new Vec3d(x, y, z);
    }
}
