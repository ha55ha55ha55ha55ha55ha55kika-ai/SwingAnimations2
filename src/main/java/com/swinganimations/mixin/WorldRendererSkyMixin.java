package com.swinganimations.mixin;

import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.RemovalsModule;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removals: не рисовать небо (солнце, луна, звёзды). В 1.21.11 небо рисует SkyRendering, а не WorldRenderer.
 * Хуки без аргументов (допустимо для @Inject) и require = 0: если имя метода не совпадёт, игра не падает, небо просто остаётся.
 */
@Mixin(SkyRendering.class)
public abstract class WorldRendererSkyMixin {
    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$noCelestial(CallbackInfo ci) {
        RemovalsModule r = ModuleManager.removals;
        if (r != null && r.skyOff()) ci.cancel();
    }
}
