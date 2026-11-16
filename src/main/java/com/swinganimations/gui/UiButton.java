package com.swinganimations.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Кнопка в стиле ClickGUI: вкладка, «чип» с значением, переключатель или обычная. */
public final class UiButton extends ClickableWidget {
    public enum Kind { TAB, CHIP, TOGGLE, PLAIN }

    private final Kind kind;
    private final Supplier<Text> label;
    private final Supplier<Text> value;
    private final BooleanSupplier state;
    private final Runnable action;

    public UiButton(int x, int y, int w, int h, Kind kind, Supplier<Text> label,
                    Supplier<Text> value, BooleanSupplier state, Runnable action) {
        super(x, y, w, h, Text.empty());
        this.kind = kind;
        this.label = label;
        this.value = value;
        this.state = state;
        this.action = action;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        action.run();
    }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = isHovered();
        int ty = y + (h - 8) / 2;
        Text l = label.get();

        switch (kind) {
            case TAB -> {
                boolean sel = state != null && state.getAsBoolean();
                if (sel) {
                    Ui.rect(ctx, x, y, w, h, 4, Ui.ACCENT_BG);
                    Ui.rect(ctx, x + w / 2 - 10, y + h - 2, 20, 2, 1, Ui.ACCENT);
                } else if (hov) {
                    Ui.rect(ctx, x, y, w, h, 4, 0x14FFFFFF);
                }
                int tw = Ui.tr().getWidth(l);
                Ui.text(ctx, l, x + (w - tw) / 2, ty - 1, sel ? Ui.ACCENT : Ui.TEXT_DIM);
            }
            case PLAIN -> {
                Ui.rect(ctx, x, y, w, h, 4, hov ? Ui.FIELD_HOVER : Ui.FIELD);
                Ui.outline(ctx, x, y, w, h, 4, hov ? Ui.ACCENT_LINE : Ui.OUTLINE);
                int tw = Ui.tr().getWidth(l);
                Ui.text(ctx, l, x + (w - tw) / 2, ty, Ui.TEXT);
            }
            case CHIP -> {
                Ui.rect(ctx, x, y, w, h, 4, hov ? Ui.FIELD_HOVER : Ui.FIELD);
                Ui.outline(ctx, x, y, w, h, 4, Ui.OUTLINE);
                Ui.text(ctx, l, x + 6, ty, Ui.TEXT_DIM);
                if (value != null) Ui.textRight(ctx, value.get(), x + w - 6, ty, Ui.ACCENT);
            }
            case TOGGLE -> {
                boolean on = state != null && state.getAsBoolean();
                Ui.rect(ctx, x, y, w, h, 4, hov ? Ui.FIELD_HOVER : Ui.FIELD);
                Ui.outline(ctx, x, y, w, h, 4, Ui.OUTLINE);
                Ui.text(ctx, l, x + 6, ty, on ? Ui.TEXT : Ui.TEXT_DIM);
                int sw = 22, sh = 10;
                int sx = x + w - 6 - sw, sy = y + (h - sh) / 2;
                Ui.rect(ctx, sx, sy, sw, sh, 5, on ? Ui.ACCENT : Ui.SWITCH_OFF);
                int kx = on ? sx + sw - sh + 1 : sx + 1;
                Ui.rect(ctx, kx, sy + 1, sh - 2, sh - 2, 4, on ? Ui.TEXT : Ui.TEXT_DIM);
            }
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // без озвучки
    }
}
