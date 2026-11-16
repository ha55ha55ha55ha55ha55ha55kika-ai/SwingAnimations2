package com.swinganimations.gui;

import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import com.swinganimations.module.Module;
import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.Setting;
import com.swinganimations.input.KeyBinds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ClickGUI в стиле тёмных клиентов: панели по категориям, модуль = строка (клик = вкл/выкл,
 * стрелка или ПКМ = раскрыть настройки), внутри переключатели, ползунки, режимы и бинд.
 * Прокрутка: колесо мыши или перетаскивание по панели.
 */
public class ClickGuiScreen extends Screen {
    private static final int PW = 126;
    private static final int HEADER = 20;
    private static final int GAP = 8;
    private static final Identifier LOGO = Identifier.of("swinganimations", "textures/gui/logo.png");

    private enum Kind { MODULE, BOOL, NUM, MODE, BIND, BUTTON }

    private static final class Row {
        final Kind kind;
        final Module module;
        final Setting setting;
        int y;
        int h;

        Row(Kind kind, Module module, Setting setting, int h) {
            this.kind = kind;
            this.module = module;
            this.setting = setting;
            this.h = h;
        }
    }

    private static final class Panel {
        final String titleKey;
        final List<Module> modules = new ArrayList<>();
        final Set<Module> expanded = new HashSet<>();
        final List<Row> rows = new ArrayList<>();
        int x;
        int scroll;
        int contentH;

        Panel(String titleKey) { this.titleKey = titleKey; }
    }

    private final Screen parent;
    private final List<Panel> panels = new ArrayList<>();
    private Module listening;
    private Panel pressPanel;
    private Row pressRow;
    private Row dragNum;
    private int pressBtn;
    private double pressX, pressY, lastY;
    private boolean moved;

    public ClickGuiScreen(Screen parent) {
        super(Text.translatable("swinganimations.title"));
        this.parent = parent;
        Panel render = new Panel("swinganimations.cat.render");
        Panel hud = new Panel("swinganimations.cat.hud");
        Panel move = new Panel("swinganimations.cat.movement");
        Panel player = new Panel("swinganimations.cat.player");
        Panel anim = new Panel("swinganimations.cat.animations");
        for (Module m : ModuleManager.MODULES) {
            switch (m.category) {
                case "render" -> render.modules.add(m);
                case "hud" -> hud.modules.add(m);
                case "movement" -> move.modules.add(m);
                case "player" -> player.modules.add(m);
                default -> anim.modules.add(m);
            }
        }
        panels.add(render);
        panels.add(hud);
        panels.add(move);
        panels.add(player);
        panels.add(anim);
    }

    private int panelTop() { return 8; }

    private int panelHeight() { return Math.max(60, height - 16); }

    @Override
    protected void init() {
        for (int i = 0; i < panels.size(); i++) panels.get(i).x = 8 + i * (PW + GAP);
    }

    // ---------------- раскладка ----------------

    private void layout(Panel p) {
        p.rows.clear();
        int y = 0;
        for (Module m : p.modules) {
            y = add(p, new Row(Kind.MODULE, m, null, 18), y);
            if (!p.expanded.contains(m)) continue;
            for (Setting s : m.settings) {
                if (!s.isVisible()) continue;
                if (s instanceof Setting.Bool) y = add(p, new Row(Kind.BOOL, m, s, 16), y);
                else if (s instanceof Setting.Mode) y = add(p, new Row(Kind.MODE, m, s, 16), y);
                else if (s instanceof Setting.Num) y = add(p, new Row(Kind.NUM, m, s, 22), y);
            }
            if (m.hasExtraButton()) y = add(p, new Row(Kind.BUTTON, m, null, 16), y);
            y = add(p, new Row(Kind.BIND, m, null, 16), y);
            y += 2;
        }
        p.contentH = y;
        int max = Math.max(0, p.contentH - (panelHeight() - HEADER - 4));
        p.scroll = Math.max(0, Math.min(max, p.scroll));
    }

    private static int add(Panel p, Row r, int y) {
        r.y = y;
        p.rows.add(r);
        return y + r.h + (r.kind == Kind.MODULE ? 2 : 0);
    }

    private int rowTop(Panel p, Row r) {
        return panelTop() + HEADER + r.y - p.scroll;
    }

    private Row rowAt(Panel p, double mx, double my) {
        if (mx < p.x || mx > p.x + PW) return null;
        int bodyTop = panelTop() + HEADER;
        if (my < bodyTop || my > panelTop() + panelHeight() - 2) return null;
        for (Row r : p.rows) {
            int ry = rowTop(p, r);
            if (my >= ry && my < ry + r.h) return r;
        }
        return null;
    }

