package minegame159.meteorclient.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import minegame159.meteorclient.events.TookDamageEvent;
import minegame159.meteorclient.gui.widgets.WLabel;
import minegame159.meteorclient.gui.widgets.WWidget;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.ToggleModule;
import minegame159.meteorclient.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DeathPosition extends ToggleModule {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    private final WLabel label = new WLabel("No latest death");

    public DeathPosition() {
        super(Category.Player, "death-position", "Sends to your chat where you died.");
    }

    @EventHandler
    private final Listener<TookDamageEvent> onTookDamage = new Listener<>(event -> {
        if (event.entity.getUuid() != null && event.entity.getUuid().equals(mc.player.getUuid()) && event.entity.getHealth() <= 0) {
            label.setText(String.format("Latest death: %.1f, %.1f, %.1f", mc.player.x, mc.player.y, mc.player.z));

            Utils.sendMessage("#yellowDied at #blue%.1f#yellow, #blue%.1f#yellow, #blue%.1f#yellow on #blue%s.", mc.player.x, mc.player.y, mc.player.z, dateFormat.format(new Date()));
        }
    });

    @Override
    public WWidget getWidget() {
        return label;
    }
}
