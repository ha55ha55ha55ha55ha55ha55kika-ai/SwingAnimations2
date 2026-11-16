package com.swinganimations.module;

import com.swinganimations.gui.Hud;
import com.swinganimations.gui.HudLayout;
import com.swinganimations.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HUD в виде стеклянных «таблеток» с иконками: верхний ряд (ватермарка, ник, FPS, BPS, время),
 * координаты, список биндов и эффекты. Каждая группа двигается мышью при открытом чате.
 */
public final class InterfaceModule extends Module {
    private static final Identifier LOGO = Identifier.of("swinganimations", "textures/gui/logo_small.png");
    private static final Identifier I_USER = icon("user");
    private static final Identifier I_PULSE = icon("pulse");
    private static final Identifier I_SPEED = icon("speed");
    private static final Identifier I_CLOCK = icon("clock");
    private static final Identifier I_PIN = icon("pin");
    private static final Identifier I_KEYS = icon("keys");
    private static final Identifier I_FLASK = icon("flask");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final int H = 16;      // высота таблетки
    private static final int GAP = 4;     // зазор между таблетками
    private static final int PAD = 6;
    private static final int ICON = 10;

    private final Setting.Bool watermark = add(new Setting.Bool("watermark", "Watermark", true));
    private final Setting.Bool name = add(new Setting.Bool("name", "Player name", true));
    private final Setting.Bool fps = add(new Setting.Bool("fps", "FPS", true));
    private final Setting.Bool bps = add(new Setting.Bool("bps", "Speed (BPS)", true));
    private final Setting.Bool time = add(new Setting.Bool("time", "Clock", true));
    private final Setting.Bool coords = add(new Setting.Bool("coords", "Coordinates", true));
    private final Setting.Bool keybinds = add(new Setting.Bool("keybinds", "Keybinds", true));
    private final Setting.Bool effects = add(new Setting.Bool("effects", "Effects", true));
    private final Setting.Num effectsScale = add(new Setting.Num("effects_scale", "Effects scale", 0.8f, 0.5f, 1.5f, 0.05f)
            .<Setting.Num>visibleWhen(() -> effects.value));

    private final HudLayout.Win winWatermark = HudLayout.win("watermark");
    private final HudLayout.Win winKeybinds = HudLayout.win("keybinds");
    private final HudLayout.Win winEffects = HudLayout.win("effects");
    private final HudLayout.Win winCoords = HudLayout.win("coords");

    // Примеры содержимого для режима правки (открыт чат), когда реальных данных нет.
    private static final List<Text> S_KB_L = List.of(Text.literal("AutoSprint"), Text.literal("TargetESP"));
    private static final List<Text> S_KB_R = List.of(Text.literal("R"), Text.literal("G"));
    private static final List<Integer> S_KB_C = List.of(Ui.TEXT, Ui.TEXT_DIM);
    private static final List<Text> S_EF_L = List.of(Text.literal("Speed II"), Text.literal("Strength"));
    private static final List<Text> S_EF_R = List.of(Text.literal("1:30"), Text.literal("0:45"));
    private static final List<Integer> S_EF_C = List.of(Ui.TEXT, Ui.TEXT);

    // Кэш: строки пересобираются только когда значение меняется, а не каждый кадр.
    private String timeStr = "";
    private long timeAt;
    private String fpsStr = "";
    private int fpsShown = -1;
    private String bpsStr = "0.0 BPS";
    private int bpsTenths = -1;
    private double bpsValue;
    private double lastPx = Double.NaN, lastPz;
    private String coordStr = "";
    private long cx = Long.MIN_VALUE, cy, cz;
    private final List<Text> kbLeft = new ArrayList<>();
    private final List<Text> kbRight = new ArrayList<>();
    private final List<Integer> kbColors = new ArrayList<>();
    private final List<Text> efLeft = new ArrayList<>();
    private final List<Text> efRight = new ArrayList<>();
    private final List<Integer> efColors = new ArrayList<>();
    private int listTimer = 100;

    public InterfaceModule() {
        super("interface", "Interface", "hud");
    }

    private static Identifier icon(String n) {
        return Identifier.of("swinganimations", "textures/gui/icon_" + n + ".png");
    }

