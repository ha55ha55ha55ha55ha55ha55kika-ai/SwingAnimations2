package com.swinganimations.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.swinganimations.SwingAnimationsClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Публичный API HolyWorld: GET https://api.holyworld.me/v1/events (без токена). Ответ: {"LITE_ANARCHY_16": [{instanceId, id,
 * metadata: {displayName, rare}}], ...}. Запросы идут в фоне, игровой поток не ждёт. Ошибки сети просто оставляют прошлый список.
 */
public final class HolyWorldApi {
    private static final String URL = "https://api.holyworld.me/v1/events";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public record Event(String server, String id, String name, String rare) { }

    private static volatile List<Event> latest = Collections.emptyList();
    private static volatile long updatedAt;
    private static ScheduledExecutorService pool;

    /** На лайте ровно 36 анархий (1–36). Всё, что выше, в списке сервера не существует. */
    public static final int MAX_ANARCHY = 36;

    private HolyWorldApi() {}

    /** Последний успешно полученный список (может быть пустым). */
    public static List<Event> latest() { return latest; }

    public static long updatedAt() { return updatedAt; }

    /** Запускает опрос раз в intervalSec секунд. Повторный вызов ничего не ломает. */
    public static synchronized void start(int intervalSec) {
        if (pool != null) return;
        pool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "swinganimations-holyworld");
            t.setDaemon(true);
            return t;
        });
        pool.scheduleAtFixedRate(HolyWorldApi::fetchSafe, 0, intervalSec, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        if (pool != null) pool.shutdownNow();
        pool = null;
    }

    private static void fetchSafe() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;
            latest = parse(resp.body());
            updatedAt = System.currentTimeMillis();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            // сеть недоступна: оставляем прошлый список, игра не страдает
        }
    }

    /** Разбор ответа. Отдельный метод, чтобы его можно было проверить на сохранённом JSON. */
    public static List<Event> parse(String json) {
        List<Event> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : new TreeMap<>(root.asMap()).entrySet()) {
            if (!e.getValue().isJsonArray()) continue;
            JsonArray arr = e.getValue().getAsJsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String id = str(o, "id", "?");
                String name = id;
                String rare = "default";
                if (o.has("metadata") && o.get("metadata").isJsonObject()) {
                    JsonObject m = o.getAsJsonObject("metadata");
                    name = str(m, "displayName", id);
                    rare = str(m, "rare", "default");
                }
                out.add(new Event(e.getKey(), id, name, rare));
            }
        }
        return out;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    /** Номер анархии из ключа сервера (число после последнего «_»); -1, если номера нет. */
    public static int number(String server) {
        int i = server.lastIndexOf('_');
        if (i < 0 || i == server.length() - 1) return -1;
        try {
            return Integer.parseInt(server.substring(i + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Ключ сервера вида LITE_ANARCHY_17 или LITE_NEW_ANARCHY_17 заканчивается на _N. */
    public static boolean onAnarchy(String server, int n) {
        return n <= 0 || server.endsWith("_" + n);
    }
}
