package com.swinganimations.module;

import com.swinganimations.event.EventBus;
import com.swinganimations.event.AttackEvent;
import com.swinganimations.render.Batch;
import com.swinganimations.render.Trig;
import com.swinganimations.util.Perf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Кастомные эффекты удара. Это собственные частицы мода (не ванильные): рисуются в мире
 * светящимися билбордами, любого цвета, ничего не отправляют на сервер.
 * Эффекты: Burst (искры), Stars (звёзды), Ring (ударная волна), Fountain (фонтан),
 * Spiral (спираль), Flash (вспышка). При включении и смене эффекта показывается пример перед вами.
 * Появляются, когда цель реально получила урон (hurt), либо сразу при ударе, если выключено «Only real hits».
 */
public final class ParticlesModule extends Module {
    private static final String[] EFFECTS = {"Burst", "Stars", "Ring", "Fountain", "Spiral", "Flash"};

    private static final class P {
        double x, y, z;
        float vx, vy, vz;
        long t0;
        float life;      // мс
        float size;
        int rgb;
        int kind;        // 0 квадрат, 1 звезда, 2 спираль, 3 свечение
        float grav, drag;
        float ang, rad;  // для спирали
        float spin;
    }

    private static final class Ring {
        double x, y, z;
        long t0;
        float life, maxR;
        int rgb;
    }

    private static final class Pend {
        final int id;
        final long t0;
        final boolean crit;

        Pend(int id, long t0, boolean crit) { this.id = id; this.t0 = t0; this.crit = crit; }
    }

    private final Setting.Mode effect = add(new Setting.Mode("effect", "Effect", EFFECTS));
    private final Setting.Num amount = add(new Setting.Num("amount", "Amount", 24f, 4f, 80f, 2f));
    private final Setting.Num size = add(new Setting.Num("size", "Size", 0.14f, 0.05f, 0.5f, 0.01f));
    private final Setting.Num speed = add(new Setting.Num("speed", "Speed", 1f, 0.4f, 3f, 0.1f));
    private final Setting.Num life = add(new Setting.Num("life", "Lifetime (s)", 0.9f, 0.3f, 3f, 0.1f));
    private final Setting.Bool gravity = add(new Setting.Bool("gravity", "Gravity", true));
    private final Setting.Bool critBoost = add(new Setting.Bool("crit_boost", "Bigger on crit", true));
    private final Setting.Bool onlyReal = add(new Setting.Bool("only_real", "Only real hits", true));
    private final ColorSettings color = new ColorSettings(this);

    private final List<P> parts = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Pend> pending = new ArrayList<>();
    private final Random rnd = new Random();
    private int lastEffect = -1;

    public ParticlesModule() {
        super("particles", "Particles", "render");
        EventBus.on(AttackEvent.class, e -> onAttack(e.target()));
    }

    @Override
    public void onEnable() {
        lastEffect = -1; // покажем пример в ближайшем тике
    }

    @Override
    public void onDisable() {
        parts.clear();
        rings.clear();
        pending.clear();
    }

    // ---------------- события ----------------

    private void onAttack(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (!isEnabled() || p == null || e == p || !(e instanceof LivingEntity)) return;
        boolean crit = p.fallDistance > 0.0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater() && !p.hasVehicle();
        if (onlyReal.value) {
            pending.add(new Pend(e.getId(), System.currentTimeMillis(), crit));
            while (pending.size() > 8) pending.remove(0);
        } else {
            spawnAt(e, crit);
        }
    }

    private void spawnAt(Entity e, boolean crit) {
        spawn(e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(), crit);
    }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        for (int i = pending.size() - 1; i >= 0; i--) {
            Pend pd = pending.get(i);
            Entity e = mc.world.getEntityById(pd.id);
            if (e == null || now - pd.t0 > 800L) {
                pending.remove(i);
            } else if (e instanceof LivingEntity le && le.hurtTime > 0) {
                spawnAt(e, pd.crit);
                pending.remove(i);
            }
        }

        if (lastEffect != effect.index) {
            lastEffect = effect.index;
            Vec3d at = p.getEyePos().add(p.getRotationVec(1f).multiply(3.0));
            spawn(at.x, at.y, at.z, false);
        }

