package com.swinganimations.gui;

import com.swinganimations.module.ThemeModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/** Общие примитивы HUD: шрифт темы, панели, полосы, цвет здоровья. Тексты и их ширина кэшируются. */
public final class Hud {
    public static final Identifier FONT = Identifier.of("swinganimations", "hud");

    private static final class Cached {
        final Text text;
        final int width;

        Cached(Text text, int width) { this.text = text; this.width = width; }
    }

    private static final Map<String, Cached> CACHE = new HashMap<>(256);
    private static boolean cacheFont;

    private Hud() {}

    private static Cached cached(String s) {
        boolean font = ThemeModule.cleanFont();
        if (font != cacheFont) {
            CACHE.clear();
            cacheFont = font;
        }
        Cached c = CACHE.get(s);
        if (c == null) {
            if (CACHE.size() > 512) CACHE.clear();
            MutableText m = Text.literal(s);
            if (font) m.setStyle(Style.EMPTY.withFont(FONT));
            c = new Cached(m, Ui.tr().getWidth(m));
            CACHE.put(s, c);
        }
        return c;
    }

    /** Сбросить кэш (после смены шрифта/перезагрузки ресурсов). */
    public static void invalidate() { CACHE.clear(); }

    public static Text t(String s) { return cached(s).text; }

    public static Text t(Text base) {
        if (!ThemeModule.cleanFont()) return base;
        return Text.empty().append(base).setStyle(Style.EMPTY.withFont(FONT));
    }

    public static int w(String s) { return cached(s).width; }

    public static int w(Text x) { return Ui.tr().getWidth(t(x)); }

    public static void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(Ui.tr(), cached(s).text, x, y, color, false);
    }

    public static void text(DrawContext c, Text s, int x, int y, int color) {
        c.drawText(Ui.tr(), t(s), x, y, color, false);
    }

    public static void textRight(DrawContext c, String s, int right, int y, int color) {
        Cached k = cached(s);
        c.drawText(Ui.tr(), k.text, right - k.width, y, color, false);
    }

    public static void textRight(DrawContext c, Text s, int right, int y, int color) {
        text(c, s, right - w(s), y, color);
    }

    public static int bg() { return ThemeModule.matte() ? Ui.BG : 0xB40D0D0D; }

    public static void panel(DrawContext c, int x, int y, int w, int h) {
        Ui.rect(c, x, y, w, h, 5, bg());
        Ui.outline(c, x, y, w, h, 5, Ui.OUTLINE);
    }

    /** Стеклянная «таблетка» (полностью скруглённая плашка) для компактных HUD-элементов. */
    public static void chip(DrawContext c, int x, int y, int w, int h) {
        int r = h / 2;
        Ui.rect(c, x, y, w, h, r, ThemeModule.matte() ? Ui.BG : 0x82181A2C);
        Ui.outline(c, x, y, w, h, r, 0x36FFFFFF);
    }

    /** Квадратная иконка 48x48, нарисованная в size x size (текстура с мягким сглаживанием). */
    public static void icon(DrawContext c, Identifier id, int x, int y, int size) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, size, size, 48, 48, 48, 48);
    }

    /** Простая полоса: тёмная подложка + заливка frac (0..1). */
    public static void bar(DrawContext c, int x, int y, int w, int h, float frac, int argb) {
        frac = MathHelper.clamp(frac, 0f, 1f);
        c.fill(x, y, x + w, y + h, 0x66000000);
        int fw = Math.round(w * frac);
        if (fw > 0) c.fill(x, y, x + fw, y + h, argb);
    }

    /** Зелёный → жёлтый → красный по доле здоровья. */
    public static int hpColor(float f) {
        f = MathHelper.clamp(f, 0f, 1f);
        int r = f < 0.5f ? 255 : (int) (255 * (1f - f) * 2f);
        int g = f > 0.5f ? 255 : (int) (255 * f * 2f);
        return 0xFF000000 | (r << 16) | (g << 8) | 0x30;
    }

    private static final int[] HEART = {0b0110110, 0b1111111, 0b1111111, 0b0111110, 0b0011100, 0b0001000};
    /** Горизонтальные отрезки сердечка {строка, x0, x1}: 7 fill вместо 30 одиночных пикселей. */
    private static final int[][] HEART_RUNS;

    static {
        java.util.List<int[]> runs = new java.util.ArrayList<>();
        for (int r = 0; r < HEART.length; r++) {
            int col = 0;
            while (col < 7) {
                if ((HEART[r] & (1 << (6 - col))) == 0) {
                    col++;
                    continue;
                }
                int e = col;
                while (e < 7 && (HEART[r] & (1 << (6 - e))) != 0) e++;
                runs.add(new int[]{r, col, e});
                col = e;
            }
        }
        HEART_RUNS = runs.toArray(new int[0][]);
    }

    /** Пиксельное сердечко 7×6. */
    public static void heart(DrawContext c, int x, int y, int argb) {
        for (int[] run : HEART_RUNS) c.fill(x + run[1], y + run[0], x + run[2], y + run[0] + 1, argb);
    }
}
