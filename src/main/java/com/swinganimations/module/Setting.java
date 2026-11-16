package com.swinganimations.module;

import java.util.function.BooleanSupplier;

/** Настройка модуля. Значение сериализуется строкой, чтобы JSON был простым. */
public abstract class Setting {
    public final String id;
    public final String fallback;
    private BooleanSupplier visible = () -> true;

    protected Setting(String id, String fallback) {
        this.id = id;
        this.fallback = fallback;
    }

    @SuppressWarnings("unchecked")
    public <T extends Setting> T visibleWhen(BooleanSupplier v) {
        this.visible = v;
        return (T) this;
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }

    public abstract String serialize();

    public abstract void deserialize(String s);

    public static final class Bool extends Setting {
        public boolean value;

        public Bool(String id, String fallback, boolean def) {
            super(id, fallback);
            this.value = def;
        }

        @Override public String serialize() { return Boolean.toString(value); }

        @Override public void deserialize(String s) { value = Boolean.parseBoolean(s); }
    }

    public static final class Num extends Setting {
        public float value;
        public final float min;
        public final float max;
        public final float step;

        public Num(String id, String fallback, float def, float min, float max, float step) {
            super(id, fallback);
            this.min = min;
            this.max = max;
            this.step = step;
            set(def);
        }

        public void set(float v) {
            if (Float.isNaN(v)) return;
            if (step > 0f) v = Math.round(v / step) * step;
            value = Math.max(min, Math.min(max, v));
        }

        public float fraction() {
            return (value - min) / (max - min);
        }

        @Override public String serialize() { return Float.toString(value); }

        @Override public void deserialize(String s) {
            try { set(Float.parseFloat(s)); } catch (NumberFormatException ignored) { }
        }
    }

    public static final class Mode extends Setting {
        public final String[] modes;
        public int index;

        public Mode(String id, String fallback, String... modes) {
            super(id, fallback);
            this.modes = modes;
        }

        public String get() { return modes[index]; }

        public boolean is(String m) { return modes[index].equals(m); }

        public void next() { index = (index + 1) % modes.length; }

        @Override public String serialize() { return get(); }

        @Override public void deserialize(String s) {
            for (int i = 0; i < modes.length; i++) if (modes[i].equals(s)) { index = i; return; }
        }
    }

    /** Скрытая строковая настройка (в ClickGUI не показывается): для списков и прочих данных модуля. */
    public static final class Str extends Setting {
        public String value;

        public Str(String id, String fallback, String def) {
            super(id, fallback);
            this.value = def;
        }

        @Override public String serialize() { return value; }

        @Override public void deserialize(String s) { value = s == null ? "" : s; }
    }
}
