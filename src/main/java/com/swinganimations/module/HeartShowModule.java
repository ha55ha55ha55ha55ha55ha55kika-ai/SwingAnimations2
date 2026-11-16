package com.swinganimations.module;

import com.swinganimations.gui.Hud;
import com.swinganimations.gui.Ui;
import com.swinganimations.render.Batch;
import com.swinganimations.render.Projection;
import com.swinganimations.util.Perf;
import com.swinganimations.util.Spring;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * После вашего удара над головой цели вылетают сердечки (5 стилей), а на экране появляется
 * «тайтл» с нанесённым уроном: «Удар 19», «Удар 17.4». Урон считается как падение здоровья цели
 * в ближайшие 0.7 с после удара. Только клиент: ни пакетов, ни геймплея это не меняет.
 *
 * Производительность: выбор целей для HP над головой (перебор сущностей, проверка видимости лучом)
 * делается раз в несколько тиков и только для ближайших N целей, а в кадре остаются проекция и
 * несколько fill. Все сердечки в мире рисуются одним draw call.
 */
public final class HeartShowModule extends Module {
    private static final int N_HI = 20;
    private static final int N_LO = 12;
    private static final float[] HX_HI = new float[N_HI];
    private static final float[] HY_HI = new float[N_HI];
    private static final float[] HX_LO = new float[N_LO];
    private static final float[] HY_LO = new float[N_LO];

    static {
        shape(HX_HI, HY_HI, N_HI);
        shape(HX_LO, HY_LO, N_LO);
    }

    private static void shape(float[] hx, float[] hy, int n) {
        for (int i = 0; i < n; i++) {
            double t = Math.PI * 2.0 * i / n;
            double s = Math.sin(t);
            hx[i] = (float) (16.0 * s * s * s / 17.0);
            hy[i] = (float) ((13.0 * Math.cos(t) - 5.0 * Math.cos(2 * t) - 2.0 * Math.cos(3 * t) - Math.cos(4 * t)) / 17.0);
        }
    }

    private final Setting.Mode style = add(new Setting.Mode("style", "Style", "Red", "Gold", "Ice", "Purple", "Rainbow"));
    private final Setting.Bool showHearts = add(new Setting.Bool("hearts", "Show hearts", true));
    private final Setting.Num count = add(new Setting.Num("count", "Hearts per hit", 6f, 1f, 16f, 1f));
    private final Setting.Num size = add(new Setting.Num("size", "Size", 0.5f, 0.2f, 1f, 0.05f));
    private final Setting.Num life = add(new Setting.Num("life", "Lifetime (ms)", 1100f, 500f, 2500f, 100f));
    private final Setting.Bool showTitle = add(new Setting.Bool("title", "Damage title", true));
    private final Setting.Bool overhead = add(new Setting.Bool("overhead", "HP above head", true));
    private final Setting.Bool overheadMobs = add(new Setting.Bool("overhead_mobs", "HP of mobs too", false).<Setting.Bool>visibleWhen(() -> this.overhead.value));
    private final Setting.Bool overheadBar = add(new Setting.Bool("overhead_bar", "HP bar", true).<Setting.Bool>visibleWhen(() -> this.overhead.value));
    private final Setting.Num overheadRange = add(new Setting.Num("overhead_range", "HP range", 32f, 8f, 64f, 4f).<Setting.Num>visibleWhen(() -> this.overhead.value));
    private final Setting.Num overheadScale = add(new Setting.Num("overhead_scale", "HP scale", 1f, 0.6f, 1.6f, 0.1f).<Setting.Num>visibleWhen(() -> this.overhead.value));
    private final Setting.Num overheadMax = add(new Setting.Num("overhead_max", "Max HP labels", 8f, 1f, 24f, 1f).<Setting.Num>visibleWhen(() -> this.overhead.value));

    private static final class Pending {
        final LivingEntity e;
        float last;
        long t0;

        Pending(LivingEntity e, float last, long t0) { this.e = e; this.last = last; this.t0 = t0; }
    }

    private static final class Heart {
        final double x, y, z;
        final float vx, vz, size;
        final long t0;
        final int idx;

        Heart(double x, double y, double z, float vx, float vz, float size, long t0, int idx) {
            this.x = x; this.y = y; this.z = z; this.vx = vx; this.vz = vz; this.size = size; this.t0 = t0; this.idx = idx;
        }
    }

    private static final class Pop {
        final Text text;
        final long t0;
        final int rgb;

        Pop(Text text, long t0, int rgb) { this.text = text; this.t0 = t0; this.rgb = rgb; }
    }

