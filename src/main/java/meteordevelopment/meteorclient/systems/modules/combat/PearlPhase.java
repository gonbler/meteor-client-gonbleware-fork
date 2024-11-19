package meteordevelopment.meteorclient.systems.modules.combat;

import org.jetbrains.annotations.NotNull;
import baritone.api.utils.RotationUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
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
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
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

    public PearlPhase() {
        super(Categories.Combat, "pearl-phase", "Phases into walls using pearls");
    }

    public void onActivate() {

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null)
            return;

        if (switch (switchMode.get()) {
            case Silent -> !InvUtils.findInHotbar(Items.ENDER_PEARL).found();
        })
            return;

        if (instaRot.get()) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(getYaw(),
                    pitch.get(), mc.player.isOnGround()));
        }

        switch (switchMode.get()) {
            case Silent -> {
                InvUtils.swap(InvUtils.findInHotbar(Items.ENDER_PEARL).slot(), true);
            }
        }

        double[] dir = forward(0.5);
        BlockPos block = BlockPos.ofFloored(mc.player.getX() + dir[0], mc.player.getY(),
                mc.player.getZ() + dir[1]);

        if (mc.options.sneakKey.isPressed())
            return;

        float[] angle = calculateAngle(mc.player.getEyePos(), block.toCenterPos());

        mc.player.setYaw(angle[0]);
        mc.player.setPitch(pitch.get());

        int s = mc.world.getPendingUpdateManager().incrementSequence().getSequence();
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, s, mc.player.getYaw(), mc.player.getPitch()));

        toggle();

        switch (switchMode.get()) {
            case Silent -> InvUtils.swapBack();
        }
    }

    private int getYaw() {
        return (int) Math.round(Rotations.getYaw(new Vec3d(Math.floor(mc.player.getX()) + 0.5, 0,
                Math.floor(mc.player.getZ()) + 0.5))) + 180;
    }

    public double[] forward(final double d) {
        float f = mc.player.input.movementForward;
        float f2 = mc.player.input.movementSideways;
        float f3 = mc.player.getYaw();
        if (f != 0.0f) {
            if (f2 > 0.0f) {
                f3 += ((f > 0.0f) ? -45 : 45);
            } else if (f2 < 0.0f) {
                f3 += ((f > 0.0f) ? 45 : -45);
            }
            f2 = 0.0f;
            if (f > 0.0f) {
                f = 1.0f;
            } else if (f < 0.0f) {
                f = -1.0f;
            }
        }
        final double d2 = Math.sin(Math.toRadians(f3 + 90.0f));
        final double d3 = Math.cos(Math.toRadians(f3 + 90.0f));
        final double d4 = f * d * d3 + f2 * d * d2;
        final double d5 = f * d * d2 - f2 * d * d3;
        return new double[] {d4, d5};
    }

    public static float[] calculateAngle(Vec3d from, Vec3d to) {
        double difX = to.x - from.x;
        double difY = (to.y - from.y) * -1.0;
        double difZ = to.z - from.z;
        double dist = MathHelper.sqrt((float) (difX * difX + difZ * difZ));

        float yD = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
        float pD = (float) MathHelper
                .clamp(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difY, dist))), -90f, 90f);

        return new float[] {yD, pD};
    }

    public enum SwitchMode {
        Silent
    }
}
