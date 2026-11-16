package com.swinganimations.mixin;

import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.SeeInvisiblesModule;
import com.swinganimations.render.InvisibleAccess;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SeeInvisibles: невидимый игрок рисуется полупрозрачным «призраком». Ваниль делает так же для
 * невидимых союзников, мы лишь включаем это для всех и задаём свою прозрачность.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void swa$ghostState(T entity, S state, float tickDelta, CallbackInfo ci) {
        try {
            SeeInvisiblesModule m = ModuleManager.seeInvisibles;
            boolean ghost = m != null && entity instanceof PlayerEntity p && m.shouldShow(p);
            ((InvisibleAccess) state).swa$setGhost(ghost);
            if (ghost) {
                state.invisible = true;
                state.invisibleToPlayer = false;
            }
        } catch (Throwable ignored) {
            // косметика: при сбое просто не показываем
        }
    }

    @ModifyConstant(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            constant = @Constant(intValue = 654311423), require = 0)
    private int swa$ghostAlpha(int original, S state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        SeeInvisiblesModule m = ModuleManager.seeInvisibles;
        return (m != null && ((InvisibleAccess) state).swa$isGhost()) ? m.ghostColor() : original;
    }
}
