package com.swinganimations.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.swinganimations.SwingAnimationsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Перетаскиваемые HUD-окна. Пока открыт чат, у окон появляется рамка: зажмите ЛКМ и тяните,
 * ПКМ возвращает окно на место. Позиция хранится как смещение от штатного места окна, поэтому
 * остальная раскладка HUD не ломается. Сохраняется в swinganimations_hud.json.
 *
 * Модуль оборачивает отрисовку окна в push(...) / pop(...), а в режиме правки (editing())
 * показывает пример содержимого, если реальных данных сейчас нет.
 */
public final class HudLayout {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Win {
        public final String id;
        public int dx, dy;
        int bx, by, w, h;
        int frame = -1;

        Win(String id) { this.id = id; }
    }

    private static final Map<String, Win> WINS = new LinkedHashMap<>();
    private static int frame;
    private static Win dragging;
    private static Win hover;
    private static int grabX, grabY;
    private static boolean wasL, wasR;

    private HudLayout() {}

    public static Win win(String id) {
        return WINS.computeIfAbsent(id, Win::new);
    }

    /** Вызывается раз в кадр перед отрисовкой HUD-модулей. */
    public static void nextFrame() { frame++; }

    /** Чат открыт: окна можно двигать, пустые окна показывают пример. */
    public static boolean editing() {
        return MinecraftClient.getInstance().currentScreen instanceof ChatScreen;
    }

    public static void push(DrawContext ctx, Win w) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(w.dx, w.dy);
    }

    /** x, y, ww, hh: прямоугольник окна в штатных координатах (без смещения). */
    public static void pop(DrawContext ctx, Win w, int x, int y, int ww, int hh) {
        w.bx = x;
        w.by = y;
        w.w = ww;
        w.h = hh;
        w.frame = frame;
        if (editing()) {
            int col = w == dragging ? Ui.ACCENT : (w == hover ? Ui.ACCENT_LINE : 0x55FFFFFF);
            Ui.outline(ctx, x - 1, y - 1, ww + 2, hh + 2, 5, col);
        }
        ctx.getMatrices().popMatrix();
    }

    // ---------------- мышь ----------------

    /** Чат открылся: сбрасываем состояние, нажатая заранее кнопка не должна начать перетаскивание. */
    public static void begin() {
        dragging = null;
        hover = null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() != null) {
            long h = mc.getWindow().getHandle();
            wasL = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            wasR = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        }
    }

    public static void end() {
        if (dragging != null) save();
        dragging = null;
        hover = null;
    }

    private static boolean inside(Win w, int mx, int my) {
        int x = w.bx + w.dx;
        int y = w.by + w.dy;
        return mx >= x && mx < x + w.w && my >= y && my < y + w.h;
    }

    /** Вызывается после отрисовки экрана чата. mx, my: координаты в масштабе GUI. */
    public static void onChatRender(DrawContext ctx, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (dragging == null) {
            hover = null;
            List<Win> list = new ArrayList<>(WINS.values());
            Collections.reverse(list); // верхнее окно (нарисованное позже) проверяем первым
            for (Win w : list) {
                if (w.frame == frame && inside(w, mx, my)) {
                    hover = w;
                    break;
                }
            }
            if (hover != null && l && !wasL) {
                dragging = hover;
                grabX = mx - hover.dx;
                grabY = my - hover.dy;
            } else if (hover != null && r && !wasR) {
                hover.dx = 0;
                hover.dy = 0;
                save();
            }
        }
        if (dragging != null) {
            if (l) {
                int sw = ctx.getScaledWindowWidth();
                int sh = ctx.getScaledWindowHeight();
                dragging.dx = MathHelper.clamp(mx - grabX, -dragging.bx, Math.max(-dragging.bx, sw - dragging.w - dragging.bx));
                dragging.dy = MathHelper.clamp(my - grabY, -dragging.by, Math.max(-dragging.by, sh - dragging.h - dragging.by));
            } else {
                dragging = null;
                save();
            }
        }
        wasL = l;
        wasR = r;

        boolean any = false;
        for (Win w : WINS.values()) if (w.frame == frame) { any = true; break; }
        if (any) {
            Text hint = Hud.t(Text.translatableWithFallback("swinganimations.hud.drag_hint",
                    "Drag HUD windows with the mouse, right click resets"));
            int tw = Ui.tr().getWidth(hint);
            int x = (ctx.getScaledWindowWidth() - tw) / 2;
            Ui.rect(ctx, x - 6, 4, tw + 12, 14, 4, 0xA0000000);
            ctx.drawText(Ui.tr(), hint, x, 8, Ui.TEXT_DIM, false);
        }
    }

    // ---------------- сохранение ----------------

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("swinganimations_hud.json");
    }

    public static void save() {
        try {
            Map<String, int[]> out = new LinkedHashMap<>();
            for (Win w : WINS.values()) {
                if (w.dx != 0 || w.dy != 0) out.put(w.id, new int[]{w.dx, w.dy});
            }
            Path p = path();
            Path tmp = p.resolveSibling("swinganimations_hud.json.tmp");
            Files.createDirectories(p.getParent());
            Files.writeString(tmp, GSON.toJson(out), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            SwingAnimationsClient.LOGGER.warn("Не удалось сохранить позиции HUD: {}", e.toString());
        }
    }

    public static void load() {
        Path p = path();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, int[]>>() { }.getType();
            Map<String, int[]> in = GSON.fromJson(r, type);
            if (in == null) return;
            for (Map.Entry<String, int[]> en : in.entrySet()) {
                int[] v = en.getValue();
                if (v == null || v.length < 2) continue;
                Win w = win(en.getKey());
                w.dx = v[0];
                w.dy = v[1];
            }
        } catch (Exception e) {
            SwingAnimationsClient.LOGGER.warn("Повреждён swinganimations_hud.json ({}), позиции сброшены", e.toString());
        }
    }
}
