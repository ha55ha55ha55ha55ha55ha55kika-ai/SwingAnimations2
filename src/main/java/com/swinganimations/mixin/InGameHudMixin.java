package com.swinganimations.mixin;

import com.swinganimations.module.CrosshairModule;
import com.swinganimations.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crosshair: пока включён наш прицел, ванильный не рисуется. require = 0: при другой сигнатуре просто останутся оба. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$hideVanillaCrosshair(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        CrosshairModule m = ModuleManager.crosshair;
        if (m != null && m.isEnabled()) ci.cancel();
    }
}
