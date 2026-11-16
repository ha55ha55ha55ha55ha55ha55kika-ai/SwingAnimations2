package com.swinganimations.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Jamendo: полные треки независимых артистов (лицензии Creative Commons). Нужен бесплатный client_id. */
public final class JamendoProvider implements MusicProvider {
    @Override public String name() { return "Jamendo"; }

    @Override public String keyLabel() { return "client_id"; }

    @Override public boolean keyRequired() { return true; }

    @Override public String keyHelp() {
        return "Free: devportal.jamendo.com > create an app > copy its client_id";
    }

    @Override public String note() { return "Full-length CC tracks (indie artists)"; }

    @Override
    public List<Track> search(String query, String key) throws IOException {
        String url = "https://api.jamendo.com/v3.0/tracks/?format=json&limit=30&audioformat=mp32&order=popularity_total"
                + "&client_id=" + Http.enc(key) + "&search=" + Http.enc(query);
        JsonObject root = JsonParser.parseString(Http.get(url)).getAsJsonObject();
        if (root.has("headers") && root.get("headers").isJsonObject()) {
            JsonObject h = root.getAsJsonObject("headers");
            String status = str(h, "status");
            if (!status.isEmpty() && !"success".equalsIgnoreCase(status)) {
                String msg = str(h, "error_message");
                throw new IOException("Jamendo: " + (msg.isEmpty() ? status : msg));
            }
        }
        List<Track> out = new ArrayList<>();
        JsonArray results = root.has("results") ? root.getAsJsonArray("results") : new JsonArray();
        for (JsonElement el : results) {
            JsonObject o = el.getAsJsonObject();
            String audio = str(o, "audio");
            if (audio.isEmpty()) continue;
            int dur = 0;
            try { dur = Integer.parseInt(str(o, "duration")); } catch (NumberFormatException ignored) { }
            out.add(new Track(str(o, "id"), str(o, "name"), str(o, "artist_name"), dur, audio, "Jamendo"));
        }
        return out;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
