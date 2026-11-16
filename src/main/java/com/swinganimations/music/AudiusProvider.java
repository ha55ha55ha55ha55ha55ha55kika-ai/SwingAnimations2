package com.swinganimations.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Audius: полные треки независимых артистов. Экспериментальный источник: Audius перешёл на API-ключи
 * (бесплатный план), поэтому ключ передаётся параметром api_key. Если Audius поменяет схему, поиск покажет ошибку.
 */
public final class AudiusProvider implements MusicProvider {
    private static final String BASE = "https://api.audius.co/v1";
    private static final String APP = "SwingAnimations";

    @Override public String name() { return "Audius"; }

    @Override public String keyLabel() { return "API key"; }

    @Override public boolean keyRequired() { return false; }

    @Override public String keyHelp() { return "Optional: audius.co/settings or api.audius.co/plans > API key"; }

    @Override public String note() { return "Full-length tracks (experimental)"; }

    @Override
    public List<Track> search(String query, String key) throws IOException {
        String auth = "&app_name=" + APP + (key == null || key.isBlank() ? "" : "&api_key=" + Http.enc(key.trim()));
        JsonObject root = JsonParser.parseString(Http.get(BASE + "/tracks/search?limit=30&query=" + Http.enc(query) + auth)).getAsJsonObject();
        List<Track> out = new ArrayList<>();
        JsonArray data = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : new JsonArray();
        for (JsonElement el : data) {
            JsonObject o = el.getAsJsonObject();
            String id = str(o, "id");
            if (id.isEmpty()) continue;
            String artist = o.has("user") && o.get("user").isJsonObject() ? str(o.getAsJsonObject("user"), "name") : "";
            int dur = 0;
            try { dur = Integer.parseInt(str(o, "duration")); } catch (NumberFormatException ignored) { }
            out.add(new Track(id, str(o, "title"), artist, dur, BASE + "/tracks/" + Http.enc(id) + "/stream?" + auth.substring(1), "Audius"));
        }
        return out;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
