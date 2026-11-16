package com.swinganimations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.RemovalsModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removals: «проседание» руки после удара. Его даёт кулдаун атаки в updateHeldItems; для этого расчёта
 * подставляем полный кулдаун. Индикатор кулдауна у прицела и урон не затрагиваются.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererBobMixin {
    @WrapOperation(method = "updateHeldItems()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getAttackCooldownProgress(F)F"),
            require = 0)
    private float swa$noHitBob(ClientPlayerEntity player, float baseTime, Operation<Float> original) {
        RemovalsModule r = ModuleManager.removals;
        if (r != null && r.handBobOff()) return 1.0f;
        return original.call(player, baseTime);
    }
}
