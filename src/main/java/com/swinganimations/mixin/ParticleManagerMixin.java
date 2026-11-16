package com.swinganimations.mixin;

import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.RemovalsModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removals: фильтр частиц (все, тотем, булава, взрыв). Вернуть null допустимо: так ваниль сообщает «частица не создана». */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$filterParticles(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz,
                                     CallbackInfoReturnable<Particle> cir) {
        try {
            RemovalsModule r = ModuleManager.removals;
            if (r != null && r.blocksParticle(effect)) cir.setReturnValue(null);
        } catch (Throwable ignored) {
            // косметика
        }
    }
}
