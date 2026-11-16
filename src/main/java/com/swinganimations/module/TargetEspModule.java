package com.swinganimations.module;

import com.swinganimations.render.Batch;
import com.swinganimations.render.Trig;
import com.swinganimations.util.Perf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Маркер на цели: Кольцо, Ромб, Призраки, Crystal (3D-ромб над головой), Orbit, Helix, Halo, Pulse. Цель = существо, которое вы ударили, и/или на которое наведён
 * прицел. Рисуется С проверкой глубины: сквозь стены не видно, это чисто косметика.
 */
public final class TargetEspModule extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("mode", "Mode", "Ring", "Diamond", "Ghosts", "Crystal", "Orbit", "Helix", "Halo", "Pulse"));
    private final Setting.Num size = add(new Setting.Num("size", "Size", 1f, 0.5f, 1.5f, 0.05f));
    private final Setting.Num speed = add(new Setting.Num("speed", "Speed", 1f, 0.3f, 3f, 0.1f));
    private final Setting.Num hold = add(new Setting.Num("hold", "Hold time (ms)", 1500f, 300f, 5000f, 100f));
    private final Setting.Mode source = add(new Setting.Mode("source", "Target", "Attacked", "Crosshair", "Both"));
    private final Setting.Bool hurt = add(new Setting.Bool("hurt", "Flash red on hit", true));
    private final ColorSettings color = new ColorSettings(this);

    private Entity target;
    private long firstSeen;
    private long lastSeen;

    public TargetEspModule() {
        super("target_esp", "TargetESP", "render");
        com.swinganimations.event.EventBus.on(com.swinganimations.event.AttackEvent.class, e -> onAttack(e.target()));
    }

    @Override
    public void onDisable() {
        target = null;
    }

    /** Вызывается из AttackEntityCallback (только клиент, пакеты не затрагиваются). */
    public void onAttack(Entity e) {
        if (!isEnabled() || source.is("Crosshair")) return;
        select(e);
    }

    private void select(Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return;
        if (e == MinecraftClient.getInstance().player) return;
        long now = System.currentTimeMillis();
        if (e != target || now - lastSeen > hold.value + 400f) firstSeen = now;
        target = e;
        lastSeen = now;
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (source.is("Attacked")) return;
        Entity t = mc.targetedEntity;
        if (t != null) select(t);
    }

    @Override
    public boolean wantsWorldRender() { return target != null; }

    @Override
    public void render(RenderContext c) {
        Entity e = target;
        if (e == null) return;
        if (!e.isAlive() || e.isRemoved()) {
            target = null;
            return;
        }
        long now = c.now;
        float elapsed = now - lastSeen;
        float fade = elapsed <= hold.value ? 1f : 1f - (elapsed - hold.value) / 300f;
        if (fade <= 0f) {
            target = null;
            return;
        }
        float a = fade * Math.min(1f, (now - firstSeen) / 200f);

        double x = MathHelper.lerp(c.tickDelta, e.lastRenderX, e.getX());
        double y = MathHelper.lerp(c.tickDelta, e.lastRenderY, e.getY());
        double z = MathHelper.lerp(c.tickDelta, e.lastRenderZ, e.getZ());
        float w = e.getWidth();
        float h = e.getHeight();

        float hurtF = (hurt.value && e instanceof LivingEntity le) ? MathHelper.clamp(le.hurtTime / 10f, 0f, 1f) : 0f;
        int rgb = Batch.mix(color.rgb(now, 0f), 0xFF3030, hurtF);

        switch (mode.get()) {
            case "Diamond" -> diamond(c, x, y + h * 0.5, z, rgb, a, hurtF);
            case "Ghosts" -> ghosts(c, x, y, z, w, h, rgb, a);
            case "Crystal" -> crystal(c, x, y, z, h, rgb, a);
            case "Orbit" -> orbit(c, x, y, z, w, h, rgb, a);
            case "Helix" -> helix(c, x, y, z, w, h, rgb, a);
            case "Halo" -> halo(c, x, y, z, h, rgb, a);
            case "Pulse" -> pulse(c, x, y, z, w, rgb, a);
            default -> ring(c, x, y, z, w, h, rgb, a);
        }
    }

    // ---------------- Кольцо ----------------

    private void ring(RenderContext c, double x, double y, double z, float w, float h, int rgb, float a) {
        double phase = c.now / 1000.0 * speed.value;
        float r = (w * 0.5f + 0.22f) * size.value;
        int q = Perf.quality();
        int layers = q == 0 ? 4 : 2;
        int segs = q == 0 ? 48 : 24;
        Batch.begin(true, true);
        Batch s = Batch.strip();
        for (int k = 0; k < layers; k++) {
            double t = 0.5 + 0.5 * Math.sin((phase - k * 0.14) * 2.2);
            double yy = y + h * t;
            float al = a * (k == 0 ? 0.95f : 0.45f / k);
            wall(s, c.m, x, yy - 0.14, z, r, 0.14f, segs, Batch.argb(rgb, 0f), Batch.argb(rgb, al));
            wall(s, c.m, x, yy, z, r, 0.04f, segs, Batch.argb(rgb, al), Batch.argb(rgb, 0f));
        }
        s.flush();
        Batch.end();
    }

    private static void wall(Batch s, Matrix4f m, double cx, double y0, double cz, float r, float hh, int segs,
                             int cBottom, int cTop) {
        double[] cs = Trig.cos(segs);
        double[] sn = Trig.sin(segs);
        s.restart();
        for (int i = 0; i <= segs; i++) {
            double px = cx + cs[i] * r;
            double pz = cz + sn[i] * r;
            s.v(m, px, y0, pz, cBottom);
            s.v(m, px, y0 + hh, pz, cTop);
        }
    }

    // ---------------- Ромб ----------------

    private void diamond(RenderContext c, double x, double y, double z, int rgb, float a, float hurtF) {
        double phase = c.now / 1000.0 * speed.value;
        float ang = (float) (Math.sin(phase * 1.7) * Math.PI);
        float s = 0.45f * size.value * (1.3f - 0.45f * hurtF);
        Batch.begin(true, true);
        Batch t = Batch.triangles();
        rhombus(t, c, x, y, z, s * 1.7f, ang, Batch.argb(rgb, a * 0.30f), Batch.argb(rgb, 0f));
        rhombus(t, c, x, y, z, s, ang, Batch.argb(rgb, a * 0.95f), Batch.argb(rgb, a * 0.55f));
        t.flush();
        Batch.end();
    }

    private static void rhombus(Batch t, RenderContext c, double x, double y, double z, float s, float ang,
                                int centerCol, int rimCol) {
        float[] lx = {0f, 0.62f * s, 0f, -0.62f * s};
        float[] ly = {s, 0f, -s, 0f};
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            t.v(c.m, x, y, z, centerCol);
            vert(t, c, x, y, z, lx[i], ly[i], ca, sa, rimCol);
            vert(t, c, x, y, z, lx[j], ly[j], ca, sa, rimCol);
        }
    }

    private static void vert(Batch t, RenderContext c, double x, double y, double z,
                             float lx, float ly, float ca, float sa, int col) {
        float rx = lx * ca - ly * sa;
        float ry = lx * sa + ly * ca;
        t.v(c.m, x + c.rx * rx + c.ux * ry, y + c.ry * rx + c.uy * ry, z + c.rz * rx + c.uz * ry, col);
    }

    // ---------------- Crystal: классический 3D-ромб над головой ----------------

    private void crystal(RenderContext c, double x, double y, double z, float h, int rgb, float a) {
        double phase = c.now / 1000.0 * speed.value;
        float sz = size.value;
        double cy = y + h + 0.42 * sz + 0.07 * Math.sin(phase * 2.0);
        float hh = 0.30f * sz;
        float rr = 0.19f * sz;
        double th = phase * 2.4;
        Batch.begin(true, false);
        Batch t = Batch.triangles();
        for (int i = 0; i < 4; i++) {
            double a0 = th + i * Math.PI / 2.0;
            double a1 = th + (i + 1) * Math.PI / 2.0;
            double x0 = x + Math.cos(a0) * rr, z0 = z + Math.sin(a0) * rr;
            double x1 = x + Math.cos(a1) * rr, z1 = z + Math.sin(a1) * rr;
            // Грани по очереди ловят «свет»: вращение выглядит объёмным.
            float light = (float) (0.5 + 0.5 * Math.cos(a0 + Math.PI / 4.0 - 0.8));
            int face = Batch.mix(rgb, 0x000000, 0.5f * (1f - light));
            int tip = Batch.mix(face, 0xFFFFFF, 0.3f);
            int low = Batch.mix(face, 0x000000, 0.35f);
            int col = Batch.argb(face, a * 0.92f);
            t.v(c.m, x, cy + hh, z, Batch.argb(tip, a * 0.92f));
            t.v(c.m, x0, cy, z0, col);
            t.v(c.m, x1, cy, z1, col);
            int lowCol = Batch.argb(low, a * 0.92f);
            t.v(c.m, x, cy - hh, z, lowCol);
            t.v(c.m, x1, cy, z1, lowCol);
            t.v(c.m, x0, cy, z0, lowCol);
        }
        t.flush();
        Batch.end();

        Batch.begin(true, true);
        Batch g = Batch.triangles();
        disc(g, c, x, cy, z, 0.5f * sz, Perf.quality() == 0 ? 16 : 10, Batch.argb(rgb, a * 0.35f), Batch.argb(rgb, 0f));
        g.flush();
        Batch.end();
    }

    // ---------------- Orbit: ромбики по орбите ----------------

    private void orbit(RenderContext c, double x, double y, double z, float w, float h, int rgb, float a) {
        double phase = c.now / 1000.0 * speed.value;
        float orbitR = (w * 0.5f + 0.45f) * size.value;
        int trail = Perf.quality() == 0 ? 5 : 3;
        Batch.begin(true, true);
        Batch t = Batch.triangles();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < trail; j++) {
                double th = phase * 2.0 + i * Math.PI / 2.0 - j * 0.12;
                double yy = y + h * (0.5 + 0.38 * Math.sin(phase * 1.3 + i * 1.7));
                float k = 1f - j / (float) trail;
                rhombus(t, c, x + Math.cos(th) * orbitR, yy, z + Math.sin(th) * orbitR,
                        0.13f * size.value * (0.5f + 0.5f * k), 0f,
                        Batch.argb(rgb, a * 0.95f * k), Batch.argb(rgb, a * 0.4f * k));
            }
        }
        t.flush();
        Batch.end();
    }

    // ---------------- Helix: двойная спираль вдоль тела ----------------

    private void helix(RenderContext c, double x, double y, double z, float w, float h, int rgb, float a) {
        double phase = c.now / 1000.0 * speed.value;
        float rad = (w * 0.5f + 0.3f) * size.value;
        int n = Perf.quality() == 0 ? 16 : 9;
        int segs = Perf.quality() == 0 ? 10 : 6;
        Batch.begin(true, true);
        Batch t = Batch.triangles();
        for (int strand = 0; strand < 2; strand++) {
            for (int j = 0; j < n; j++) {
                double u = (j / (double) n + phase * 0.28) % 1.0;
                double th = u * Math.PI * 4.0 + strand * Math.PI + phase * 1.6;
                float fade = (float) Math.sin(Math.PI * u);
                disc(t, c, x + Math.cos(th) * rad, y + h * u, z + Math.sin(th) * rad,
                        0.07f * size.value * (0.6f + 0.4f * fade), segs,
                        Batch.argb(rgb, a * 0.95f * fade), Batch.argb(rgb, 0f));
            }
        }
        t.flush();
        Batch.end();
    }

    // ---------------- Halo: светящийся нимб над головой ----------------

    private void halo(RenderContext c, double x, double y, double z, float h, int rgb, float a) {
        double phase = c.now / 1000.0 * speed.value;
        float sz = size.value;
        float r = 0.30f * sz;
        double yy = y + h + 0.26 + 0.05 * Math.sin(phase * 2.0);
        int segs = Perf.quality() == 0 ? 40 : 24;
        double[] cs = Trig.cos(segs);
        double[] sn = Trig.sin(segs);
        float core0 = r - 0.035f * sz;
        float core1 = r + 0.035f * sz;
        float glow = 0.07f * sz;
        // полосы: {внутренний радиус, альфа, внешний радиус, альфа}
        float[][] bands = {
                {core0 - glow, 0f, core0, 1f},
                {core0, 1f, core1, 1f},
                {core1, 1f, core1 + glow, 0f}
        };
        Batch.begin(true, true);
        Batch s = Batch.strip();
        for (float[] bd : bands) {
            s.restart();
            for (int i = 0; i <= segs; i++) {
                double tilt = 0.04 * Math.sin(phase * 2.0 + i * Math.PI * 2.0 / segs);
                s.v(c.m, x + cs[i] * bd[0], yy + tilt, z + sn[i] * bd[0], Batch.argb(rgb, a * 0.95f * bd[1]));
                s.v(c.m, x + cs[i] * bd[2], yy + tilt, z + sn[i] * bd[2], Batch.argb(rgb, a * 0.95f * bd[3]));
            }
        }
        s.flush();
        Batch.end();
    }

    // ---------------- Pulse: волны от ног ----------------

    private void pulse(RenderContext c, double x, double y, double z, float w, int rgb, float a) {
        double phase = c.now / 1000.0 * speed.value * 0.6;
        int segs = Perf.quality() == 0 ? 48 : 24;
        double[] cs = Trig.cos(segs);
        double[] sn = Trig.sin(segs);
        Batch.begin(true, true);
        Batch s = Batch.strip();
        for (int k = 0; k < 3; k++) {
            float f = (float) ((phase + k / 3.0) % 1.0);
            float rOut = (w * 0.5f + 0.15f + f * 1.3f) * size.value;
            float rIn = rOut - 0.16f * (1f - 0.5f * f) * size.value;
            float al = a * (float) Math.pow(1f - f, 1.4);
            s.restart();
            for (int i = 0; i <= segs; i++) {
                s.v(c.m, x + cs[i] * rIn, y + 0.03, z + sn[i] * rIn, Batch.argb(rgb, 0f));
                s.v(c.m, x + cs[i] * rOut, y + 0.03, z + sn[i] * rOut, Batch.argb(rgb, al));
            }
        }
        s.flush();
        Batch.end();
    }

    // ---------------- Призраки ----------------

    private void ghosts(RenderContext c, double x, double y, double z, float w, float h, int rgb, float a) {
        double phase = c.now / 1000.0 * speed.value;
        float orbit = (w * 0.5f + 0.35f) * size.value;
        Batch.begin(true, true);
        Batch t = Batch.triangles();
        int trail = Perf.quality() == 0 ? 10 : 5;
        int discSegs = Perf.quality() == 0 ? 12 : 8;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < trail; j++) {
                double th = phase * 2.2 + i * (Math.PI * 2.0 / 3.0) - j * 0.09 * (10.0 / trail);
                double yy = y + h * (0.45 + 0.35 * Math.sin(phase * 1.5 + i * 2.1 - j * 0.05));
                double px = x + Math.cos(th) * orbit;
                double pz = z + Math.sin(th) * orbit;
                float k = 1f - j / (float) trail;
                disc(t, c, px, yy, pz, 0.16f * size.value * (0.4f + 0.6f * k), discSegs,
                        Batch.argb(rgb, a * 0.85f * k), Batch.argb(rgb, 0f));
            }
        }
        t.flush();
        Batch.end();
    }

    private static void disc(Batch t, RenderContext c, double x, double y, double z, float r, int segs,
                             int centerCol, int rimCol) {
        double[] cs = Trig.cos(segs);
        double[] sn = Trig.sin(segs);
        for (int i = 0; i < segs; i++) {
            t.v(c.m, x, y, z, centerCol);
            edge(t, c, x, y, z, r, cs[i], sn[i], rimCol);
            edge(t, c, x, y, z, r, cs[i + 1], sn[i + 1], rimCol);
        }
    }

    private static void edge(Batch t, RenderContext c, double x, double y, double z, float r, double cosA, double sinA, int col) {
        float cx = (float) cosA * r;
        float cy = (float) sinA * r;
        t.v(c.m, x + c.rx * cx + c.ux * cy, y + c.ry * cx + c.uy * cy, z + c.rz * cx + c.uz * cy, col);
    }
}
