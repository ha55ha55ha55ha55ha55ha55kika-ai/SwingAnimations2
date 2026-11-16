package com.swinganimations.module;

import net.minecraft.util.math.MathHelper;

/** Общий набор настроек цвета: акцент, радуга или свой RGB. */
public final class ColorSettings {
    private final Setting.Mode mode;
    private final Setting.Num r;
    private final Setting.Num g;
    private final Setting.Num b;

    public ColorSettings(Module m) {
        mode = m.add(new Setting.Mode("color_mode", "Color", "Accent", "Rainbow", "Custom"));
        r = m.add(new Setting.Num("red", "Red", 139f, 0f, 255f, 1f).<Setting.Num>visibleWhen(this::custom));
        g = m.add(new Setting.Num("green", "Green", 185f, 0f, 255f, 1f).<Setting.Num>visibleWhen(this::custom));
        b = m.add(new Setting.Num("blue", "Blue", 255f, 0f, 255f, 1f).<Setting.Num>visibleWhen(this::custom));
    }

    private boolean custom() { return mode.is("Custom"); }

    /** RGB без альфы. offset 0..1 сдвигает оттенок радуги. */
    public int rgb(long now, float offset) {
        if (mode.is("Rainbow")) {
            float hue = (((now / 25L) % 360L) / 360f + offset) % 1f;
            return MathHelper.hsvToRgb(hue, 0.65f, 1f);
        }
        if (mode.is("Custom")) return ((int) r.value << 16) | ((int) g.value << 8) | (int) b.value;
        return com.swinganimations.gui.Ui.ACCENT & 0xFFFFFF;
    }
}
