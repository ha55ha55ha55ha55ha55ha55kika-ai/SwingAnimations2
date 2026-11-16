package com.swinganimations.module;

import com.mojang.blaze3d.systems.RenderSystem;
import com.swinganimations.event.AttackEvent;
import com.swinganimations.event.EventBus;
import com.swinganimations.event.KillEvent;
import com.swinganimations.render.Batch;
import com.swinganimations.render.Trig;
import com.swinganimations.util.Perf;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * KillEffect: после убийства сущность распадается на части (голова, торс, руки, ноги / тело, голова, лапы),
 * летят брызги крови, на полу остаются растекающиеся лужи, а на экран попадают красные потёки.
 * Части процедурные (цветные боксы по пропорциям сущности), физика считается в тиках, рендер интерполируется.
 */
public final class KillEffectModule extends Module {
    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final Set<String> HUMANOIDS = Set.of("player", "zombie", "husk", "drowned", "zombie_villager",
            "zombified_piglin", "skeleton", "stray", "wither_skeleton", "bogged", "piglin", "piglin_brute",
            "villager", "wandering_trader", "pillager", "vindicator", "evoker", "illusioner", "witch", "enderman");

    private static final class Part {
        double x, y, z, px, py, pz, vx, vy, vz;
        float rx, ry, rz, prx, pry, prz, avx, avy, avz, trx, trz;
        final float hx, hy, hz;
        final int rgb;
        final float blood;
        int age, maxAge, light = 15;
        boolean resting;

        Part(float hx, float hy, float hz, int rgb, float blood) {
            this.hx = hx; this.hy = hy; this.hz = hz; this.rgb = rgb; this.blood = blood;
        }
    }

    private static final class Drop {
        double x, y, z, px, py, pz, vx, vy, vz;
        float size;
        int age;
    }

    private static final class Puddle {
        final double x, y, z;
        float target;
        final long t0;
        final float[] shape = new float[12];

