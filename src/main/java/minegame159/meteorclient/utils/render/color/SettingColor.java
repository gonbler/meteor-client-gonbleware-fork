package minegame159.meteorclient.utils.render.color;

import net.minecraft.nbt.CompoundTag;

public class SettingColor extends Color {
    private static final float[] hsb = new float[3];

    public double rainbowSpeed;

    public SettingColor() {
        super();
    }

    public SettingColor(int r, int g, int b) {
        super(r, g, b);
    }

    public SettingColor(int r, int g, int b, int a) {
        super(r, g, b, a);
    }

    public SettingColor(SettingColor color) {
        super(color);

        this.rainbowSpeed = color.rainbowSpeed;
    }

    public void update() {
        if (rainbowSpeed > 0) {
            java.awt.Color.RGBtoHSB(r, g, b, hsb);
            int c = java.awt.Color.HSBtoRGB(hsb[0] + (float) rainbowSpeed, hsb[1], hsb[2]);

            r = Color.toRGBAR(c);
            g = Color.toRGBAG(c);
            b = Color.toRGBAB(c);
        }
    }

    @Override
    public void set(Color value) {
        super.set(value);

        if (value instanceof SettingColor) {
            rainbowSpeed = ((SettingColor) value).rainbowSpeed;
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();

        tag.putDouble("rainbowSpeed", rainbowSpeed);

        return tag;
    }

    @Override
    public SettingColor fromTag(CompoundTag tag) {
        super.fromTag(tag);

        rainbowSpeed = tag.getDouble("rainbowSpeed");

        return this;
    }
}
