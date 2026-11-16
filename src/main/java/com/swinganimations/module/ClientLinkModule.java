package com.swinganimations.module;

import com.swinganimations.gui.Hud;
import com.swinganimations.gui.Ui;
import com.swinganimations.link.ClientLink;
import com.swinganimations.render.Projection;
import com.swinganimations.util.Perf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientLink: значок нашего клиента у ника, в чате и в TAB у тех, кто тоже играет с модом,
 * и плашка «что слушает» над головой (если у игрока играет Music).
 * Связь: канал сервера (нужен плагин-реле) и/или HTTP-реле. Сколько и что показывать о себе, настраивается тут же.
 */
public final class ClientLinkModule extends Module {
    /**
     * Реле по умолчанию для всех, кто играет с этим билдом (впишите свои значения перед сборкой, чтобы игрокам
     * не пришлось ничего настраивать). Токен внутри jar не секрет, он только отсекает случайный спам.
     * Если у игрока в конфиге relay_url / relay_token не пустые, используются они.
     */
    private static final String DEFAULT_RELAY_URL = "http://78.154.103.37:14682";
    private static final String DEFAULT_RELAY_TOKEN = "VpVSfC7r5xW9VlS2zCMwIVM9";

    private final Setting.Bool share = add(new Setting.Bool("share", "Share that I use the client", true));
    private final Setting.Bool shareMusic = add(new Setting.Bool("share_music", "Share what I listen to", true));
    private final Setting.Bool badgeName = add(new Setting.Bool("badge_name", "Badge by nick", true));
    private final Setting.Bool badgeChat = add(new Setting.Bool("badge_chat", "Badge in chat", true));
    private final Setting.Bool badgeTab = add(new Setting.Bool("badge_tab", "Badge in TAB", true));
    private final Setting.Bool musicTags = add(new Setting.Bool("music_tags", "Show what others listen to", true));
    private final Setting.Num musicRange = add(new Setting.Num("music_range", "Music tag range", 32f, 8f, 64f, 4f).<Setting.Num>visibleWhen(() -> this.musicTags.value));
    private final Setting.Mode transport = add(new Setting.Mode("transport", "Transport", "Both", "Server channel", "HTTP relay"));
    // Правятся в swinganimations_modules.json (в ClickGUI не показываются).
    private final Setting.Str relayUrl = add(new Setting.Str("relay_url", "Relay URL", ""));
    private final Setting.Str relayToken = add(new Setting.Str("relay_token", "Relay token", ""));

    private final List<PlayerEntity> tagged = new ArrayList<>();
    private final float[] out = new float[2];
    private int timer;

    public ClientLinkModule() {
        super("client_link", "ClientLink", "hud");
    }

    @Override
    public void onDisable() {
        tagged.clear();
    }

    public boolean sharing() { return share.value; }

    public boolean sharingMusic() { return shareMusic.value; }

    public boolean badgeName() { return badgeName.value; }

    public boolean badgeChat() { return badgeChat.value; }

    public boolean badgeTab() { return badgeTab.value; }

    public boolean useChannel() { return !transport.is("HTTP relay"); }

    public boolean useHttp() { return !transport.is("Server channel"); }

    public String relayUrl() {
        String u = relayUrl.value == null ? "" : relayUrl.value.trim();
        if (u.isEmpty()) u = DEFAULT_RELAY_URL.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u.startsWith("http://") || u.startsWith("https://") ? u : "";
    }

    public String relayToken() {
        String t = relayToken.value == null ? "" : relayToken.value.trim();
        return t.isEmpty() ? DEFAULT_RELAY_TOKEN.trim() : t;
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (++timer < 20) {
            if (musicTags.value && timer % 5 == 0) scanTags(mc);
            return;
        }
        timer = 0;
        ClientLink.tick(mc, this); // сам решает, когда слать /hello (30-45 с) и когда опрашивать список
        scanTags(mc);
    }

    /** Кто из игроков с клиентом рядом и слушает музыку (раз в несколько тиков, не в кадре). */
    private void scanTags(MinecraftClient mc) {
        tagged.clear();
        if (!musicTags.value || mc.world == null || mc.player == null) return;
        double r = musicRange.value;
        double r2 = r * r;
        int limit = Perf.quality() == 0 ? 6 : 3;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isInvisible()) continue;
            ClientLink.Peer peer = ClientLink.peer(p.getUuid());
            if (peer == null || !peer.listening()) continue;
            if (mc.player.squaredDistanceTo(p) > r2) continue;
            tagged.add(p);
        }
        tagged.sort((a, b) -> Double.compare(mc.player.squaredDistanceTo(a), mc.player.squaredDistanceTo(b)));
        while (tagged.size() > limit) tagged.remove(tagged.size() - 1);
    }

    @Override
    public void renderHud(DrawContext ctx, float td) {
        if (tagged.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.options.hudHidden || !Projection.valid()) return;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        org.joml.Matrix3x2fStack m = ctx.getMatrices();
        for (int i = 0; i < tagged.size(); i++) {
            PlayerEntity p = tagged.get(i);
            if (p.isRemoved() || !p.isAlive()) continue;
            ClientLink.Peer peer = ClientLink.peer(p.getUuid());
            if (peer == null || !peer.listening()) continue;
            double x = MathHelper.lerp(td, p.lastRenderX, p.getX());
            double y = MathHelper.lerp(td, p.lastRenderY, p.getY()) + p.getHeight() + 1.25;
            double z = MathHelper.lerp(td, p.lastRenderZ, p.getZ());
            if (!Projection.project(x, y, z, out, sw, sh)) continue;

            boolean paused = peer.state() == ClientLink.STATE_PAUSED;
            String line = peer.artist().isEmpty() ? peer.title() : peer.artist() + " - " + peer.title();
            line = Ui.tr().trimToWidth(line, 150);
            int tw = Hud.w(line);
            int pw = tw + 22;
            float scale = MathHelper.clamp(3.2f / (float) Math.sqrt(Math.max(Projection.distanceSq(x, y, z), 1.0)), 0.55f, 1.05f);
            m.pushMatrix();
            m.translate(out[0], out[1]);
            m.scale(scale, scale);
            Ui.rect(ctx, -pw / 2, -12, pw, 12, 3, 0xA0000000);
            Hud.text(ctx, paused ? "||" : "\u266A", -pw / 2 + 5, -10, paused ? Ui.TEXT_DIM : Ui.ACCENT);
            Hud.text(ctx, line, -pw / 2 + 17, -10, paused ? Ui.TEXT_DIM : Ui.TEXT);
            m.popMatrix();
        }
    }
}
