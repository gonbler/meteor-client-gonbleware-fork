package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.LookAtEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
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
                    .defaultValue(SwitchMode.Silent).build());

    private final Setting<Keybind> phaseBind = sgGeneral.add(new KeybindSetting.Builder()
            .name("key-bind").description("Phase on keybind press").build());

    private final Setting<Double> movementPredictionFactor =
            sgGeneral.add(new DoubleSetting.Builder().name("movement-prediction-factor")
                    .description("How far to predict your movement ahead").defaultValue(0)
                    .range(-5, 5).sliderRange(-5, 5).build());

    private boolean needRotation = false;
    private boolean active = false;
    private boolean keyUnpressed = false;

    private int rotationSetTicks = 0;

    public PearlPhase() {
        super(Categories.Combat, "pearl-phase", "Phases into walls using pearls");
    }

    private void activate() {
        active = true;

        if (mc.player == null || mc.world == null)
            return;

        Box boundingBox = mc.player.getBoundingBox().shrink(0.05, 0.1, 0.05);
        double feetY = mc.player.getY();

        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        for (BlockPos pos : BlockPos.iterate((int) Math.floor(feetBox.minX),
                (int) Math.floor(feetBox.minY), (int) Math.floor(feetBox.minZ),
                (int) Math.floor(feetBox.maxX), (int) Math.floor(feetBox.maxY),
                (int) Math.floor(feetBox.maxZ))) {
            Block block = mc.world.getBlockState(pos).getBlock();

            if (block.equals(Blocks.OBSIDIAN) || block.equals(Blocks.BEDROCK)) {
                deactivate(false);
                return;
            }
        }

        if (switch (switchMode.get()) {
            case Silent -> !InvUtils.findInHotbar(Items.ENDER_PEARL).found();
        }) {
            deactivate(false);
            return;
        }

        if (mc.options.sneakKey.isPressed()) {
            deactivate(false);
            return;
        }

        rotationSetTicks = 0;
        MeteorClient.ROTATION.snapAt(calculateTargetPos(), true);
        needRotation = true;
    }

    private void deactivate(boolean phased) {
        active = false;

        if (phased) {
            info("Phased");
        } else {
            needRotation = false;
        }
    }

    private void update() {
        if (!phaseBind.get().isPressed()) {
            keyUnpressed = true;
        }

        if (phaseBind.get().isPressed() && keyUnpressed) {
            activate();
            keyUnpressed = false;
        }

        if (!active) {
            return;
        }

        Box boundingBox = mc.player.getBoundingBox().shrink(0.05, 0.1, 0.05);
        double feetY = mc.player.getY();

        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        for (BlockPos pos : BlockPos.iterate((int) Math.floor(feetBox.minX),
                (int) Math.floor(feetBox.minY), (int) Math.floor(feetBox.minZ),
                (int) Math.floor(feetBox.maxX), (int) Math.floor(feetBox.maxY),
                (int) Math.floor(feetBox.maxZ))) {
            Block block = mc.world.getBlockState(pos).getBlock();

            if (block.equals(Blocks.OBSIDIAN) || block.equals(Blocks.BEDROCK)) {
                deactivate(false);
                return;
            }
        }


        if (switch (switchMode.get()) {
            case Silent -> !InvUtils.findInHotbar(Items.ENDER_PEARL).found();
        }) {
            deactivate(false);
            return;
        }

        if (mc.options.sneakKey.isPressed()) {
            deactivate(false);
            return;
        }

        needRotation = true;

        float[] dir = MeteorClient.ROTATION.getRotation(calculateTargetPos());
        if (rotationSetTicks > 1) {
            throwPearl(dir[0], dir[1]);
            rotationSetTicks = 0;
        }
    }

    private void throwPearl(float yaw, float pitch) {
        switch (switchMode.get()) {
            case Silent -> {
                InvUtils.swap(InvUtils.findInHotbar(Items.ENDER_PEARL).slot(), true);
            }
        }

        int sequence = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

        mc.getNetworkHandler()
                .sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch));

        deactivate(true);

        switch (switchMode.get()) {
            case Silent -> InvUtils.swapBack();
        }
    }

    @EventHandler()
    public void onRotate(LookAtEvent event) {
        if (needRotation) {
            event.setTarget(calculateTargetPos(), 100f);
            rotationSetTicks++;

            if (!active) {
                needRotation = false;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        update();
    }

    private Vec3d calculateTargetPos() {
        final double X_OFFSET = Math.PI / 13;
        final double Z_OFFSET = Math.PI / 4;

        // Get the player's current velocity
        Vec3d velocity = mc.player.getVelocity();

        // Predict future player position
        double predictedX = mc.player.getX() + velocity.x * movementPredictionFactor.get();
        double predictedZ = mc.player.getZ() + velocity.z * movementPredictionFactor.get();

        // Calculate target Y position
        double y = mc.player.getY() - 0.5;

        // Calculate target X position
        double x = predictedX
                + MathHelper.clamp(toClosest(predictedX, Math.floor(predictedX) + X_OFFSET,
                        Math.floor(predictedX) + Z_OFFSET) - predictedX, -0.2, 0.2);

        // Calculate target Z position
        double z = predictedZ
                + MathHelper.clamp(toClosest(predictedZ, Math.floor(predictedZ) + X_OFFSET,
                        Math.floor(predictedZ) + Z_OFFSET) - predictedZ, -0.2, 0.2);

        return new Vec3d(x, y, z);
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
        Silent
    }
}
