package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ElytraFakeFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>().name("mode")
            .description("Determines how to fake fly").defaultValue(Mode.Chestplate).build());

    public final Setting<Double> fireworkDelay =
            sgGeneral.add(new DoubleSetting.Builder().name("firework-delay")
                    .description("Length of a firework.").defaultValue(2.1).min(0).max(5).build());

    public final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("horizontal-speed").description("Controls how fast will you go horizontally.")
            .defaultValue(50).min(0).max(100).build());

    public final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("vertical-speed").description("Controls how fast will you go veritcally.")
            .defaultValue(30).min(0).max(100).build());

    public final Setting<Double> accelTime =
            sgGeneral.add(new DoubleSetting.Builder().name("accel-time")
                    .description("Controls how fast will you accelerate and decelerate in second")
                    .defaultValue(0.2).min(0.001).max(2).build());

    private int fireworkTicksLeft = 0;
    private boolean needsFirework = false;
    private Vec3d lastMovement = Vec3d.ZERO;

    private Vec3d currentVelocity = Vec3d.ZERO;
    private InventorySlotSwap slotSwap = null;

    public ElytraFakeFly() {
        super(Categories.Movement, "elytra-fakefly",
                "Gives you more control over your elytra but funnier.");
    }

    @Override
    public void onActivate() {
        needsFirework = true;
        currentVelocity = mc.player.getVelocity();

        mc.player.jump();
        mc.player.setOnGround(false);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false));
    }

    @Override
    public void onDeactivate() {
        equipChestplate(slotSwap);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean isUsingFirework = getIsUsingFirework();

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

        double actualAccelTime = accelTime.get();

        boolean desiredVelocityReset = false;

        if (desiredVelocity.y == 0) {
            desiredVelocity.add(0, 1e-5, 0);
        }

        if (!isUsingFirework) {
            desiredVelocity = new Vec3d(0, 0, 0);

            desiredVelocityReset = true;

            actualAccelTime = 2.0;
        }

        // Accelerate or decelerate toward desired velocity
        currentVelocity =
                new Vec3d(mc.player.getVelocity().x, currentVelocity.y, mc.player.getVelocity().z);
        Vec3d velocityDifference = desiredVelocity.subtract(currentVelocity);
        double maxDelta = (horizontalSpeed.get() / 20) / (actualAccelTime * 20);
        if (velocityDifference.lengthSquared() > maxDelta * maxDelta) {
            velocityDifference = velocityDifference.normalize().multiply(maxDelta);
        }
        currentVelocity = currentVelocity.add(velocityDifference);


        Box boundingBox = mc.player.getBoundingBox();

        double playerFeetY = boundingBox.minY;

        Box groundBox = new Box(boundingBox.minX, playerFeetY - 0.1, boundingBox.minZ,
                boundingBox.maxX, playerFeetY, boundingBox.maxZ);

        for (BlockPos pos : BlockPos.iterate((int) Math.floor(groundBox.minX),
                (int) Math.floor(groundBox.minY), (int) Math.floor(groundBox.minZ),
                (int) Math.floor(groundBox.maxX), (int) Math.floor(groundBox.maxY),
                (int) Math.floor(groundBox.maxZ))) {
            BlockState blockState = mc.world.getBlockState(pos);

            // Skip air or non-solid blocks
            if (!blockState.isSolidBlock(mc.world, pos)) {
                continue;
            }

            double blockTopY = pos.getY() + 1.0;
            double distanceToBlock = playerFeetY - blockTopY;

            if (distanceToBlock >= 0 && distanceToBlock < 0.1) {
                if (currentVelocity.y < 0) {
                    currentVelocity = new Vec3d(currentVelocity.x, 0.1, currentVelocity.z);
                }
            }
        }

        if (fireworkTicksLeft < ((int) (fireworkDelay.get() * 20.0) - 3) && fireworkTicksLeft > 3
                && !isUsingFirework) {
            fireworkTicksLeft = 0;
        }

        slotSwap = equipElytra();

        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player,
                ClientCommandC2SPacket.Mode.START_FALL_FLYING));

        if (fireworkTicksLeft <= 0) {
            needsFirework = true;
        }

        if (needsFirework) {
            if (currentVelocity.length() > 1e-7 || desiredVelocityReset) {
                useFirework();
                needsFirework = false;
            }
        }

        if (fireworkTicksLeft >= 0) {
            fireworkTicksLeft--;
        }

        if (mode.get() == Mode.Chestplate) {
            equipChestplate(slotSwap);
            slotSwap = null;
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
            // rubberband = true;
        }
    }

    private boolean getIsUsingFirework() {
        boolean usingFirework = false;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity firework) {
                if (firework.getOwner() != null && firework.getOwner().equals(mc.player)) {
                    usingFirework = true;
                }
            }
        }

        return usingFirework;
    }

    public boolean isFlying() {
        return isActive();
    }

    public void equipChestplate(InventorySlotSwap slotSwap) {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem()
                .equals(Items.DIAMOND_CHESTPLATE)
                || mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem()
                        .equals(Items.NETHERITE_CHESTPLATE)) {
            return;
        }

        FindItemResult result = InvUtils.findInHotbar(Items.NETHERITE_CHESTPLATE);
        if (!result.found()) {
            result = InvUtils.findInHotbar(Items.DIAMOND_CHESTPLATE);
        }

        if (result.found()) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 6, result.slot(),
                    SlotActionType.SWAP, mc.player);

            if (slotSwap != null) {
                // Move elytra to inventory slot
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slotSwap.inventorySlot,
                    result.slot(), SlotActionType.SWAP, mc.player);
            }
            return;
        }

        result = InvUtils.find(Items.NETHERITE_CHESTPLATE);
        if (!result.found()) {
            result = InvUtils.find(Items.DIAMOND_CHESTPLATE);
        }

        if (result.found()) {
            InvUtils.move().from(result.slot()).toArmor(2);
        }
    }

    public InventorySlotSwap equipElytra() {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
            return null;
        }

        FindItemResult result = InvUtils.findInHotbar(Items.ELYTRA);

        if (result.found()) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 6, result.slot(),
                    SlotActionType.SWAP, mc.player);
            return null;
        }

        result = InvUtils.find(Items.ELYTRA);

        if (!result.found()) {
            return null;
        }

        FindItemResult hotbarSlot = InvUtils.findInHotbar(x -> {
            if (x.getItem() == Items.TOTEM_OF_UNDYING) {
                return false;
            }
            return true;
        });

        // Move elytra to hotbarSlot
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, result.slot(),
                hotbarSlot.found() ? hotbarSlot.slot() : 0, SlotActionType.SWAP, mc.player);

        // Equip elytra
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 6,
                hotbarSlot.found() ? hotbarSlot.slot() : 0, SlotActionType.SWAP, mc.player);

        InventorySlotSwap slotSwap = new InventorySlotSwap();
        slotSwap.hotbarSlot = hotbarSlot.found() ? hotbarSlot.slot() : 0;
        slotSwap.inventorySlot = result.slot();

        return slotSwap;
    }

    private void useFirework() {
        fireworkTicksLeft = (int) (fireworkDelay.get() * 20.0);

        int hotbarSilentSwapSlot = -1;
        int inventorySilentSwapSlot = -1;

        FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!itemResult.found()) {
            FindItemResult invResult = InvUtils.find(Items.FIREWORK_ROCKET);

            if (!invResult.found()) {
                return;
            }


            FindItemResult hotbarSlotToSwapToResult = InvUtils.findInHotbar(x -> {
                if (x.getItem() == Items.TOTEM_OF_UNDYING) {
                    return false;
                }
                return true;
            });

            inventorySilentSwapSlot = invResult.slot();
            hotbarSilentSwapSlot =
                    hotbarSlotToSwapToResult.found() ? hotbarSlotToSwapToResult.slot() : 0;

            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    inventorySilentSwapSlot, hotbarSilentSwapSlot, SlotActionType.SWAP, mc.player);

            // Re-search in hotbar
            itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        }

        if (!itemResult.found()) {
            return;
        }

        if (itemResult.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
        } else {
            InvUtils.swap(itemResult.slot(), true);

            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);

            InvUtils.swapBack();
        }

        if (inventorySilentSwapSlot != -1 && hotbarSilentSwapSlot != -1) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    inventorySilentSwapSlot, hotbarSilentSwapSlot, SlotActionType.SWAP, mc.player);
        }
    }

    private class InventorySlotSwap {
        public int hotbarSlot;
        public int inventorySlot;
    }

    public enum Mode {
        Chestplate, Elytra
    }
}
