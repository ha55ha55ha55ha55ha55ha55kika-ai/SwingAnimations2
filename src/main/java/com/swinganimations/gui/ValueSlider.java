package com.swinganimations.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Ползунок в стиле ClickGUI: подпись и значение сверху, тонкая дорожка с акцентом и кружком. */
public final class ValueSlider extends SliderWidget {
    public interface Setter { void set(float v); }

    private final Text label;
    private final String fmt;
    private final float min;
    private final float max;
    private final Setter setter;

    public ValueSlider(int x, int y, int w, int h, Text label, float min, float max,
                       float current, String fmt, Setter setter) {
        super(x, y, w, h, Text.empty(), (current - min) / (max - min));
        this.label = label;
        this.fmt = fmt;
        this.min = min;
        this.max = max;
        this.setter = setter;
    }

    private float real() {
        float v = (float) (min + (max - min) * this.value);
        return Math.round(v * 100f) / 100f;
    }

    @Override
    protected void updateMessage() {
        setMessage(label);
    }

    @Override
    protected void applyValue() {
        setter.set(real());
    }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = isHovered();
        Ui.text(ctx, label, x + 2, y, Ui.TEXT_DIM);
        Ui.textRight(ctx, Text.literal(String.format(Locale.ROOT, fmt, real())), x + w - 2, y, Ui.TEXT);

        int tx = x + 4, tw = w - 8, th = 4, ty = y + h - 7;
        Ui.rect(ctx, tx, ty, tw, th, 2, Ui.FIELD);
        Ui.outline(ctx, tx, ty, tw, th, 2, Ui.OUTLINE);
        int filled = (int) Math.round(tw * this.value);
        if (filled > 0) Ui.rect(ctx, tx, ty, Math.max(th, filled), th, 2, Ui.ACCENT);
        int r = hov ? 4 : 3;
        Ui.rect(ctx, tx + filled - r, ty + th / 2 - r, r * 2, r * 2, r, Ui.KNOB);
    }
}
