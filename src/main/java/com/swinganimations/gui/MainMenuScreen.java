package com.swinganimations.gui;

import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import com.swinganimations.SwingAnimationsClient;
import com.swinganimations.module.ThemeModule;
import com.swinganimations.render.Batch;
import com.swinganimations.util.NickChanger;
import com.swinganimations.util.Spring;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Главное меню в стиле современных клиентов: размытая панорама, логотип, стеклянные кнопки с иконками,
 * плашка аккаунта справа внизу и смена ника. Если что-то пойдёт не так при отрисовке, меню само
 * переключается на ванильное и больше не подменяет его до перезапуска.
 */
public final class MainMenuScreen extends Screen {
    /** true: меню упало, дальше показываем ванильное. */
    public static boolean failed;

    private static final Identifier LOGO = Identifier.of("swinganimations", "textures/gui/logo_small.png"); // 128x92
    private static final Identifier I_USER = icon("user");
    private static final Identifier I_PULSE = icon("pulse");
    private static final Identifier I_PIN = icon("pin");
    private static final Identifier I_FLASK = icon("flask");
    private static final Identifier I_KEYS = icon("keys");
    private static final Identifier I_SPEED = icon("speed");

    private static final class Btn {
        final int x, y, w, h;
        final Text label;
        final Identifier icon;
        final Runnable action;
        final Spring hover = new Spring(0f, 220f, 24f);

        Btn(int x, int y, int w, int h, Text label, Identifier icon, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.icon = icon;
            this.action = action;
        }

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> btns = new ArrayList<>();
    private final List<Btn> modalBtns = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();
    private int logoY;
    private int chipX, chipY, chipW, chipH;
    private boolean nickOpen;
    private TextFieldWidget nickField;
    private String nickDraft = "";
    private Text nickMsg;
    private int nickMsgColor = 0xFFFF8A8A;
    private int nx, ny, nw, nh;

    public MainMenuScreen() {
        super(Text.literal("SwingAnimations"));
    }

    private static Identifier icon(String n) {
        return Identifier.of("swinganimations", "textures/gui/icon_" + n + ".png");
    }

    private static Text tr(String key, String fallback) {
        return Text.translatableWithFallback("swinganimations.menu." + key, fallback);
    }

    // ---------------- раскладка ----------------

    @Override
    protected void init() {
        btns.clear();
        modalBtns.clear();
        int bw = Math.min(180, width - 24);
        int bh = 22;
        int gap = 5;
        int logoH = 28;
        int stackH = 5 * bh + 4 * gap;
        int total = logoH + 16 + stackH;
        int top = Math.max(8, (height - total) / 2 - 4);
        logoY = top;
        int x = (width - bw) / 2;
        int y = top + logoH + 16;

        btns.add(new Btn(x, y, bw, bh, tr("multiplayer", "Multiplayer"), I_PULSE,
                () -> client.setScreen(new MultiplayerScreen(this))));
        y += bh + gap;
        btns.add(new Btn(x, y, bw, bh, tr("singleplayer", "Singleplayer"), I_PIN,
                () -> client.setScreen(new SelectWorldScreen(this))));
        y += bh + gap;
        btns.add(new Btn(x, y, bw, bh, tr("nickname", "Change nickname"), I_USER, this::openNick));
        y += bh + gap;
        btns.add(new Btn(x, y, bw, bh, tr("mod_settings", "Mod settings"), I_FLASK,
                () -> client.setScreen(new ClickGuiScreen(this))));
        y += bh + gap;
        int half = (bw - gap) / 2;
        btns.add(new Btn(x, y, half, bh, tr("options", "Options"), I_KEYS,
                () -> client.setScreen(new OptionsScreen(this, client.options))));
        btns.add(new Btn(x + half + gap, y, bw - half - gap, bh, tr("quit", "Quit"), I_SPEED,
                () -> client.scheduleStop()));

        chipH = 24;
        chipW = 100;
        chipX = width - chipW - 8;
        chipY = height - chipH - 8;

        nw = Math.min(230, width - 20);
        nh = 86;
        nx = (width - nw) / 2;
        ny = (height - nh) / 2;
        int mh = 18;
        int mhalf = (nw - 20 - 6) / 2;
        modalBtns.add(new Btn(nx + 10, ny + nh - mh - 8, mhalf, mh, tr("apply", "Apply"), null, this::applyNick));
        modalBtns.add(new Btn(nx + 10 + mhalf + 6, ny + nh - mh - 8, nw - 20 - mhalf - 6, mh, tr("cancel", "Cancel"), null, this::closeNick));

        nickField = new TextFieldWidget(textRenderer, nx + 16, ny + 30, nw - 32, 12, Text.empty());
        nickField.setDrawsBackground(false);
        nickField.setMaxLength(16);
        nickField.setTextPredicate(s -> s.matches("[A-Za-z0-9_]*"));
        nickField.setText(nickDraft);
        nickField.setFocused(nickOpen);
    }

