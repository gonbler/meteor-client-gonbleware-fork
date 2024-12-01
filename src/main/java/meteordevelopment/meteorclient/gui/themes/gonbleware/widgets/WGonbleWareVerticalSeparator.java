/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.gonbleware.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.gonbleware.GonbleWareGuiTheme;
import meteordevelopment.meteorclient.gui.themes.gonbleware.GonbleWareWidget;
import meteordevelopment.meteorclient.gui.widgets.WVerticalSeparator;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WGonbleWareVerticalSeparator extends WVerticalSeparator implements GonbleWareWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        GonbleWareGuiTheme theme = theme();
        Color colorEdges = theme.separatorEdges.get();
        Color colorCenter = theme.separatorCenter.get();

        double s = theme.scale(1);
        double offsetX = Math.round(width / 2.0);

        renderer.quad(x + offsetX, y, s, height / 2, colorEdges, colorEdges, colorCenter, colorCenter);
        renderer.quad(x + offsetX, y + height / 2, s, height / 2, colorCenter, colorCenter, colorEdges, colorEdges);
    }
}
