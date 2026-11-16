package com.swinganimations.module;

import com.swinganimations.gui.Hud;
import com.swinganimations.gui.Ui;
import com.swinganimations.render.Projection;
import com.swinganimations.util.Perf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Таймер над зажжённым динамитом: зелёный → жёлтый → красный по мере приближения взрыва.
 *
 * Предсказание. Сервер присылает «запал» (fuse) метаданными, но на кастомных серверах он бывает
 * нестандартным, замороженным или просто неверным, а при низком TPS время идёт медленнее 20 тиков/с.
 * Поэтому мод не верит запалу слепо, а учится: каждый динамит, замеченный с момента появления,
 * после взрыва даёт замер «сколько реальных миллисекунд длится один тик запала». Медиана замеров
 * хранится отдельно для каждого сервера (и в конфиге), и таймер считается в реальном времени:
 * осталось = k * полный_запал - прошло. На ванильном сервере k = 50 мс, то есть классические 4 секунды.
 * До первого замера работает честный запал из метаданных.
 *
 * Только клиент: пакеты и геймплей не затрагиваются.
 */
public final class TntTimerModule extends Module {
    private static final float DEFAULT_MS = 50f;
    private static final float MIN_MS = 15f;
    private static final float MAX_MS = 250f;
    private static final double LEARN_RANGE_SQ = 48.0 * 48.0;

    private final Setting.Num range = add(new Setting.Num("range", "Range", 48f, 8f, 128f, 4f));
    private final Setting.Num scale = add(new Setting.Num("scale", "Scale", 1f, 0.6f, 1.8f, 0.1f));
    private final Setting.Num maxLabels = add(new Setting.Num("max_labels", "Max labels", 16f, 1f, 40f, 1f));
    private final Setting.Bool predict = add(new Setting.Bool("predict", "Learn server fuse time", true));
    private final Setting.Bool smooth = add(new Setting.Bool("smooth", "Smooth colors", true));
    private final Setting.Bool decimals = add(new Setting.Bool("decimals", "Decimals", true));
    private final Setting.Bool bar = add(new Setting.Bool("bar", "Progress bar", true));
    /** Скрытая настройка: выученные k по серверам, «адрес=мс;адрес=мс». */
    private final Setting.Str learned = add(new Setting.Str("learned", "Learned", ""));

    private static final class Rec {
        TntEntity e;
        long firstMs;
        int firstAge;
        int total;
        int lastFuse;
        boolean spawnSeen;
        int seenTick;
        double lastD2;
        double d2;
    }

    private static final class Learn {
        final float[] ring = new float[9];
        int n;
        int pos;

        void add(float v) {
            ring[pos] = v;
            pos = (pos + 1) % ring.length;
            if (n < ring.length) n++;
        }

        float median() {
            if (n == 0) return DEFAULT_MS;
            float[] c = Arrays.copyOf(ring, n);
            Arrays.sort(c);
            return c[n / 2];
        }
    }

    private final Map<Integer, Rec> recs = new HashMap<>();
    private final Map<String, Learn> learn = new LinkedHashMap<>();
    private final List<Rec> vis = new ArrayList<>();
    private final float[] out = new float[2];
    private ClientWorld lastWorld;
    private String key = "local";
    private boolean loaded;
    private int clientTick;

    public TntTimerModule() {
        super("tnt_timer", "TntTimer", "hud");
    }

    @Override
    public void onDisable() {
        recs.clear();
        vis.clear();
    }

    // ---------------- обучение ----------------

    private void loadLearned() {
        if (loaded) return;
        loaded = true;
        for (String part : learned.value.split(";")) {
            int i = part.lastIndexOf('=');
            if (i <= 0) continue;
            try {
                float v = Float.parseFloat(part.substring(i + 1));
                if (v < MIN_MS || v > MAX_MS) continue;
                Learn l = new Learn();
                l.add(v);
                learn.put(part.substring(0, i), l);
            } catch (NumberFormatException ignored) {
                // битая запись: пропускаем
            }
        }
    }

