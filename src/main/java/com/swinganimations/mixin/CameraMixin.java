package com.swinganimations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.swinganimations.module.FreeLookModule;
import com.swinganimations.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FreeLook: камера берёт свои углы, а не углы игрока. Два независимых способа, чтобы работало при любой разнице в сигнатурах:
 * 1) подмена первого setRotation в Camera.update; 2) на время Camera.update углы игрока временно заменяются углами камеры
 * и сразу возвращаются (Camera.update читает их у наблюдаемой сущности). Оба дают один и тот же результат.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique private Entity swa$ent;
    @Unique private float swa$yaw, swa$pitch, swa$prevYaw, swa$prevPitch;

    @WrapOperation(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0), require = 0)
    private void swa$freeLookRotation(Camera camera, float yaw, float pitch, Operation<Void> original) {
        FreeLookModule m = ModuleManager.freeLook;
        if (m != null && m.isActive()) {
            original.call(camera, m.cameraYaw(), m.cameraPitch());
        } else {
            original.call(camera, yaw, pitch);
        }
    }

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void swa$freeLookHead(CallbackInfo ci) {
        swa$ent = null;
        try {
            FreeLookModule m = ModuleManager.freeLook;
            if (m == null || !m.isActive()) return;
            Entity e = MinecraftClient.getInstance().getCameraEntity();
            if (e == null || e != MinecraftClient.getInstance().player) return;
            swa$ent = e;
            swa$yaw = e.getYaw();
            swa$pitch = e.getPitch();
            swa$prevYaw = e.prevYaw;
            swa$prevPitch = e.prevPitch;
            e.setYaw(m.cameraYaw());
            e.setPitch(m.cameraPitch());
            e.prevYaw = m.cameraYaw();
            e.prevPitch = m.cameraPitch();
        } catch (Throwable ignored) {
            swa$ent = null;
        }
    }

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void swa$freeLookTail(CallbackInfo ci) {
        Entity e = swa$ent;
        swa$ent = null;
        if (e == null) return;
        e.setYaw(swa$yaw);
        e.setPitch(swa$pitch);
        e.prevYaw = swa$prevYaw;
        e.prevPitch = swa$prevPitch;
    }
}