    // ---------------- отрисовка ----------------

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (com.swinganimations.module.ThemeModule.blur() && client != null && client.world != null) {
            super.renderBackground(ctx, mouseX, mouseY, delta); // ванильное размытие меню + затемнение
        } else {
            ctx.fill(0, 0, width, height, 0x59000000);
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        com.swinganimations.module.ThemeModule.sync();
        renderBackground(ctx, mx, my, delta);
        for (Panel p : panels) {
            layout(p);
            renderPanel(ctx, p, mx, my);
        }
        drawLogo(ctx);
        if (listening != null) {
            Text t = Text.translatable("swinganimations.ui.bind_hint");
            Ui.text(ctx, t, (width - Ui.tr().getWidth(t)) / 2, height - 12, Ui.ACCENT);
        }
    }

    /** Логотип в правом верхнем углу (рисуется, только если не перекрывает панели). */
    private void drawLogo(DrawContext ctx) {
        int w = 110, h = Math.round(w * 370f / 512f);
        int lastRight = panels.isEmpty() ? 0 : panels.get(panels.size() - 1).x + PW;
        int x = width - 10 - w;
        if (x < lastRight + GAP) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, x, 10, 0f, 0f, w, h, 512, 370, 512, 370);
    }

    private void renderPanel(DrawContext ctx, Panel p, int mx, int my) {
        int top = panelTop();
        int ph = panelHeight();
        Ui.rect(ctx, p.x, top, PW, ph, 6, Ui.BG);
        Ui.outline(ctx, p.x, top, PW, ph, 6, Ui.OUTLINE);
        Text title = Text.translatable(p.titleKey);
        Ui.text(ctx, title, p.x + (PW - Ui.tr().getWidth(title)) / 2, top + 6, Ui.ACCENT);
        ctx.fill(p.x + 6, top + HEADER - 1, p.x + PW - 6, top + HEADER, Ui.SEP);

        ctx.enableScissor(p.x, top + HEADER, p.x + PW, top + ph - 2);
        for (Row r : p.rows) {
            int ry = rowTop(p, r);
            if (ry + r.h < top + HEADER || ry > top + ph) continue;
            boolean hov = mx >= p.x && mx <= p.x + PW && my >= ry && my < ry + r.h;
            drawRow(ctx, p, r, ry, hov);
        }
        ctx.disableScissor();
    }

    private void drawRow(DrawContext ctx, Panel p, Row r, int ry, boolean hov) {
        int x = p.x + 5;
        int w = PW - 10;
        int ty = ry + (r.h - 8) / 2;
        switch (r.kind) {
            case MODULE -> {
                boolean on = r.module.isEnabled();
                Ui.rect(ctx, x, ry, w, r.h, 4, on ? Ui.ACCENT_BG : (hov ? Ui.FIELD_HOVER : Ui.FIELD));
                if (on) Ui.rect(ctx, x, ry + 3, 2, r.h - 6, 1, Ui.ACCENT);
                Ui.text(ctx, Text.translatableWithFallback("swinganimations.module." + r.module.id, r.module.fallbackName),
                        x + 7, ty, on ? Ui.ACCENT : Ui.TEXT);
                Text arrow = Text.literal(p.expanded.contains(r.module) ? "v" : ">");
                Ui.textRight(ctx, arrow, x + w - 6, ty, Ui.TEXT_DIM);
            }
            case BOOL -> {
                Setting.Bool b = (Setting.Bool) r.setting;
                Ui.text(ctx, label(r), x + 4, ty, b.value ? Ui.TEXT : Ui.TEXT_DIM);
                int sw = 20, sh = 9;
                int sx = x + w - 4 - sw, sy = ry + (r.h - sh) / 2;
                Ui.rect(ctx, sx, sy, sw, sh, 4, b.value ? Ui.ACCENT : Ui.SWITCH_OFF);
                Ui.rect(ctx, b.value ? sx + sw - sh + 1 : sx + 1, sy + 1, sh - 2, sh - 2, 3, b.value ? Ui.TEXT : Ui.TEXT_DIM);
            }
            case MODE -> {
                Setting.Mode m = (Setting.Mode) r.setting;
                Ui.text(ctx, label(r), x + 4, ty, Ui.TEXT_DIM);
                Ui.textRight(ctx, Text.translatableWithFallback("swinganimations.value." + m.get().toLowerCase(Locale.ROOT), m.get()),
                        x + w - 4, ty, Ui.ACCENT);
            }
            case NUM -> {
                Setting.Num n = (Setting.Num) r.setting;
                Ui.text(ctx, label(r), x + 4, ry, Ui.TEXT_DIM);
                String v = n.step >= 1f ? String.format(Locale.ROOT, "%.0f", n.value) : String.format(Locale.ROOT, "%.2f", n.value);
                Ui.textRight(ctx, Text.literal(v), x + w - 4, ry, Ui.TEXT);
                int tx = x + 4, tw = w - 8, tr = ry + r.h - 7;
                Ui.rect(ctx, tx, tr, tw, 4, 2, Ui.FIELD);
                Ui.outline(ctx, tx, tr, tw, 4, 2, Ui.OUTLINE);
                int filled = Math.round(tw * n.fraction());
                if (filled > 0) Ui.rect(ctx, tx, tr, Math.max(4, filled), 4, 2, Ui.ACCENT);
                int kr = (hov || dragNum == r) ? 4 : 3;
                Ui.rect(ctx, tx + filled - kr, tr + 2 - kr, kr * 2, kr * 2, kr, Ui.KNOB);
            }
            case BIND -> {
                Ui.text(ctx, Text.translatable("swinganimations.ui.bind"), x + 4, ty, Ui.TEXT_DIM);
                Text val = listening == r.module ? Text.literal("...") : bindText(r.module);
                Ui.textRight(ctx, val, x + w - 4, ty, listening == r.module ? Ui.ACCENT : Ui.TEXT);
            }
            case BUTTON -> {
                Ui.rect(ctx, x, ry, w, r.h, 4, hov ? Ui.FIELD_HOVER : Ui.FIELD);
                Ui.outline(ctx, x, ry, w, r.h, 4, hov ? Ui.ACCENT_LINE : Ui.OUTLINE);
                Text t = Text.translatable("swinganimations.ui.open_anim");
                Ui.text(ctx, t, x + (w - Ui.tr().getWidth(t)) / 2, ty, Ui.TEXT);
            }
        }
    }

    private static Text label(Row r) {
        return Text.translatableWithFallback("swinganimations.setting." + r.module.id + "." + r.setting.id, r.setting.fallback);
    }

    private static Text bindText(Module m) {
        if (m.bindCode < 0) return Text.translatable("swinganimations.ui.none");
        if (m.bindMouse) {
            return switch (m.bindCode) {
                case 0 -> Text.literal("LMB");
                case 1 -> Text.literal("RMB");
                case 2 -> Text.literal("MMB");
                default -> Text.literal("Mouse " + (m.bindCode + 1));
            };
        }
        return InputUtil.fromKeyCode(m.bindCode, -1).getLocalizedText();
    }

    // ---------------- ввод ----------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (listening != null) {
            listening.bindMouse = true;
            listening.bindCode = button;
            listening = null;
            ModuleManager.save();
            return true;
        }
        for (Panel p : panels) {
            Row r = rowAt(p, mx, my);
            if (r == null) continue;
            pressPanel = p;
            pressRow = r;
            pressBtn = button;
            pressX = mx;
            pressY = my;
            lastY = my;
            moved = false;
            if (r.kind == Kind.NUM && button == 0) {
                dragNum = r;
                applyNum(r, mx, p);
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void applyNum(Row r, double mx, Panel p) {
        Setting.Num n = (Setting.Num) r.setting;
        int tx = p.x + 5 + 4, tw = PW - 10 - 8;
        double f = Math.max(0.0, Math.min(1.0, (mx - tx) / tw));
        n.set((float) (n.min + (n.max - n.min) * f));
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (dragNum != null && pressPanel != null) {
            applyNum(dragNum, mx, pressPanel);
            return true;
        }
        if (pressRow != null && pressPanel != null) {
            if (Math.abs(my - pressY) > 4) moved = true;
            if (moved) {
                pressPanel.scroll -= (int) (my - lastY);
                lastY = my;
            }
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        boolean handled = false;
        if (dragNum != null) {
            dragNum = null;
            ModuleManager.save();
            handled = true;
        } else if (pressRow != null && !moved) {
            activate(pressPanel, pressRow, pressBtn, mx);
            handled = true;
        }
        pressRow = null;
        pressPanel = null;
        return handled || super.mouseReleased(click);
    }

    private void activate(Panel p, Row r, int button, double mx) {
        switch (r.kind) {
            case MODULE -> {
                boolean arrow = mx > p.x + PW - 22;
                if (button == 1 || arrow) {
                    if (!p.expanded.remove(r.module)) p.expanded.add(r.module);
                } else if (button == 0) {
                    r.module.toggle();
                    ModuleManager.save();
                }
            }
            case BOOL -> {
                Setting.Bool b = (Setting.Bool) r.setting;
                b.value = !b.value;
                ModuleManager.save();
            }
            case MODE -> {
                ((Setting.Mode) r.setting).next();
                ModuleManager.save();
            }
            case BIND -> {
                if (button == 1) {
                    r.module.bindCode = -1;
                    r.module.bindMouse = false;
                    ModuleManager.save();
                } else {
                    listening = r.module;
                }
            }
            case BUTTON -> {
                if (client != null) client.setScreen(new SettingsScreen(this));
            }
            default -> { }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        for (Panel p : panels) {
            if (mx >= p.x && mx <= p.x + PW) {
                p.scroll -= (int) (vertical * 14);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (listening != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listening.bindCode = -1;
                listening.bindMouse = false;
            } else {
                listening.bindMouse = false;
                listening.bindCode = keyCode;
            }
            listening = null;
            ModuleManager.save();
            return true;
        }
        if (KeyBinds.OPEN != null && KeyBinds.OPEN.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        ModuleManager.save();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
