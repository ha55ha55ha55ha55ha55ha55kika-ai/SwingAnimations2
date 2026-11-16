package com.swinganimations.mixin;

import com.swinganimations.module.CustomWorldModule;
import com.swinganimations.module.ModuleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CustomWorld: оттенок неба. В разных версиях getSkyColor возвращает либо int (ARGB), либо Vec3d,
 * поэтому тип проверяется на лету. require = 0: при несовпадении сигнатуры небо остаётся ванильным.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void swa$skyTint(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Object> cir) {
        try {
            CustomWorldModule m = ModuleManager.customWorld;
            if (m == null || !m.skyTintOn()) return;
            Object v = cir.getReturnValue();
            Object t = m.tintValue(v, 1f);
            if (t != v) cir.setReturnValue(t);
        } catch (Throwable ignored) {
            // косметика
        }
    }

    /** Облака берут мягкий оттенок, чтобы не превращаться в сплошную краску. */
    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void swa$cloudTint(float tickDelta, CallbackInfoReturnable<Object> cir) {
        try {
            CustomWorldModule m = ModuleManager.customWorld;
            if (m == null || !m.skyTintOn()) return;
            Object v = cir.getReturnValue();
            Object t = m.tintValue(v, 0.4f);
            if (t != v) cir.setReturnValue(t);
        } catch (Throwable ignored) {
            // косметика
        }
    }
}
