package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.movement.MovementFix;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PearlPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SwitchMode> switchMode =
            sgGeneral.add(new EnumSetting.Builder<SwitchMode>().name("Switch Mode")
                    .description("Which method of switching should be used.")
                    .defaultValue(SwitchMode.SilentHotbar).build());

    private final Setting<Keybind> phaseBind = sgGeneral.add(new KeybindSetting.Builder()
            .name("key-bind").description("Phase on keybind press").build());

    private final Setting<RotateMode> rotateMode =
            sgGeneral.add(new EnumSetting.Builder<RotateMode>().name("rotate-mode")
                    .description("Which method of rotating should be used.")
                    .defaultValue(RotateMode.DelayedInstantWebOnly).build());

    private boolean active = false;
    private boolean keyUnpressed = false;

    public PearlPhase() {
        super(Categories.Combat, "pearl-phase", "Phases into walls using pearls");
    }

    private void activate() {
        active = true;

        if (mc.player == null || mc.world == null)
            return;

        update();
    }

    private void deactivate(boolean phased) {
        active = false;

        if (phased) {
            info("Phased");
        }
    }

    private void update() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (!active) {
            return;
        }

        Box boundingBox = mc.player.getBoundingBox().shrink(0.05, 0.1, 0.05);
        double feetY = mc.player.getY();

        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        // Can't phase if we're already phased
        if (BlockPos.stream(feetBox).anyMatch(blockPos -> {
            return mc.world.getBlockState(blockPos).isSolidBlock(mc.world, blockPos);
        })) {
            deactivate(false);
        }

        if (switch (switchMode.get()) {
            case SilentHotbar -> !InvUtils.findInHotbar(Items.ENDER_PEARL).found();
            case SilentSwap -> !InvUtils.find(Items.ENDER_PEARL).found();
        }) {
            deactivate(false);
            return;
        }

        // Can't phase while sneaking
        if (mc.options.sneakKey.isPressed()) {
            deactivate(false);
            return;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!active) {
            return;
        }

        Vec3d targetPos = calculateTargetPos();
        float[] angle = MeteorClient.ROTATION.getRotation(targetPos);

        // Rotation Modes:
        // Movement: Requests a rotation from the RotationManager and waits for it to be fulfilled
        // Instant: Instantly sends a movement packet with the rotation
        // DelayedInstant: Requests a rotation from the RotationManager and waits for it to be
        // fulfilled, then sends a movement packet with the rotation
        // DelayedInstantWebOnly: Same as DelayedInstant, but only sends a movement packet when in
        // webs

        // Movement fails in webs on Grim,
        // instant is a bit iffy since it doesn't work when you rubberband

        // DelayedInstantWebOnly should work best for grim?
        switch (rotateMode.get()) {
            case Movement -> {
                MeteorClient.ROTATION.requestRotation(targetPos, 1000f);

                if (MeteorClient.ROTATION.lookingAt(Box.of(targetPos, 0.05, 0.05, 0.05))) {
                    throwPearl(angle[0], angle[1]);
                }
            }
            case Instant -> {
                if (mc.player.isOnGround()) {
                    MeteorClient.ROTATION.snapAt(targetPos);

                    throwPearl(angle[0], angle[1]);
                }
            }
            case DelayedInstant -> {
                MeteorClient.ROTATION.requestRotation(targetPos, 1000f);

                if (MeteorClient.ROTATION.lookingAt(Box.of(targetPos, 0.05, 0.05, 0.05))) {
                    MeteorClient.ROTATION.snapAt(targetPos);

                    throwPearl(angle[0], angle[1]);
                }
            }
            case DelayedInstantWebOnly -> {
                MeteorClient.ROTATION.requestRotation(targetPos, 1000f);

                if (MeteorClient.ROTATION.lookingAt(Box.of(targetPos, 0.05, 0.05, 0.05))) {
                    if (MovementFix.inWebs) {
                        MeteorClient.ROTATION.snapAt(targetPos);
                    }

                    throwPearl(angle[0], angle[1]);
                }
            }
        }
    }

    private void throwPearl(float yaw, float pitch) {
        if (MeteorClient.SWAP.beginSwap(Items.ENDER_PEARL, true)) {
            int sequence = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

            mc.getNetworkHandler().sendPacket(
                    new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch));

            deactivate(true);

            MeteorClient.SWAP.endSwap(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        // Custom keypress implementation because.... I don't like binding modules like this? idk
        if (!phaseBind.get().isPressed()) {
            keyUnpressed = true;
        }

        if (phaseBind.get().isPressed() && keyUnpressed
                && !(mc.currentScreen instanceof ChatScreen)) {
            activate();
            keyUnpressed = false;
        }

        update();
    }

    private Vec3d calculateTargetPos() {
        final double X_OFFSET = Math.PI / 13;
        final double Z_OFFSET = Math.PI / 4;

        // cache pos
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        // Calculate position based on the x and z offets
        double x = playerX + MathHelper.clamp(
                toClosest(playerX, Math.floor(playerX) + X_OFFSET, Math.floor(playerX) + Z_OFFSET)
                        - playerX,
                -0.2, 0.2);

        double z = playerZ + MathHelper.clamp(
                toClosest(playerZ, Math.floor(playerZ) + X_OFFSET, Math.floor(playerZ) + Z_OFFSET)
                        - playerZ,
                -0.2, 0.2);

        return new Vec3d(x, mc.player.getY() - 0.5, z);
    }

    private double toClosest(double num, double min, double max) {
        double dmin = num - min;
        double dmax = max - num;

        if (dmax > dmin) {
            return min;
        } else {
            return max;
        }
    }

    public enum SwitchMode {
        SilentHotbar, SilentSwap
    }

    public enum RotateMode {
        Movement, Instant, DelayedInstant, DelayedInstantWebOnly
    }
}
