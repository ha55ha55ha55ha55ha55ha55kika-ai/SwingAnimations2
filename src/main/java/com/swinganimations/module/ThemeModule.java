package com.swinganimations.module;

import com.swinganimations.gui.Ui;
import com.swinganimations.render.Batch;

/**
 * Редактор темы: пресеты цвета акцента или свой RGB, шрифт HUD, размытие фона ClickGUI, стиль панелей.
 * Всегда включён; значения применяются каждый кадр через sync().
 */
public final class ThemeModule extends Module {
    private static final String[] NAMES = {"Ice", "Purple", "Gold", "Mint", "Rose", "Crimson", "Custom"};
    private static final int[] RGB = {0x8BB9FF, 0xB65CFF, 0xFFC93C, 0x5CE0B0, 0xFF7FB0, 0xFF3A4F, 0};

    private final Setting.Mode preset = add(new Setting.Mode("preset", "Accent preset", NAMES));
    private final Setting.Num red = add(new Setting.Num("red", "Red", 139f, 0f, 255f, 1f).<Setting.Num>visibleWhen(this::custom));
    private final Setting.Num green = add(new Setting.Num("green", "Green", 185f, 0f, 255f, 1f).<Setting.Num>visibleWhen(this::custom));
    private final Setting.Num blue = add(new Setting.Num("blue", "Blue", 255f, 0f, 255f, 1f).<Setting.Num>visibleWhen(this::custom));
    private final Setting.Mode font = add(new Setting.Mode("font", "HUD font", "Default", "Poppins"));
    private final Setting.Mode hudStyle = add(new Setting.Mode("hud_style", "HUD panels", "Normal", "Matte"));
    private final Setting.Bool blur = add(new Setting.Bool("blur", "ClickGUI background blur", true));
    private final Setting.Bool mainMenu = add(new Setting.Bool("main_menu", "Custom main menu", true));

    public ThemeModule() {
        super("theme", "Theme", "hud");
    }

    @Override public boolean isEnabled() { return true; }

    @Override public void setEnabled(boolean v) { }

    private boolean custom() { return preset.is("Custom"); }

    private int accentRgb() {
        if (custom()) return ((int) red.value << 16) | ((int) green.value << 8) | (int) blue.value;
        return RGB[preset.index];
    }

    /** Переносит настройки темы в палитру Ui. Дёшево, вызывается каждый кадр. */
    public static void sync() {
        ThemeModule t = ModuleManager.theme;
        if (t == null) return;
        int rgb = t.accentRgb();
        Ui.ACCENT = 0xFF000000 | rgb;
        Ui.ACCENT_BG = 0x2D000000 | rgb;
        Ui.ACCENT_LINE = 0x91000000 | rgb;
        Ui.KNOB = 0xFF000000 | Batch.mix(rgb, 0xFFFFFF, 0.35f);
    }

    public static boolean blur() {
        ThemeModule t = ModuleManager.theme;
        return t == null || t.blur.value;
    }

    public static boolean mainMenu() {
        ThemeModule t = ModuleManager.theme;
        return t == null || t.mainMenu.value;
    }

    public static boolean cleanFont() {
        ThemeModule t = ModuleManager.theme;
        return t != null && t.font.is("Poppins");
    }

    public static boolean matte() {
        ThemeModule t = ModuleManager.theme;
        return t != null && t.hudStyle.is("Matte");
    }
}
