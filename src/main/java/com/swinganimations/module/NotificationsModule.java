package com.swinganimations.module;

import com.swinganimations.event.EventBus;
import com.swinganimations.event.ModuleToggleEvent;
import com.swinganimations.event.NotificationEvent;
import com.swinganimations.gui.Hud;
import com.swinganimations.gui.Ui;
import com.swinganimations.util.Spring;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Всплывающие уведомления справа снизу (выезжают на пружине): включение модулей и сообщения других модулей. */
public final class NotificationsModule extends Module {
    private static final class Toast {
        final String title, text;
        final int rgb;
        final long t0;
        final Spring slide = new Spring(0f, 170f, 21f);

        Toast(String title, String text, int rgb, long t0) {
            this.title = title; this.text = text; this.rgb = rgb; this.t0 = t0;
        }
    }

    private final Setting.Num duration = add(new Setting.Num("duration", "Duration (ms)", 2500f, 1000f, 6000f, 250f));
    private final Setting.Bool toggles = add(new Setting.Bool("toggles", "Module toggles", true));

    private final List<Toast> toasts = new ArrayList<>();

    public NotificationsModule() {
        super("notifications", "Notifications", "hud");
        EventBus.on(NotificationEvent.class, e -> push(e.title(), e.text(), e.rgb()));
        EventBus.on(ModuleToggleEvent.class, e -> {
            Module m = e.module();
            if (!toggles.value || m == this || m instanceof ThemeModule) return;
            String name = Text.translatableWithFallback("swinganimations.module." + m.id, m.fallbackName).getString();
            push(name, e.enabled() ? "enabled" : "disabled", e.enabled() ? (Ui.ACCENT & 0xFFFFFF) : 0xFF6B6B);
        });
    }

    @Override
    public void onDisable() {
        toasts.clear();
    }

    private void push(String title, String text, int rgb) {
        if (!isEnabled() || MinecraftClient.getInstance().player == null) return;
        toasts.add(new Toast(title, text, rgb, System.currentTimeMillis()));
        while (toasts.size() > 6) toasts.remove(0);
    }

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        if (toasts.isEmpty() || MinecraftClient.getInstance().options.hudHidden) return;
        long now = System.currentTimeMillis();
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int h = 24;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            boolean alive = now - t.t0 < duration.value;
            t.slide.target = alive ? 1f : 0f;
            t.slide.update();
            if (!alive && t.slide.value < 0.02f) toasts.remove(i);
        }
        int row = 0;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            int w = Math.max(Hud.w(t.title), Hud.w(t.text)) + 22;
            w = Math.max(w, 100);
            int x = sw - 8 - Math.round(w * Math.max(0f, t.slide.value));
            int y = sh - 52 - row * (h + 4);
            Hud.panel(ctx, x, y, w, h);
            ctx.fill(x + 4, y + 5, x + 6, y + h - 5, 0xFF000000 | t.rgb);
            Hud.text(ctx, t.title, x + 11, y + 4, Ui.TEXT);
            Hud.text(ctx, t.text, x + 11, y + 14, Ui.TEXT_DIM);
            row++;
        }
    }
}
