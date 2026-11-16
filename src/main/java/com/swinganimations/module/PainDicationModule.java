package com.swinganimations.module;

import com.swinganimations.render.Batch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import org.joml.Matrix4f;

/**
 * PainDication: красная виньетка по краям экрана при малом здоровье. Чем меньше HP, тем она ярче и больше.
 * Рисуется собственной геометрией (радиальный градиент), без текстур.
 */
public final class PainDicationModule extends Module {
    private static final int N = 48; // сегментов по периметру (кратно 4, углы попадают в вершины)
    private static final float[] PX = new float[N + 1];
    private static final float[] PY = new float[N + 1];
    private static final int[] COLORS = {0xE01010, 0xA00014, 0xFF5A00};

    static {
        for (int i = 0; i <= N; i++) {
            float u = 4f * i / N;
            int side = Math.min((int) u, 3);
            float f = u - side;
            switch (side) {
                case 0 -> { PX[i] = -1f + 2f * f; PY[i] = -1f; }
                case 1 -> { PX[i] = 1f; PY[i] = -1f + 2f * f; }
                case 2 -> { PX[i] = 1f - 2f * f; PY[i] = 1f; }
                default -> { PX[i] = -1f; PY[i] = 1f - 2f * f; }
            }
        }
    }

    private final Setting.Num threshold = add(new Setting.Num("threshold", "Start at HP %", 40f, 10f, 100f, 5f));
    private final Setting.Num intensity = add(new Setting.Num("intensity", "Intensity", 0.8f, 0.2f, 1f, 0.05f));
    private final Setting.Num size = add(new Setting.Num("size", "Vignette size", 0.5f, 0.2f, 1f, 0.05f));
    private final Setting.Bool pulse = add(new Setting.Bool("pulse", "Heartbeat pulse", true));
    private final Setting.Bool hurtFlash = add(new Setting.Bool("hurt_flash", "Flash on damage", true));
    private final Setting.Mode color = add(new Setting.Mode("color", "Color", "Red", "Crimson", "Orange"));

    private float shown;
    private long last;

    public PainDicationModule() {
        super("pain_dication", "PainDication", "hud");
    }

    @Override
    public void onDisable() {
        shown = 0f;
        last = 0L;
    }

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || p.isSpectator()) return;

        float ratio = p.getHealth() / Math.max(1f, p.getMaxHealth());
        float thr = threshold.value / 100f;
        float t = ratio < thr ? 1f - ratio / thr : 0f;
        float target = t > 0f ? 0.18f + 0.82f * t : 0f;
        float flash = hurtFlash.value && p.hurtTime > 0 ? 0.45f * p.hurtTime / Math.max(1, p.maxHurtTime) : 0f;

        long now = System.currentTimeMillis();
        float dt = last == 0L ? 0f : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        shown += (target - shown) * Math.min(1f, dt * 6f);

        float a = shown;
        if (pulse.value && t > 0f) {
            float rate = 0.9f + 1.6f * t;
            float ph = (now / 1000f * rate) % 1f;
            float beat = (float) (Math.exp(-ph * 9f) + 0.6f * Math.exp(-Math.abs(ph - 0.28f) * 30f));
            a *= 0.8f + 0.3f * Math.min(1f, beat);
        }
        a = Math.min(1f, (a + flash) * intensity.value);
        if (a < 0.01f) return;

        float w = mc.getWindow().getScaledWidth();
        float h = mc.getWindow().getScaledHeight();
        float hw = w * 0.5f;
        float hh = h * 0.5f;
        float k0 = 1f - size.value * (0.55f + 0.35f * Math.min(1f, t));
        int rgb = COLORS[Math.min(color.index, COLORS.length - 1)];
        int cIn = Batch.argb(rgb, 0f);
        int cMid = Batch.argb(rgb, a * 0.4f);
        int cOut = Batch.argb(rgb, a);

        Matrix4f m = new Matrix4f();
        Batch.beginScreen(false);
        Batch s = Batch.strip();
        for (int row = 0; row < 2; row++) {
            s.restart();
            for (int i = 0; i <= N; i++) {
                float nx = PX[i];
                float ny = PY[i];
                float len = (float) Math.sqrt(nx * nx + ny * ny);
                float ix = hw + nx / len * k0 * hw;
                float iy = hh + ny / len * k0 * hh;
                float ox = hw + nx * hw * 1.03f;
                float oy = hh + ny * hh * 1.03f;
                float mx = (ix + ox) * 0.5f;
                float my = (iy + oy) * 0.5f;
                if (row == 0) {
                    s.v(m, ix, iy, 0, cIn);
                    s.v(m, mx, my, 0, cMid);
                } else {
                    s.v(m, mx, my, 0, cMid);
                    s.v(m, ox, oy, 0, cOut);
                }
            }
        }
        s.flush();
        Batch.end();
    }
}
