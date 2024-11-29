package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.PlayerJumpEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerTravelEvent;
import meteordevelopment.meteorclient.events.entity.player.UpdatePlayerVelocity;
import meteordevelopment.meteorclient.events.input.KeyboardInputEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.util.math.MathHelper;

public class MovementFix extends Module {
    public static MovementFix MOVE_FIX;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> grim = sgGeneral.add(new BoolSetting.Builder().name("grim")
            .description("Mode for grim.").defaultValue(true).build());

    private final Setting<Boolean> travel = sgGeneral.add(new BoolSetting.Builder().name("travel")
            .description("Fixes rotation for travel events.").defaultValue(true).build());

    public final Setting<UpdateMode> updateMode =
            sgGeneral.add(new EnumSetting.Builder<UpdateMode>().name("update-mode")
                    .description("When to fix movement.").defaultValue(UpdateMode.Packet).build());

    public static float fixYaw;
    public static float fixPitch;

    private float prevYaw;
    private float prevPitch;

    public MovementFix() {
        super(Categories.Movement, "movement-fix", "Fixes movement for rotations");
        MOVE_FIX = this;
    }

    @EventHandler
    public void onPreJump(PlayerJumpEvent.Pre e) {
        if (!grim.get() || mc.player.isRiding()) {
            return;
        }

        prevYaw = mc.player.getYaw();
        prevPitch = mc.player.getPitch();
        mc.player.setYaw(fixYaw);
        mc.player.setPitch(fixPitch);
    }

    @EventHandler
    public void onPostJump(PlayerJumpEvent.Post e) {
        if (!grim.get() || mc.player.isRiding()) {
            return;
        }

        mc.player.setYaw(prevYaw);
        mc.player.setPitch(prevPitch);
    }

    @EventHandler
    public void onPreTravel(PlayerTravelEvent.Pre e) {
        if (!grim.get() || !travel.get() || mc.player.isRiding()) {
            return;
        }
        prevYaw = mc.player.getYaw();
        prevPitch = mc.player.getPitch();
        mc.player.setYaw(fixYaw);
        mc.player.setPitch(fixPitch);
    }

    @EventHandler
    public void onPostTravel(PlayerTravelEvent.Post e) {
        if (!grim.get() || !travel.get() || mc.player.isRiding()) {
            return;
        }

        mc.player.setYaw(prevYaw);
        mc.player.setPitch(prevPitch);
    }

    @EventHandler
    public void onPlayerMove(UpdatePlayerVelocity event) {
        if (!grim.get() || mc.player.isRiding())
            return;

        event.cancel();

        event.setVelocity(PlayerUtils.movementInputToVelocity(event.getMovementInput(),
                event.getSpeed(), fixYaw));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onKeyInput(KeyboardInputEvent e) {
        if (!grim.get() || mc.player.isRiding() || Modules.get().get(Freecam.class).isActive()
                || mc.player.isFallFlying())
            return;

        float mF = mc.player.input.movementForward;
        float mS = mc.player.input.movementSideways;
        float delta = (mc.player.getYaw() - fixYaw) * MathHelper.RADIANS_PER_DEGREE;
        float cos = MathHelper.cos(delta);
        float sin = MathHelper.sin(delta);
        mc.player.input.movementSideways = Math.round(mS * cos - mF * sin);
        mc.player.input.movementForward = Math.round(mF * cos + mS * sin);
    }

    public enum UpdateMode {
        Packet, Mouse, Both
    }
}
