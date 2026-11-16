package com.swinganimations.animation;

public enum Curve {
    LINEAR {
        @Override public float apply(float t) { return t; }
    },
    EASE_IN {
        @Override public float apply(float t) { return t * t * t; }
    },
    EASE_OUT {
        @Override public float apply(float t) { float u = 1f - t; return 1f - u * u * u; }
    },
    EASE_IN_OUT {
        @Override public float apply(float t) { return t * t * (3f - 2f * t); }
    };

    public abstract float apply(float t);

    /** strength 0 = линейно, 1 = полная кривая. */
    public float blend(float t, float strength) {
        return t + (apply(t) - t) * strength;
    }
}
