package com.swinganimations.module;

import com.swinganimations.render.Batch;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockOutline: красивая обводка блока, на который вы смотрите. Стили: контур, заливка, свечение, уголки.
 * Анимации: пульс и бегущая волна по высоте. Всё процедурно (без текстур), только визуал.
 */
public final class BlockOutlineModule extends Module {
    private static final double EXPAND = 0.003;

    private final Setting.Mode style = add(new Setting.Mode("style", "Style", "Outline", "Fill", "Outline+Fill", "Glow", "Corners"));
    private final Setting.Mode animation = add(new Setting.Mode("animation", "Animation", "None", "Pulse", "Wave"));
    private final Setting.Num speed = add(new Setting.Num("speed", "Speed", 1f, 0.25f, 4f, 0.25f)
            .<Setting.Num>visibleWhen(() -> !this.animation.is("None")));
    private final Setting.Num width = add(new Setting.Num("width", "Line width", 2f, 0.5f, 6f, 0.5f));
    private final Setting.Num fillOpacity = add(new Setting.Num("fill_opacity", "Fill opacity", 0.25f, 0.05f, 0.8f, 0.05f));
    private final Setting.Bool hideVanilla = add(new Setting.Bool("hide_vanilla", "Hide vanilla outline", true));
    private final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through walls", false));
    private final ColorSettings color = new ColorSettings(this);

    // состояние текущего кадра
    private BlockPos pos;
    private long now;
    private double tSec;
    private double camX, camY, camZ;
    private final List<double[]> edges = new ArrayList<>();
    private final List<double[]> boxes = new ArrayList<>();

    public BlockOutlineModule() {
        super("block_outline", "BlockOutline", "render");
    }

    /** Нужно ли скрыть ванильную обводку (читает WorldRendererMixin). */
    public boolean hidesVanilla() { return isEnabled() && hideVanilla.value; }

    @Override
    public boolean wantsWorldRender() {
        HitResult h = MinecraftClient.getInstance().crosshairTarget;
        return h != null && h.getType() == HitResult.Type.BLOCK;
    }

    @Override
    public void render(RenderContext c) {
        HitResult h = c.mc.crosshairTarget;
        if (!(h instanceof BlockHitResult bhr) || h.getType() != HitResult.Type.BLOCK) return;
        pos = bhr.getBlockPos();
        BlockState st = c.mc.world.getBlockState(pos);
        if (st.isAir()) return;
        VoxelShape shape = st.getOutlineShape(c.mc.world, pos, ShapeContext.of(c.mc.player));
        if (shape.isEmpty()) return;

        now = c.now;
        tSec = (now % 7_200_000L) / 1000.0 * speed.value;
        camX = c.camera.getCameraPos().x;
        camY = c.camera.getCameraPos().y;
        camZ = c.camera.getCameraPos().z;

        final double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        edges.clear();
        boxes.clear();
        shape.forEachEdge((x1, y1, z1, x2, y2, z2) -> edges.add(new double[]{
                ex(pos.getX() + x1, cx), ex(pos.getY() + y1, cy), ex(pos.getZ() + z1, cz),
                ex(pos.getX() + x2, cx), ex(pos.getY() + y2, cy), ex(pos.getZ() + z2, cz)}));
        shape.forEachBox((x1, y1, z1, x2, y2, z2) -> boxes.add(new double[]{
                pos.getX() + x1 - EXPAND, pos.getY() + y1 - EXPAND, pos.getZ() + z1 - EXPAND,
                pos.getX() + x2 + EXPAND, pos.getY() + y2 + EXPAND, pos.getZ() + z2 + EXPAND}));

        boolean glow = style.is("Glow");
        boolean corners = style.is("Corners");
        boolean fill = style.is("Fill") || style.is("Outline+Fill") || glow;
        boolean lines = !style.is("Fill");
        float fillA = fillOpacity.value * (glow ? 0.35f : 1f);

        double dist = Math.sqrt(c.camera.getCameraPos().squaredDistanceTo(cx, cy, cz));
        float hw = width.value * 0.0035f * (float) MathHelper.clamp(dist / 4.0, 0.5, 3.0);

        Matrix4f m = c.m;
        Batch.begin(!throughWalls.value, glow);
        Batch b = Batch.triangles();
        if (fill) {
            for (double[] bx : boxes) box(b, m, bx, fillA);
        }
        if (lines) {
            for (double[] e : edges) {
                if (corners) {
                    double dx = e[3] - e[0], dy = e[4] - e[1], dz = e[5] - e[2];
                    double k = 0.28;
                    edge(b, m, e[0], e[1], e[2], e[0] + dx * k, e[1] + dy * k, e[2] + dz * k, hw, 0.95f, 0.95f);
                    edge(b, m, e[3] - dx * k, e[4] - dy * k, e[5] - dz * k, e[3], e[4], e[5], hw, 0.95f, 0.95f);
                } else if (glow) {
                    edge(b, m, e[0], e[1], e[2], e[3], e[4], e[5], hw * 4.5f, 0.55f, 0f);
                    edge(b, m, e[0], e[1], e[2], e[3], e[4], e[5], hw * 0.9f, 1f, 1f);
                } else {
                    edge(b, m, e[0], e[1], e[2], e[3], e[4], e[5], hw, 0.95f, 0.95f);
                }
            }
        }
        b.flush();
        Batch.end();
    }

