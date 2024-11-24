package meteordevelopment.meteorclient.systems.modules.combat;

import org.jetbrains.annotations.NotNull;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.LookAtEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PearlPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<SwitchMode> switchMode =
            sgGeneral.add(new EnumSetting.Builder<SwitchMode>().name("Switch Mode")
                    .description("Which method of switching should be used.")
                    .defaultValue(SwitchMode.Silent).build());
    private final Setting<Integer> pitch = sgGeneral
            .add(new IntSetting.Builder().name("Pitch").description("How deep down to look.")
                    .defaultValue(85).range(-90, 90).sliderRange(0, 90).build());

    private final Setting<Boolean> instaRot =
            sgGeneral.add(new BoolSetting.Builder().name("Instant Rotation")
                    .description("Instantly rotates.").defaultValue(false).build());
    private final Setting<Double> pitchVelocityScaling = sgGeneral.add(new DoubleSetting.Builder()
            .name("Pitch velo scaling").description("How to scale pitch with your velocity")
            .defaultValue(0).range(-5, 5).sliderRange(-5, 5).build());

    private final double[] diagonalAngles = {45, 135, 225, 315};

    private boolean needRotation = false;
    private float rotYaw = 0;
    private float rotPitch = 0;

    public PearlPhase() {
        super(Categories.Combat, "pearl-phase", "Phases into walls using pearls");
    }

    public void onActivate() {
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
                toggle();
                return;
            }
        }


        if (switch (switchMode.get()) {
            case Silent -> !InvUtils.findInHotbar(Items.ENDER_PEARL).found();
        }) {
            toggle();
            return;
        }

        if (mc.options.sneakKey.isPressed()) {
            toggle();
            return;
        }

        // Get base pitch from configuration
        double basePitch = pitch.get();
        double throwYaw = getYaw();

        // Calculate the direction vector for the throw
        double throwDirectionX = -Math.sin(Math.toRadians(throwYaw));
        double throwDirectionZ = Math.cos(Math.toRadians(throwYaw));

        // Player's velocity vector
        Vec3d playerVelocity = mc.player.getVelocity();
        double velocityX = playerVelocity.x;
        double velocityZ = playerVelocity.z;

        // Normalize velocity vector for dot product calculation
        double velocityMagnitude = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        if (velocityMagnitude > 0) { // Avoid division by zero
            velocityX /= velocityMagnitude;
            velocityZ /= velocityMagnitude;
        }

        double dotProduct = (velocityX * throwDirectionX) + (velocityZ * throwDirectionZ);

        double phasePitch = basePitch + dotProduct * pitchVelocityScaling.get();

        needRotation = true;

        rotYaw = (float)throwYaw;
        rotPitch = (float)phasePitch;

        MeteorClient.ROTATION.snapAt(rotYaw, rotPitch, true);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (needRotation) {
            MeteorClient.ROTATION.snapAt(rotYaw, rotPitch, true);
        }
    }

    private void update() {
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
                toggle();
                return;
            }
        }


        if (switch (switchMode.get()) {
            case Silent -> !InvUtils.findInHotbar(Items.ENDER_PEARL).found();
        }) {
            toggle();
            return;
        }

        if (mc.options.sneakKey.isPressed()) {
            toggle();
            return;
        }

        // Get base pitch from configuration
        double basePitch = pitch.get();
        double throwYaw = getYaw();

        // Calculate the direction vector for the throw
        double throwDirectionX = -Math.sin(Math.toRadians(throwYaw));
        double throwDirectionZ = Math.cos(Math.toRadians(throwYaw));

        // Player's velocity vector
        Vec3d playerVelocity = mc.player.getVelocity();
        double velocityX = playerVelocity.x;
        double velocityZ = playerVelocity.z;

        // Normalize velocity vector for dot product calculation
        double velocityMagnitude = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        if (velocityMagnitude > 0) { // Avoid division by zero
            velocityX /= velocityMagnitude;
            velocityZ /= velocityMagnitude;
        }

        double dotProduct = (velocityX * throwDirectionX) + (velocityZ * throwDirectionZ);

        double phasePitch = basePitch + dotProduct * pitchVelocityScaling.get();

        rotYaw = (float)throwYaw;
        rotPitch = (float)phasePitch;

        if (MeteorClient.ROTATION.inFov((float)throwYaw, (float)phasePitch, 3f)) {
            throwPearl(throwYaw, phasePitch);
        }
    }

    private void throwPearl(double throwYaw, double phasePitch) {
        switch (switchMode.get()) {
            case Silent -> {
                InvUtils.swap(InvUtils.findInHotbar(Items.ENDER_PEARL).slot(), true);
            }
        }

        int sequence = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence,
                (float) throwYaw, (float) phasePitch));

        needRotation = false;

        toggle();

        switch (switchMode.get()) {
            case Silent -> InvUtils.swapBack();
        }
    }

    @EventHandler()
    public void onRotate(LookAtEvent event) {
        if (!isActive()) {
            return;
        }

        if (needRotation) {
            event.setRotation(rotYaw, rotPitch, 180, 20);
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        update();
    }

    private double getYaw() {
        if (mc.player == null)
            return 0;

        // Player's current yaw
        float playerYaw = mc.player.getYaw();

        // Movement inputs
        boolean forward = mc.options.forwardKey.isPressed();
        boolean backward = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();

        // Determine movement direction
        double moveYaw = playerYaw; // Default to forward
        if (forward && left) {
            moveYaw -= 45; // Forward-left
        } else if (forward && right) {
            moveYaw += 45; // Forward-right
        } else if (backward && left) {
            moveYaw -= 135; // Backward-left
        } else if (backward && right) {
            moveYaw += 135; // Backward-right
        } else if (forward) {
            moveYaw = playerYaw; // Forward
        } else if (backward) {
            moveYaw += 180; // Backward
        } else if (left) {
            moveYaw -= 90; // Left
        } else if (right) {
            moveYaw += 90; // Right
        }

        // Normalize yaw to 0-360 degrees
        moveYaw = (moveYaw + 360) % 360;

        moveYaw = getClosestAngle(moveYaw, diagonalAngles);

        // Target position to phase to
        double targetX = mc.player.getX() - Math.sin(Math.toRadians(moveYaw));
        double targetZ = mc.player.getZ() + Math.cos(Math.toRadians(moveYaw));
        Vec3d targetPos =
                new Vec3d(Math.floor(targetX) + 0.5, mc.player.getY(), Math.floor(targetZ) + 0.5);

        return Rotations.getYaw(targetPos);
    }

    // Utility to find the closest angle in a set
    private double getClosestAngle(double angle, double[] angles) {
        double closest = angles[0];
        double minDiff = Math.abs(angle - closest);
        for (double candidate : angles) {
            double diff = Math.abs(angle - candidate);
            if (diff < minDiff) {
                closest = candidate;
                minDiff = diff;
            }
        }
        return closest;
    }

    public enum SwitchMode {
        Silent
    }
}
