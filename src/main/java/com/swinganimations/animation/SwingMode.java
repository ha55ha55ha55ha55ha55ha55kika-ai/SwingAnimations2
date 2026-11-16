package com.swinganimations.animation;

import com.swinganimations.config.HandProfile;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;

/** Чтобы добавить режим: новая константа + ключ swinganimations.mode.<имя_в_нижнем_регистре> в lang-файлах. */
public enum SwingMode implements SwingAnimation {

    /** Копия ванильной математики вращения (в игре для VANILLA вызывается оригинальный метод). */
    VANILLA {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float f = (float) Math.sin(p * p * Math.PI);
            float g = (float) Math.sin(Math.sqrt(p) * Math.PI);
            Xf.rotY(m, side * (45f + f * -20f));
            Xf.rotZ(m, side * g * -20f);
            Xf.rotX(m, g * -80f);
            Xf.rotY(m, side * -45f);
        }
    },

    /**
     * Лежачий меч: клинок горизонтально, удар плавной дугой. Поза в покое НЕ единичная.
     * Тонкая настройка: amplitude, windup, hold, easeIn/easeOut, thrust, sway, intensity.
     */
    LYING {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float e = Xf.envelope(p, s.windup, s.hold, s.easeIn, s.easeOut);
            float amp = s.amplitude;
            float k = s.intensity;
            float fwd = Math.max(e, 0f);
            Xf.translate(m, 0.09f * side - 0.04f * side * s.sway * amp * e,
                    0.22f - 0.03f * amp * e,
                    -0.08f - 0.25f * s.thrust * amp * fwd);
            Xf.rotY(m, 90f * side - 14f * side * s.sway * amp * k * e);
            Xf.rotZ(m, -70f * side);
            Xf.rotX(m, -100f - 60f * k * amp * e);
        }
    },

    /**
     * Стоячий меч: клинок вертикально, плавный удар, по умолчанию не до конца (amplitude < 1).
     * Поза в покое почти единичная (лёгкий наклон наружу).
     */
    STANDING {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float e = Xf.envelope(p, s.windup, s.hold, s.easeIn, s.easeOut);
            float amp = s.amplitude;
            float k = s.intensity;
            float fwd = Math.max(e, 0f);
            Xf.translate(m, -0.06f * side * s.sway * amp * e,
                    -0.04f * amp * e,
                    -0.22f * s.thrust * amp * fwd);
            Xf.rotZ(m, -8f * side);
            Xf.rotY(m, -14f * side * s.sway * amp * k * e);
            Xf.rotZ(m, -20f * side * amp * k * e);
            Xf.rotX(m, -58f * amp * k * e);
        }
    },

    BLOCK_HIT {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float a = Xf.bell(p);
            float b = Xf.clamp01(a * 1.8f); // быстро входим в позу блока и держим
            Xf.translate(m, side * -0.10f * b, 0.04f * b, 0.02f * b);
            Xf.rotY(m, side * 28f * b);
            Xf.rotX(m, -60f * b - 20f * a);
            Xf.rotZ(m, side * -18f * b);
        }
    },

    SMOOTH {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float e = Xf.easeOutCubic(p);
            float g = Xf.bell(e);
            Xf.translate(m, side * -0.12f * g, 0.04f * g, -0.08f * g);
            Xf.rotY(m, side * -25f * g);
            Xf.rotZ(m, side * -22f * g);
            Xf.rotX(m, -60f * g);
        }
    },

    SLASH {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float a = Xf.bell(p);
            float sweep = (Xf.smooth(p) - 0.5f) * 2f; // -1..1
            Xf.translate(m, 0f, 0f, -0.08f * a);
            Xf.rotY(m, side * sweep * -55f);
            Xf.rotZ(m, side * -40f * a);
            Xf.rotX(m, -25f * a);
        }
    },

    SPIN {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float a = Xf.bell(p);
            Xf.translate(m, 0f, 0.02f * a, -0.12f * a);
            Xf.rotX(m, -25f * a);
            Xf.rotZ(m, side * 360f * Xf.smooth(p)); // полный оборот вокруг оси предмета
        }
    },

    PUNCH {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float q = Xf.pulse(p);
            Xf.translate(m, side * -0.04f * q, 0.03f * q, -0.55f * q);
            Xf.rotX(m, -12f * q);
        }
    },

    SWONG {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float angle;
            if (p < 0.3f) {
                angle = Xf.lerp(0f, 28f, Xf.smooth(p / 0.3f));                     // замах назад
            } else if (p < 0.55f) {
                angle = Xf.lerp(28f, -80f, Xf.easeOutCubic((p - 0.3f) / 0.25f));   // быстрый удар
            } else {
                angle = Xf.lerp(-80f, 0f, Xf.smooth((p - 0.55f) / 0.45f));         // возврат
            }
            float strike = p < 0.3f ? 0f : Xf.bell((p - 0.3f) / 0.7f);
            float windup = Xf.bell(Math.min(1f, p / 0.6f));
            Xf.translate(m, side * -0.05f * strike, 0.03f * strike, 0.08f * windup - 0.22f * strike);
            Xf.rotX(m, angle);
            Xf.rotZ(m, side * -18f * Xf.bell(p));
            Xf.rotY(m, side * -10f * Xf.bell(p));
        }
    },

    STAB {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float q = Xf.pulse(p);
            Xf.translate(m, side * -0.02f * q, -0.02f * q, -0.65f * q);
            Xf.rotX(m, -78f * q); // остриём вперёд
            Xf.rotY(m, side * 10f * q);
        }
    },

    EXHIBITION {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float a = Xf.bell(p);
            float wave2 = (float) Math.sin(p * Math.PI * 2.0);
            float wave3 = (float) Math.sin(p * Math.PI * 3.0);
            Xf.translate(m, side * -0.08f * a, -0.05f * a + 0.03f * wave2, 0f);
            Xf.rotX(m, -35f * a);
            Xf.rotY(m, side * wave2 * 22f);
            Xf.rotZ(m, side * (wave3 * 18f - 20f * a));
        }
    },

    /** Ванильные повороты, но с множителем «Сила анимации» (s.intensity). */
    VANILLA_PLUS {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float k = s.intensity;
            float f = (float) Math.sin(p * p * Math.PI);
            float g = (float) Math.sin(Math.sqrt(p) * Math.PI);
            Xf.rotY(m, side * (45f + f * -20f * k));
            Xf.rotZ(m, side * g * -20f * k);
            Xf.rotX(m, g * -80f * k);
            Xf.rotY(m, side * -45f);
        }
    },

    /** Поза «боком»: предмет повёрнут и выталкивается вперёд. Поза в покое НЕ единичная. */
    PUSH {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float k = s.intensity;
            float a = Xf.bell(p);
            Xf.translate(m, 0f, 0f, (k / 3f) * -a);
            Xf.rotY(m, 75f * side);
            Xf.rotZ(m, (-75f * (k / 4f) * a - 60f) * side);
            Xf.rotX(m, -75f);
        }
    },

    /** Плоская поза: предмет лежит горизонтально, удар по вертикальной дуге. Поза в покое НЕ единичная. */
    FLAT {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float k = s.intensity;
            float a = Xf.bell(p);
            Xf.translate(m, 0.09f * side, 0.22f, -0.08f);
            Xf.rotY(m, 90f * side);
            Xf.rotZ(m, -70f * side);
            Xf.rotX(m, -100f - 60f * k * a);
        }
    },

    /** Поза блока 1.8 в покое + замах по дуге. Поза в покое НЕ единичная. */
    BLOCK_SWING {
        @Override
        public void apply(MatrixStack m, float p, Arm arm, HandProfile s) {
            float side = Xf.sign(arm);
            float k = s.intensity;
            float g = (float) Math.sin(Math.sqrt(p) * Math.PI);
            Xf.translate(m, 0f, 0.20f, 0f);
            Xf.rotY(m, 30f * side);
            Xf.rotY(m, g * 75f * side * k);
            Xf.rotX(m, g * -65f * k);
            Xf.rotY(m, 30f * side);
            Xf.rotX(m, -80f);
            Xf.rotY(m, 35f * side);
        }
    };

    public String translationKey() {
        return "swinganimations.mode." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
