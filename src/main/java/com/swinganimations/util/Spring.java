package com.swinganimations.util;

/** Пружинная анимация (слегка недодемпфированная: мягкий «подскок» у цели). Вызывайте update() раз в кадр. */
public final class Spring {
    public float value;
    public float target;
    public float velocity;
    private final float stiffness;
    private final float damping;
    private long last = System.nanoTime();

    public Spring(float initial, float stiffness, float damping) {
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public Spring(float initial) {
        this(initial, 170f, 22f);
    }

    public void snap(float v) {
        value = v;
        target = v;
        velocity = 0f;
    }

    /** Пружина уже у цели и не движется: update() ничего не считает. */
    public boolean settled() {
        return Math.abs(target - value) < 0.0004f && Math.abs(velocity) < 0.0004f;
    }

    public float update() {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1_000_000_000f);
        last = now;
        if (settled()) {
            value = target;
            velocity = 0f;
            return value;
        }
        int steps = Math.max(1, (int) Math.ceil(dt / 0.008f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float a = stiffness * (target - value) - damping * velocity;
            velocity += a * h;
            value += velocity * h;
        }
        return value;
    }
}