        for (int i = parts.size() - 1; i >= 0; i--) {
            P q = parts.get(i);
            if (now - q.t0 > q.life) parts.remove(i);
        }
        for (int i = rings.size() - 1; i >= 0; i--) {
            Ring r = rings.get(i);
            if (now - r.t0 > r.life) rings.remove(i);
        }
    }

    // ---------------- создание частиц ----------------

    private int cap() {
        int q = Perf.quality();
        return q == 0 ? 500 : (q == 1 ? 300 : 150);
    }

    private P part(double x, double y, double z, int kind, float sizeMul, float lifeMul, long now) {
        P q = new P();
        q.x = x;
        q.y = y;
        q.z = z;
        q.kind = kind;
        q.t0 = now;
        q.size = size.value * sizeMul * (0.7f + rnd.nextFloat() * 0.6f);
        q.life = life.value * 1000f * lifeMul * (0.65f + rnd.nextFloat() * 0.35f);
        q.rgb = color.rgb(now, rnd.nextFloat());
        q.spin = (rnd.nextFloat() - 0.5f) * 6f;
        return q;
    }

    private void sphereVelocity(P q, float spd) {
        float u = rnd.nextFloat() * 2f - 1f;
        float th = rnd.nextFloat() * MathHelper.TAU;
        float s = (float) Math.sqrt(1f - u * u);
        q.vx = s * MathHelper.cos(th) * spd;
        q.vy = u * spd;
        q.vz = s * MathHelper.sin(th) * spd;
    }

    private void spawn(double x, double y, double z, boolean crit) {
        long now = System.currentTimeMillis();
        float boost = crit && critBoost.value ? 1.6f : 1f;
        int n = Math.min(Math.round(amount.value * boost), Math.max(0, cap() - parts.size()));
        float spd = speed.value * (crit && critBoost.value ? 1.2f : 1f);
        float grav = gravity.value ? 1f : 0f;
        String fx = effect.get();

        switch (fx) {
            case "Burst" -> {
                for (int i = 0; i < n; i++) {
                    P q = part(x, y, z, 0, 1f, 1f, now);
                    sphereVelocity(q, (2f + rnd.nextFloat() * 3f) * spd);
                    q.drag = 2.2f;
                    q.grav = 3f * grav;
                    parts.add(q);
                }
            }
            case "Stars" -> {
                for (int i = 0; i < n; i++) {
                    P q = part(x, y, z, 1, 1.7f, 1.1f, now);
                    sphereVelocity(q, (1.5f + rnd.nextFloat() * 2.5f) * spd);
                    q.drag = 1.6f;
                    q.grav = 1.5f * grav;
                    parts.add(q);
                }
            }
            case "Ring" -> {
                Ring r = new Ring();
                r.x = x;
                r.y = y;
                r.z = z;
                r.t0 = now;
                r.life = 650f / Math.max(0.4f, spd);
                r.maxR = 1.6f * (crit && critBoost.value ? 1.3f : 1f);
                r.rgb = color.rgb(now, 0f);
                rings.add(r);
                for (int i = 0; i < n / 2; i++) {
                    P q = part(x, y, z, 0, 0.9f, 0.8f, now);
                    float th = rnd.nextFloat() * MathHelper.TAU;
                    float v = (3f + rnd.nextFloat() * 2f) * spd;
                    q.vx = MathHelper.cos(th) * v;
                    q.vz = MathHelper.sin(th) * v;
                    q.vy = (rnd.nextFloat() - 0.3f) * 1.2f;
                    q.drag = 3f;
                    q.grav = 2f * grav;
                    parts.add(q);
                }
            }
            case "Fountain" -> {
                for (int i = 0; i < n; i++) {
                    P q = part(x, y, z, rnd.nextInt(3) == 0 ? 1 : 0, 1f, 1.2f, now);
                    float th = rnd.nextFloat() * MathHelper.TAU;
                    float h = rnd.nextFloat() * 1.3f * spd;
                    q.vx = MathHelper.cos(th) * h;
                    q.vz = MathHelper.sin(th) * h;
                    q.vy = (3.5f + rnd.nextFloat() * 2.5f) * spd;
                    q.drag = 0.2f;
                    q.grav = 9f * (gravity.value ? 1f : 0.35f);
                    parts.add(q);
                }
            }
            case "Spiral" -> {
                for (int i = 0; i < n; i++) {
                    P q = part(x, y - 0.4, z, 2, 1f, 1.1f, now);
                    q.ang = (i % 2 == 0 ? 0f : MathHelper.PI) + (i / 2) * 0.55f;
                    q.rad = 0.5f + rnd.nextFloat() * 0.5f;
                    q.vy = (0.9f + rnd.nextFloat() * 0.8f) * spd;
                    parts.add(q);
                }
            }
            default -> { // Flash
                P g = part(x, y, z, 3, 7f, 0.4f, now);
                g.size = size.value * 7f * (crit && critBoost.value ? 1.3f : 1f);
                g.life = 380f;
                parts.add(g);
                for (int i = 0; i < n / 2; i++) {
                    P q = part(x, y, z, 0, 0.9f, 0.7f, now);
                    sphereVelocity(q, (4f + rnd.nextFloat() * 3f) * spd);
                    q.drag = 3.5f;
                    parts.add(q);
                }
            }
        }
    }

    // ---------------- отрисовка ----------------

    @Override
    public boolean wantsWorldRender() { return !parts.isEmpty() || !rings.isEmpty(); }

    /** Вершина билборда: смещение от центра по осям камеры, угол ang, радиус rad. */
    private static void bv(Batch b, RenderContext c, double x, double y, double z, float ang, float rad, int col) {
        float ca = MathHelper.cos(ang) * rad;
        float sa = MathHelper.sin(ang) * rad;
        b.v(c.m, x + c.rx * ca + c.ux * sa, y + c.ry * ca + c.uy * sa, z + c.rz * ca + c.uz * sa, col);
    }

    @Override
    public void render(RenderContext c) {
        if (parts.isEmpty() && rings.isEmpty()) return;
        Batch.begin(true, true);
        Batch tri = Batch.triangles();
        for (int i = 0; i < parts.size(); i++) {
            P q = parts.get(i);
            float age = (c.now - q.t0) / 1000f;
            float f = (c.now - q.t0) / q.life;
            if (f < 0f || f >= 1f) continue;
            double x, y, z;
            if (q.kind == 2) {
                float th = q.ang + 7f * age;
                float r = q.rad * (0.3f + 0.7f * f);
                x = q.x + MathHelper.cos(th) * r;
                z = q.z + MathHelper.sin(th) * r;
                y = q.y + q.vy * age;
            } else if (q.kind == 3) {
                x = q.x;
                y = q.y;
                z = q.z;
            } else {
                float disp = q.drag > 0.001f ? (1f - (float) Math.exp(-q.drag * age)) / q.drag : age;
                x = q.x + q.vx * disp;
                y = q.y + q.vy * disp - 0.5 * q.grav * age * age;
                z = q.z + q.vz * disp;
            }
            float alpha = (float) Math.pow(1f - f, 1.5);
            float rot = q.spin * age;
            if (q.kind == 3) {
                float s = q.size * (0.5f + f);
                int mid = Batch.argb(q.rgb, alpha * 0.85f);
                int edge = Batch.argb(q.rgb, 0f);
                int seg = 14;
                for (int k = 0; k < seg; k++) {
                    float a0 = MathHelper.TAU * k / seg;
                    float a1 = MathHelper.TAU * (k + 1) / seg;
                    bv(tri, c, x, y, z, 0f, 0f, mid);
                    bv(tri, c, x, y, z, a0, s, edge);
                    bv(tri, c, x, y, z, a1, s, edge);
                }
                continue;
            }
            float s = q.size * (1f - 0.5f * f);
            int col = Batch.argb(q.rgb, alpha);
            if (q.kind == 1) {
                // 4-лучевая звезда: 8 вершин, лучи длинные, впадины короткие
                for (int k = 0; k < 8; k++) {
                    float a0 = rot + MathHelper.TAU * k / 8f;
                    float a1 = rot + MathHelper.TAU * (k + 1) / 8f;
                    float r0 = (k % 2 == 0) ? s : s * 0.35f;
                    float r1 = (k % 2 == 0) ? s * 0.35f : s;
                    bv(tri, c, x, y, z, 0f, 0f, col);
                    bv(tri, c, x, y, z, a0, r0, col);
                    bv(tri, c, x, y, z, a1, r1, col);
                }
            } else {
                float rr = s * 1.4142f;
                float a = rot + MathHelper.PI / 4f;
                for (int k = 0; k < 2; k++) {
                    int o = k * 2;
                    bv(tri, c, x, y, z, a + MathHelper.HALF_PI * o, rr, col);
                    bv(tri, c, x, y, z, a + MathHelper.HALF_PI * (o + 1), rr, col);
                    bv(tri, c, x, y, z, a + MathHelper.HALF_PI * (o + 2), rr, col);
                }
            }
        }
        tri.flush();

        if (!rings.isEmpty()) {
            int segs = Perf.quality() == 0 ? 48 : 24;
            double[] cs = Trig.cos(segs);
            double[] sn = Trig.sin(segs);
            Batch strip = Batch.strip();
            for (int k = 0; k < rings.size(); k++) {
                Ring r = rings.get(k);
                float f = (c.now - r.t0) / r.life;
                if (f < 0f || f >= 1f) continue;
                float alpha = 1f - f;
                float e = 1f - (1f - f) * (1f - f) * (1f - f);
                float rOut = r.maxR * e;
                float rIn = rOut * 0.78f;
                strip.restart();
                for (int i = 0; i <= segs; i++) {
                    strip.v(c.m, r.x + cs[i] * rIn, r.y, r.z + sn[i] * rIn, Batch.argb(r.rgb, 0f));
                    strip.v(c.m, r.x + cs[i] * rOut, r.y, r.z + sn[i] * rOut, Batch.argb(r.rgb, alpha));
                }
            }
            strip.flush();
        }
        Batch.end();
    }
}
