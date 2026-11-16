package com.swinganimations.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Removals: отключает отдельные визуальные элементы на клиенте. Геймплей, урон и пакеты не затрагиваются.
 * Сами подмены делают миксины (читают методы *Off() этого модуля).
 */
public final class RemovalsModule extends Module {
    private static final Set<String> TOTEM = Set.of("totem_of_undying");
    private static final Set<String> MACE = Set.of("gust", "small_gust", "gust_emitter_large", "gust_emitter_small", "dust_pillar");
    private static final Set<String> EXPLOSION = Set.of("explosion", "explosion_emitter");

    private final Setting.Bool fire = add(new Setting.Bool("fire", "Fire overlay", true));
    private final Setting.Bool allParticles = add(new Setting.Bool("all_particles", "All particles", false));
    private final Setting.Bool totemParticles = add(new Setting.Bool("totem_particles", "Totem pop particles", true));
    private final Setting.Bool maceParticles = add(new Setting.Bool("mace_particles", "Mace smash particles", true));
    private final Setting.Bool explosionParticles = add(new Setting.Bool("explosion_particles", "Explosion particles", true));
    private final Setting.Bool nausea = add(new Setting.Bool("nausea", "Nausea / portal distortion", true));
    private final Setting.Bool hurtTilt = add(new Setting.Bool("hurt_tilt", "Screen shake on hit", true));
    private final Setting.Bool handBob = add(new Setting.Bool("hand_bob", "Hand dip on hit", true));
    private final Setting.Bool sky = add(new Setting.Bool("sky", "Sky", false));
    private final Setting.Bool entityShadows = add(new Setting.Bool("entity_shadows", "Entity shadows", false));
    private final Setting.Bool blockShadows = add(new Setting.Bool("block_shadows", "Block shadows (AO)", false));
    private final Setting.Bool rain = add(new Setting.Bool("rain", "Rain and its sound", false));
    private final Setting.Bool badHearts = add(new Setting.Bool("bad_hearts", "Poison / frozen hearts", true));
    private final Setting.Bool witherHeart = add(new Setting.Bool("wither_heart", "Wither hearts", true));

    private final Map<ParticleType<?>, Byte> particleClass = new IdentityHashMap<>();
    private boolean aoApplied;

    public RemovalsModule() {
        super("removals", "Removals", "render");
    }

    public boolean fireOff() { return isEnabled() && fire.value; }
    public boolean nauseaOff() { return isEnabled() && nausea.value; }
    public boolean hurtTiltOff() { return isEnabled() && hurtTilt.value; }
    public boolean handBobOff() { return isEnabled() && handBob.value; }
    public boolean skyOff() { return isEnabled() && sky.value; }
    public boolean shadowsOff() { return isEnabled() && entityShadows.value; }
    public boolean aoOff() { return isEnabled() && blockShadows.value; }
    public boolean rainOff() { return isEnabled() && rain.value; }
    public boolean badHeartsOff() { return isEnabled() && badHearts.value; }
    public boolean witherHeartOff() { return isEnabled() && witherHeart.value; }

    /** true: эту частицу создавать не нужно. */
    public boolean blocksParticle(ParticleEffect effect) {
        if (!isEnabled() || effect == null) return false;
        if (allParticles.value) return true;
        ParticleType<?> type = effect.getType();
        byte cls = particleClass.computeIfAbsent(type, t -> {
            Identifier id = Registries.PARTICLE_TYPE.getId(t);
            String path = id == null ? "" : id.getPath();
            if (TOTEM.contains(path)) return (byte) 1;
            if (MACE.contains(path)) return (byte) 2;
            if (EXPLOSION.contains(path)) return (byte) 3;
            return (byte) 0;
        });
        return (cls == 1 && totemParticles.value) || (cls == 2 && maceParticles.value) || (cls == 3 && explosionParticles.value);
    }

    @Override
    public void tick(MinecraftClient mc) {
        // Тени от блоков (AO) читаются при построении чанков: при смене настройки перестраиваем мир один раз.
        boolean want = blockShadows.value;
        if (want != aoApplied) {
            aoApplied = want;
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onDisable() {
        if (aoApplied) {
            aoApplied = false;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) mc.worldRenderer.reload();
        }
    }
}
