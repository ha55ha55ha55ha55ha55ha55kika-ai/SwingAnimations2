package com.swinganimations.animation;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;

/** Хелперы трансформаций без аллокаций: вращаем матрицы JOML напрямую, без Quaternionf. */
public final class Xf {
    private static final float RAD = (float) (Math.PI / 180.0);

    private Xf() {}

    public static void translate(MatrixStack m, float x, float y, float z) {
        m.translate(x, y, z);
    }

    public static void rotX(MatrixStack m, float deg) {
        if (deg == 0f) return;
        float r = deg * RAD;
        MatrixStack.Entry e = m.peek();
        e.getPositionMatrix().rotateX(r);
        e.getNormalMatrix().rotateX(r);
    }

    public static void rotY(MatrixStack m, float deg) {
        if (deg == 0f) return;
        float r = deg * RAD;
        MatrixStack.Entry e = m.peek();
        e.getPositionMatrix().rotateY(r);
        e.getNormalMatrix().rotateY(r);
    }

    public static void rotZ(MatrixStack m, float deg) {
        if (deg == 0f) return;
        float r = deg * RAD;
        MatrixStack.Entry e = m.peek();
        e.getPositionMatrix().rotateZ(r);
        e.getNormalMatrix().rotateZ(r);
    }

    public static void scale(MatrixStack m, float s) {
        m.scale(s, s, s);
    }

    public static float sign(Arm arm) {
        return arm == Arm.RIGHT ? 1f : -1f;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** smoothstep 0..1 */
    public static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    /** Колокол: 0 -> 1 -> 0 на отрезке 0..1 */
    public static float bell(float p) {
        return (float) Math.sin(clamp01(p) * Math.PI);
    }

    public static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    /** Плавность входа/выхода по отдельности: 0 = линейно, 1 = очень плавно (кроссфейд ease-in и ease-out). */
    public static float ease(float u, float easeIn, float easeOut) {
        u = clamp01(u);
        float a = 1f + 2f * clamp01(easeIn);
        float b = 1f + 2f * clamp01(easeOut);
        float in = (float) Math.pow(u, a);
        float out = 1f - (float) Math.pow(1f - u, b);
        return (1f - u) * in + u * out;
    }

    /**
     * Огибающая удара: замах назад (значения < 0), удар до 1, задержка на пике, плавный возврат в 0.
     * В p=0 и p=1 возвращает 0. Всё без аллокаций.
     */
    public static float envelope(float p, float windup, float hold, float easeIn, float easeOut) {
        p = clamp01(p);
        float wd = 0.25f * clamp01(windup);   // доля времени на замах
        float hd = 0.15f * clamp01(hold);     // доля времени на задержку на пике
        float r = 1f - wd - hd;
        float strike = 0.4f * r;
        float back = 0.35f * clamp01(windup); // отвод назад, в долях амплитуды
        if (p < wd) return -back * smooth(p / wd);
        p -= wd;
        if (p < strike) return lerp(-back, 1f, ease(p / strike, easeIn, easeOut));
        p -= strike;
        if (p < hd) return 1f;
        p -= hd;
        return 1f - smooth(p / (r - strike));
    }

    /** Быстрый подъём до пика (30% времени) и плавный возврат. */
    public static float pulse(float p) {
        return p < 0.3f ? smooth(p / 0.3f) : 1f - smooth((p - 0.3f) / 0.7f);
    }
}
