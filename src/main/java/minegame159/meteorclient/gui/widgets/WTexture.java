package minegame159.meteorclient.gui.widgets;

import minegame159.meteorclient.gui.renderer.GuiRenderer;
import net.minecraft.client.texture.AbstractTexture;

public class WTexture extends WWidget {
    private final double width, height;
    private final double rotation;
    private final AbstractTexture texture;

    public WTexture(double width, double height, double rotation, AbstractTexture texture) {
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.texture = texture;
    }

    @Override
    protected void onCalculateSize(GuiRenderer renderer) {
        super.width = width * 2;
        super.height = height * 2;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.texture(x, y, super.width, super.height, rotation, texture);
    }
}
