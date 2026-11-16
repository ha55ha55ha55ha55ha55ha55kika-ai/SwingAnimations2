package com.swinganimations.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Deezer: открытый поиск без ключа. Играет официальные 30-секундные превью-клипы (полные треки Deezer не отдаёт). */
public final class DeezerProvider implements MusicProvider {
    @Override public String name() { return "Deezer"; }

    @Override public String keyLabel() { return null; }

    @Override public boolean keyRequired() { return false; }

    @Override public String keyHelp() { return ""; }

    @Override public String note() { return "Any song, 30-second previews, no setup"; }

    @Override
    public List<Track> search(String query, String key) throws IOException {
        String body = Http.get("https://api.deezer.com/search?limit=30&q=" + Http.enc(query));
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (root.has("error")) throw new IOException("Deezer: " + root.get("error").toString());
        List<Track> out = new ArrayList<>();
        JsonArray data = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();
        for (JsonElement el : data) {
            JsonObject o = el.getAsJsonObject();
            String preview = str(o, "preview");
            if (preview.isEmpty()) continue;
            String artist = o.has("artist") && o.get("artist").isJsonObject() ? str(o.getAsJsonObject("artist"), "name") : "";
            out.add(new Track(str(o, "id"), str(o, "title"), artist, 30, preview, "Deezer"));
        }
        return out;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
