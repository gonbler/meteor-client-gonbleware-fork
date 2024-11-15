package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.commands.ServerCommand;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Packet;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Pitch40;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Bounce;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Slide;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Vanilla;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.systems.modules.player.Rotation;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class GrimFakeFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> fireworkDelay = sgGeneral.add(new DoubleSetting.Builder()
            .name("fall-multiplier").description("Controls how fast will you go down naturally.")
            .defaultValue(2.3).min(0).max(5).build());

    private int fireworkTicksLeft = 0;
    private boolean isFlying = false;
    private boolean needsFirework = false;
    private Vec3d lastMovement = Vec3d.ZERO;
    private boolean needsElytra = false;
    private boolean wearingElytra = false;

    private Vec3d vel = Vec3d.ZERO;

    public GrimFakeFly() {
        super(Categories.Movement, "elytra-fakefly",
                "Gives you more control over your elytra but funnier.");
    }

    @Override
    public void onActivate() {
        super.onActivate();

        needsElytra = true;
        needsFirework = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (needsElytra) {
            equipElytra();

            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player,
                    ClientCommandC2SPacket.Mode.START_FALL_FLYING));

            needsElytra = false;
            wearingElytra = true;
        } else {
            equipChestplate();

            if (wearingElytra) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player,
                        ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }

            wearingElytra = false;
        }

        if (needsFirework) {
            if (wearingElytra) {
                if (fireworkTicksLeft <= 0) {
                    useFirework();
                }

                needsFirework = false;
            } else {
                needsElytra = true;
            }
        }

        if (fireworkTicksLeft >= 0) {
            fireworkTicksLeft--;
        }

        vel = new Vec3d(0, 0, 0);

        if (mc.options.forwardKey.isPressed()) {
            vel = vel.add(0, 0, 30 / 20);
            vel = vel.rotateY(-(float) Math.toRadians(mc.player.getYaw()));
        }
        if (mc.options.backKey.isPressed()) {
            vel = vel.add(0, 0, 30 / 20);
            vel = vel.rotateY((float) Math.toRadians(mc.player.getYaw()));
        }

        if (mc.options.jumpKey.isPressed()) {
            vel = vel.add(0, 30 / 20, 0);
        }
        if (mc.options.sneakKey.isPressed()) {
            vel = vel.add(0, -30 / 20, 0);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isActive()) {
            return;
        }

        if (lastMovement == null) {
            lastMovement = event.movement;
        }

        Vec3d newMovement = vel;

        System.out.println(vel.toString());

        mc.player.setVelocity(newMovement);
        ((IVec3d) event.movement).set(newMovement.x, newMovement.y, newMovement.z);

        if (!lastMovement.equals(newMovement)) {
            needsElytra = true;
            needsFirework = true;
        }

        lastMovement = newMovement;
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        /*
         * if (event.packet instanceof PlayerMoveC2SPacket) { if
         * (!mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
         * mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player,
         * ClientCommandC2SPacket.Mode.START_FALL_FLYING)); } }
         */
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {

    }

    private boolean equipChestplate() {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem()
                .equals(Items.DIAMOND_CHESTPLATE)
                || mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem()
                        .equals(Items.NETHERITE_CHESTPLATE)) {
            return false;
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

            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);

            InvUtils.swapBack();
        }
    }
}