    /** Цель с HP над головой: всё, что можно посчитать один раз, лежит здесь. */
    private static final class Label {
        final LivingEntity e;
        final Spring spring;
        float shownHp = Float.NaN;
        float shownAbs = Float.NaN;
        String hp = "";
        String ab = "";

        Label(LivingEntity e, float frac) {
            this.e = e;
            this.spring = new Spring(frac, 170f, 22f);
        }
    }

    private static final class Cand {
        final LivingEntity e;
        final double d2;

        Cand(LivingEntity e, double d2) { this.e = e; this.d2 = d2; }
    }

    private static final long TRACK_MS = 700L;
    private static final long TITLE_MS = 1300L;
    private static final int SCAN_TICKS = 4;

    private final List<Pending> pending = new ArrayList<>();
    private final List<Heart> hearts = new ArrayList<>();
    private final List<Pop> titles = new ArrayList<>();
    private final Map<Integer, Label> labelById = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Cand> cands = new ArrayList<>();
    private final float[] out = new float[2];
    private int scanTimer;

    public HeartShowModule() {
        super("heart_show", "HeartShow", "render");
        com.swinganimations.event.EventBus.on(com.swinganimations.event.AttackEvent.class, e -> onAttack(e.target()));
    }

    @Override
    public void onDisable() {
        labelById.clear();
        labels.clear();
        cands.clear();
        pending.clear();
        hearts.clear();
        titles.clear();
    }

    private static float total(LivingEntity e) {
        return e.getHealth() + e.getAbsorptionAmount();
    }

