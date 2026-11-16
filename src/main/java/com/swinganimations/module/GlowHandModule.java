package com.swinganimations.module;

import com.swinganimations.SwingAnimationsClient;
import com.swinganimations.render.Batch;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * GlowHand: свечение вокруг руки от первого лица. Режим Glow: несколько слегка увеличенных полупрозрачных копий руки цвета темы
 * (контур и мягкое сияние). Режим Fill: анимированная заливка внутри руки с настройкой прозрачности (при 100% рука
 * полностью заменяется цветом). Без GLSL-шейдеров. Хук стоит на вызове отрисовки руки в HeldItemRenderer (см. HeldItemGlowMixin).
 */
public final class GlowHandModule extends Module {
    private static final int FULL_LIGHT = 15728880;
    /** Сплошной белый квадрат: цвет слоя получается чистым, без рисунка скина. */
    private static final Identifier WHITE = Identifier.of("swinganimations", "textures/misc/white.png");

    private final Setting.Mode mode = add(new Setting.Mode("mode", "Mode", "Glow", "Fill"));
    private final Setting.Bool hand = add(new Setting.Bool("hand", "Empty hand", true));
    private final Setting.Bool items = add(new Setting.Bool("items", "Held items (sword etc.)", true));
    private final Setting.Num intensity = add(new Setting.Num("intensity", "Glow intensity", 0.8f, 0.2f, 1.5f, 0.05f)
            .<Setting.Num>visibleWhen(() -> mode.is("Glow")));
    private final Setting.Num spread = add(new Setting.Num("spread", "Glow size", 0.05f, 0.02f, 0.12f, 0.01f)
            .<Setting.Num>visibleWhen(() -> mode.is("Glow")));
    private final Setting.Num opacity = add(new Setting.Num("opacity", "Fill opacity", 0.6f, 0.1f, 1f, 0.05f)
            .<Setting.Num>visibleWhen(() -> mode.is("Fill")));
    private final Setting.Num speed = add(new Setting.Num("speed", "Animation speed", 1f, 0.2f, 3f, 0.1f));
    private final ColorSettings color = new ColorSettings(this);

    public GlowHandModule() {
        super("glow_hand", "GlowHand", "render");
    }

    private static boolean warned;

    /**
     * Вызывается из миксина сразу после ванильной отрисовки руки. В 1.21.9+ рука рисуется через OrderedRenderCommandQueue:
     * слои свечения отправляются туда же через submitModelPart с цветом (tintedColor). Ошибка отключает модуль, а не игру.
     */
    public void drawSafe(PlayerEntityRenderer r, MatrixStack m, OrderedRenderCommandQueue queue, Identifier skin, boolean right) {
        try {
            draw(r, m, queue, right);
        } catch (Throwable t) {
            SwingAnimationsClient.LOGGER.warn("GlowHand отключён из-за ошибки: {}", t.toString());
            setEnabled(false);
        }
    }

    private void draw(PlayerEntityRenderer r, MatrixStack m, OrderedRenderCommandQueue queue, boolean right) {
        if (!hand.value) return;
        ModelPart part = right ? r.getModel().rightArm : r.getModel().leftArm;
        long now = System.currentTimeMillis();
        float t = now / 1000f * speed.value;
        RenderLayer layer = RenderLayer.getEntityTranslucent(WHITE);
        int base = color.rgb(now, 0f);
        if (mode.is("Fill")) {
            float k = 0.5f + 0.5f * (float) Math.sin(t * 2.2f);
            int c0 = Batch.mix(base, Batch.mix(base, 0xFFFFFF, 0.55f), k);
            float op = opacity.value;
            layer(m, queue, layer, part, right, 1.025f, Batch.argb(c0, op));
            float shimmer = 0.5f + 0.5f * (float) Math.sin(t * 3.1f + 1.7f);
            int c1 = Batch.mix(color.rgb(now, 0.12f), 0xFFFFFF, 0.35f);
            layer(m, queue, layer, part, right, 1.05f, Batch.argb(c1, op * 0.45f * shimmer));
        } else {
            float pulse = 0.85f + 0.15f * (float) Math.sin(t * 2.4f);
            float in = intensity.value * pulse;
            for (int k = 1; k <= 4; k++) {
                float s = 1f + spread.value * k * 0.6f;
                float a = Math.min(1f, in * 0.30f / (1f + (k - 1) * 0.9f));
                layer(m, queue, layer, part, right, s, Batch.argb(color.rgb(now, k * 0.05f), a));
            }
        }
    }

    /** Рисует руку ещё раз, увеличенной относительно центра её кубоида. */
    private static void layer(MatrixStack m, OrderedRenderCommandQueue queue, RenderLayer layer, ModelPart part,
                              boolean right, float scale, int argb) {
        float px = (part.pivotX + (right ? -1f : 1f)) / 16f;
        float py = (part.pivotY + 4f) / 16f;
        float pz = part.pivotZ / 16f;
        m.push();
        m.translate(px, py, pz);
        m.scale(scale, scale, scale);
        m.translate(-px, -py, -pz);
        queue.submitModelPart(part, m, layer, FULL_LIGHT, OverlayTexture.DEFAULT_UV, null, argb, null);
        m.pop();
    }

    /**
     * Свечение предметов в руке в 1.21.11 пока выключено: раньше предмет перерисовывался через перекрашивающий
     * VertexConsumerProvider, которого в command-queue рендере нет.
     */
    public void drawItemSafe(ItemRenderState state, MatrixStack m, Object queue, int overlay) {
        if (warned) return;
        warned = true;
        SwingAnimationsClient.LOGGER.info("GlowHand: свечение предметов на 1.21.11 не поддерживается, работает только рука.");
    }
}
