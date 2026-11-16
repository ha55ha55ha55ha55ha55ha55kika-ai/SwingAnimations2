package com.swinganimations.render;

import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Проекция мировых координат на экран (для HUD-элементов над сущностями). Матрицы берутся из WorldRenderer. */
public final class Projection {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static double cx, cy, cz;
    private static boolean valid;

    private Projection() {}

    public static void capture(Matrix4f view, Matrix4f proj, Vec3d cam) {
        VIEW.set(view);
        PROJ.set(proj);
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        valid = true;
    }

    public static boolean valid() { return valid; }

    public static double distanceSq(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distance(double x, double y, double z) {
        return Math.sqrt(distanceSq(x, y, z));
    }

    /** out[0], out[1] = экранные координаты (в масштабированных пикселях GUI). false, если точка вне кадра. */
    public static boolean project(double x, double y, double z, float[] out, int scaledW, int scaledH) {
        if (!valid) return false;
        Vector4f v = TMP.set((float) (x - cx), (float) (y - cy), (float) (z - cz), 1f);
        VIEW.transform(v);
        PROJ.transform(v);
        if (v.w <= 0.05f) return false;
        float nx = v.x / v.w;
        float ny = v.y / v.w;
        if (Math.abs(nx) > 1.2f || Math.abs(ny) > 1.2f) return false;
        out[0] = (nx * 0.5f + 0.5f) * scaledW;
        out[1] = (0.5f - ny * 0.5f) * scaledH;
        return true;
    }
}
