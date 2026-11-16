package com.swinganimations.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/** Данные одного кадра для модулей. Матрица уже в мировых координатах (камера вычтена). */
public final class RenderContext {
    public final MatrixStack ms;
    public final Matrix4f m;
    public final Camera camera;
    public final float tickDelta;
    public final long now;
    public final MinecraftClient mc;
    /** Оси «вправо» и «вверх» камеры в мировых координатах (для билбордов). */
    public final float rx, ry, rz, ux, uy, uz;

    public RenderContext(MatrixStack ms, Camera camera, float tickDelta, long now, MinecraftClient mc) {
        this.ms = ms;
        this.m = ms.peek().getPositionMatrix();
        this.camera = camera;
        this.tickDelta = tickDelta;
        this.now = now;
        this.mc = mc;
        float yaw = (float) Math.toRadians(camera.getYaw());
        float pitch = (float) Math.toRadians(camera.getPitch());
        float sy = (float) Math.sin(yaw), cy = (float) Math.cos(yaw);
        float sp = (float) Math.sin(pitch), cp = (float) Math.cos(pitch);
        rx = -cy; ry = 0f; rz = -sy;
        ux = -sy * sp; uy = cp; uz = cy * sp;
    }
}