    @Override
    public void onDisable() {
        listTimer = 100;
        lastPx = Double.NaN;
    }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p != null) updateSpeed(p);
        if (++listTimer < 5) return;
        listTimer = 0;
        kbLeft.clear();
        kbRight.clear();
        kbColors.clear();
        for (Module m : ModuleManager.MODULES) {
            if (m.bindCode < 0) continue;
            kbLeft.add(Text.translatableWithFallback("swinganimations.module." + m.id, m.fallbackName));
            kbRight.add(keyText(m));
            kbColors.add(m.isEnabled() ? Ui.TEXT : Ui.TEXT_DIM);
        }
        efLeft.clear();
        efRight.clear();
        efColors.clear();
        if (p != null) {
            for (StatusEffectInstance e : p.getStatusEffects()) {
                Text nm = e.getEffectType().value().getName();
                Text n = e.getAmplifier() > 0 ? Text.empty().append(nm).append(" " + (e.getAmplifier() + 1)) : nm;
                efLeft.add(n);
                efRight.add(Text.literal(duration(e)));
                efColors.add(e.getEffectType().value().isBeneficial() ? Ui.TEXT : 0xFFFF8A8A);
            }
        }
    }

    /** Горизонтальная скорость в блоках в секунду: сдвиг за тик × 20, со сглаживанием. */
    private void updateSpeed(ClientPlayerEntity p) {
        double x = p.getX(), z = p.getZ();
        if (!Double.isNaN(lastPx)) {
            double d = Math.hypot(x - lastPx, z - lastPz);
            if (d < 10.0) bpsValue += (d * 20.0 - bpsValue) * 0.25; // телепорты не считаем
        }
        lastPx = x;
        lastPz = z;
        int tenths = (int) Math.round(bpsValue * 10.0);
        if (tenths != bpsTenths) {
            bpsTenths = tenths;
            bpsStr = String.format(Locale.ROOT, "%.1f BPS", tenths / 10.0);
        }
    }

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.options.hudHidden) return;
        int x = 8;
        int y = 8;
        if (watermark.value || name.value || fps.value || bps.value || time.value) y = topRow(ctx, mc, x, y);
        if (coords.value) y = coordsChip(ctx, p, x, y);
        if (keybinds.value) y = keybindsBlock(ctx, x, y);
        if (effects.value) effectsBlock(ctx, x, y);
    }

    // ---------------- таблетки ----------------

    /** Рисует таблетку «иконка + текст» и возвращает её ширину. icon == null: логотип мода. */
    private int chip(DrawContext ctx, int x, int y, Identifier icon, String text, int color) {
        boolean logo = icon == null;
        int iw = logo ? 17 : ICON;
        int w = PAD + iw + 4 + Hud.w(text) + 7;
        Hud.chip(ctx, x, y, w, H);
        if (logo) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, x + PAD, y + (H - 12) / 2, 0f, 0f, 17, 12, 128, 92, 128, 92);
        } else {
            Hud.icon(ctx, icon, x + PAD, y + (H - ICON) / 2, ICON);
        }
        Hud.text(ctx, text, x + PAD + iw + 4, y + (H - 8) / 2, color);
        return w;
    }

    private int topRow(DrawContext ctx, MinecraftClient mc, int x, int y) {
        int fpsNow = mc.getCurrentFps();
        if (fpsNow != fpsShown) {
            fpsShown = fpsNow;
            fpsStr = fpsNow + " FPS";
        }
        long sec = System.currentTimeMillis() / 1000L;
        if (sec != timeAt) {
            timeAt = sec;
            timeStr = LocalTime.now().format(TIME);
        }
        HudLayout.push(ctx, winWatermark);
        int cx0 = x;
        if (watermark.value) cx0 += chip(ctx, cx0, y, null, "SwingAnimations", Ui.TEXT) + GAP;
        if (name.value) cx0 += chip(ctx, cx0, y, I_USER, mc.getSession().getUsername(), Ui.TEXT) + GAP;
        if (fps.value) cx0 += chip(ctx, cx0, y, I_PULSE, fpsStr, Ui.TEXT) + GAP;
        if (bps.value) cx0 += chip(ctx, cx0, y, I_SPEED, bpsStr, Ui.TEXT) + GAP;
        if (time.value) cx0 += chip(ctx, cx0, y, I_CLOCK, timeStr, Ui.TEXT) + GAP;
        HudLayout.pop(ctx, winWatermark, x, y, Math.max(1, cx0 - GAP - x), H);
        return y + H + GAP;
    }

    private int coordsChip(DrawContext ctx, ClientPlayerEntity p, int x, int y) {
        long nx = Math.round(p.getX()), ny = Math.round(p.getY()), nz = Math.round(p.getZ());
        if (nx != cx || ny != cy || nz != cz) {
            cx = nx;
            cy = ny;
            cz = nz;
            coordStr = nx + " / " + ny + " / " + nz;
        }
        HudLayout.push(ctx, winCoords);
        int w = chip(ctx, x, y, I_PIN, coordStr, Ui.TEXT);
        HudLayout.pop(ctx, winCoords, x, y, w, H);
        return y + H + GAP;
    }

    // ---------------- списки ----------------

    private int keybindsBlock(DrawContext ctx, int x, int y) {
        Text title = Text.translatableWithFallback("swinganimations.hud.keybinds", "Keybinds");
        if (kbLeft.isEmpty()) {
            if (!HudLayout.editing()) return y;
            return block(ctx, x, y, I_KEYS, title, S_KB_L, S_KB_R, S_KB_C, false, winKeybinds, 1f);
        }
        return block(ctx, x, y, I_KEYS, title, kbLeft, kbRight, kbColors, false, winKeybinds, 1f);
    }

    private int effectsBlock(DrawContext ctx, int x, int y) {
        Text title = Text.translatableWithFallback("swinganimations.hud.effects", "Effects");
        if (efLeft.isEmpty()) {
            if (!HudLayout.editing()) return y;
            return block(ctx, x, y, I_FLASK, title, S_EF_L, S_EF_R, S_EF_C, true, winEffects, effectsScale.value);
        }
        return block(ctx, x, y, I_FLASK, title, efLeft, efRight, efColors, true, winEffects, effectsScale.value);
    }

    /**
     * Заголовок-таблетка и под ним по таблетке на строку, все одной ширины.
     * rightAlign: правый текст прижат к краю (эффекты), иначе стоит сразу за названием (бинды).
     */
    private int block(DrawContext ctx, int x, int y, Identifier icon, Text title, List<Text> left, List<Text> right,
                      List<Integer> colors, boolean rightAlign, HudLayout.Win win, float sc) {
        int w = PAD + ICON + 4 + Hud.w(title) + 7;
        for (int i = 0; i < left.size(); i++) {
            w = Math.max(w, PAD + ICON + 4 + Hud.w(left.get(i)) + 8 + Hud.w(right.get(i)) + 7);
        }
        w = Math.max(w, 92);
        int rows = left.size();
        int total = (rows + 1) * (H + GAP) - GAP;

        HudLayout.push(ctx, win);
        org.joml.Matrix3x2fStack m = ctx.getMatrices();
        boolean scaled = sc != 1f;
        if (scaled) {
            // Масштаб от левого верхнего угла блока: рамка перетаскивания считается по уменьшенному размеру.
            m.pushMatrix();
            m.translate(x, y);
            m.scale(sc, sc);
            m.translate(-x, -y);
        }
        Hud.chip(ctx, x, y, w, H);
        Hud.icon(ctx, icon, x + PAD, y + (H - ICON) / 2, ICON);
        Hud.text(ctx, title, x + PAD + ICON + 4, y + (H - 8) / 2, Ui.TEXT);
        for (int i = 0; i < rows; i++) {
            int ry = y + (i + 1) * (H + GAP);
            Hud.chip(ctx, x, ry, w, H);
            Hud.icon(ctx, icon, x + PAD, ry + (H - ICON) / 2, ICON);
            int tx = x + PAD + ICON + 4;
            int ty = ry + (H - 8) / 2;
            Hud.text(ctx, left.get(i), tx, ty, colors.get(i));
            if (rightAlign) {
                Hud.textRight(ctx, right.get(i), x + w - 7, ty, Ui.TEXT_DIM);
            } else {
                Hud.text(ctx, right.get(i), tx + Hud.w(left.get(i)) + 8, ty, Ui.TEXT_DIM);
            }
        }
        if (scaled) m.popMatrix();
        int shownH = Math.round(total * sc);
        HudLayout.pop(ctx, win, x, y, Math.round(w * sc), shownH);
        return y + shownH + GAP;
    }

    private static Text keyText(Module m) {
        if (m.bindMouse) return Text.literal("M" + (m.bindCode + 1));
        return InputUtil.fromKeyCode(m.bindCode, -1).getLocalizedText();
    }

    private static String duration(StatusEffectInstance e) {
        if (e.isInfinite()) return "**:**";
        int sec = Math.max(0, e.getDuration() / 20);
        return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }
}
