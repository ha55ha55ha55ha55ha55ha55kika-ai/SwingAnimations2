package com.swinganimations.mixin;

import com.swinganimations.event.EventBus;
import com.swinganimations.event.KillEvent;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** KillEffect: клиент получает статус 3 (смерть) и публикует событие в шину. require = 0: при сбое эффект просто не сработает. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {
    @Inject(method = "handleStatus(B)V", at = @At("HEAD"), require = 0)
    private void swa$death(byte status, CallbackInfo ci) {
        if (status != 3) return;
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (self.getEntityWorld().isClient) EventBus.post(new KillEvent(self));
        } catch (Throwable ignored) {
            // косметика
        }
    }
}
