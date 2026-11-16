package com.swinganimations.module;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * CustomWorld: «мир под себя», только на вашем экране.
 * <ul>
 *   <li>Время суток: день, полдень, закат, ночь, полночь, рассвет или своё значение в часах.
 *       Меняется то, что видите вы (небо, солнце, освещение), сервер и другие игроки не затрагиваются.</li>
 *   <li>Оттенок неба: меняет цвет (hue), насыщенность и силу, сохраняя яркость времени суток.</li>
 *   <li>Fullbright: максимальная яркость без зелий и без изменения настроек яркости.</li>
 *   <li>Ясная погода: без дождя и грозы на экране.</li>
 * </ul>
 * Сами значения подменяют миксины (WorldMixin, ClientWorldMixin, SimpleOptionMixin).
 */
public final class CustomWorldModule extends Module {
    private static final String[] TIMES = {"Off", "Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise", "Custom"};
    private static final long[] TICKS = {-1L, 1000L, 6000L, 12000L, 14000L, 18000L, 23000L, -1L};

    private final Setting.Mode time = add(new Setting.Mode("time", "Time of day", TIMES));
    private final Setting.Num hours = add(new Setting.Num("hours", "Hour (0-24)", 12f, 0f, 24f, 0.5f)
            .<Setting.Num>visibleWhen(() -> this.time.is("Custom")));
    private final Setting.Bool fullbright = add(new Setting.Bool("fullbright", "Fullbright", false));
    private final Setting.Bool clearWeather = add(new Setting.Bool("clear_weather", "Clear weather", false));
    private final Setting.Bool sky = add(new Setting.Bool("sky", "Sky tint", false));
    private static final String[] PRESETS = {"Red", "Orange", "Yellow", "Lime", "Green", "Teal", "Cyan", "Blue", "Purple", "Pink", "Custom"};
    private static final float[] PRESET_HUE = {0f, 24f, 48f, 90f, 130f, 170f, 190f, 225f, 275f, 325f, 0f};
    private final Setting.Mode preset = add(new Setting.Mode("preset", "Sky color", PRESETS)
            .<Setting.Mode>visibleWhen(() -> this.sky.value));
    private final Setting.Num hue = add(new Setting.Num("hue", "Hue", 280f, 0f, 360f, 5f)
            .<Setting.Num>visibleWhen(() -> this.sky.value && this.preset.is("Custom")));
    private final Setting.Num sat = add(new Setting.Num("sat", "Saturation", 70f, 0f, 100f, 5f)
            .<Setting.Num>visibleWhen(() -> this.sky.value && this.preset.is("Custom")));
    private final Setting.Num strength = add(new Setting.Num("strength", "Tint strength", 100f, 0f, 100f, 5f)
            .<Setting.Num>visibleWhen(() -> this.sky.value));

    public CustomWorldModule() {
        super("custom_world", "CustomWorld", "render");
    }

    /** Принудительное время суток в тиках или -1, если время не меняем. */
    public long forcedTime() {
        if (!isEnabled() || time.index == 0) return -1L;
        if (time.is("Custom")) {
            float h = ((hours.value - 6f) % 24f + 24f) % 24f;
            return (long) (h * 1000f);
        }
        return TICKS[time.index];
    }

    public boolean fullbrightOn() { return isEnabled() && fullbright.value; }

    public boolean clearWeatherOn() { return isEnabled() && clearWeather.value; }

    public boolean skyTintOn() { return isEnabled() && sky.value && strength.value > 0f; }

    /** Перекрашивает цвет неба: оттенок и насыщенность заменяются (с учётом силы), яркость остаётся. */
    public int tint(int rgb) { return tint(rgb, 1f); }

    /** satScale < 1 даёт более мягкий оттенок (для облаков). */
    public int tint(int rgb, float satScale) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float s = max <= 0f ? 0f : d / max;
        float h;
        if (d <= 1.0e-5f) h = 0f;
        else if (max == r) h = (((g - b) / d) % 6f) / 6f;
        else if (max == g) h = (((b - r) / d) + 2f) / 6f;
        else h = (((r - g) / d) + 4f) / 6f;
        if (h < 0f) h += 1f;

        float k = strength.value / 100f;
        boolean custom = preset.is("Custom");
        float target = (custom ? hue.value : PRESET_HUE[preset.index]) / 360f;
        float targetSat = (custom ? sat.value : 82f) / 100f * satScale;
        float dh = ((target - h + 1.5f) % 1f) - 0.5f;   // кратчайшая дуга по кругу оттенков
        float nh = (h + dh * k + 1f) % 1f;
        float ns = MathHelper.lerp(k, s, targetSat);
        return MathHelper.hsvToRgb(nh, MathHelper.clamp(ns, 0f, 1f), max) & 0xFFFFFF;
    }

    /** Применяет оттенок к значению любого из двух возможных типов (int ARGB или Vec3d). */
    public Object tintValue(Object v, float satScale) {
        if (v instanceof Integer i) return (i & 0xFF000000) | tint(i & 0xFFFFFF, satScale);
        if (v instanceof Vec3d d) return tint(d, satScale);
        return v;
    }

    public Vec3d tint(Vec3d c, float satScale) {
        int rgb = (MathHelper.clamp((int) Math.round(c.x * 255.0), 0, 255) << 16)
                | (MathHelper.clamp((int) Math.round(c.y * 255.0), 0, 255) << 8)
                | MathHelper.clamp((int) Math.round(c.z * 255.0), 0, 255);
        int t = tint(rgb, satScale);
        return new Vec3d(((t >> 16) & 255) / 255.0, ((t >> 8) & 255) / 255.0, (t & 255) / 255.0);
    }
}
