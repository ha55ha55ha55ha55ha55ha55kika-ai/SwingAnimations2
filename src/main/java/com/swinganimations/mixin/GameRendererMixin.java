package com.swinganimations.mixin;

import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.RemovalsModule;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removals: тряска экрана при получении урона. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$noHurtTilt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        RemovalsModule r = ModuleManager.removals;
        if (r != null && r.hurtTiltOff()) ci.cancel();
    }
}
