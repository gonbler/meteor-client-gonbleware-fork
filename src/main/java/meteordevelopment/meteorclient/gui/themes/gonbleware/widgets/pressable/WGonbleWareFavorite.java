/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.gonbleware.widgets.pressable;

import meteordevelopment.meteorclient.gui.themes.gonbleware.GonbleWareWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WFavorite;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WGonbleWareFavorite extends WFavorite implements GonbleWareWidget {
    public WGonbleWareFavorite(boolean checked) {
        super(checked);
    }

    @Override
    protected Color getColor() {
        return theme().favoriteColor.get();
    }
}
