/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.gonbleware.widgets.pressable;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.gonbleware.GonbleWareWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;

public class WGonbleWareTriangle extends WTriangle implements GonbleWareWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE, theme().backgroundColor.get(pressed, mouseOver));
    }
}