    private void storeLearned() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Learn> en : learn.entrySet()) {
            sb.append(en.getKey()).append('=')
                    .append(String.format(Locale.ROOT, "%.2f", en.getValue().median())).append(';');
        }
        learned.value = sb.toString();
        ModuleManager.save();
    }

    /** Текущее k: мс реального времени на один тик запала на этом сервере. */
    private float msPerTick() {
        if (!predict.value) return DEFAULT_MS;
        Learn l = learn.get(key);
        return l == null ? DEFAULT_MS : MathHelper.clamp(l.median(), MIN_MS, MAX_MS);
    }

    /** Динамит исчез: если мы видели его с появления и он был рядом, это замер длительности запала. */
    private void sample(Rec r, long now) {
        if (!predict.value || !r.spawnSeen || r.total < 10 || r.lastD2 > LEARN_RANGE_SQ) return;
        float elapsed = (now - r.firstMs) + r.firstAge * DEFAULT_MS;
        float k = elapsed / r.total;
        if (k < MIN_MS || k > MAX_MS) return;
        Learn l = learn.get(key);
        if (l == null) {
            if (learn.size() >= 40) learn.remove(learn.keySet().iterator().next());
            l = new Learn();
            learn.put(key, l);
        }
        l.add(k);
        storeLearned();
    }

    // ---------------- тик ----------------

    @Override
    public void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null || mc.player == null) return;
        loadLearned();
        if (w != lastWorld) {
            // смена мира/сервера: старые записи не должны давать ложных замеров
            recs.clear();
            lastWorld = w;
        }
        ServerInfo si = mc.getCurrentServerEntry();
        key = si == null ? "local" : si.address.toLowerCase(Locale.ROOT);

        clientTick++;
        long now = System.currentTimeMillis();
        for (Entity en : w.getEntities()) {
            if (!(en instanceof TntEntity t)) continue;
            int fuse = t.getFuse();
            Rec r = recs.get(t.getId());
            if (r == null) {
                r = new Rec();
                r.firstMs = now;
                r.firstAge = t.age;
                r.total = fuse + t.age;
                r.spawnSeen = t.age <= 2 && fuse >= 8;
                recs.put(t.getId(), r);
            }
            r.e = t;
            r.lastFuse = fuse;
            r.seenTick = clientTick;
            r.lastD2 = mc.player.squaredDistanceTo(t);
        }
        Iterator<Rec> it = recs.values().iterator();
        while (it.hasNext()) {
            Rec r = it.next();
            if (r.seenTick == clientTick) continue;
            it.remove();
            sample(r, now);
        }
    }

    // ---------------- HUD ----------------

    /** Сколько миллисекунд осталось до взрыва. */
    private float remainingMs(Rec r, long now, float td, float k) {
        if (r.spawnSeen) {
            return k * r.total - (now - r.firstMs) - r.firstAge * DEFAULT_MS;
        }
        // Динамит был уже зажжён, когда мы его увидели: берём запал из метаданных, переведённый в реальное время.
        return k * (r.lastFuse - td);
    }

    @Override
    public void renderHud(DrawContext ctx, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (recs.isEmpty() || mc.world == null || mc.player == null || mc.options.hudHidden || !Projection.valid()) return;

        double r2 = (double) range.value * range.value;
        vis.clear();
        for (Rec r : recs.values()) {
            TntEntity t = r.e;
            if (t == null || t.isRemoved()) continue;
            double x = MathHelper.lerp(td, t.lastRenderX, t.getX());
            double y = MathHelper.lerp(td, t.lastRenderY, t.getY()) + t.getHeight() + 0.45;
            double z = MathHelper.lerp(td, t.lastRenderZ, t.getZ());
            r.d2 = Projection.distanceSq(x, y, z);
            if (r.d2 <= r2) vis.add(r);
        }
        if (vis.isEmpty()) return;
        vis.sort((a, b) -> Double.compare(a.d2, b.d2));

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();
        float k = msPerTick();
        boolean withBar = bar.value && Perf.quality() < 2;
        org.joml.Matrix3x2fStack m = ctx.getMatrices();
        int n = Math.min(vis.size(), (int) maxLabels.value);
        for (int i = 0; i < n; i++) {
            Rec r = vis.get(i);
            TntEntity t = r.e;
            double x = MathHelper.lerp(td, t.lastRenderX, t.getX());
            double y = MathHelper.lerp(td, t.lastRenderY, t.getY()) + t.getHeight() + 0.45;
            double z = MathHelper.lerp(td, t.lastRenderZ, t.getZ());
            if (!Projection.project(x, y, z, out, sw, sh)) continue;

            float rem = Math.max(0f, remainingMs(r, now, td, k));
            float totalMs = r.spawnSeen ? k * r.total : k * Math.max(1, Math.max(r.total, r.lastFuse));
            float frac = MathHelper.clamp(rem / Math.max(1f, totalMs), 0f, 1f);
            int color = color(frac);

            float sec = rem / 1000f;
            String txt;
            if (decimals.value) {
                int tenths = (int) (sec * 10f);
                txt = (tenths / 10) + "." + (tenths % 10) + "s";
            } else {
                txt = (int) Math.ceil(sec) + "s";
            }

            int tw = Hud.w(txt);
            int pw = Math.max(tw, 18) + 10;
            int ph = withBar ? 16 : 13;
            float s = MathHelper.clamp(3.2f / (float) Math.sqrt(Math.max(r.d2, 1.0)), 0.55f, 1.15f) * scale.value;

            m.pushMatrix();
            m.translate(out[0], out[1]);
            m.scale(s, s);
            int top = -ph;
            Ui.rect(ctx, -pw / 2, top, pw, ph, 3, 0xA0000000);
            Hud.text(ctx, txt, -tw / 2, top + 3, color);
            if (withBar) Hud.bar(ctx, -pw / 2 + 4, top + 12, pw - 8, 2, frac, color);
            m.popMatrix();
        }
    }

    private int color(float frac) {
        if (smooth.value) {
            // красный наступает чуть раньше нуля, чтобы последние мгновения читались сразу
            return Hud.hpColor(MathHelper.clamp((frac - 0.12f) / 0.88f, 0f, 1f));
        }
        if (frac > 0.5f) return 0xFF55FF55;
        if (frac > 0.25f) return 0xFFFFD24A;
        return 0xFFFF5555;
    }
}
