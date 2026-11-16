package com.swinganimations.mixin;

import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.PerformanceModule;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Performance / Entity culling: сущности дальше заданной дистанции не рисуются совсем.
 * Только клиент, на хитбоксы и геймплей не влияет. require = 0: при несовпадении сигнатуры игра не падает.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$cull(Entity entity, Frustum frustum, double x, double y, double z,
                          CallbackInfoReturnable<Boolean> cir) {
        try {
            com.swinganimations.module.KillEffectModule k = ModuleManager.killEffect;
            if (k != null && k.isHidden(entity)) {
                cir.setReturnValue(false);
                return;
            }
            PerformanceModule m = ModuleManager.performance;
            if (m != null && m.shouldCull(entity, x, y, z)) cir.setReturnValue(false);
        } catch (Throwable ignored) {
            // косметика: при сбое просто не отсекаем
        }
    }
}