    /** Чуть выталкивает точку от центра блока, чтобы линии не мерцали (z-fighting) на гранях. */
    private static double ex(double v, double center) {
        return v > center ? v + EXPAND : (v < center ? v - EXPAND : v);
    }

    private float anim(double y) {
        float yf = (float) MathHelper.clamp(y - pos.getY(), 0.0, 1.0);
        return switch (animation.index) {
            case 1 -> 0.6f + 0.4f * (float) Math.sin(tSec * 4.0);
            case 2 -> {
                float w = 0.5f + 0.5f * (float) Math.sin((yf - tSec * 0.8) * Math.PI * 2.0);
                yield 0.3f + 0.7f * w * w;
            }
            default -> 1f;
        };
    }

    private int col(double y, float alpha) {
        float off = (float) MathHelper.clamp(y - pos.getY(), 0.0, 1.0) * 0.2f;
        return Batch.argb(color.rgb(now, off), alpha * anim(y));
    }

    /** Линия как лента, повёрнутая к камере: в центре alpha = centerA, по краям rimA. */
    private void edge(Batch b, Matrix4f m, double ax, double ay, double az, double bx, double by, double bz,
                      float hw, float centerA, float rimA) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double vx = camX - (ax + bx) * 0.5, vy = camY - (ay + by) * 0.5, vz = camZ - (az + bz) * 0.5;
        double nx = dy * vz - dz * vy, ny = dz * vx - dx * vz, nz = dx * vy - dy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-9) return;
        double s = hw / len;
        nx *= s;
        ny *= s;
        nz *= s;
        int cA = col(ay, centerA), cB = col(by, centerA);
        int rA = col(ay, rimA), rB = col(by, rimA);
        tri(b, m, ax - nx, ay - ny, az - nz, rA, ax, ay, az, cA, bx - nx, by - ny, bz - nz, rB);
        tri(b, m, ax, ay, az, cA, bx, by, bz, cB, bx - nx, by - ny, bz - nz, rB);
        tri(b, m, ax, ay, az, cA, ax + nx, ay + ny, az + nz, rA, bx, by, bz, cB);
        tri(b, m, ax + nx, ay + ny, az + nz, rA, bx + nx, by + ny, bz + nz, rB, bx, by, bz, cB);
    }

    private static void tri(Batch b, Matrix4f m,
                            double x1, double y1, double z1, int c1,
                            double x2, double y2, double z2, int c2,
                            double x3, double y3, double z3, int c3) {
        b.v(m, x1, y1, z1, c1);
        b.v(m, x2, y2, z2, c2);
        b.v(m, x3, y3, z3, c3);
    }

    private void box(Batch b, Matrix4f m, double[] q, float a) {
        double x1 = q[0], y1 = q[1], z1 = q[2], x2 = q[3], y2 = q[4], z2 = q[5];
        quad(b, m, a, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2); // низ
        quad(b, m, a, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2); // верх
        quad(b, m, a, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1); // север
        quad(b, m, a, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2); // юг
        quad(b, m, a, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1); // запад
        quad(b, m, a, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1); // восток
    }

    private void quad(Batch b, Matrix4f m, float a,
                      double ax, double ay, double az, double bx, double by, double bz,
                      double cx, double cy, double cz, double dx, double dy, double dz) {
        int ca = col(ay, a), cb = col(by, a), cc = col(cy, a), cd = col(dy, a);
        tri(b, m, ax, ay, az, ca, bx, by, bz, cb, cx, cy, cz, cc);
        tri(b, m, ax, ay, az, ca, cx, cy, cz, cc, dx, dy, dz, cd);
    }
}
