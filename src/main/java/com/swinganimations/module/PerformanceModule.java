package com.swinganimations.module;

import com.swinganimations.util.Perf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticlesMode;

/**
 * Оптимизация FPS (только клиент, геймплей не затрагивается):
 * - Adaptive quality: если FPS ниже цели, визуалы мода (сердечки, HP, ESP, трейл) автоматически упрощаются;
 * - Entity culling: сущности дальше N блоков вообще не рисуются;
 * - Тихие твики ванильных настроек (тени, частицы, облака): при выключении возвращаются исходные значения.
 * Исходные значения лежат в скрытых настройках и переживают перезапуск игры.
 */
public final class PerformanceModule extends Module {
    private final Setting.Bool adaptive = add(new Setting.Bool("adaptive", "Adaptive quality", true));
    private final Setting.Num targetFps = add(new Setting.Num("target_fps", "Target FPS", 60f, 30f, 240f, 5f).<Setting.Num>visibleWhen(() -> this.adaptive.value));
    private final Setting.Bool cull = add(new Setting.Bool("cull", "Entity culling", true));
    private final Setting.Num cullDistance = add(new Setting.Num("cull_distance", "Cull distance", 48f, 16f, 128f, 8f).<Setting.Num>visibleWhen(() -> this.cull.value));
    private final Setting.Bool cullPlayers = add(new Setting.Bool("cull_players", "Cull players too", false).<Setting.Bool>visibleWhen(() -> this.cull.value));
    private final Setting.Bool shadows = add(new Setting.Bool("shadows", "Entity shadows off", true));
    private final Setting.Mode particles = add(new Setting.Mode("particles", "Particles", "Keep", "Decreased", "Minimal"));
    private final Setting.Bool clouds = add(new Setting.Bool("clouds", "Clouds off", false));

    private final Setting.Str origShadows = add(new Setting.Str("orig_shadows", "", ""));
    private final Setting.Str origParticles = add(new Setting.Str("orig_particles", "", ""));
    private final Setting.Str origClouds = add(new Setting.Str("orig_clouds", "", ""));

    private int timer;
    private int lowStreak;
    private int highStreak;
    private int quality;

    public PerformanceModule() {
        super("performance", "Performance", "render");
    }

    @Override
    public void onEnable() {
        quality = 0;
        lowStreak = 0;
        highStreak = 0;
        timer = 0;
        applyTweaks(MinecraftClient.getInstance());
    }

    @Override
    public void onDisable() {
        Perf.setQuality(0);
        MinecraftClient mc = MinecraftClient.getInstance();
        restoreAll(mc);
    }

    /** Вызывается рендер-миксином: true = сущность не рисуем. cam* = позиция камеры. */
    public boolean shouldCull(Entity e, double cx, double cy, double cz) {
        if (!enabled || !cull.value) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e == mc.player || e == mc.cameraEntity) return false;
        if (e instanceof PlayerEntity && !cullPlayers.value) return false;
        double d = cullDistance.value;
        return e.squaredDistanceTo(cx, cy, cz) > d * d;
    }

    @Override
    public void tick(MinecraftClient mc) {
        applyTweaks(mc);
        if (saveDirty) {
            saveDirty = false;
            ModuleManager.save();
        }
        if (!adaptive.value) {
            quality = 0;
            Perf.setQuality(0);
            return;
        }
        if (++timer < 20) return;
        timer = 0;
        int fps = mc.getCurrentFps();
        if (fps <= 0) return;
        float target = targetFps.value;
        if (fps < target * 0.85f) {
            lowStreak++;
            highStreak = 0;
        } else if (fps > target * 1.4f) {
            highStreak++;
            lowStreak = 0;
        } else {
            lowStreak = 0;
            highStreak = 0;
        }
        if (lowStreak >= 2 && quality < 2) {
            quality++;
            lowStreak = 0;
        } else if (highStreak >= 5 && quality > 0) {
            quality--;
            highStreak = 0;
        }
        Perf.setQuality(quality);
    }

    // ---------------- ванильные твики с возвратом исходных значений ----------------

    private void applyTweaks(MinecraftClient mc) {
        GameOptions o = mc.options;
        if (o == null) return;
        boolean changed = false;

        if (shadows.value && origShadows.value.isEmpty()) {
            origShadows.value = Boolean.toString(o.getEntityShadows().getValue());
            o.getEntityShadows().setValue(false);
            changed = true;
        } else if (!shadows.value && !origShadows.value.isEmpty()) {
            o.getEntityShadows().setValue(Boolean.parseBoolean(origShadows.value));
            origShadows.value = "";
            changed = true;
        }

        if (!particles.is("Keep")) {
            ParticlesMode want = particles.is("Minimal") ? ParticlesMode.MINIMAL : ParticlesMode.DECREASED;
            if (origParticles.value.isEmpty()) {
                origParticles.value = o.getParticles().getValue().name();
                changed = true;
            }
            if (appliedParticles != want) {
                o.getParticles().setValue(want);
                appliedParticles = want;
            }
        } else if (!origParticles.value.isEmpty()) {
            o.getParticles().setValue(parseParticles(origParticles.value));
            origParticles.value = "";
            appliedParticles = null;
            changed = true;
        }

        if (clouds.value && origClouds.value.isEmpty()) {
            origClouds.value = o.getCloudRenderMode().getValue().name();
            o.getCloudRenderMode().setValue(CloudRenderMode.OFF);
            changed = true;
        } else if (!clouds.value && !origClouds.value.isEmpty()) {
            o.getCloudRenderMode().setValue(parseClouds(origClouds.value));
            origClouds.value = "";
            changed = true;
        }

        // Сохраняем позже из tick(): во время загрузки конфига save() затёр бы ещё не прочитанные модули.
        if (changed) saveDirty = true;
    }

    private ParticlesMode appliedParticles;
    private boolean saveDirty;

    private void restoreAll(MinecraftClient mc) {
        GameOptions o = mc.options;
        if (o == null) return;
        if (!origShadows.value.isEmpty()) {
            o.getEntityShadows().setValue(Boolean.parseBoolean(origShadows.value));
            origShadows.value = "";
        }
        if (!origParticles.value.isEmpty()) {
            o.getParticles().setValue(parseParticles(origParticles.value));
            origParticles.value = "";
        }
        if (!origClouds.value.isEmpty()) {
            o.getCloudRenderMode().setValue(parseClouds(origClouds.value));
            origClouds.value = "";
        }
        appliedParticles = null;
    }

    private static ParticlesMode parseParticles(String s) {
        for (ParticlesMode m : ParticlesMode.values()) if (m.name().equals(s)) return m;
        return ParticlesMode.ALL;
    }

    private static CloudRenderMode parseClouds(String s) {
        for (CloudRenderMode m : CloudRenderMode.values()) if (m.name().equals(s)) return m;
        return CloudRenderMode.FANCY;
    }
}
