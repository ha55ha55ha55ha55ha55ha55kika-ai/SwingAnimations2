package com.swinganimations.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.swinganimations.SwingAnimationsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Конфиг читается один раз и кэшируется; в рендере только get(). */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile SwingConfig config = createDefault();

    private ConfigManager() {}

    public static SwingConfig get() {
        return config;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("swinganimations.json");
    }

    public static SwingConfig createDefault() {
        SwingConfig c = new SwingConfig();
        Presets.apply(c, "Smooth");
        c.rebuildCaches();
        return c;
    }

    public static void load() {
        Path p = path();
        if (!Files.exists(p)) {
            config = createDefault();
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            SwingConfig c = GSON.fromJson(r, SwingConfig.class);
            if (c == null) throw new JsonParseException("empty config");
            c.sanitize();
            config = c;
        } catch (Exception e) {
            SwingAnimationsClient.LOGGER.warn("Конфиг повреждён ({}), создаю заново. Старый файл сохранён как .broken", e.toString());
            try {
                Files.move(p, p.resolveSibling("swinganimations.json.broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // не критично
            }
            config = createDefault();
            save();
        }
    }

    public static void save() {
        Path p = path();
        Path tmp = p.resolveSibling("swinganimations.json.tmp");
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(tmp, GSON.toJson(config), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SwingAnimationsClient.LOGGER.warn("Не удалось сохранить конфиг: {}", e.toString());
        }
    }
}
