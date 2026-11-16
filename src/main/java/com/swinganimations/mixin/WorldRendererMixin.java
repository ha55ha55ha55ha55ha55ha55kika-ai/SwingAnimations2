package com.swinganimations.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.swinganimations.module.BlockOutlineModule;
import com.swinganimations.module.ModuleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Хук в конец WorldRenderer.render: сюда рисуются мировые визуалы (TargetESP, Trails, JumpCircle).
 * require = 0: при несовпадении сигнатуры игра не падает, просто нет мировых эффектов.
 * Сигнатура 1.21.11: (allocator, tickCounter, renderBlockOutline, camera, position, projection, cullProjection, fog, fogColor, renderSky).
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    private static final String RENDER = "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V";

    @Inject(method = RENDER, at = @At("RETURN"), require = 0)
    private void swa$renderWorld(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
                                 Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f cullProjection,
                                 GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        ModuleManager.renderWorld(positionMatrix, projectionMatrix, camera, tickCounter.getTickProgress(false));
    }

    /** BlockOutline: ванильная обводка блока скрывается через флаг renderBlockOutline (первый boolean-аргумент). */
    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private boolean swa$hideVanillaOutline(boolean renderBlockOutline) {
        BlockOutlineModule m = ModuleManager.blockOutline;
        return renderBlockOutline && !(m != null && m.hidesVanilla());
    }
}
