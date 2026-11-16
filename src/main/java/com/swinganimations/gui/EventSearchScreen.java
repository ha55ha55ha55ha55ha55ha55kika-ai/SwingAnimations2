package com.swinganimations.gui;

import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import com.swinganimations.api.HolyWorldApi;
import com.swinganimations.module.EventHudModule;
import com.swinganimations.util.Spring;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Поиск анархии для EventHud: сетка 1–36 (кликом или касанием), быстрый ввод номера, подсветка анархий с ивентами
 * цветом лучшей редкости. Цифры 4–9 выбирают сразу, для 1–3 нужна вторая цифра или Enter.
 */
public final class EventSearchScreen extends Screen {
    private static final int COLS = 6;
    private static final int MAX = HolyWorldApi.MAX_ANARCHY;

    private final Screen parent;
    private final EventHudModule module;
    private TextFieldWidget field;
    private final Spring[] hover = new Spring[MAX + 1];
    private final Spring appear = new Spring(0f, 170f, 20f);
    private final int[] best = new int[MAX + 1];
    private int[] counts = new int[MAX + 1];

    private int px, py, pw, ph, gx, gy, cw, ch, fieldX, fieldY, fieldW, btnY, btnW;
    private static final int GAP = 4;
    private static final int BTN_H = 18;

    public EventSearchScreen(Screen parent, EventHudModule module) {
        super(Text.literal("Anarchy search"));
        this.parent = parent;
        this.module = module;
        for (int i = 0; i <= MAX; i++) hover[i] = new Spring(0f, 220f, 24f);
        appear.target = 1f;
    }

    @Override
    protected void init() {
        cw = Math.min(34, Math.max(22, (width - 24 - 24 - (COLS - 1) * GAP) / COLS));
        int fixed = 22 + 22 + 8 + 12 + 6 + BTN_H + 12;
        ch = MathHelper.clamp((height - 16 - fixed - 5 * GAP) / 6, 14, 24);
        int gridW = COLS * cw + (COLS - 1) * GAP;
        int gridH = 6 * ch + 5 * GAP;
        pw = gridW + 24;
        ph = fixed + gridH;
        px = (width - pw) / 2;
        py = Math.max(6, (height - ph) / 2);
        fieldX = px + 12;
        fieldY = py + 22;
        fieldW = pw - 24;
        gx = px + 12;
        gy = fieldY + 22 + 8;
        btnY = gy + gridH + 6 + 12;
        btnW = (pw - 24 - GAP) / 2;

        field = new TextFieldWidget(textRenderer, fieldX + 8, fieldY + 6, fieldW - 16, 12, Text.empty());
        field.setDrawsBackground(false);
        field.setMaxLength(2);
        field.setTextPredicate(s -> s.matches("\\d{0,2}"));
        field.setFocused(true);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private void choose(int n) {
        module.select(n);
        close();
    }

    private boolean matches(int n, String filter) {
        return filter.isEmpty() || String.valueOf(n).startsWith(filter);
    }

    private void commit() {
        String t = field.getText();
        if (t.isEmpty()) return;
        try {
            int n = Integer.parseInt(t);
            if (n >= 1 && n <= MAX) choose(n);
        } catch (NumberFormatException ignored) {
            // не число: ничего не делаем
        }
    }

    // ---------------- отрисовка ----------------

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        java.util.Arrays.fill(best, 0);
        counts = module.anarchyCounts(best);
        long now = System.currentTimeMillis();
        float a = appear.update();
        float s = 0.92f + 0.08f * MathHelper.clamp(a, 0f, 1.1f);
        org.joml.Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(px + pw / 2f, py + ph / 2f);
        m.scale(s, s);
        m.translate(-(px + pw / 2f), -(py + ph / 2f));

        Ui.rect(ctx, px, py, pw, ph, 8, Ui.BG);
        Ui.outline(ctx, px, py, pw, ph, 8, Ui.OUTLINE);
        Hud.text(ctx, "Поиск анархии", px + 12, py + 8, Ui.TEXT);
        int sel = module.selected();
        Hud.textRight(ctx, sel > 0 ? "Сейчас: A" + sel : "Сейчас: все", px + pw - 12, py + 8, Ui.ACCENT);

        // поле ввода
        Ui.rect(ctx, fieldX, fieldY, fieldW, 20, 5, Ui.FIELD);
        Ui.outline(ctx, fieldX, fieldY, fieldW, 20, 5, field.isFocused() ? Ui.ACCENT_LINE : Ui.OUTLINE);
        if (field.getText().isEmpty()) Hud.text(ctx, "Введите номер 1–" + MAX + " или нажмите на плитку", fieldX + 8, fieldY + 6, 0x77BEC3CD);
        field.render(ctx, mx, my, delta);

        // сетка анархий
        String filter = field.getText();
        for (int n = 1; n <= MAX; n++) {
            int col = (n - 1) % COLS, row = (n - 1) / COLS;
            int x = gx + col * (cw + GAP);
            int y = gy + row * (ch + GAP);
            boolean hov = mx >= x && mx < x + cw && my >= y && my < y + ch;
            Spring hv = hover[n];
            hv.target = hov ? 1f : 0f;
            float h = hv.update();
            boolean on = matches(n, filter);
            boolean isSel = n == sel;
            int cnt = counts[n];
            int rc = EventHudModule.rankColor(Math.max(1, best[n]));

            m.pushMatrix();
            m.translate(x + cw / 2f, y + ch / 2f);
            float k = 1f + 0.07f * h;
            m.scale(k, k);
            m.translate(-(x + cw / 2f), -(y + ch / 2f));
            int bg = isSel ? ((0x66 << 24) | (Ui.ACCENT & 0xFFFFFF)) : (on ? (hov ? Ui.FIELD_HOVER : Ui.FIELD) : 0x80181818);
            Ui.rect(ctx, x, y, cw, ch, 5, bg);
            int line = isSel ? Ui.ACCENT : (cnt > 0 && on ? ((0x99 << 24) | (rc & 0xFFFFFF)) : Ui.OUTLINE);
            Ui.outline(ctx, x, y, cw, ch, 5, line);
            String label = String.valueOf(n);
            int tc = !on ? 0x55FFFFFF : (cnt > 0 || isSel ? Ui.TEXT : Ui.TEXT_DIM);
            Hud.text(ctx, label, x + (cw - Hud.w(label)) / 2, y + (ch - 8) / 2, tc);
            if (cnt > 0) {
                float pulse = 0.65f + 0.35f * (float) Math.sin(now / 260.0 + n);
                ctx.fill(x + cw - 6, y + 3, x + cw - 3, y + 6, ((int) (255 * pulse) << 24) | (rc & 0xFFFFFF));
            }
            m.popMatrix();
        }

        // легенда
        int ly = gy + 6 * ch + 5 * GAP + 6;
        ctx.fill(gx, ly + 2, gx + 3, ly + 5, Ui.ACCENT);
        Hud.text(ctx, "есть ивент", gx + 7, ly, Ui.TEXT_DIM);

        // кнопки
        button(ctx, mx, my, px + 12, btnY, btnW, "Все анархии", sel == 0);
        button(ctx, mx, my, px + 12 + btnW + GAP, btnY, btnW, "Закрыть", false);
        m.popMatrix();
    }

