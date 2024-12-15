package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;

public class GrimDisabler extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<HorizontalDisablerMode> horizontalDisblerMode = sgGeneral
            .add(new EnumSetting.Builder<HorizontalDisablerMode>().name("horizontal-disabler-mode")
                    .description("Determines mode of disabler for horizontal movement")
                    .defaultValue(HorizontalDisablerMode.YawOverflow).build());

    private final Setting<Keybind> toggleHorizontalDisabler =
            sgGeneral.add(new KeybindSetting.Builder().name("toggle-horizontal-disabler")
                    .description("Keybind to toggle the horizontal disabler")
                    .visible(() -> horizontalDisblerMode.get() != HorizontalDisablerMode.None)
                    .build());

    private final Setting<Boolean> horizontalDisablerActive =
            sgGeneral.add(new BoolSetting.Builder().name("horizontal-disabler-active")
                    .description("Determines if the horizontal disabler is active or not")
                    .defaultValue(true).build());

    private boolean lastToggleConstant = false;

    public GrimDisabler() {
        super(Categories.Movement, "grim-disabler",
                "Disables the Grim anti-cheat. Allows use of modules such as Speed and ClickTp");
    }

    @EventHandler
    public void onPreMove(SendMovementPacketsEvent.Pre event) {
        // Todo, things?
    }

    private void update() {
        if (toggleHorizontalDisabler.get().isPressed() && !lastToggleConstant) {
            horizontalDisablerActive.set(!horizontalDisablerActive.get());
        }
        lastToggleConstant = toggleHorizontalDisabler.get().isPressed();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        update();
    }

    public boolean shouldSetYawOverflowRotation() {
        return isActive() && horizontalDisblerMode.get() == HorizontalDisablerMode.YawOverflow
                && horizontalDisablerActive.get() && !mc.player.isFallFlying();
    }

    @Override
    public String getInfoString() {
        if (horizontalDisblerMode.get() == HorizontalDisablerMode.None) {
            return "";
        }

        if (!horizontalDisablerActive.get()) {
            return "";
        }

        return String.format("%s", horizontalDisblerMode.get().toString());
    }

    public enum HorizontalDisablerMode {
        None, YawOverflow
    }
}
