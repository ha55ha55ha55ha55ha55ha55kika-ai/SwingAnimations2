package com.swinganimations.config;

import com.swinganimations.animation.Curve;
import com.swinganimations.animation.SwingMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Presets {
    private static final Map<String, PresetData> BUILTIN = new LinkedHashMap<>();

    static {
        add("1.8", new HandProfile(SwingMode.BLOCK_HIT, 1.0f, Curve.LINEAR, 1f, 0.04f, -0.03f, 0f, 0.95f, false, false));
        add("Smooth", new HandProfile(SwingMode.SMOOTH, 0.8f, Curve.EASE_OUT, 1f, 0f, 0f, 0f, 0.95f, true, false));
        add("Minimal", new HandProfile(SwingMode.VANILLA, 1.2f, Curve.LINEAR, 1f, 0.05f, -0.08f, -0.02f, 0.8f, false, false));
        add("Aggressive", new HandProfile(SwingMode.SLASH, 1.6f, Curve.EASE_IN, 0.8f, 0f, 0f, 0f, 1.0f, false, true));
        add("Lying", fine(new HandProfile(SwingMode.LYING, 1.0f, Curve.EASE_IN_OUT, 0.6f, 0f, 0f, 0f, 1.0f, true, false),
                1.0f, 0.2f, 0.1f, 0.6f, 0.8f, 0.3f, 0.2f));
        add("Standing", fine(new HandProfile(SwingMode.STANDING, 0.9f, Curve.EASE_OUT, 0.5f, 0f, 0f, 0f, 1.0f, true, false),
                0.55f, 0.35f, 0.15f, 0.6f, 0.8f, 0.25f, 0.3f));
        add("Showcase", new HandProfile(SwingMode.EXHIBITION, 0.7f, Curve.EASE_IN_OUT, 1f, 0f, 0.02f, 0f, 1.0f, true, true));
    }

    private Presets() {}

    private static HandProfile fine(HandProfile h, float amp, float windup, float hold,
                                    float easeIn, float easeOut, float thrust, float sway) {
        h.amplitude = amp; h.windup = windup; h.hold = hold;
        h.easeIn = easeIn; h.easeOut = easeOut; h.thrust = thrust; h.sway = sway;
        return h;
    }

    private static void add(String name, HandProfile hp) {
        BUILTIN.put(name, new PresetData(hp.copy(), hp.copy()));
    }

    public static List<String> names(SwingConfig cfg) {
        List<String> l = new ArrayList<>(BUILTIN.keySet());
        l.addAll(cfg.customPresets.keySet());
        return l;
    }

    public static boolean apply(SwingConfig cfg, String name) {
        PresetData d = BUILTIN.get(name);
        if (d == null) d = cfg.customPresets.get(name);
        if (d == null) return false;
        cfg.mainHand.copyFrom(d.main);
        cfg.offHand.copyFrom(d.off);
        cfg.mainHand.sanitize();
        cfg.offHand.sanitize();
        cfg.activePreset = name;
        return true;
    }

    public static String saveCurrent(SwingConfig cfg) {
        int n = 1;
        while (cfg.customPresets.containsKey("Custom " + n) || BUILTIN.containsKey("Custom " + n)) n++;
        String name = "Custom " + n;
        cfg.customPresets.put(name, new PresetData(cfg.mainHand.copy(), cfg.offHand.copy()));
        cfg.activePreset = name;
        return name;
    }

    public static void next(SwingConfig cfg) {
        List<String> names = names(cfg);
        int i = names.indexOf(cfg.activePreset);
        apply(cfg, names.get((i + 1) % names.size()));
    }
}
