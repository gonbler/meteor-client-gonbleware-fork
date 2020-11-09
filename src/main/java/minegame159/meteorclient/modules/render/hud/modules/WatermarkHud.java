package minegame159.meteorclient.modules.render.hud.modules;

import minegame159.meteorclient.Config;
import minegame159.meteorclient.modules.render.hud.HUD;

public class WatermarkHud extends DoubleTextHudModule {
    public WatermarkHud(HUD hud) {
        super(hud, "watermark", "Displays a meteor watermark.", "Meteor Client ");
    }

    @Override
    protected String getRight() {
        return Config.INSTANCE.version.getOriginalString();
    }
}
