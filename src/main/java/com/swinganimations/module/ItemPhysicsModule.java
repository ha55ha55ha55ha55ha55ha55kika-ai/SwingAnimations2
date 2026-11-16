package com.swinganimations.module;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.Map;

/**
 * ItemPhysics: выброшенные предметы не парят и не крутятся на месте, а падают кувыркаясь и затем
 * лежат на земле. Плоские предметы (инструменты, еда, ресурсы) ложатся плашмя со случайным
 * поворотом, блоки остаются блоками. В воде и лаве предметы покачиваются на поверхности.
 * Только клиент: хитбокс и физика сервера не меняются, меняется лишь то, как предмет нарисован.
 *
 * Как устроено: миксин ItemEntityRenderer подменяет два вызова в render(): сдвиг вверх (ваниль
 * «парит» с синусоидой) и вращение вокруг Y. Вместо них применяется наша матрица, а анимация
 * каждого предмета (угол кувырка, плавная укладка) хранится по id сущности.
 */
public final class ItemPhysicsModule extends Module {
    /** Состояние анимации одного предмета. */
    public static final class Anim {
        public float rot;      // угол кувырка вокруг X, градусы
        public float yaw;      // случайный поворот лежащего предмета, радианы
        public float bob;      // ванильное «парение» (его мы вычитаем)
        public float wave;     // покачивание на воде
        public float settle;   // 0 = в воздухе, 1 = лежит
        public float dir = 1f;
        public boolean block;
        public boolean skip;   // рисовать как в ванили
        public boolean applied;
        long last;
    }

    /** Предмет, который рисуется прямо сейчас (выставляет миксин). */
    public static Anim CURRENT;

    private static final float REST_HEIGHT = 0.03f;

    private final Setting.Num speed = add(new Setting.Num("speed", "Rotation speed", 1f, 0.2f, 3f, 0.1f));
    private final Setting.Bool blocks = add(new Setting.Bool("blocks", "Affect block items", true));
    private final Setting.Bool liquids = add(new Setting.Bool("liquids", "Float in liquids", true));
    private final Setting.Bool randomYaw = add(new Setting.Bool("random_yaw", "Random orientation", true));

    private final Map<Integer, Anim> anims = new HashMap<>();
    private int calls;

    public ItemPhysicsModule() {
        super("item_physics", "ItemPhysics", "render");
    }

    @Override
    public void onDisable() {
        anims.clear();
        CURRENT = null;
    }

    /** Вызывается из updateRenderState: считает анимацию предмета на этот кадр. */
    public Anim capture(ItemEntity e, float td) {
        Anim a = anims.get(e.getId());
        boolean fresh = a == null;
        if (fresh) {
            a = new Anim();
            a.dir = (e.getId() & 1) == 0 ? 1f : -1f;
            anims.put(e.getId(), a);
        }
        long now = System.nanoTime();
        float dt = a.last == 0L ? 0f : Math.min(0.1f, (now - a.last) / 1.0e9f);
        a.last = now;
        if ((++calls & 1023) == 0) anims.values().removeIf(x -> now - x.last > 5_000_000_000L);

        a.block = e.getStack().getItem() instanceof BlockItem;
        a.skip = a.block && !blocks.value;
        boolean liquid = liquids.value && (e.isTouchingWater() || e.isInLava());
        boolean ground = e.isOnGround() || liquid;

        a.bob = MathHelper.sin((e.getItemAge() + td) / 10.0f + e.uniqueOffset) * 0.1f + 0.1f;
        a.yaw = randomYaw.value ? (float) (((e.getId() * 2654435761L) & 0xFFFFL) / 65535.0 * Math.PI * 2.0) : 0f;
        a.wave = liquid ? MathHelper.sin(now / 1.0e9f * 2.2f + e.getId()) * 0.025f : 0f;

        float flat = a.block ? 0f : -90f;
        float target = flat + 360f * Math.round((a.rot - flat) / 360f);
        if (fresh && ground) a.rot = target;
        if (ground) {
            a.rot += (target - a.rot) * Math.min(1f, dt * 14f);
            a.settle = 1f - MathHelper.clamp(Math.abs(target - a.rot) / 60f, 0f, 1f);
        } else {
            a.rot += a.dir * 360f * speed.value * dt * 0.9f;
            a.settle = 0f;
        }
        return a;
    }

    /** Подмена ванильного translate(0, bob + h, 0). y = bob + h: полуприподнятость модели над землёй. */
    public static boolean translate(MatrixStack ms, float y) {
        Anim a = CURRENT;
        if (a == null || a.skip) return false;
        a.applied = true;
        float h = y - a.bob;
        float height;
        if (a.block) {
            height = h + a.wave;
        } else {
            float rest = REST_HEIGHT + a.wave;
            height = h + (rest - h) * a.settle;
        }
        ms.translate(0f, height, 0f);
        ms.multiply(RotationAxis.POSITIVE_Y.rotation(a.yaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(a.rot));
        return true;
    }

    /** Ванильное вращение вокруг Y отбрасываем, если наша матрица уже применена. */
    public static boolean skipRotation() {
        Anim a = CURRENT;
        return a != null && !a.skip && a.applied;
    }
}
