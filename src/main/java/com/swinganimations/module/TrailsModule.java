package com.swinganimations.module;

import com.swinganimations.render.Batch;
import com.swinganimations.util.Perf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

/** Светящийся след-лента за игроком. Точки хранятся в кольцевом буфере (без аллокаций в кадре). */
public final class TrailsModule extends Module {
    private static final int CAP = 512;

    private final Setting.Num duration = add(new Setting.Num("duration", "Duration (ms)", 600f, 100f, 2000f, 50f));
    private final Setting.Num height = add(new Setting.Num("height", "Height", 1f, 0.1f, 1f, 0.05f));
    private final Setting.Num opacity = add(new Setting.Num("opacity", "Opacity", 0.65f, 0.1f, 1f, 0.05f));
    private final Setting.Bool firstPerson = add(new Setting.Bool("first_person", "Show in first person", false));
    private final ColorSettings color = new ColorSettings(this);

    private final double[] px = new double[CAP];
    private final double[] py = new double[CAP];
    private final double[] pz = new double[CAP];
    private final long[] pt = new long[CAP];
    private int start;
    private int size;

    public TrailsModule() {
        super("trails", "Trails", "render");
    }

    @Override
    public void onDisable() {
        size = 0;
    }

    @Override
    public boolean wantsWorldRender() { return true; }

    private int idx(int i) {
        return (start + i) % CAP;
    }

    @Override
    public void render(RenderContext c) {
        ClientPlayerEntity p = c.mc.player;
        if (p == null) return;
        if (!firstPerson.value && c.mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            size = 0;
            return;
        }

        long now = c.now;
        while (size > 0 && now - pt[idx(0)] > duration.value) {
            start = (start + 1) % CAP;
            size--;
        }

        double x = MathHelper.lerp(c.tickDelta, p.prevX, p.getX());
        double y = MathHelper.lerp(c.tickDelta, p.prevY, p.getY());
        double z = MathHelper.lerp(c.tickDelta, p.prevZ, p.getZ());
        boolean add = size == 0;
        if (!add) {
            int last = idx(size - 1);
            double dx = x - px[last], dy = y - py[last], dz = z - pz[last];
            int q = Perf.quality();
            add = dx * dx + dy * dy + dz * dz > 0.0009 * (1 + 3 * q * q) || now - pt[last] > 60L * (1 + q);
        }
        if (add) {
            if (size == CAP) {
                start = (start + 1) % CAP;
                size--;
            }
            int w = idx(size);
            px[w] = x; py[w] = y; pz[w] = z; pt[w] = now;
            size++;
        }
        if (size < 2) return;

        float h = p.getHeight() * height.value;
        Batch.begin(true, false);
        Batch strip = Batch.strip();
        for (int i = 0; i < size; i++) {
            int k = idx(i);
            float age = 1f - (now - pt[k]) / duration.value;
            float a = MathHelper.clamp(age, 0f, 1f) * opacity.value;
            int rgb = color.rgb(now, (float) i / size * 0.5f);
            strip.v(c.m, px[k], py[k] + h, pz[k], Batch.argb(rgb, a * 0.8f));
            strip.v(c.m, px[k], py[k], pz[k], Batch.argb(rgb, a));
        }
        strip.flush();
        Batch.end();
    }
}
