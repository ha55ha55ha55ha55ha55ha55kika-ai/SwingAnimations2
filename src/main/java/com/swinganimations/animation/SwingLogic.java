package com.swinganimations.animation;

import com.swinganimations.SwingAnimationsClient;
import com.swinganimations.config.ConfigManager;
import com.swinganimations.config.HandProfile;
import com.swinganimations.config.SwingConfig;
import com.swinganimations.gui.SettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** Вся логика выбора профиля и сборки трансформации. Без аллокаций в горячем пути. */
public final class SwingLogic {
    /** Рука, для которой в меню включён «Тест» (только рендер-поток). */
    public static Hand previewHand;
    private static boolean failed;

    private SwingLogic() {}

    public static HandProfile select(AbstractClientPlayerEntity player, Hand hand, ItemStack stack) {
        if (failed || player == null || hand == null || stack == null || stack.isEmpty()) return null;
        SwingConfig c = ConfigManager.get();
        if (!c.enabled) return null;
        if (!matches(c, stack)) return null;
        if (!isPlainHold(player, hand, stack)) return null;
        return hand == Hand.MAIN_HAND ? c.mainHand : c.offHand;
    }

    /** Играет ли для этой руки кастомная анимация (тогда её нельзя сбрасывать ударом). */
    public static boolean keepsAnimation(AbstractClientPlayerEntity player, Hand hand) {
        if (player == null || hand == null) return false;
        return select(player, hand, player.getStackInHand(hand)) != null;
    }

    /** Режим мода активен для предмета в основной руке (для блокировки re-equip). */
    public static boolean activeForMainHand() {
        try {
            AbstractClientPlayerEntity p = MinecraftClient.getInstance().player;
            return keepsAnimation(p, Hand.MAIN_HAND);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean matches(SwingConfig c, ItemStack stack) {
        return switch (c.filter) {
            case ALL -> true;
            case SWORDS -> stack.isIn(ItemTags.SWORDS);
            case CUSTOM -> c.isCustom(Registries.ITEM.getId(stack.getItem()));
        };
    }

    /** Только «простое» удержание: без use-анимации (щит, лук, еда, трезубец...), карт и арбалета. */
    private static boolean isPlainHold(AbstractClientPlayerEntity player, Hand hand, ItemStack stack) {
        if (player.isUsingItem() && player.getActiveHand() == hand) return false;
        if (player.isUsingRiptide()) return false;
        if (stack.isOf(Items.CROSSBOW) || stack.isOf(Items.FILLED_MAP)) return false;
        // Сравнение по имени: не зависит от пакета enum UseAction (он переезжал между версиями).
        return "NONE".equals(stack.getUseAction().name());
    }

    /** Реальный прогресс свинга, либо синтетический в режиме «Тест» при открытом меню. */
    public static float rawProgress(Hand hand, float real) {
        if (previewHand == hand && real <= 0f
                && MinecraftClient.getInstance().currentScreen instanceof SettingsScreen) {
            return (System.currentTimeMillis() % 900L) / 900f;
        }
        return real;
    }

    public static float walkFactor(AbstractClientPlayerEntity player) {
        if (!player.isOnGround()) return 0f;
        Vec3d v = player.getVelocity();
        float speed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
        return Xf.clamp01(speed / 0.25f);
    }

    /** Визуальная оценка крита (как у ванили, без проверки спринта). */
    public static boolean isCrit(AbstractClientPlayerEntity player) {
        return player.fallDistance > 0.0
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasVehicle()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS);
    }

    public static void applyAll(MatrixStack m, Arm arm, HandProfile p, float progress,
                                float time, float walk, boolean crit) {
        float side = Xf.sign(arm);
        if (p.x != 0f || p.y != 0f || p.z != 0f) Xf.translate(m, p.x, p.y, p.z);
        Xf.rotX(m, p.pitch);
        Xf.rotY(m, p.yaw);
        Xf.rotZ(m, p.roll);
        if (p.scale != 1f) Xf.scale(m, p.scale);

        if (walk > 0f) {
            float w = time * 0.28f;
            float sway = (float) Math.sin(w);
            Xf.translate(m, sway * 0.015f * walk, Math.abs((float) Math.cos(w)) * 0.01f * walk, 0f);
            Xf.rotZ(m, side * sway * 2f * walk);
        }

        if (p.mode != SwingMode.VANILLA) {
            p.mode.apply(m, progress, arm, p);
        }

        if (crit && progress > 0f && progress < 1f) {
            float a = Xf.bell(progress);
            Xf.translate(m, 0f, 0.06f * a, -0.10f * a);
            Xf.rotZ(m, side * -40f * a);
            Xf.rotX(m, -15f * a);
        }
    }

    /** Любая ошибка в рендере: один раз логируем и отключаем мод до перезапуска, но игра не падает. */
    public static void fail(Throwable t) {
        if (!failed) {
            failed = true;
            SwingAnimationsClient.LOGGER.error("SwingAnimations: ошибка рендера, мод отключён до перезапуска", t);
        }
    }
}
