package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;

public class GrimFakeFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> fireworkDelay =
            sgGeneral.add(new DoubleSetting.Builder().name("firework-delay")
                    .description("Length of a firework.").defaultValue(2.1).min(0).max(5).build());

    public final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("horizontal-speed").description("Controls how fast will you go horizontally.")
            .defaultValue(30).min(0).max(100).build());

    public final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("vertical-speed").description("Controls how fast will you go veritcally.")
            .defaultValue(30).min(0).max(100).build());

    public final Setting<Double> accelTime =
            sgGeneral.add(new DoubleSetting.Builder().name("accel-time")
                    .description("Controls how fast will you accelerate and decelerate in second")
                    .defaultValue(0.5).min(0.001).max(2).build());

    private int fireworkTicksLeft = 0;
    private boolean isFlying = false;
    private boolean needsFirework = false;
    private Vec3d lastMovement = Vec3d.ZERO;
    private int moreElytraTicks = 0;
    private boolean wearingElytra = false;

    private Vec3d currentVelocity = Vec3d.ZERO;
    private boolean rubberband = false;

    public GrimFakeFly() {
        super(Categories.Movement, "elytra-fakefly",
                "Gives you more control over your elytra but funnier.");
    }

    @Override
    public void onActivate() {
        super.onActivate();

        moreElytraTicks = 5;
        needsFirework = true;
        currentVelocity = mc.player.getVelocity();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (wearingElytra) {
            if (needsFirework && moreElytraTicks <= 2) {
                if (currentVelocity.length() > 0) {
                    useFirework();
                    needsFirework = false;
                }
            }
        }

        if (!wearingElytra) {
            equipElytra();

            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player,
                    ClientCommandC2SPacket.Mode.START_FALL_FLYING));

            wearingElytra = true;
        }


        Vec3d desiredVelocity = new Vec3d(0, 0, 0);

        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        Vec3d direction = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();

        if (mc.options.forwardKey.isPressed()) {
            desiredVelocity = desiredVelocity.add(
                    direction.multiply(horizontalSpeed.get() / 20, 0, horizontalSpeed.get() / 20));
        }

        if (mc.options.backKey.isPressed()) {
            desiredVelocity = desiredVelocity.add(direction.multiply(-horizontalSpeed.get() / 20, 0,
                    -horizontalSpeed.get() / 20));
        }

        if (mc.options.leftKey.isPressed()) {
            desiredVelocity = desiredVelocity.add(
                    direction.multiply(horizontalSpeed.get() / 20, 0, horizontalSpeed.get() / 20)
                            .rotateY((float) Math.PI / 2));
        }


        if (mc.options.rightKey.isPressed()) {
            desiredVelocity = desiredVelocity.add(
                    direction.multiply(horizontalSpeed.get() / 20, 0, horizontalSpeed.get() / 20)
                            .rotateY(-(float) Math.PI / 2));
        }

        if (mc.options.jumpKey.isPressed()) {
            desiredVelocity = desiredVelocity.add(0, verticalSpeed.get() / 20, 0);
        }
        if (mc.options.sneakKey.isPressed()) {
            desiredVelocity = desiredVelocity.add(0, -verticalSpeed.get() / 20, 0);
        }

        // Accelerate or decelerate toward desired velocity
        currentVelocity = new Vec3d(mc.player.getVelocity().x, currentVelocity.y, mc.player.getVelocity().z);
        Vec3d velocityDifference = desiredVelocity.subtract(currentVelocity);
        double maxDelta = (horizontalSpeed.get() / 20) / (accelTime.get() * 20);
        if (velocityDifference.lengthSquared() > maxDelta * maxDelta) {
            velocityDifference = velocityDifference.normalize().multiply(maxDelta);
        }
        currentVelocity = currentVelocity.add(velocityDifference);

        boolean using = false;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity firework) {
                if (firework.getOwner() != null && firework.getOwner().equals(mc.player)) {
                    using = true;
                }
            }
        }

        if (fireworkTicksLeft < ((int) (fireworkDelay.get() * 20.0) - 3) && fireworkTicksLeft > 3
                && !using) {
            fireworkTicksLeft = 0;
        }

        if (fireworkTicksLeft <= 0) {
            if (currentVelocity.length() > 0) {
                moreElytraTicks = 3;
            }
            needsFirework = true;
        }

        if (fireworkTicksLeft >= 0) {
            fireworkTicksLeft--;
        }

        if (moreElytraTicks <= 0) {
            equipChestplate();

            // mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
            wearingElytra = false;
        }

        if (moreElytraTicks >= 0) {
            moreElytraTicks--;
        }
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (!isActive()) {
            return;
        }

        if (lastMovement == null) {
            lastMovement = event.movement;
        }

        Vec3d newMovement = currentVelocity;

        mc.player.setVelocity(newMovement);
        ((IVec3d) event.movement).set(newMovement.x, newMovement.y, newMovement.z);

        lastMovement = newMovement;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {

    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            rubberband = true;
        }
    }

    public boolean isFlying() {
        return isActive();
    }

    private boolean equipChestplate() {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem()
                .equals(Items.DIAMOND_CHESTPLATE)
                || mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem()
                        .equals(Items.NETHERITE_CHESTPLATE)) {
            return false;
        }

        FindItemResult result = InvUtils.findInHotbar(Items.NETHERITE_CHESTPLATE);
        if (!result.found()) {
            result = InvUtils.findInHotbar(Items.DIAMOND_CHESTPLATE);
        }

        if (result.found()) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 6, result.slot(),
                    SlotActionType.SWAP, mc.player);
            return true;
        }

        int bestSlot = -1;
        boolean breakLoop = false;

        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            Item item = mc.player.getInventory().main.get(i).getItem();

            if (item == Items.DIAMOND_CHESTPLATE) {
                bestSlot = i;
            } else if (item == Items.NETHERITE_CHESTPLATE) {
                bestSlot = i;
                breakLoop = true;
            }

            if (breakLoop)
                break;
        }

        if (bestSlot != -1)
            equip(bestSlot);

        return bestSlot != -1;
    }

    private void equipElytra() {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
            return;
        }

        FindItemResult result = InvUtils.findInHotbar(Items.ELYTRA);

        if (result.found()) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 6, result.slot(),
                    SlotActionType.SWAP, mc.player);
            return;
        }

        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            Item item = mc.player.getInventory().main.get(i).getItem();

            if (item == Items.ELYTRA) {
                equip(i);
                break;
            }
        }
    }

    private void equip(int slot) {
        InvUtils.move().from(slot).toArmor(2);
    }

    private void useFirework() {
        fireworkTicksLeft = (int) (fireworkDelay.get() * 20.0);

        FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!itemResult.found())
            return;

        if (itemResult.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
        } else {
            InvUtils.swap(itemResult.slot(), true);
            int sequence = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

            PlayerInteractItemC2SPacket playerInteractItemC2SPacket =
                    new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(),
                            mc.player.getPitch());
            ItemStack itemStack = mc.player.getStackInHand(Hand.MAIN_HAND);
            TypedActionResult<ItemStack> typedActionResult =
                    itemStack.use(mc.world, mc.player, Hand.MAIN_HAND);
            ItemStack itemStack2 = (ItemStack) typedActionResult.getValue();
            if (itemStack2 != itemStack) {
                mc.player.setStackInHand(Hand.MAIN_HAND, itemStack2);
            }

            mc.getNetworkHandler().sendPacket(playerInteractItemC2SPacket);
            mc.player.swingHand(Hand.MAIN_HAND);

            InvUtils.swapBack();
        }
    }
}
