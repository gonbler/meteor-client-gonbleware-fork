/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.gonbleware.widgets.pressable;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.gonbleware.GonbleWareGuiTheme;
import meteordevelopment.meteorclient.gui.themes.gonbleware.GonbleWareWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import net.minecraft.util.math.MathHelper;

public class WGonbleWareCheckbox extends WCheckbox implements GonbleWareWidget {
    private double animProgress;

    public WGonbleWareCheckbox(boolean checked) {
        super(checked);
        animProgress = checked ? 1 : 0;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        GonbleWareGuiTheme theme = theme();

        animProgress += (checked ? 1 : -1) * delta * 14;
        animProgress = MathHelper.clamp(animProgress, 0, 1);

        renderBackground(renderer, this, pressed, mouseOver);

        if (animProgress > 0) {
            double cs = (width - theme.scale(2)) / 1.75 * animProgress;
            renderer.quad(x + (width - cs) / 2, y + (height - cs) / 2, cs, cs, theme.checkboxColor.get());
        }
    }
}
