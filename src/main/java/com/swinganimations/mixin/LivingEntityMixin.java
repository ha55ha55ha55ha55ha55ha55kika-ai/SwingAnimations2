package com.swinganimations.mixin;

import com.swinganimations.animation.SwingLogic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ваниль перезапускает свинг (handSwingTicks = -1), если удар/клик пришёл во второй половине
 * текущего свинга. Из-за этого анимация сбрасывалась на середине и «дёргалась».
 * Пока кастомная анимация играет, перезапуск игнорируем: свинг доигрывается до конца.
 * Пакет удара на сервер отправляется отдельно (ClientPlayerEntity.swingHand(Hand)), урон не страдает.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow public boolean handSwinging;
    @Shadow public int handSwingTicks;
    @Shadow private int jumpingCooldown;

    @Inject(method = "tickMovement()V", at = @At("HEAD"), require = 0)
    private void swa$noJumpDelay(CallbackInfo ci) {
        try {
            com.swinganimations.module.NoJumpDelayModule m = com.swinganimations.module.ModuleManager.noJumpDelay;
            if (m != null && m.isEnabled() && (Object) this == MinecraftClient.getInstance().player) jumpingCooldown = 0;
        } catch (Throwable ignored) {
            // не критично
        }
    }

    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$noRestart(Hand hand, boolean fromServerPlayer, CallbackInfo ci) {
        try {
            if (!handSwinging || handSwingTicks < 0) return;
            ClientPlayerEntity me = MinecraftClient.getInstance().player;
            if ((Object) this != me) return;
            if (SwingLogic.keepsAnimation(me, hand)) ci.cancel();
        } catch (Throwable t) {
            SwingLogic.fail(t);
        }
    }
}
