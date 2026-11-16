package com.swinganimations.relay;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реле для канала swinganimations:link. Берёт пакет от одного клиента SwingAnimations и пересылает остальным
 * клиентам с модом, добавив в начало UUID отправителя. Содержимое пакета не разбирает.
 * Новым игрокам при подключении отдаёт последнее известное состояние остальных.
 */
public final class SwingLinkRelay extends JavaPlugin implements PluginMessageListener, Listener {
    private static final String CHANNEL = "swinganimations:link";
    private static final int MAX_BODY = 600;
    private static final long MIN_INTERVAL_MS = 400L;

    private final Map<UUID, byte[]> last = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAt = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Messenger m = getServer().getMessenger();
        m.registerIncomingPluginChannel(this, CHANNEL, this);
        m.registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SwingLinkRelay: канал " + CHANNEL + " активен");
    }

    @Override
    public void onDisable() {
        Messenger m = getServer().getMessenger();
        m.unregisterIncomingPluginChannel(this);
        m.unregisterOutgoingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message == null || message.length == 0 || message.length > MAX_BODY) return;
        if (message[0] != 1) return; // версия формата
        long now = System.currentTimeMillis();
        Long prev = lastAt.put(player.getUniqueId(), now);
        if (prev != null && now - prev < MIN_INTERVAL_MS) return; // защита от флуда
        last.put(player.getUniqueId(), message.clone());
        byte[] out = pack(player.getUniqueId(), message);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || !other.getListeningPluginChannels().contains(CHANNEL)) continue;
            other.sendPluginMessage(this, CHANNEL, out);
        }
    }

    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent e) {
        if (!CHANNEL.equals(e.getChannel())) return;
        Player joined = e.getPlayer();
        for (Map.Entry<UUID, byte[]> en : last.entrySet()) {
            Player src = Bukkit.getPlayer(en.getKey());
            if (src == null || src.equals(joined)) continue;
            joined.sendPluginMessage(this, CHANNEL, pack(en.getKey(), en.getValue()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
        lastAt.remove(e.getPlayer().getUniqueId());
    }

    private static byte[] pack(UUID id, byte[] body) {
        ByteBuffer b = ByteBuffer.allocate(16 + body.length);
        b.putLong(id.getMostSignificantBits());
        b.putLong(id.getLeastSignificantBits());
        b.put(body);
        return b.array();
    }
}