    private void button(DrawContext ctx, int mx, int my, int x, int y, int w, String label, boolean active) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + BTN_H;
        Ui.rect(ctx, x, y, w, BTN_H, 6, active ? ((0x55 << 24) | (Ui.ACCENT & 0xFFFFFF)) : (hov ? Ui.FIELD_HOVER : Ui.FIELD));
        Ui.outline(ctx, x, y, w, BTN_H, 6, active || hov ? Ui.ACCENT_LINE : Ui.OUTLINE);
        Hud.text(ctx, label, x + (w - Hud.w(label)) / 2, y + (BTN_H - 8) / 2, Ui.TEXT);
    }

    // ---------------- ввод ----------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (button != 0) return super.mouseClicked(click, doubled);
        if (mx >= fieldX && mx < fieldX + fieldW && my >= fieldY && my < fieldY + 20) {
            field.setFocused(true);
            field.mouseClicked(click, doubled);
            return true;
        }
        for (int n = 1; n <= MAX; n++) {
            int col = (n - 1) % COLS, row = (n - 1) / COLS;
            int x = gx + col * (cw + GAP);
            int y = gy + row * (ch + GAP);
            if (mx >= x && mx < x + cw && my >= y && my < y + ch) {
                choose(n == module.selected() ? 0 : n); // повторное нажатие снимает выбор
                return true;
            }
        }
        if (my >= btnY && my < btnY + BTN_H) {
            if (mx >= px + 12 && mx < px + 12 + btnW) {
                choose(0);
                return true;
            }
            if (mx >= px + 12 + btnW + GAP && mx < px + 12 + 2 * btnW + GAP) {
                close();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(input);
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        }
        if (field.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        boolean r = field.charTyped(input);
        String t = field.getText();
        if (t.equals("0")) {
            field.setText("");
        } else if (t.length() == 1 && t.charAt(0) >= '4') {
            choose(Integer.parseInt(t)); // 4–9: двузначного номера не бывает, выбираем сразу
        } else if (t.length() == 2) {
            int n = Integer.parseInt(t);
            if (n >= 1 && n <= MAX) choose(n);
            else field.setText(t.substring(0, 1)); // 37–39: лишняя цифра не принимается
        }
        return r;
    }
}
