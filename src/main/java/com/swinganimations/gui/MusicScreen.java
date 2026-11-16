package com.swinganimations.gui;

import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import com.swinganimations.module.MusicModule;
import com.swinganimations.music.MusicPlayer;
import com.swinganimations.music.MusicProvider;
import com.swinganimations.music.Providers;
import com.swinganimations.music.Track;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Мини-браузер музыки: вкладки источников, поиск, список результатов и плеер (пред., пауза, след., стоп,
 * прогресс, громкость). Клик по треку запускает его, дальше очередь играет сама. Закрытие окна музыку не останавливает.
 */
public final class MusicScreen extends Screen {
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "SwingMusic-Search");
        t.setDaemon(true);
        return t;
    });
    private static final int ROW_H = 22;
    private static final int PAD = 12;
    private static final int RED = 0xFFFF6B6B;

    private final Screen parent;
    private final MusicModule module;
    private TextFieldWidget field;

    private volatile List<Track> results = List.of();
    private volatile String status = "";
    private volatile boolean loading;
    private volatile int searchSeq;
    private String lastQuery = "";
    private boolean keyMode;
    private float scroll;
    private boolean draggingVolume;

    // раскладка
    private int px, py, pw, ph;
    private int fieldX, fieldY, fieldW;
    private int searchBtnX, searchBtnW, keyBtnX, keyBtnW;
    private int listX, listY, listW, listH;
    private int barY;
    private int volX, volW;
    private int[] tabX, tabW;

    // подписи строк (обрезаны под ширину один раз, а не каждый кадр)
    private List<Track> shownFor;
    private String[] titleShown = new String[0];
    private String[] artistShown = new String[0];
    private String[] durShown = new String[0];
    private Track playingShown;
    private String playingLine = "";

    public MusicScreen(Screen parent, MusicModule module) {
        super(Text.literal("Music"));
        this.parent = parent;
        this.module = module;
        MusicProvider p = provider();
        if (p.keyRequired() && module.key(p).isBlank()) {
            keyMode = true;
            status = p.keyHelp();
        }
    }

    private MusicProvider provider() { return Providers.get(module.sourceIndex()); }

    // ---------------- раскладка ----------------

    @Override
    protected void init() {
        pw = Math.min(430, width - 16);
        ph = Math.min(310, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        layoutTop();
        listX = px + PAD;
        listY = py + 62;
        listW = pw - PAD * 2;
        barY = py + ph - 58;
        listH = Math.max(ROW_H, barY - 6 - listY);
        volW = 56;
        volX = px + pw - PAD - volW;

        String old = field == null ? "" : field.getText();
        field = new TextFieldWidget(textRenderer, fieldX + 8, fieldY + 6, fieldW - 16, 12, Text.empty());
        field.setDrawsBackground(false);
        field.setMaxLength(200);
        field.setText(old);
        field.setFocused(true);
        shownFor = null;
    }

    private void layoutTop() {
        List<MusicProvider> all = Providers.ALL;
        tabX = new int[all.size()];
        tabW = new int[all.size()];
        int x = px + pw - PAD;
        for (int i = all.size() - 1; i >= 0; i--) {
            tabW[i] = Hud.w(all.get(i).name()) + 14;
            x -= tabW[i];
            tabX[i] = x;
            x -= 4;
        }
        MusicProvider p = provider();
        keyBtnW = p.keyLabel() != null ? 34 : 0;
        searchBtnW = 56;
        fieldY = py + 26;
        fieldX = px + PAD;
        fieldW = pw - PAD * 2 - searchBtnW - 4 - (keyBtnW > 0 ? keyBtnW + 4 : 0);
        keyBtnX = fieldX + fieldW + 4;
        searchBtnX = keyBtnX + (keyBtnW > 0 ? keyBtnW + 4 : 0);
        if (field != null) field.setWidth(fieldW - 16);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    // ---------------- поиск ----------------

    private void submit() {
        MusicProvider p = provider();
        if (keyMode) {
            String k = field.getText().trim();
            if (k.isEmpty()) return;
            module.setKey(p, k);
            keyMode = false;
            field.setText("");
            status = "Key saved";
            if (!lastQuery.isEmpty()) {
                field.setText(lastQuery);
                search();
            }
            return;
        }
        search();
    }

    private void search() {
        String q = field.getText().trim();
        if (q.isEmpty()) return;
        MusicProvider p = provider();
        String key = module.key(p);
        if (p.keyRequired() && key.isBlank()) {
            keyMode = true;
            status = p.keyHelp();
            field.setText("");
            return;
        }
        lastQuery = q;
        int seq = ++searchSeq;
        loading = true;
        status = "Searching...";
        results = List.of();
        scroll = 0f;
        CompletableFuture.supplyAsync(() -> {
            try {
                return p.search(q, key);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, EXEC).whenComplete((list, err) -> {
            if (seq != searchSeq) return; // пришёл устаревший ответ
            loading = false;
            if (err != null) {
                Throwable c = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                String m = c.getMessage();
                status = "Error: " + (m == null || m.isEmpty() ? c.getClass().getSimpleName() : m);
                results = List.of();
            } else {
                results = list;
                status = list.isEmpty() ? "Nothing found" : "";
            }
        });
    }

    private void setProvider(int i) {
        if (i == module.sourceIndex()) return;
        module.setSource(i);
        searchSeq++;
        loading = false;
        results = List.of();
        scroll = 0f;
        MusicProvider p = provider();
        keyMode = p.keyRequired() && module.key(p).isBlank();
        status = keyMode ? p.keyHelp() : "";
        layoutTop();
        if (!keyMode && !field.getText().trim().isEmpty()) search();
    }

    private void layoutRows() {
        List<Track> r = results;
        shownFor = r;
        int n = r.size();
        titleShown = new String[n];
        artistShown = new String[n];
        durShown = new String[n];
        int textW = listW - 24 - 56;
        for (int i = 0; i < n; i++) {
            Track t = r.get(i);
            titleShown[i] = fit(t.title().isEmpty() ? "Untitled" : t.title(), textW);
            artistShown[i] = fit(t.artist(), textW);
            durShown[i] = t.durationSec() > 0 ? MusicPlayer.fmt(t.durationSec() * 1000L) : "";
        }
    }

    private static String fit(String s, int maxW) {
        if (Hud.w(s) <= maxW) return s;
        String r = s;
        while (r.length() > 1 && Hud.w(r + "...") > maxW) r = r.substring(0, r.length() - 1);
        return r + "...";
    }

    // ---------------- отрисовка ----------------

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        Ui.rect(ctx, px, py, pw, ph, 8, Ui.BG);
        Ui.outline(ctx, px, py, pw, ph, 8, Ui.OUTLINE);

        // заголовок и вкладки
        Hud.text(ctx, "Music", px + PAD, py + 10, Ui.TEXT);
        int cur = module.sourceIndex();
        for (int i = 0; i < tabX.length; i++) {
            boolean sel = i == cur;
            boolean hov = in(mx, my, tabX[i], py + 6, tabW[i], 16);
            Ui.rect(ctx, tabX[i], py + 6, tabW[i], 16, 6, sel ? ((0x55 << 24) | (Ui.ACCENT & 0xFFFFFF)) : (hov ? Ui.FIELD_HOVER : Ui.FIELD));
            Ui.outline(ctx, tabX[i], py + 6, tabW[i], 16, 6, sel || hov ? Ui.ACCENT_LINE : Ui.OUTLINE);
            String name = Providers.ALL.get(i).name();
            Hud.text(ctx, name, tabX[i] + (tabW[i] - Hud.w(name)) / 2, py + 10, sel ? Ui.TEXT : Ui.TEXT_DIM);
        }

        // поле ввода и кнопки
        MusicProvider p = provider();
        Ui.rect(ctx, fieldX, fieldY, fieldW, 20, 5, Ui.FIELD);
        Ui.outline(ctx, fieldX, fieldY, fieldW, 20, 5, field.isFocused() ? Ui.ACCENT_LINE : Ui.OUTLINE);
        if (field.getText().isEmpty()) {
            String hint = keyMode ? "Paste " + p.keyLabel() + " and press Enter" : "Search songs or artists";
            Hud.text(ctx, hint, fieldX + 8, fieldY + 6, 0x77BEC3CD);
        }
        field.render(ctx, mx, my, delta);
        if (keyBtnW > 0) button(ctx, mx, my, keyBtnX, fieldY, keyBtnW, 20, "Key", keyMode);
        button(ctx, mx, my, searchBtnX, fieldY, searchBtnW, 20, keyMode ? "Save" : "Search", false);

        Hud.text(ctx, fit(p.note(), pw - PAD * 2), px + PAD, py + 50, Ui.TEXT_DIM);
        drawList(ctx, mx, my);
        drawPlayer(ctx, mx, my);
    }

    private void drawList(DrawContext ctx, int mx, int my) {
        List<Track> r = results;
        if (r != shownFor) layoutRows();
        int n = r.size();
        int visible = Math.max(1, listH / ROW_H);
        Ui.rect(ctx, listX, listY, listW, listH, 6, 0x66000000);
        if (n == 0) {
            String s = status;
            if (s.isEmpty()) s = "Type a song or artist and press Enter";
            wrapCentered(ctx, s, listX + listW / 2, listY + listH / 2 - 8, listW - 20, s.startsWith("Error") ? RED : Ui.TEXT_DIM);
            if (keyMode) wrapCentered(ctx, "The key is stored only on this computer", listX + listW / 2, listY + listH / 2 + 12, listW - 20, Ui.TEXT_DIM);
            return;
        }
        scroll = MathHelper.clamp(scroll, 0f, Math.max(0, n - visible));
        Track playing = MusicPlayer.isActive() ? MusicPlayer.current() : null;
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        int first = (int) Math.floor(scroll);
        for (int i = first; i < Math.min(n, first + visible + 2); i++) {
            int y = listY + Math.round((i - scroll) * ROW_H);
            boolean hov = in(mx, my, listX, y, listW, ROW_H) && in(mx, my, listX, listY, listW, listH);
            boolean isPlaying = playing != null && playing.equals(r.get(i));
            if (isPlaying) Ui.rect(ctx, listX + 2, y + 1, listW - 4, ROW_H - 2, 5, (0x33 << 24) | (Ui.ACCENT & 0xFFFFFF));
            else if (hov) Ui.rect(ctx, listX + 2, y + 1, listW - 4, ROW_H - 2, 5, Ui.FIELD_HOVER);
            if (isPlaying) {
                icon(ctx, 0, listX + 9, y + ROW_H / 2, 8, Ui.ACCENT);
            } else {
                String num = String.valueOf(i + 1);
                Hud.text(ctx, num, listX + 14 - Hud.w(num) / 2, y + 7, 0x66FFFFFF);
            }
            Hud.text(ctx, titleShown[i], listX + 24, y + 3, Ui.TEXT);
            Hud.text(ctx, artistShown[i], listX + 24, y + 12, Ui.TEXT_DIM);
            if (!durShown[i].isEmpty()) Hud.textRight(ctx, durShown[i], listX + listW - 8, y + 7, Ui.TEXT_DIM);
        }
        ctx.disableScissor();
        if (n > visible) {
            int trackH = listH - 8;
            int thumbH = Math.max(14, trackH * visible / n);
            int thumbY = listY + 4 + Math.round((trackH - thumbH) * (scroll / (n - visible)));
            ctx.fill(listX + listW - 4, thumbY, listX + listW - 2, thumbY + thumbH, 0x66FFFFFF);
        }
    }

    private void drawPlayer(DrawContext ctx, int mx, int my) {
        Ui.rect(ctx, px + 6, barY, pw - 12, 52, 6, 0x66000000);
        MusicPlayer.State st = MusicPlayer.state();
        boolean on = st == MusicPlayer.State.PLAYING || st == MusicPlayer.State.LOADING;
        int bx = px + PAD;
        int by = barY + 5;
        iconButton(ctx, mx, my, bx, by, 1);
        iconButton(ctx, mx, my, bx + 26, by, on ? 3 : 0);
        iconButton(ctx, mx, my, bx + 52, by, 2);
        iconButton(ctx, mx, my, bx + 78, by, 4);

        // название и состояние
        int tx = bx + 108;
        int tw = volX - 8 - tx;
        Track t = MusicPlayer.current();
        String line;
        int col = Ui.TEXT;
        if (st == MusicPlayer.State.ERROR) {
            line = fit("Error: " + MusicPlayer.error(), tw);
            col = RED;
        } else if (t == null) {
            line = "Nothing playing";
            col = Ui.TEXT_DIM;
        } else {
            if (t != playingShown) {
                playingShown = t;
                playingLine = fit(t.artist().isEmpty() ? t.title() : t.title() + " - " + t.artist(), tw);
            }
            line = st == MusicPlayer.State.LOADING ? "Loading..." : playingLine;
        }
        Hud.text(ctx, line, tx, by + 6, col);

        // громкость
        float vol = MusicPlayer.volume();
        Hud.text(ctx, "Vol", volX - 22, by + 6, Ui.TEXT_DIM);
        ctx.fill(volX, by + 9, volX + volW, by + 12, 0x55FFFFFF);
        ctx.fill(volX, by + 9, volX + Math.round(volW * vol), by + 12, Ui.ACCENT);
        int kx = volX + Math.round(volW * vol);
        ctx.fill(kx - 2, by + 6, kx + 2, by + 15, Ui.KNOB);

        // прогресс
        long pos = t != null && st != MusicPlayer.State.IDLE ? MusicPlayer.positionMs() : 0L;
        long dur = t != null ? t.durationSec() * 1000L : 0L;
        float frac = dur > 0 ? Math.min(1f, pos / (float) dur) : 0f;
        int ly = barY + 36;
        Hud.text(ctx, MusicPlayer.fmt(pos), bx, ly - 3, Ui.TEXT_DIM);
        String right = dur > 0 ? MusicPlayer.fmt(dur) : "--:--";
        Hud.textRight(ctx, right, px + pw - PAD, ly - 3, Ui.TEXT_DIM);
        Hud.bar(ctx, bx + 34, ly, pw - PAD * 2 - 68, 3, frac, Ui.ACCENT);
    }

    /** kind: 0 играть, 1 назад, 2 вперёд, 3 пауза, 4 стоп. */
    private void iconButton(DrawContext ctx, int mx, int my, int x, int y, int kind) {
        boolean hov = in(mx, my, x, y, 22, 20);
        Ui.rect(ctx, x, y, 22, 20, 6, hov ? Ui.FIELD_HOVER : Ui.FIELD);
        Ui.outline(ctx, x, y, 22, 20, 6, hov ? Ui.ACCENT_LINE : Ui.OUTLINE);
        icon(ctx, kind, x + 11, y + 10, 8, hov ? Ui.ACCENT : Ui.TEXT);
    }

    /** Иконки рисуются fill'ами, без текстур и без символов, которых нет в шрифте. cx, cy: центр, s: размер. */
    private static void icon(DrawContext c, int kind, int cx, int cy, int s, int col) {
        int w = Math.round(s * 0.8f);
        switch (kind) {
            case 0 -> {
                for (int j = 0; j < w; j++) {
                    int h = Math.max(1, Math.round(s * (1f - j / (float) w)));
                    c.fill(cx - w / 2 + j, cy - h / 2, cx - w / 2 + j + 1, cy - h / 2 + h, col);
                }
            }
            case 1 -> {
                c.fill(cx - w / 2 - 1, cy - s / 2, cx - w / 2 + 1, cy + s / 2, col);
                for (int j = 0; j < w; j++) {
                    int h = Math.max(1, Math.round(s * (1f - j / (float) w)));
                    c.fill(cx + w / 2 - j, cy - h / 2, cx + w / 2 - j + 1, cy - h / 2 + h, col);
                }
            }
            case 2 -> {
                for (int j = 0; j < w; j++) {
                    int h = Math.max(1, Math.round(s * (1f - j / (float) w)));
                    c.fill(cx - w / 2 + j - 1, cy - h / 2, cx - w / 2 + j, cy - h / 2 + h, col);
                }
                c.fill(cx + w / 2 - 1, cy - s / 2, cx + w / 2 + 1, cy + s / 2, col);
            }
            case 3 -> {
                c.fill(cx - 3, cy - s / 2, cx - 1, cy + s / 2, col);
                c.fill(cx + 1, cy - s / 2, cx + 3, cy + s / 2, col);
            }
            default -> c.fill(cx - s / 2 + 1, cy - s / 2 + 1, cx + s / 2 - 1, cy + s / 2 - 1, col);
        }
    }

    private void button(DrawContext ctx, int mx, int my, int x, int y, int w, int h, String label, boolean active) {
        boolean hov = in(mx, my, x, y, w, h);
        Ui.rect(ctx, x, y, w, h, 6, active ? ((0x55 << 24) | (Ui.ACCENT & 0xFFFFFF)) : (hov ? Ui.FIELD_HOVER : Ui.FIELD));
        Ui.outline(ctx, x, y, w, h, 6, active || hov ? Ui.ACCENT_LINE : Ui.OUTLINE);
        Hud.text(ctx, label, x + (w - Hud.w(label)) / 2, y + (h - 8) / 2, Ui.TEXT);
    }

    /** Многострочный текст по центру: длинные сообщения об ошибках и инструкции переносятся по словам. */
    private void wrapCentered(DrawContext ctx, String s, int cx, int y, int maxW, int col) {
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        int yy = y;
        for (String wd : words) {
            String test = line.length() == 0 ? wd : line + " " + wd;
            if (Hud.w(test) > maxW && line.length() > 0) {
                Hud.text(ctx, line.toString(), cx - Hud.w(line.toString()) / 2, yy, col);
                yy += 10;
                line = new StringBuilder(wd);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) Hud.text(ctx, line.toString(), cx - Hud.w(line.toString()) / 2, yy, col);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------- ввод ----------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mxd = click.x(), myd = click.y();
        int button = click.button();
        if (button != 0) return super.mouseClicked(click, doubled);
        int mx = (int) mxd, my = (int) myd;
        for (int i = 0; i < tabX.length; i++) {
            if (in(mx, my, tabX[i], py + 6, tabW[i], 16)) {
                setProvider(i);
                return true;
            }
        }
        if (in(mx, my, fieldX, fieldY, fieldW, 20)) {
            field.setFocused(true);
            field.mouseClicked(click, doubled);
            return true;
        }
        if (keyBtnW > 0 && in(mx, my, keyBtnX, fieldY, keyBtnW, 20)) {
            MusicProvider p = provider();
            keyMode = !keyMode || (p.keyRequired() && module.key(p).isBlank());
            status = keyMode ? p.keyHelp() : "";
            field.setText("");
            return true;
        }
        if (in(mx, my, searchBtnX, fieldY, searchBtnW, 20)) {
            submit();
            return true;
        }
        if (in(mx, my, listX, listY, listW, listH)) {
            List<Track> r = results;
            int idx = (int) Math.floor((my - listY) / (double) ROW_H + scroll);
            if (idx >= 0 && idx < r.size()) {
                MusicPlayer.setVolume(module.volume());
                MusicPlayer.play(r, idx);
            }
            return true;
        }
        int bx = px + PAD;
        int by = barY + 5;
        if (in(mx, my, bx, by, 22, 20)) { MusicPlayer.prev(); return true; }
        if (in(mx, my, bx + 26, by, 22, 20)) { MusicPlayer.togglePause(); return true; }
        if (in(mx, my, bx + 52, by, 22, 20)) { MusicPlayer.next(); return true; }
        if (in(mx, my, bx + 78, by, 22, 20)) { MusicPlayer.stop(); return true; }
        if (in(mx, my, volX - 4, by + 3, volW + 8, 14)) {
            draggingVolume = true;
            setVolumeAt(mxd);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void setVolumeAt(double mx) {
        module.setVolume(MathHelper.clamp((float) ((mx - volX) / volW), 0f, 1f), false);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (draggingVolume) {
            setVolumeAt(mx);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (draggingVolume) {
            draggingVolume = false;
            module.setVolume(MusicPlayer.volume(), true);
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (in((int) mx, (int) my, listX, listY, listW, listH)) {
            scroll -= (float) vAmount;
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(input);
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (field.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        return field.charTyped(input);
    }
}
