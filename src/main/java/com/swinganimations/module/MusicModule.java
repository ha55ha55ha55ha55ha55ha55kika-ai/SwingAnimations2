package com.swinganimations.module;

import com.swinganimations.gui.Hud;
import com.swinganimations.gui.HudLayout;
import com.swinganimations.gui.MusicScreen;
import com.swinganimations.gui.Ui;
import com.swinganimations.music.MusicPlayer;
import com.swinganimations.music.MusicProvider;
import com.swinganimations.music.Providers;
import com.swinganimations.music.Track;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundCategory;

/**
 * Music: мини-браузер для поиска песен и плеер прямо в игре. Окно открывается клавишей (по умолчанию M) или из ClickGUI;
 * на экране показывается плашка «сейчас играет» (её можно перетащить, открыв чат). Музыка идёт в отдельном потоке
 * через Java Sound и не затрагивает сервер. Источники: Deezer (превью 30 с, без настройки), Jamendo и Audius (полные треки).
 */
public final class MusicModule extends Module {
    private static final String[] NAMES = Providers.ALL.stream().map(MusicProvider::name).toArray(String[]::new);

    private final Setting.Num volume = add(new Setting.Num("volume", "Volume", 0.6f, 0f, 1f, 0.05f));
    private final Setting.Mode source = add(new Setting.Mode("source", "Source", NAMES));
    private final Setting.Bool hud = add(new Setting.Bool("hud", "Now playing widget", true));
    private final Setting.Bool muteGame = add(new Setting.Bool("mute_game", "Mute Minecraft music", true));
    private final Setting.Bool open = add(new Setting.Bool("open", "Open music browser", false));
    private final Setting.Str jamendoKey = add(new Setting.Str("jamendo_key", "Jamendo client_id", ""));
    private final Setting.Str audiusKey = add(new Setting.Str("audius_key", "Audius API key", ""));

    private final HudLayout.Win win = HudLayout.win("music");
    private int tickCounter;
    private String shownTitle = "";
    private String shownArtist = "";
    private int shownWidth = -1;

    public MusicModule() {
        super("music", "Music", "hud");
    }

    @Override
    public void onEnable() {
        MusicPlayer.setVolume(volume.value);
    }

    @Override
    public void onDisable() {
        MusicPlayer.stop();
    }

    // ---------------- доступ для экрана ----------------

    public int sourceIndex() { return source.index; }

    public void setSource(int i) {
        source.index = Math.max(0, Math.min(NAMES.length - 1, i));
        ModuleManager.save();
    }

    public float volume() { return volume.value; }

    public void setVolume(float v, boolean persist) {
        volume.set(v);
        MusicPlayer.setVolume(volume.value);
        if (persist) ModuleManager.save();
    }

    public String key(MusicProvider p) {
        return switch (p.name()) {
            case "Jamendo" -> jamendoKey.value;
            case "Audius" -> audiusKey.value;
            default -> "";
        };
    }

    public void setKey(MusicProvider p, String key) {
        String k = key == null ? "" : key.trim();
        switch (p.name()) {
            case "Jamendo" -> jamendoKey.value = k;
            case "Audius" -> audiusKey.value = k;
            default -> { }
        }
        ModuleManager.save();
    }

    /** Открывает окно поиска; включает модуль, если он выключен. */
    public void openBrowser() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isEnabled()) setEnabled(true);
        if (mc.currentScreen instanceof MusicScreen) return;
        mc.setScreen(new MusicScreen(mc.currentScreen, this));
    }

    // ---------------- тик и HUD ----------------

    @Override
    public void tick(MinecraftClient mc) {
        if (open.value) {
            open.value = false;
            openBrowser();
        }
        // Родная музыка Minecraft мешала бы нашей: гасим её раз в полсекунды, пока играет наш трек.
        if (muteGame.value && MusicPlayer.state() == MusicPlayer.State.PLAYING && ++tickCounter >= 10) {
            tickCounter = 0;
            mc.getSoundManager().stopSounds(null, SoundCategory.MUSIC);
        }
    }

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        if (!hud.value) return;
        Track t = MusicPlayer.current();
        boolean active = t != null && MusicPlayer.isActive();
        String title;
        String artist;
        float frac;
        String time;
        boolean paused = MusicPlayer.state() == MusicPlayer.State.PAUSED;
        if (active) {
            title = t.title();
            artist = t.artist();
            long dur = Math.max(1L, t.durationSec() * 1000L);
            frac = Math.min(1f, MusicPlayer.positionMs() / (float) dur);
            time = MusicPlayer.state() == MusicPlayer.State.PAUSED ? "paused" : MusicPlayer.fmt(MusicPlayer.positionMs());
        } else if (HudLayout.editing()) {
            title = "Now playing";
            artist = "Open chat to move this widget";
            frac = 0.4f;
            time = "0:00";
        } else {
            return;
        }

        int w = 132;
        int h = 30;
        int x = 6;
        int y = 6;
        if (!title.equals(shownTitle) || !artist.equals(shownArtist) || shownWidth != w) {
            shownTitle = fit(title, w - 22);
            shownArtist = fit(artist, w - 22);
            shownWidth = w;
        }
        HudLayout.push(ctx, win);
        Hud.panel(ctx, x, y, w, h);
        // значок: ▶ или пауза
        int ix = x + 7;
        int iy = y + 9;
        if (paused) {
            ctx.fill(ix, iy, ix + 2, iy + 8, Ui.ACCENT);
            ctx.fill(ix + 4, iy, ix + 6, iy + 8, Ui.ACCENT);
        } else {
            for (int j = 0; j < 6; j++) {
                int hh = Math.max(1, 8 - j * 8 / 6);
                ctx.fill(ix + j, iy + 4 - hh / 2, ix + j + 1, iy + 4 - hh / 2 + hh, Ui.ACCENT);
            }
        }
        Hud.text(ctx, shownTitle, x + 18, y + 4, Ui.TEXT);
        Hud.text(ctx, shownArtist, x + 18, y + 14, Ui.TEXT_DIM);
        Hud.bar(ctx, x + 6, y + h - 5, w - 12, 2, frac, Ui.ACCENT);
        HudLayout.pop(ctx, win, x, y, w, h);
    }

    private static String fit(String s, int maxW) {
        if (Hud.w(s) <= maxW) return s;
        String r = s;
        while (r.length() > 1 && Hud.w(r + "...") > maxW) r = r.substring(0, r.length() - 1);
        return r + "...";
    }
}
