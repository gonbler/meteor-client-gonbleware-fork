package minegame159.meteorclient.modules.movement;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import minegame159.meteorclient.events.KeyEvent;
import minegame159.meteorclient.events.TickEvent;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.ToggleModule;
import minegame159.meteorclient.settings.BoolSetting;
import minegame159.meteorclient.settings.Setting;
import minegame159.meteorclient.settings.SettingGroup;
import minegame159.meteorclient.utils.KeyAction;

public class AirJump extends ToggleModule {
    public AirJump() {
        super(Category.Movement, "air-jump", "Lets you jump in air.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> maintainY = sgGeneral.add(new BoolSetting.Builder()
            .name("maintain-level")
            .description("Maintains your Y level")
            .defaultValue(false)
            .build()
    );

    private int level = 0;

    @EventHandler
    private final Listener<KeyEvent> onKey = new Listener<>(event -> {
        if ((event.action == KeyAction.Press || event.action == KeyAction.Repeat) && mc.options.keyJump.matchesKey(event.key, 0)) {
            mc.player.jump();
            level = mc.player.getBlockPos().getY();
        }
        if ((event.action == KeyAction.Press || event.action == KeyAction.Repeat) && mc.options.keySneak.matchesKey(event.key, 0)){
            level -= 1;
        }
    });

    @EventHandler
    private final Listener<TickEvent> onTick = new Listener<>(event -> {
        if (maintainY.get() && mc.player.getBlockPos().getY() == level){
            mc.player.jump();
        }
    });
}
