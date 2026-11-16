package com.swinganimations.mixin;

import com.swinganimations.module.ItemPhysicsModule;
import com.swinganimations.module.ModuleManager;
import com.swinganimations.render.ItemPhysicsHolder;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ItemPhysics. Все хуки с require = 0: если в этой версии сигнатуры другие, мод просто не меняет
 * рендер предметов (ваниль остаётся как есть), игра не падает.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V";

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void swa$capture(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        try {
            ItemPhysicsModule m = ModuleManager.itemPhysics;
            ((ItemPhysicsHolder) state).swa$setAnim(m != null && m.isEnabled() ? m.capture(entity, tickDelta) : null);
        } catch (Throwable ignored) {
            // косметика: при сбое рисуем как в ванили
            ((ItemPhysicsHolder) state).swa$setAnim(null);
        }
    }

    @Inject(method = RENDER, at = @At("HEAD"), require = 0)
    private void swa$begin(ItemEntityRenderState state, MatrixStack ms, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        ItemPhysicsModule.Anim a = ((ItemPhysicsHolder) state).swa$anim();
        if (a != null) a.applied = false;
        ItemPhysicsModule.CURRENT = a;
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 0)
    private void swa$end(ItemEntityRenderState state, MatrixStack ms, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        ItemPhysicsModule.CURRENT = null;
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"), require = 0)
    private void swa$translateF(MatrixStack ms, float x, float y, float z) {
        if (!ItemPhysicsModule.translate(ms, y)) ms.translate(x, y, z);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V"), require = 0)
    private void swa$translateD(MatrixStack ms, double x, double y, double z) {
        if (!ItemPhysicsModule.translate(ms, (float) y)) ms.translate(x, y, z);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V"), require = 0)
    private void swa$rotate(MatrixStack ms, Quaternionf q) {
        if (!ItemPhysicsModule.skipRotation()) ms.multiply(q);
    }
}
