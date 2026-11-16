package com.swinganimations.music;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Минимальный HTTP-клиент на HttpURLConnection (java.base): таймауты, ручные редиректы (в том числе http → https). */
final class Http {
    private static final String UA = "SwingAnimations-Music/1.0 (Minecraft client mod)";
    private static final int MAX_BODY = 4 * 1024 * 1024;

    private Http() {}

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static String get(String url) throws IOException {
        try (InputStream in = open(url)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY) throw new IOException("Response too large");
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** Открывает поток; вызывающий обязан закрыть его. Бросает IOException на коды 4xx/5xx. */
    static InputStream open(String url) throws IOException {
        String cur = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) URI.create(cur).toURL().openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(20_000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirect without Location");
                cur = URI.create(cur).resolve(loc).toString();
                continue;
            }
            if (code >= 400) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c.getInputStream();
        }
        throw new IOException("Too many redirects");
    }
}
