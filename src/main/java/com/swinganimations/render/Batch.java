package com.swinganimations.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Тонкая обёртка над BufferBuilder: считает вершины, чтобы не отправлять пустой буфер.
 * Для TRIANGLE_STRIP есть restart(): несколько лент склеиваются в один draw call вырожденными треугольниками.
 * 1.21.11: BufferRenderer и ShaderProgramKeys удалены, рисуем через собственные RenderPipeline (POSITION_COLOR)
 * и RenderPass сразу в основной framebuffer. Публичный API (strip/triangles/v/restart/flush/begin/end) не менялся.
 */
public final class Batch {
    private final BufferBuilder b;
    private final boolean strip;
    private int n;
    private boolean restart;
    private Matrix4f lm;
    private float lx, ly, lz;
    private int lc;

    private Batch(DrawMode mode) {
        this.strip = mode == DrawMode.TRIANGLE_STRIP;
        this.b = Tessellator.getInstance().begin(mode, VertexFormats.POSITION_COLOR);
    }

    public static Batch strip() { return new Batch(DrawMode.TRIANGLE_STRIP); }

    public static Batch triangles() { return new Batch(DrawMode.TRIANGLES); }

    /** Следующая вершина начинает новую ленту (только для strip). */
    public void restart() { restart = true; }

    private void put(Matrix4f m, float x, float y, float z, int argb) {
        b.vertex(m, x, y, z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
        n++;
    }

    public void v(Matrix4f m, double x, double y, double z, int argb) {
        float fx = (float) x, fy = (float) y, fz = (float) z;
        if (restart) {
            restart = false;
            if (n > 0) {
                put(lm, lx, ly, lz, lc);
                put(m, fx, fy, fz, argb);
            }
        }
        put(m, fx, fy, fz, argb);
        lm = m;
        lx = fx;
        ly = fy;
        lz = fz;
        lc = argb;
    }

    public void flush() {
        BuiltBuffer built = b.endNullable();
        if (built == null) return;
        if (n >= 3) draw(built, pipeline(strip, depthTest, additive)); else built.close();
    }

    // ---- состояние рендера ----

    private static boolean depthTest;
    private static boolean additive;
    private static boolean screen;
    private static RawProjectionMatrix guiProjection;

    /**
     * Экранный режим для HUD/GUI: вершины задаются в масштабированных GUI-координатах (как в DrawContext),
     * отрисовка идёт с ортопроекцией окна, без глубины. Нужен, потому что DrawContext в 1.21.11 отложенный.
     */
    public static void beginScreen(boolean additive) {
        Batch.depthTest = false;
        Batch.additive = additive;
        Batch.screen = true;
    }

    /** depthTest: проверять глубину; additive: аддитивное смешивание (SRC_ALPHA, ONE). Запись глубины и отсечение граней выключены. */
    public static void begin(boolean depthTest, boolean additive) {
        Batch.depthTest = depthTest;
        Batch.additive = additive;
    }

    public static void end() {
        depthTest = false;
        additive = false;
        screen = false;
    }

    // ---- пайплайны и отрисовка ----

    private static final Map<Integer, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static MappableRingBuffer ring;

    private static RenderPipeline pipeline(boolean strip, boolean depth, boolean add) {
        int key = (strip ? 4 : 0) | (depth ? 2 : 0) | (add ? 1 : 0);
        return PIPELINES.computeIfAbsent(key, k -> RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                        .withLocation(Identifier.of("swinganimations", "pipeline/batch_" + k))
                        .withVertexFormat(VertexFormats.POSITION_COLOR, strip ? DrawMode.TRIANGLE_STRIP : DrawMode.TRIANGLES)
                        .withBlend(add ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(depth ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build()));
    }

    private static void draw(BuiltBuffer built, RenderPipeline pipeline) {
        try (built) {
            BuiltBuffer.DrawParameters dp = built.getDrawParameters();
            VertexFormat format = dp.format();
            int indexCount = dp.indexCount();
            ByteBuffer vertices = built.getBuffer();
            int size = dp.vertexCount() * format.getVertexSize();

            if (ring == null || ring.size() < size) {
                if (ring != null) ring.close();
                ring = new MappableRingBuffer(() -> "swinganimations batch",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, Math.max(size, 4096));
            }
            GpuBuffer vbo = ring.getBlocking();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (GpuBuffer.MappedView view = encoder.mapBuffer(vbo.slice(0, vertices.remaining()), false, true)) {
                MemoryUtil.memCopy(vertices, view.data());
            }

            RenderSystem.ShapeIndexBuffer shape = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            GpuBuffer ibo = shape.getIndexBuffer(indexCount);

            boolean gui = screen;
            if (gui) {
                var win = MinecraftClient.getInstance().getWindow();
                if (guiProjection == null) guiProjection = new RawProjectionMatrix("swinganimations gui");
                Matrix4f ortho = new Matrix4f().setOrtho(0f, win.getScaledWidth(), win.getScaledHeight(), 0f, -1000f, 1000f);
                RenderSystem.backupProjectionMatrix();
                RenderSystem.setProjectionMatrix(guiProjection.set(ortho), com.mojang.blaze3d.systems.ProjectionType.ORTHOGRAPHIC);
            }
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                    .write(gui ? IDENTITY : RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
            var fb = MinecraftClient.getInstance().getFramebuffer();
            try (RenderPass pass = encoder.createRenderPass(() -> "swinganimations batch",
                    fb.getColorAttachmentView(), OptionalInt.empty(),
                    fb.getDepthAttachmentView(), OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, vbo);
                pass.setIndexBuffer(ibo, shape.getIndexType());
                pass.drawIndexed(0, 0, indexCount, 1);
            } finally {
                if (gui) RenderSystem.restoreProjectionMatrix();
            }
            ring.rotate();
        }
    }

    /** Вызывать при закрытии клиента (GameRenderer.close), чтобы освободить GPU-буфер. */
    public static void close() {
        if (ring != null) {
            ring.close();
            ring = null;
        }
        if (guiProjection != null) {
            guiProjection.close();
            guiProjection = null;
        }
    }

    // ---- цвет ----

    public static int argb(int rgb, float alpha) {
        return ((int) (MathHelper.clamp(alpha, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int rgbA, int rgbB, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int r = (int) MathHelper.lerp(t, (rgbA >> 16) & 255, (rgbB >> 16) & 255);
        int g = (int) MathHelper.lerp(t, (rgbA >> 8) & 255, (rgbB >> 8) & 255);
        int bl = (int) MathHelper.lerp(t, rgbA & 255, rgbB & 255);
        return (r << 16) | (g << 8) | bl;
    }
}