    /** Из AttackEntityCallback: запоминаем цель и её здоровье на момент удара. */
    public void onAttack(Entity e) {
        if (!isEnabled() || !(e instanceof LivingEntity le) || !le.isAlive()) return;
        if (e == MinecraftClient.getInstance().player) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < pending.size(); i++) {
            Pending p = pending.get(i);
            if (p.e == le) {
                p.t0 = now;
                return;
            }
        }
        pending.add(new Pending(le, total(le), now));
        while (pending.size() > 16) pending.remove(0);
    }

    private int heartCap() {
        int q = Perf.quality();
        return q == 0 ? 64 : (q == 1 ? 32 : 16);
    }

    @Override
    public void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        for (int i = pending.size() - 1; i >= 0; i--) {
            Pending p = pending.get(i);
            float cur = total(p.e);
            if (cur < p.last - 0.01f) {
                burst(p.e, p.last - cur, now);
            }
            p.last = cur;
            if (p.e.isRemoved() || now - p.t0 > TRACK_MS) pending.remove(i);
        }
        float lifeMs = life.value;
        for (int i = hearts.size() - 1; i >= 0; i--) {
            if (now - hearts.get(i).t0 > lifeMs) hearts.remove(i);
        }
        if (overhead.value) {
            int every = SCAN_TICKS * (Perf.quality() + 1);
            if (++scanTimer >= every) {
                scanTimer = 0;
                scan(mc);
            }
        } else if (!labels.isEmpty()) {
            labels.clear();
            labelById.clear();
        }
    }

    private void burst(LivingEntity e, float dmg, long now) {
        if (showHearts.value) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double top = e.getY() + e.getHeight() + 0.2;
            int n = (int) count.value;
            for (int i = 0; i < n; i++) {
                hearts.add(new Heart(
                        e.getX() + (r.nextDouble() - 0.5) * e.getWidth() * 1.3,
                        top + r.nextDouble() * 0.25,
                        e.getZ() + (r.nextDouble() - 0.5) * e.getWidth() * 1.3,
                        (r.nextFloat() - 0.5f) * 0.8f, (r.nextFloat() - 0.5f) * 0.8f,
                        size.value * (0.7f + 0.6f * r.nextFloat()), now + i * 35L, i));
            }
            int cap = heartCap();
            while (hearts.size() > cap) hearts.remove(0);
        }
        if (showTitle.value) {
            String v = Math.abs(dmg - Math.round(dmg)) < 0.05f
                    ? String.format(Locale.ROOT, "%d", Math.round(dmg))
                    : String.format(Locale.ROOT, "%.1f", dmg);
            titles.add(0, new Pop(Text.translatableWithFallback("swinganimations.heartshow.hit", "Hit %s", v),
                    now, base(0, now)));
            while (titles.size() > 3) titles.remove(titles.size() - 1);
        }
    }

    private int base(int idx, long now) {
        return switch (style.get()) {
            case "Gold" -> 0xFFC93C;
            case "Ice" -> 0x4FB4FF;
            case "Purple" -> 0xB65CFF;
            case "Rainbow" -> MathHelper.hsvToRgb((((now / 20L) + idx * 60L) % 360L) / 360f, 0.6f, 1f);
            default -> 0xFF3A4F;
        };
    }

    // ---------------- выбор целей для HP над головой (раз в несколько тиков) ----------------

    private int labelLimit() {
        int max = (int) overheadMax.value;
        int q = Perf.quality();
        return Math.max(1, q == 0 ? max : (q == 1 ? max / 2 : max / 4));
    }

    private void consider(Entity en, ClientPlayerEntity me, double r2) {
        if (!(en instanceof LivingEntity le) || le == me || !le.isAlive() || le.isInvisible()) return;
        if (le instanceof ArmorStandEntity) return;
        if (!(le instanceof PlayerEntity) && !overheadMobs.value) return;
        double d2 = me.squaredDistanceTo(le);
        if (d2 > r2) return;
        cands.add(new Cand(le, d2));
    }

    /** Перебор сущностей и проверка видимости лучом: только здесь, не в кадре. */
    private void scan(MinecraftClient mc) {
        ClientWorld w = mc.world;
        ClientPlayerEntity me = mc.player;
        if (w == null || me == null) return;
        double r2 = (double) overheadRange.value * overheadRange.value;
        cands.clear();
        if (overheadMobs.value) {
            for (Entity en : w.getEntities()) consider(en, me, r2);
        } else {
            for (PlayerEntity p : w.getPlayers()) consider(p, me, r2);
        }
        cands.sort((a, b) -> Double.compare(a.d2, b.d2));

        int limit = labelLimit();
        int rays = limit * 2;
        labels.clear();
        Map<Integer, Label> next = new HashMap<>();
        for (int i = 0; i < cands.size() && labels.size() < limit && rays > 0; i++) {
            LivingEntity le = cands.get(i).e;
            rays--;
            if (!me.canSee(le)) continue;
            Label l = labelById.get(le.getId());
            if (l == null || l.e != le) {
                float frac = MathHelper.clamp(le.getHealth() / Math.max(1f, le.getMaxHealth()), 0f, 1f);
                l = new Label(le, frac);
            }
            next.put(le.getId(), l);
            labels.add(l);
        }
        labelById.clear();
        labelById.putAll(next);
        cands.clear();
    }

    // ---------------- сердечки в мире ----------------

    @Override
    public boolean wantsWorldRender() { return !hearts.isEmpty(); }

    @Override
    public void render(RenderContext c) {
        int n = hearts.size();
        if (n == 0) return;
        float lifeMs = life.value;
        boolean lo = Perf.quality() >= 1;
        int segs = lo ? N_LO : N_HI;
        float[] hx = lo ? HX_LO : HX_HI;
        float[] hy = lo ? HY_LO : HY_HI;
        boolean rainbow = style.is("Rainbow");
        int flat = rainbow ? 0 : base(0, c.now);
        Vec3d cam = c.camera.getCameraPos();
        Batch.begin(true, false);
        Batch t = Batch.triangles();
        for (int k = 0; k < n; k++) {
            Heart h = hearts.get(k);
            long age = c.now - h.t0;
            if (age < 0) continue;
            float f = age / lifeMs;
            if (f >= 1f) continue;
            double dx = h.x - cam.x, dz = h.z - cam.z;
            if (dx * dx + dz * dz > 2500.0) continue;
            float rise = 1f - (1f - f) * (1f - f);
            double x = h.x + h.vx * f + Math.sin(f * 7.0 + h.idx) * 0.04;
            double y = h.y + 0.9 * rise;
            double z = h.z + h.vz * f;
            float alpha = f < 0.7f ? 1f : (1f - f) / 0.3f;
            float s = h.size * 0.5f * (0.5f + 0.5f * Math.min(1f, f * 6f));
            int rgb = rainbow ? base(h.idx, c.now) : flat;
            int dark = Batch.argb(Batch.mix(rgb, 0x000000, 0.6f), alpha * 0.9f);
            heart(t, c, segs, hx, hy, x, y, z, s * 1.2f, dark, dark);
            heart(t, c, segs, hx, hy, x, y, z, s, Batch.argb(Batch.mix(rgb, 0xFFFFFF, 0.35f), alpha), Batch.argb(rgb, alpha));
        }
        t.flush();
        Batch.end();
    }

    private static void heart(Batch t, RenderContext c, int segs, float[] hx, float[] hy,
                              double x, double y, double z, float s, int centerCol, int rimCol) {
        for (int i = 0; i < segs; i++) {
            int j = i + 1 == segs ? 0 : i + 1;
            t.v(c.m, x, y, z, centerCol);
            pt(t, c, x, y, z, hx[i] * s, hy[i] * s, rimCol);
            pt(t, c, x, y, z, hx[j] * s, hy[j] * s, rimCol);
        }
    }

    private static void pt(Batch t, RenderContext c, double x, double y, double z, float lx, float ly, int col) {
        t.v(c.m, x + c.rx * lx + c.ux * ly, y + c.ry * lx + c.uy * ly, z + c.rz * lx + c.uz * ly, col);
    }

    // ---------------- тайтл с уроном ----------------

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        overhead(ctx, tickDelta);
        if (titles.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (int i = titles.size() - 1; i >= 0; i--) {
            if (now - titles.get(i).t0 > TITLE_MS) titles.remove(i);
        }
        if (titles.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        for (int i = 0; i < titles.size(); i++) {
            Pop p = titles.get(i);
            long age = now - p.t0;
            float a = age < 120 ? age / 120f : (age > 1000 ? 1f - (age - 1000) / 300f : 1f);
            if (i > 0) a *= 0.7f;
            float pop = age < 150 ? 1.35f - 0.35f * (age / 150f) : 1f;
            int alpha = MathHelper.clamp((int) (a * 255f), 5, 255);
            float scale = (i == 0 ? 2.2f : 1.6f) * pop;
            int tw = mc.textRenderer.getWidth(p.text);
            org.joml.Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(w / 2f, h * 0.34f + i * 22f);
            m.scale(scale, scale);
            ctx.drawText(mc.textRenderer, p.text, -tw / 2, -4, (alpha << 24) | (p.rgb & 0xFFFFFF), true);
            m.popMatrix();
        }
    }

    // ---------------- HP над головой (в кадре: только проекция и рисование) ----------------

    private static String fmt(float v) {
        return Math.abs(v - Math.round(v)) < 0.05f ? Integer.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Строки HP/поглощения пересчитываются только когда значение реально изменилось. */
    private static void refreshText(Label l, float cur, float abs) {
        if (cur != l.shownHp) {
            l.shownHp = cur;
            l.hp = fmt(cur);
        }
        if (abs != l.shownAbs) {
            l.shownAbs = abs;
            l.ab = abs > 0.05f ? "+" + fmt(abs) : "";
        }
    }

    private void overhead(DrawContext ctx, float td) {
        if (!overhead.value || labels.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.options.hudHidden || !Projection.valid()) return;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        boolean quality0 = Perf.quality() == 0;
        org.joml.Matrix3x2fStack m = ctx.getMatrices();
        for (int i = 0; i < labels.size(); i++) {
            Label l = labels.get(i);
            LivingEntity le = l.e;
            if (le.isRemoved() || !le.isAlive()) continue;
            double x = MathHelper.lerp(td, le.lastRenderX, le.getX());
            double y = MathHelper.lerp(td, le.lastRenderY, le.getY()) + le.getHeight() + 0.85;
            double z = MathHelper.lerp(td, le.lastRenderZ, le.getZ());
            if (!Projection.project(x, y, z, out, sw, sh)) continue;

            float cur = le.getHealth();
            float max = Math.max(1f, le.getMaxHealth());
            float abs = le.getAbsorptionAmount();
            l.spring.target = MathHelper.clamp(cur / max, 0f, 1f);
            float f = l.spring.update();
            int color = Hud.hpColor(f);
            refreshText(l, cur, abs);
            String hp = l.hp;
            String ab = l.ab;

            double d2 = Projection.distanceSq(x, y, z);
            boolean bar = overheadBar.value && quality0 && d2 < 28.0 * 28.0;
            int content = 10 + Hud.w(hp) + (ab.isEmpty() ? 0 : 3 + Hud.w(ab));
            int pw = Math.max(content, 36) + 10;
            int ph = bar ? 21 : 13;
            float scale = MathHelper.clamp(3.2f / (float) Math.sqrt(Math.max(d2, 1.0)) * 1f, 0.55f, 1.15f) * overheadScale.value;

            m.pushMatrix();
            m.translate(out[0], out[1]);
            m.scale(scale, scale);
            int top = -ph;
            Ui.rect(ctx, -pw / 2, top, pw, ph, 3, 0xA0000000);
            int tx = -content / 2;
            Hud.heart(ctx, tx, top + 3, color);
            Hud.text(ctx, hp, tx + 10, top + 3, color);
            if (!ab.isEmpty()) Hud.text(ctx, ab, tx + 13 + Hud.w(hp), top + 3, 0xFFFFD24A);
            if (bar) {
                int bw = pw - 8;
                Hud.bar(ctx, -pw / 2 + 4, top + 14, bw, 3, f, color);
                if (abs > 0.05f) ctx.fill(-pw / 2 + 4, top + 17, -pw / 2 + 4 + Math.round(bw * MathHelper.clamp(abs / max, 0f, 1f)), top + 18, 0xFFFFD24A);
            }
            m.popMatrix();
        }
    }
}
