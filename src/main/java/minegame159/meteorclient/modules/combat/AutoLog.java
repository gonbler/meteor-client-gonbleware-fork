package minegame159.meteorclient.modules.combat;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import minegame159.meteorclient.accountsfriends.FriendManager;
import minegame159.meteorclient.events.TickEvent;
import minegame159.meteorclient.events.TookDamageEvent;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.ToggleModule;
import minegame159.meteorclient.settings.BoolSetting;
import minegame159.meteorclient.settings.IntSetting;
import minegame159.meteorclient.settings.Setting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.text.LiteralText;

public class AutoLog extends ToggleModule {
    private Setting<Integer> health = addSetting(new IntSetting.Builder()
            .name("health")
            .description("Disconnects when health is lower or equal to this value.")
            .defaultValue(6)
            .min(0)
            .max(20)
            .sliderMax(20)
            .build()
    );

    private Setting<Boolean> onlyTrusted = addSetting(new BoolSetting.Builder()
            .name("only-trusted")
            .description("Disconnects when non-trusted player appears in your render distance.")
            .defaultValue(false)
            .build()
    );

    private long lastLog = System.currentTimeMillis();
    private boolean shouldLog = false;

    public AutoLog() {
        super(Category.Combat, "auto-log", "Automatically disconnects when low on health.");
    }

    @EventHandler
    private Listener<TookDamageEvent> onTookDamage = new Listener<>(event -> {
        if (!shouldLog && mc.player != null && event.entity.getUuid().equals(mc.player.getUuid()) && event.entity.getHealth() <= health.get()) {
            shouldLog = true;
            lastLog = System.currentTimeMillis();
        }
    });

    @EventHandler
    private Listener<TickEvent> onTick = new Listener<>(event -> {
        if (shouldLog && System.currentTimeMillis() - lastLog <= 1000) {
            shouldLog = false;
            mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(new LiteralText("Health was lower than " + health.get())));
        }

        if (onlyTrusted.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity && !FriendManager.INSTANCE.isTrusted((PlayerEntity) entity)) {
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(new LiteralText("Non-trusted player appeared in your render distance")));
                    break;
                }
            }
        }
    });
}
