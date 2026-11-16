package com.swinganimations.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Палитра и примитивы в стиле тёмного ClickGUI: полупрозрачные скруглённые панели, акцент голубой. */
public final class Ui {
    public static int ACCENT = 0xFF8BB9FF;
    public static int ACCENT_BG = 0x2D8BB9FF;
    public static int ACCENT_LINE = 0x918BB9FF;
    public static int KNOB = 0xFFAEDCFF;
    public static final int BG = 0xE60D0D0D;
    public static final int FIELD = 0xD21C1C1C;
    public static final int FIELD_HOVER = 0xD2282828;
    public static final int OUTLINE = 0x12FFFFFF;
    public static final int SEP = 0x20FFFFFF;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xB4BEC3CD;
    public static final int SWITCH_OFF = 0xFF3A3A3A;

    private Ui() {}

    public static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    private static final int[][] INSETS = new int[9][];

    /** Сдвиг по X для каждой строки угла радиуса r (считается один раз, без sqrt в кадре). */
    private static int[] insets(int r) {
        int[] t = INSETS[r];
        if (t == null) {
            t = new int[r];
            for (int i = 0; i < r; i++) {
                double dy = r - i - 0.5;
                t[i] = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            }
            INSETS[r] = t;
        }
        return t;
    }

    /** Скруглённый прямоугольник: строки с одинаковым сдвигом склеены в один fill (радиус до 8). */
    public static void rect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(8, Math.min(r, Math.min(w, h) / 2));
        if (r <= 0) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        int[] in = insets(r);
        int top = 0;
        while (top < r && in[top] > 0) top++;
        int s = 0;
        while (s < top) {
            int e = s + 1;
            while (e < top && in[e] == in[s]) e++;
            c.fill(x + in[s], y + s, x + w - in[s], y + e, color);
            c.fill(x + in[s], y + h - e, x + w - in[s], y + h - s, color);
            s = e;
        }
        c.fill(x, y + top, x + w, y + h - top, color);
    }

    /** Тонкая скруглённая обводка (вертикальные куски угла склеены). */
    public static void outline(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.min(8, Math.min(r, Math.min(w, h) / 2));
        c.fill(x + r, y, x + w - r, y + 1, color);
        c.fill(x + r, y + h - 1, x + w - r, y + h, color);
        c.fill(x, y + r, x + 1, y + h - r, color);
        c.fill(x + w - 1, y + r, x + w, y + h - r, color);
        if (r <= 0) return;
        int[] in = insets(r);
        int s = 0;
        while (s < r) {
            int e = s + 1;
            while (e < r && in[e] == in[s]) e++;
            int v = in[s];
            c.fill(x + v, y + s, x + v + 1, y + e, color);
            c.fill(x + w - v - 1, y + s, x + w - v, y + e, color);
            c.fill(x + v, y + h - e, x + v + 1, y + h - s, color);
            c.fill(x + w - v - 1, y + h - e, x + w - v, y + h - s, color);
            s = e;
        }
    }

    public static void text(DrawContext c, Text t, int x, int y, int color) {
        c.drawText(tr(), t, x, y, color, false);
    }

    public static void textRight(DrawContext c, Text t, int right, int y, int color) {
        c.drawText(tr(), t, right - tr().getWidth(t), y, color, false);
    }
}