        Puddle(double x, double y, double z, float target, long t0, Random r) {
            this.x = x; this.y = y; this.z = z; this.target = target; this.t0 = t0;
            float[] raw = new float[12];
            for (int i = 0; i < 12; i++) raw[i] = 0.72f + r.nextFloat() * 0.55f;
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < 12; i++) shape[i] = (raw[i] + raw[(i + 1) % 12]) * 0.5f;
                System.arraycopy(shape, 0, raw, 0, 12);
            }
        }
    }

    private static final class Spurt {
        final double x, y, z;
        int ticks;

        Spurt(double x, double y, double z, int ticks) { this.x = x; this.y = y; this.z = z; this.ticks = ticks; }
    }

    private static final class ScreenDrop {
        float x, y, vy, size, alpha;
        long start;
    }

    private static final class Splat {
        float x, y, r, slide, life;
        long t0;
    }

    private final Setting.Mode scope = add(new Setting.Mode("scope", "Kills", "Mine", "Nearby"));
    private final Setting.Num range = add(new Setting.Num("range", "Max distance", 24f, 8f, 64f, 4f));
    private final Setting.Bool dismember = add(new Setting.Bool("dismember", "Dismember", true));
    private final Setting.Bool hideBody = add(new Setting.Bool("hide_body", "Hide vanilla corpse", true));
    private final Setting.Num bloodAmount = add(new Setting.Num("blood", "Blood amount", 1f, 0.3f, 2f, 0.1f));
    private final Setting.Bool puddlesOn = add(new Setting.Bool("puddles", "Blood puddles", true));
    private final Setting.Bool screenBlood = add(new Setting.Bool("screen_blood", "Blood on screen", true));
    private final Setting.Num screenRange = add(new Setting.Num("screen_range", "Screen blood distance", 6f, 2f, 16f, 1f)
            .<Setting.Num>visibleWhen(() -> this.screenBlood.value));
    private final Setting.Num partsLife = add(new Setting.Num("parts_life", "Parts lifetime (s)", 10f, 3f, 30f, 1f));
    private final Setting.Num puddleLife = add(new Setting.Num("puddle_life", "Puddle lifetime (s)", 25f, 5f, 90f, 5f)
            .<Setting.Num>visibleWhen(() -> this.puddlesOn.value));

    private final Random rnd = new Random();
    private final List<Part> parts = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>();
    private final List<Puddle> puddles = new ArrayList<>();
    private final List<Spurt> spurts = new ArrayList<>();
    private final Map<Integer, Long> lastHit = new HashMap<>();
    private final Map<Integer, Long> hidden = new HashMap<>();
    private final List<ScreenDrop> screenDrops = new ArrayList<>();
    private final List<Splat> splats = new ArrayList<>();
    private long lastHud;

    private static final Matrix3f M3 = new Matrix3f();
    private static final Matrix4f MAT = new Matrix4f();
    private static final Vector3f V = new Vector3f();
    private static final Vector3f NRM = new Vector3f();

    public KillEffectModule() {
        super("kill_effect", "KillEffect", "render");
        EventBus.on(AttackEvent.class, e -> {
            if (isEnabled()) lastHit.put(e.target().getId(), System.currentTimeMillis());
        });
        EventBus.on(KillEvent.class, e -> {
            if (isEnabled()) onKill(e.entity());
        });
    }

    @Override
    public void onDisable() {
        parts.clear();
        drops.clear();
        puddles.clear();
        spurts.clear();
        hidden.clear();
        lastHit.clear();
        screenDrops.clear();
        splats.clear();
    }

    public boolean isHidden(Entity e) {
        return enabled && !hidden.isEmpty() && hidden.containsKey(e.getId());
    }

    @Override
    public boolean wantsWorldRender() { return !parts.isEmpty() || !drops.isEmpty() || !puddles.isEmpty(); }

    // ---------------- события ----------------

    private void onKill(LivingEntity le) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || le == mc.player) return;
        if (le.getHeight() < 0.2f) return;
        double r = range.value;
        if (mc.player.squaredDistanceTo(le) > r * r) return;
        long now = System.currentTimeMillis();
        if (scope.is("Mine")) {
            Long t = lastHit.get(le.getId());
            if (t == null || now - t > 4500L) return;
        }
        spawn(le, mc, now);
    }

    private int dropCap() { return Perf.quality() == 0 ? 320 : (Perf.quality() == 1 ? 160 : 70); }

    private int puddleCap() { return Perf.quality() == 0 ? 64 : (Perf.quality() == 1 ? 32 : 14); }

    private void spawn(LivingEntity le, MinecraftClient mc, long now) {
        String id = Registries.ENTITY_TYPE.getId(le.getType()).getPath();
        float h = le.getHeight();
        float w = le.getWidth();
        double ex = le.getX(), ey = le.getY(), ez = le.getZ();
        float yaw = (float) Math.toRadians(le.getBodyYaw());
        double kx = ex - mc.player.getX();
        double kz = ez - mc.player.getZ();
        double kl = Math.sqrt(kx * kx + kz * kz);
        if (kl < 1e-3) { kx = 0; kz = 1; kl = 1; }
        kx /= kl;
        kz /= kl;
        int[] pal = palette(id);
        int life = (int) (partsLife.value * 20f);

        if (dismember.value) {
            if (HUMANOIDS.contains(id)) {
                float u = h / 32f;
                for (int side = -1; side <= 1; side += 2) {
                    addPart(ex, ey, ez, yaw, side * 2 * u, 6 * u, 0, 2 * u, 6 * u, 2 * u, pal[2], 0.15f, kx, kz, life);   // нога
                    addPart(ex, ey, ez, yaw, side * 6 * u, 18 * u, 0, 2 * u, 6 * u, 2 * u, pal[3], 0.3f, kx, kz, life);   // рука
                }
                addPart(ex, ey, ez, yaw, 0, 18 * u, 0, 4 * u, 6 * u, 2 * u, pal[1], 0.25f, kx, kz, life);                 // торс
                addPart(ex, ey, ez, yaw, 0, 28 * u, 0, 4 * u, 4 * u, 4 * u, pal[0], 0.2f, kx, kz, life);                  // голова
            } else {
                float hw = Math.max(0.06f, w * 0.34f);
                float len = Math.max(hw, w * 0.62f);
                addPart(ex, ey, ez, yaw, 0, h * 0.55f, 0, hw, Math.max(0.05f, h * 0.2f), len, pal[1], 0.25f, kx, kz, life);   // тело
                float hs = Math.max(0.05f, h * 0.17f);
                addPart(ex, ey, ez, yaw, 0, h * 0.78f, len + hs * 0.6f, hs, hs, hs, pal[0], 0.2f, kx, kz, life);              // голова
                if (h >= 0.4f) {
                    float lh = Math.max(0.05f, h * 0.17f);
                    float lw = Math.max(0.03f, w * 0.1f);
                    for (int sx = -1; sx <= 1; sx += 2) {
                        for (int sz = -1; sz <= 1; sz += 2) {
                            addPart(ex, ey, ez, yaw, sx * w * 0.25f, lh, sz * len * 0.6f, lw, lh, lw, pal[2], 0.1f, kx, kz, life);
                        }
                    }
                }
            }
            int gibs = (int) (8 * bloodAmount.value) + 4;
            int[] gibColors = {0x8F1B1B, 0xB03A3A, 0xC85A5A, 0x5A0F0F};
            for (int i = 0; i < gibs; i++) {
                float s = 0.035f + rnd.nextFloat() * 0.05f;
                Part p = new Part(s, s, s, gibColors[rnd.nextInt(gibColors.length)], 0f);
                p.x = p.px = ex + (rnd.nextDouble() - 0.5) * w * 0.5;
                p.y = p.py = ey + h * (0.3 + rnd.nextDouble() * 0.5);
                p.z = p.pz = ez + (rnd.nextDouble() - 0.5) * w * 0.5;
                double sp = 0.12 + rnd.nextDouble() * 0.3;
                double a = rnd.nextDouble() * Math.PI * 2;
                p.vx = Math.cos(a) * sp + kx * 0.1;
                p.vz = Math.sin(a) * sp + kz * 0.1;
                p.vy = 0.1 + rnd.nextDouble() * 0.3;
                p.avx = (rnd.nextFloat() - 0.5f) * 0.8f;
                p.avy = (rnd.nextFloat() - 0.5f) * 0.8f;
                p.avz = (rnd.nextFloat() - 0.5f) * 0.8f;
                p.maxAge = (int) (life * 0.7f) + rnd.nextInt(30);
                parts.add(p);
            }
            while (parts.size() > 160) parts.remove(0);
        }

        // брызги: взрыв из центра + фонтан из шеи
        int burst = (int) (38 * bloodAmount.value * (Perf.quality() == 0 ? 1f : 0.5f));
        for (int i = 0; i < burst; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double sp = 0.07 + rnd.nextDouble() * 0.3;
            addDrop(ex, ey + h * (0.4 + rnd.nextDouble() * 0.4), ez,
                    Math.cos(a) * sp + kx * 0.08, 0.06 + rnd.nextDouble() * 0.3, Math.sin(a) * sp + kz * 0.08,
                    0.03f + rnd.nextFloat() * 0.04f);
        }
        spurts.add(new Spurt(ex, ey + h * 0.8, ez, 28));

        if (hideBody.value) hidden.put(le.getId(), now + 2500L);

        if (screenBlood.value && mc.options.getPerspective().isFirstPerson()) {
            double d = mc.player.distanceTo(le);
            if (d <= screenRange.value) spawnScreen(mc, (float) (1.0 - d / screenRange.value), now);
        }
    }

    private void addPart(double ex, double ey, double ez, float yaw, float lx, float ly, float lz,
                         float hx, float hy, float hz, int rgb, float blood, double kx, double kz, int life) {
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        double ox = lx * cos - lz * sin;
        double oz = lx * sin + lz * cos;
        Part p = new Part(hx, hy, hz, rgb, blood);
        p.x = p.px = ex + ox;
        p.y = p.py = ey + ly + 0.02;
        p.z = p.pz = ez + oz;
        p.ry = p.pry = -yaw;
        p.vx = ox * 0.9 + kx * 0.14 + (rnd.nextDouble() - 0.5) * 0.06;
        p.vz = oz * 0.9 + kz * 0.14 + (rnd.nextDouble() - 0.5) * 0.06;
        p.vy = 0.1 + rnd.nextDouble() * 0.2;
        p.avx = (rnd.nextFloat() - 0.5f) * 0.6f;
        p.avy = (rnd.nextFloat() - 0.5f) * 0.6f;
        p.avz = (rnd.nextFloat() - 0.5f) * 0.6f;
        p.maxAge = life + rnd.nextInt(40);
        parts.add(p);
    }

    private void addDrop(double x, double y, double z, double vx, double vy, double vz, float size) {
        if (drops.size() >= dropCap()) return;
        Drop d = new Drop();
        d.x = d.px = x; d.y = d.py = y; d.z = d.pz = z;
        d.vx = vx; d.vy = vy; d.vz = vz;
        d.size = size;
        drops.add(d);
    }

    private void addPuddle(double x, double y, double z, float amount) {
        for (Puddle p : puddles) {
            double dx = p.x - x, dz = p.z - z;
            if (dx * dx + dz * dz < 0.09 && Math.abs(p.y - y) < 0.2) {
                p.target = Math.min(0.8f, p.target + 0.03f + amount * 0.4f);
                return;
            }
        }
        if (puddles.size() >= puddleCap()) return;
        puddles.add(new Puddle(x, y, z, 0.16f + rnd.nextFloat() * 0.12f + amount, System.currentTimeMillis(), rnd));
    }

    private void spawnScreen(MinecraftClient mc, float intensity, long now) {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int nDrops = (int) ((8 + 16 * intensity) * (Perf.quality() == 0 ? 1f : 0.5f));
        for (int i = 0; i < nDrops; i++) {
            ScreenDrop d = new ScreenDrop();
            d.x = rnd.nextFloat() * sw;
            d.y = rnd.nextFloat() * sh * 0.45f;
            d.vy = 15f + rnd.nextFloat() * 60f;
            d.size = 2f + rnd.nextFloat() * 3f;
            d.alpha = 0.75f + rnd.nextFloat() * 0.2f;
            d.start = now + (long) (rnd.nextFloat() * 700f);
            screenDrops.add(d);
        }
        int nSplat = 2 + (int) (3 * intensity);
        for (int i = 0; i < nSplat; i++) {
            Splat s = new Splat();
            s.x = sw * (0.1f + rnd.nextFloat() * 0.8f);
            s.y = sh * (0.05f + rnd.nextFloat() * 0.55f);
            s.r = 8f + rnd.nextFloat() * 18f * (0.5f + intensity);
            s.slide = 6f + rnd.nextFloat() * 10f;
            s.life = 3.5f + rnd.nextFloat() * 2.5f;
            s.t0 = now;
            splats.add(s);
            for (int k = 0; k < 2; k++) {
                ScreenDrop d = new ScreenDrop();
                d.x = s.x + (rnd.nextFloat() - 0.5f) * s.r;
                d.y = s.y + s.r * 0.5f;
                d.vy = 10f + rnd.nextFloat() * 25f;
                d.size = 2.5f + rnd.nextFloat() * 2.5f;
                d.alpha = 0.9f;
                d.start = now + 80L + (long) (rnd.nextFloat() * 400f);
                screenDrops.add(d);
            }
        }
        while (screenDrops.size() > 70) screenDrops.remove(0);
        while (splats.size() > 12) splats.remove(0);
    }

    // ---------------- физика (тики) ----------------

    private static boolean solidAt(World w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        BlockState st = w.getBlockState(bp);
        return !st.getCollisionShape(w, bp).isEmpty();
    }

    /** Верх поверхности блока, если точка лежит внутри его коллизии; иначе NaN. */
    private static double surfaceY(World w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        BlockState st = w.getBlockState(bp);
        VoxelShape sh = st.getCollisionShape(w, bp);
        if (sh.isEmpty()) return Double.NaN;
        double top = bp.getY() + sh.getMax(Direction.Axis.Y);
        return y < top ? top : Double.NaN;
    }

    private static double halfY(Part p) {
        M3.identity().rotateYXZ(p.ry, p.rx, p.rz);
        return Math.abs(M3.m01()) * p.hx + Math.abs(M3.m11()) * p.hy + Math.abs(M3.m21()) * p.hz;
    }

    @Override
    public void tick(MinecraftClient mc) {
        World w = mc.world;
        if (w == null) return;
        long now = System.currentTimeMillis();
        Entity aimed = mc.targetedEntity;
        if (aimed != null) lastHit.put(aimed.getId(), now);
        if (lastHit.size() > 40) lastHit.values().removeIf(v -> now - v > 6000L);
        if (!hidden.isEmpty()) hidden.values().removeIf(v -> now > v);

        for (int i = parts.size() - 1; i >= 0; i--) {
            Part p = parts.get(i);
            tickPart(w, p);
            if (p.age > p.maxAge) parts.remove(i);
        }
        for (int i = spurts.size() - 1; i >= 0; i--) {
            Spurt s = spurts.get(i);
            float k = s.ticks / 28f;
            int n = Math.max(1, Math.round(3f * bloodAmount.value * (0.3f + 0.7f * k)));
            for (int j = 0; j < n; j++) {
                addDrop(s.x, s.y, s.z, (rnd.nextDouble() - 0.5) * 0.14, (0.16 + rnd.nextDouble() * 0.16) * (0.5 + 0.5 * k),
                        (rnd.nextDouble() - 0.5) * 0.14, 0.025f + rnd.nextFloat() * 0.03f);
            }
            if (--s.ticks <= 0) spurts.remove(i);
        }
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            d.px = d.x; d.py = d.y; d.pz = d.z;
            d.age++;
            d.vy -= 0.045;
            d.vx *= 0.985;
            d.vz *= 0.985;
            double nx = d.x + d.vx, ny = d.y + d.vy, nz = d.z + d.vz;
            double g = surfaceY(w, nx, ny, nz);
            if (!Double.isNaN(g)) {
                if (puddlesOn.value && d.size > 0.02f && rnd.nextFloat() < 0.6f) addPuddle(nx, g, nz, d.size);
                drops.remove(i);
                continue;
            }
            if (solidAt(w, nx, ny, nz)) {
                d.vx = 0;
                d.vz = 0;
                d.vy = Math.min(d.vy, 0.0) * 0.5;
            } else {
                d.x = nx; d.y = ny; d.z = nz;
            }
            if (d.age > 120) drops.remove(i);
        }
        if (!puddles.isEmpty()) {
            long life = (long) (puddleLife.value * 1000f);
            puddles.removeIf(p -> now - p.t0 > life);
        }
    }

    private void tickPart(World w, Part p) {
        p.px = p.x; p.py = p.y; p.pz = p.z;
        p.prx = p.rx; p.pry = p.ry; p.prz = p.rz;
        p.age++;
        if (p.age % 10 == 1) p.light = w.getLightLevel(BlockPos.ofFloored(p.x, p.y + 0.2, p.z));
        double hy = halfY(p);
        if (p.resting) {
            p.rx += (p.trx - p.rx) * 0.35f;
            p.rz += (p.trz - p.rz) * 0.35f;
            double g = surfaceY(w, p.x, p.y - hy - 0.2, p.z);
            if (!Double.isNaN(g)) p.y += (g + halfY(p) - p.y) * 0.4;
            return;
        }
        p.vy -= 0.065;
        p.vx *= 0.985;
        p.vz *= 0.985;
        p.vy *= 0.995;
        double nx = p.x + p.vx, ny = p.y + p.vy, nz = p.z + p.vz;
        if (solidAt(w, nx, p.y, nz)) {
            p.vx *= -0.3;
            p.vz *= -0.3;
            nx = p.x;
            nz = p.z;
            p.avy *= 0.5f;
        }
        boolean onGround = false;
        double g = surfaceY(w, nx, ny - hy, nz);
        if (!Double.isNaN(g)) {
            ny = g + hy;
            if (p.vy < 0) {
                double impact = -p.vy;
                p.vy = impact > 0.14 ? impact * 0.32 : 0.0;
                if (impact > 0.14) {
                    int n = 3 + (int) (impact * 10);
                    for (int i = 0; i < n; i++) {
                        addDrop(nx, g + 0.02, nz, (rnd.nextDouble() - 0.5) * 0.2, 0.08 + rnd.nextDouble() * 0.15,
                                (rnd.nextDouble() - 0.5) * 0.2, 0.025f);
                    }
                }
            }
            p.vx *= 0.72;
            p.vz *= 0.72;
            p.avx *= 0.7f;
            p.avy *= 0.7f;
            p.avz *= 0.7f;
            onGround = true;
        }
        p.x = nx; p.y = ny; p.z = nz;
        p.rx += p.avx;
        p.ry += p.avy;
        p.rz += p.avz;
        if (onGround) {
            if (Math.abs(p.vx) + Math.abs(p.vz) < 0.012 && Math.abs(p.vy) < 0.01) {
                p.resting = true;
                p.trx = Math.round(p.rx / HALF_PI) * HALF_PI;
                p.trz = Math.round(p.rz / HALF_PI) * HALF_PI;
            }
        } else if (p.blood > 0f && Math.abs(p.vx) + Math.abs(p.vy) + Math.abs(p.vz) > 0.1 && rnd.nextFloat() < 0.3f * bloodAmount.value) {
            addDrop(p.x, p.y, p.z, p.vx * 0.3, p.vy * 0.3, p.vz * 0.3, 0.025f);
        }
    }

    // ---------------- мировой рендер ----------------

    @Override
    public void render(RenderContext c) {
        long now = c.now;
        float td = c.tickDelta;
        Vec3d cam = c.camera.getCameraPos();

        if (!puddles.isEmpty()) {
            long life = (long) (puddleLife.value * 1000f);
            double[] cs = Trig.cos(12);
            double[] sn = Trig.sin(12);
            Batch.begin(true, false);
            Batch t = Batch.triangles();
            for (Puddle p : puddles) {
                long ageMs = now - p.t0;
                float r = p.target * (1f - (float) Math.exp(-ageMs / 1000f * 1.6f));
                if (r < 0.02f) continue;
                float a = MathHelper.clamp((life - ageMs) / 4000f, 0f, 1f) * 0.88f;
                int cc = Batch.argb(0x7A0C0C, a);
                int ce = Batch.argb(0x5E0808, a * 0.75f);
                int ch = Batch.argb(0xA81818, a * 0.55f);
                for (int k = 0; k < 12; k++) {
                    int k2 = k + 1;
                    double r1 = r * p.shape[k % 12], r2 = r * p.shape[k2 % 12];
                    t.v(c.m, p.x, p.y + 0.012, p.z, cc);
                    t.v(c.m, p.x + cs[k] * r1, p.y + 0.012, p.z + sn[k] * r1, ce);
                    t.v(c.m, p.x + cs[k2] * r2, p.y + 0.012, p.z + sn[k2] * r2, ce);
                    t.v(c.m, p.x, p.y + 0.014, p.z, ch);
                    t.v(c.m, p.x + cs[k] * r1 * 0.5, p.y + 0.014, p.z + sn[k] * r1 * 0.5, ch);
                    t.v(c.m, p.x + cs[k2] * r2 * 0.5, p.y + 0.014, p.z + sn[k2] * r2 * 0.5, ch);
                }
            }
            t.flush();
            Batch.end();
        }

        if (!parts.isEmpty()) {
            Batch.begin(true, false);
            RenderSystem.depthMask(true); // непрозрачные части сами правильно перекрывают друг друга
            Batch t = Batch.triangles();
            for (Part p : parts) drawPart(t, c, p, td);
            t.flush();
            Batch.end();
        }

        if (!drops.isEmpty()) {
            Batch.begin(true, false);
            Batch t = Batch.triangles();
            for (Drop d : drops) {
                double x = MathHelper.lerp(td, d.px, d.x), y = MathHelper.lerp(td, d.py, d.y), z = MathHelper.lerp(td, d.pz, d.z);
                double dx = d.vx * 1.6, dy = d.vy * 1.6, dz = d.vz * 1.6;
                if (dx * dx + dy * dy + dz * dz < 1e-6) dy = 0.01;
                double vx = x - cam.x, vy = y - cam.y, vz = z - cam.z;
                // боковой вектор ленты: dir × view
                double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
                double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (len < 1e-9) continue;
                double k = d.size / len;
                sx *= k; sy *= k; sz *= k;
                float fade = d.age > 100 ? (120 - d.age) / 20f : 1f;
                int head = Batch.argb(0xC01414, 0.95f * fade);
                int tail = Batch.argb(0x7A0A0A, 0.15f * fade);
                double tx = x - dx, ty = y - dy, tz = z - dz;
                t.v(c.m, tx + sx, ty + sy, tz + sz, tail);
                t.v(c.m, tx - sx, ty - sy, tz - sz, tail);
                t.v(c.m, x - sx, y - sy, z - sz, head);
                t.v(c.m, tx + sx, ty + sy, tz + sz, tail);
                t.v(c.m, x - sx, y - sy, z - sz, head);
                t.v(c.m, x + sx, y + sy, z + sz, head);
            }
            t.flush();
            Batch.end();
        }
    }

    private static final int[][] FACE_V = {{1, 3, 7, 5}, {0, 2, 6, 4}, {2, 3, 7, 6}, {0, 1, 5, 4}, {4, 5, 7, 6}, {0, 1, 3, 2}};
    private static final float[][] FACE_N = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private final float[] cx = new float[8], cy = new float[8], cz = new float[8];

    private void drawPart(Batch t, RenderContext c, Part p, float td) {
        float s = p.maxAge - p.age < 30 ? Math.max(0f, (p.maxAge - p.age) / 30f) : 1f;
        if (s <= 0f) return;
        double x = MathHelper.lerp(td, p.px, p.x), y = MathHelper.lerp(td, p.py, p.y), z = MathHelper.lerp(td, p.pz, p.z);
        float rx = MathHelper.lerp(td, p.prx, p.rx), ry = MathHelper.lerp(td, p.pry, p.ry), rz = MathHelper.lerp(td, p.prz, p.rz);
        MAT.identity().translation((float) x, (float) y, (float) z).rotateYXZ(ry, rx, rz);
        for (int i = 0; i < 8; i++) {
            MAT.transformPosition(((i & 1) == 0 ? -1f : 1f) * p.hx * s, ((i & 2) == 0 ? -1f : 1f) * p.hy * s,
                    ((i & 4) == 0 ? -1f : 1f) * p.hz * s, V);
            cx[i] = V.x; cy[i] = V.y; cz[i] = V.z;
        }
        int base = Batch.mix(p.rgb, 0x7A0A0A, p.blood);
        float light = 0.28f + 0.72f * p.light / 15f;
        for (int f = 0; f < 6; f++) {
            MAT.transformDirection(FACE_N[f][0], FACE_N[f][1], FACE_N[f][2], NRM);
            float shade = 0.42f + 0.58f * MathHelper.clamp(NRM.x * 0.25f + NRM.y * 0.85f + NRM.z * 0.45f, 0f, 1f);
            int col = Batch.argb(Batch.mix(0x000000, base, Math.min(1f, shade * light)), 1f);
            int[] q = FACE_V[f];
            t.v(c.m, cx[q[0]], cy[q[0]], cz[q[0]], col);
            t.v(c.m, cx[q[1]], cy[q[1]], cz[q[1]], col);
            t.v(c.m, cx[q[2]], cy[q[2]], cz[q[2]], col);
            t.v(c.m, cx[q[0]], cy[q[0]], cz[q[0]], col);
            t.v(c.m, cx[q[2]], cy[q[2]], cz[q[2]], col);
            t.v(c.m, cx[q[3]], cy[q[3]], cz[q[3]], col);
        }
    }

    // ---------------- кровь на экране ----------------

    private static int red(float a, int rgb) {
        return ((int) (MathHelper.clamp(a, 0f, 1f) * 255f) << 24) | rgb;
    }

    private static void blob(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy += 2) {
            int hw = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            ctx.fill(cx - hw, cy + dy, cx + hw, cy + dy + 2, color);
        }
    }

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        if (screenDrops.isEmpty() && splats.isEmpty()) return;
        long now = System.currentTimeMillis();
        float dt = lastHud == 0L ? 0f : Math.min(0.05f, (now - lastHud) / 1000f);
        lastHud = now;
        int sh = ctx.getScaledWindowHeight();

        for (int i = splats.size() - 1; i >= 0; i--) {
            Splat s = splats.get(i);
            float age = (now - s.t0) / 1000f;
            if (age > s.life) {
                splats.remove(i);
                continue;
            }
            s.y += (s.slide + age * 3f) * dt;
            float a = MathHelper.clamp(1f - age / s.life, 0f, 1f);
            int r = Math.round(s.r);
            blob(ctx, Math.round(s.x), Math.round(s.y), r, red(0.78f * a, 0x7E0B0B));
            blob(ctx, Math.round(s.x) - r / 4, Math.round(s.y) - r / 4, Math.max(2, r / 2), red(0.5f * a, 0xB01818));
            blob(ctx, Math.round(s.x) + r, Math.round(s.y) + r / 2, Math.max(2, r / 4), red(0.7f * a, 0x7E0B0B));
        }
        for (int i = screenDrops.size() - 1; i >= 0; i--) {
            ScreenDrop d = screenDrops.get(i);
            if (now < d.start) continue;
            d.vy = Math.min(d.vy + 520f * dt, 900f);
            d.y += d.vy * dt;
            if (d.y > sh + 30) {
                screenDrops.remove(i);
                continue;
            }
            int x = Math.round(d.x);
            int y = Math.round(d.y);
            int w = Math.max(1, Math.round(d.size * 0.55f));
            float trail = Math.min(d.vy * 0.07f + 6f, 70f);
            for (int k = 0; k < 5; k++) {
                int y0 = Math.round(d.y - trail * (k + 1) / 5f);
                int y1 = Math.round(d.y - trail * k / 5f);
                ctx.fill(x - w / 2, y0, x - w / 2 + w, y1, red(d.alpha * (0.7f - 0.13f * k), 0x8A0C0C));
            }
            int hs = Math.round(d.size);
            ctx.fill(x - hs / 2, y - hs, x - hs / 2 + hs, y + hs / 2, red(d.alpha, 0xA01010));
            ctx.fill(x - hs / 2 + 1, y - hs + 1, x - hs / 2 + 2, y - hs / 2, red(d.alpha * 0.8f, 0xE04040));
        }
    }

    // ---------------- палитра ----------------

    /** {голова, торс/тело, ноги, руки}. */
    private static int[] palette(String id) {
        return switch (id) {
            case "player" -> new int[]{0xC8A07E, 0x2AA5A5, 0x3C3C98, 0xC8A07E};
            case "zombie", "husk", "drowned", "zombie_villager" -> new int[]{0x5B8F4A, 0x2D7A7A, 0x3A3A8C, 0x5B8F4A};
            case "zombified_piglin" -> new int[]{0xC78A7B, 0x7A5A3A, 0x5A4630, 0xC78A7B};
            case "skeleton", "stray", "bogged" -> new int[]{0xD8D8CC, 0xCFCFC2, 0xC2C2B5, 0xD8D8CC};
            case "wither_skeleton" -> new int[]{0x3A3A3A, 0x303030, 0x2A2A2A, 0x3A3A3A};
            case "piglin", "piglin_brute" -> new int[]{0xD99A80, 0x8A5A32, 0x5A3A22, 0xD99A80};
            case "villager", "wandering_trader" -> new int[]{0xB68B6B, 0x7A5A38, 0x6A4A2C, 0xB68B6B};
            case "pillager", "vindicator", "evoker", "illusioner" -> new int[]{0x8C9A8C, 0x4A4A55, 0x3A3A44, 0x8C9A8C};
            case "witch" -> new int[]{0x9E8A6A, 0x4B2E83, 0x3A2466, 0x9E8A6A};
            case "enderman" -> new int[]{0x151515, 0x101010, 0x0C0C0C, 0x101010};
            case "creeper" -> new int[]{0x4FB84F, 0x3FA03F, 0x338A33, 0x4FB84F};
            case "pig" -> new int[]{0xF0A0A0, 0xE89494, 0xD98484, 0xE89494};
            case "cow", "mooshroom" -> new int[]{0x3A2A1E, 0x4A3426, 0x3A2A1E, 0x3A2A1E};
            case "sheep", "chicken", "snow_golem", "polar_bear", "panda" -> new int[]{0xF2F2F2, 0xE8E8E8, 0xD8D8D8, 0xE0E0E0};
            case "wolf" -> new int[]{0xCFCFCF, 0xBDBDBD, 0xA8A8A8, 0xBDBDBD};
            case "cat", "ocelot" -> new int[]{0xD2AE55, 0xC9A24A, 0xB8923C, 0xC9A24A};
            case "horse", "donkey", "mule" -> new int[]{0x9B6A3A, 0x8B5A2B, 0x6E4520, 0x8B5A2B};
            case "spider", "cave_spider" -> new int[]{0x2A2A2A, 0x232323, 0x1A1A1A, 0x232323};
            case "slime" -> new int[]{0x6FCF6F, 0x5FBF5F, 0x4FAF4F, 0x5FBF5F};
            case "magma_cube" -> new int[]{0xA83A18, 0x8A2E10, 0x6E240C, 0x8A2E10};
            case "blaze" -> new int[]{0xE8A317, 0xD89410, 0xC88408, 0xD89410};
            case "iron_golem" -> new int[]{0xB8B4A8, 0xA8A498, 0x98948A, 0xA8A498};
            case "rabbit" -> new int[]{0xB69B7B, 0xA68B6B, 0x96795A, 0xA68B6B};
            case "fox" -> new int[]{0xE08A3A, 0xD97B2A, 0xC8681A, 0xD97B2A};
            default -> {
                int hue = Math.floorMod(id.hashCode(), 360);
                int body = MathHelper.hsvToRgb(hue / 360f, 0.35f, 0.55f);
                yield new int[]{Batch.mix(body, 0xFFFFFF, 0.1f), body, Batch.mix(body, 0x000000, 0.2f), body};
            }
        };
    }
}