    // ---------------- ник ----------------

    private void openNick() {
        nickOpen = true;
        nickMsg = null;
        nickDraft = client.getSession().getUsername();
        nickField.setText(nickDraft);
        nickField.setFocused(true);
    }

    private void closeNick() {
        nickOpen = false;
        nickDraft = nickField.getText();
        nickField.setFocused(false);
    }

    private void applyNick() {
        String name = nickField.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            nickMsg = tr("nick_invalid", "3-16 characters: A-Z, 0-9, _");
            nickMsgColor = 0xFFFF8A8A;
            return;
        }
        String err = NickChanger.apply(name);
        if (err == null) {
            nickDraft = name;
            closeNick();
        } else {
            nickMsg = Text.translatableWithFallback("swinganimations.menu.nick_failed", "Could not change nickname (%s)", err);
            nickMsgColor = 0xFFFF8A8A;
        }
    }

    // ---------------- отрисовка ----------------

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        try {
            draw(ctx, mx, my, delta);
        } catch (Throwable t) {
            failed = true;
            SwingAnimationsClient.LOGGER.error("Главное меню упало, возвращаю ванильное", t);
            if (client != null) client.setScreen(new TitleScreen());
        }
    }

    private void draw(DrawContext ctx, int mx, int my, float delta) {
        ThemeModule.sync();
        renderBackground(ctx, mx, my, delta); // ванильная панорама + размытие
        int rgb = Ui.ACCENT & 0xFFFFFF;
        ctx.fillGradient(0, 0, width, height, 0x28000000 | rgb, 0xB0000000 | Batch.mix(rgb, 0x000000, 0.8f));
        particles(ctx, rgb);

        boolean active = !nickOpen;
        drawLogo(ctx);
        for (Btn b : btns) drawBtn(ctx, b, mx, my, active, rgb);
        drawChip(ctx, mx, my, active, rgb);
        drawFooter(ctx);
        if (nickOpen) drawModal(ctx, mx, my, delta, rgb);

        float f = MathHelper.clamp((System.currentTimeMillis() - openedAt) / 450f, 0f, 1f);
        if (f < 1f) ctx.fill(0, 0, width, height, ((int) ((1f - f) * 255f) << 24));
    }

    private void particles(DrawContext ctx, int rgb) {
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        for (int i = 0; i < 36; i++) {
            float sx = frac(i * 0.6180339f);
            float sy = frac(i * 0.4142136f + 0.3f);
            float speed = 0.006f + 0.012f * frac(i * 0.7071f);
            float py = frac(sy - t * speed) * height;
            float px = sx * width + (float) Math.sin(t * 0.6f + i) * 6f;
            float a = 0.12f + 0.30f * Math.abs((float) Math.sin(t * 0.9f + i * 1.7f));
            int s = i % 3 == 0 ? 2 : 1;
            ctx.fill((int) px, (int) py, (int) px + s, (int) py + s, Batch.argb(Batch.mix(0xFFFFFF, rgb, 0.4f), a));
        }
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private void drawLogo(DrawContext ctx) {
        float ts = 1.5f;
        String title = "SwingAnimations";
        int tw = Math.round(Hud.w(title) * ts);
        int lw = 36;
        int lh = 26;
        int total = lw + 8 + tw;
        int x = (width - total) / 2;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, x, logoY + 1, 0f, 0f, lw, lh, 128, 92, 128, 92);
        org.joml.Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x + lw + 8, logoY + (lh - 8 * ts) / 2f + 1f);
        m.scale(ts, ts);
        Hud.text(ctx, title, 0, 0, Ui.TEXT);
        m.popMatrix();
    }

    private void drawBtn(DrawContext ctx, Btn b, int mx, int my, boolean active, int rgb) {
        b.hover.target = active && b.hit(mx, my) ? 1f : 0f;
        float k = MathHelper.clamp(b.hover.update(), 0f, 1f);
        Ui.rect(ctx, b.x, b.y, b.w, b.h, 6, 0x9A14162A);
        if (k > 0.01f) Ui.rect(ctx, b.x, b.y, b.w, b.h, 6, ((int) (k * 0x3A) << 24) | rgb);
        int outline = ((0x30 + (int) (k * 0x70)) << 24) | Batch.mix(0xFFFFFF, rgb, k);
        Ui.outline(ctx, b.x, b.y, b.w, b.h, 6, outline);
        int tx = b.x + 10;
        if (b.icon != null) {
            Hud.icon(ctx, b.icon, b.x + 10, b.y + (b.h - 10) / 2, 10);
            tx += 16;
        }
        Hud.text(ctx, b.label, tx, b.y + (b.h - 8) / 2, Ui.TEXT);
    }

    private void drawChip(DrawContext ctx, int mx, int my, boolean active, int rgb) {
        String name = client.getSession().getUsername();
        Text sub = tr("change_hint", "Change nickname");
        chipW = Math.max(96, 24 + Math.max(Hud.w(name), Hud.w(sub)) + 10);
        chipX = width - chipW - 8;
        chipY = height - chipH - 8;
        boolean hov = active && mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + chipH;
        Ui.rect(ctx, chipX, chipY, chipW, chipH, 8, hov ? 0xB0222438 : 0x9A14162A);
        Ui.outline(ctx, chipX, chipY, chipW, chipH, 8, hov ? Ui.ACCENT_LINE : 0x36FFFFFF);
        Ui.rect(ctx, chipX + 4, chipY + 4, 16, 16, 4, 0xFF000000 | rgb);
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        Hud.text(ctx, letter, chipX + 4 + (16 - Hud.w(letter)) / 2, chipY + 8, 0xFF10121C);
        Hud.text(ctx, name, chipX + 24, chipY + 4, Ui.TEXT);
        Hud.text(ctx, sub, chipX + 24, chipY + 13, Ui.TEXT_DIM);
    }

    private void drawFooter(DrawContext ctx) {
        String s = "SwingAnimations v" + version("swinganimations") + " | Minecraft " + version("minecraft");
        Hud.text(ctx, s, 8, height - 14, 0x90FFFFFF);
    }

    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private void drawModal(DrawContext ctx, int mx, int my, float delta, int rgb) {
        ctx.fill(0, 0, width, height, 0x96000000);
        Hud.panel(ctx, nx, ny, nw, nh);
        Hud.text(ctx, tr("nick_title", "Change nickname"), nx + 10, ny + 9, Ui.TEXT);
        Ui.rect(ctx, nx + 10, ny + 26, nw - 20, 20, 5, Ui.FIELD);
        Ui.outline(ctx, nx + 10, ny + 26, nw - 20, 20, 5, nickField.isFocused() ? Ui.ACCENT_LINE : Ui.OUTLINE);
        nickField.render(ctx, mx, my, delta);
        if (nickMsg != null) {
            Hud.text(ctx, nickMsg, nx + 10, ny + 50, nickMsgColor);
        } else {
            Hud.text(ctx, tr("nick_hint", "Offline servers only, until the game restarts"), nx + 10, ny + 50, Ui.TEXT_DIM);
        }
        for (Btn b : modalBtns) drawBtn(ctx, b, mx, my, true, rgb);
    }

    // ---------------- ввод ----------------

    private void click() {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (button != 0) return true;
        if (nickOpen) {
            for (Btn b : modalBtns) {
                if (b.hit(mx, my)) {
                    click();
                    b.action.run();
                    return true;
                }
            }
            if (mx >= nx && mx < nx + nw && my >= ny && my < ny + nh) {
                nickField.mouseClicked(click, doubled);
            } else {
                closeNick();
            }
            return true;
        }
        for (Btn b : btns) {
            if (b.hit(mx, my)) {
                click();
                b.action.run();
                return true;
            }
        }
        if (mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + chipH) {
            click();
            openNick();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (nickOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeNick();
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyNick();
            } else {
                nickField.keyPressed(input);
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (nickOpen) return nickField.charTyped(input);
        return super.charTyped(input);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
