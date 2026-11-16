package com.swinganimations.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.swinganimations.animation.SwingLogic;
import com.swinganimations.animation.SwingMode;
import com.swinganimations.config.HandProfile;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 1) @WrapMethod на renderFirstPersonItem: выбор профиля, подмена swingProgress, push/pop.
 * 2) @WrapOperation на вызов applySwingOffset. В 1.21.4 он вызывается не напрямую из
 *    renderFirstPersonItem, а из вспомогательного метода, поэтому ищем вызов по ВСЕМ методам класса
 *    (swingArm + renderFirstPersonItem). require = 0: если вызов не найден, игра НЕ крашится, мод сам отключается.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Unique private HandProfile swa$profile;
    @Unique private float swa$progress;
    @Unique private float swa$time;
    @Unique private float swa$walk;
    @Unique private boolean swa$crit;
    @Unique private boolean swa$applied;

    @WrapMethod(method = "renderFirstPersonItem")
    private void swa$renderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                                           Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                           MatrixStack matrices, OrderedRenderCommandQueue commandQueue, int light,
                                           Operation<Void> original) {
        float passed = swingProgress;
        swa$profile = null;
        swa$applied = false;
        try {
            HandProfile profile = SwingLogic.select(player, hand, item);
            if (profile != null) {
                float raw = SwingLogic.rawProgress(hand, swingProgress);
                float shaped = profile.shape(raw);
                swa$progress = shaped;
                swa$time = player.age + tickDelta;
                swa$walk = profile.walkSway ? SwingLogic.walkFactor(player) : 0f;
                swa$crit = profile.critAnimation && SwingLogic.isCrit(player);
                passed = profile.mode == SwingMode.VANILLA ? shaped : 0f;
                swa$profile = profile;
            }
        } catch (Throwable t) {
            SwingLogic.fail(t);
            swa$profile = null;
            passed = swingProgress;
        }

        HandProfile active = swa$profile;
        matrices.push();
        try {
            original.call(player, tickDelta, pitch, hand, passed, item, equipProgress,
                    matrices, commandQueue, light);
        } finally {
            matrices.pop();
            swa$profile = null;
        }

        if (active != null && !swa$applied) {
            SwingLogic.fail(new IllegalStateException(
                    "applySwingOffset hook did not fire in this Minecraft version; mod disabled"));
        }
    }

    @WrapOperation(
            method = {"swingArm", "renderFirstPersonItem"},
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"),
            require = 0)
    private void swa$applySwingOffset(HeldItemRenderer instance, MatrixStack matrices, Arm arm,
                                      float progress, Operation<Void> original) {
        HandProfile p = swa$profile;
        if (p == null) {
            original.call(instance, matrices, arm, progress);
            return;
        }
        swa$applied = true;
        try {
            SwingLogic.applyAll(matrices, arm, p, swa$progress, swa$time, swa$walk, swa$crit);
        } catch (Throwable t) {
            SwingLogic.fail(t);
        }
        if (p.mode == SwingMode.VANILLA) {
            original.call(instance, matrices, arm, progress);
        }
    }

    /** После удара кулдаун обнуляется и ваниль опускает/переподнимает предмет. Для кастомной анимации отключаем. */
    @WrapOperation(
            method = "updateHeldItems",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getAttackCooldownProgress(F)F"),
            require = 0)
    private float swa$noCooldownDip(net.minecraft.client.network.ClientPlayerEntity player, float baseTime,
                                    Operation<Float> original) {
        if (SwingLogic.activeForMainHand()) return 1.0f;
        return original.call(player, baseTime);
    }

    /** Потеря прочности при ударе меняет стек -> ваниль запускает re-equip. Считаем стеки равными, если предмет тот же. */
    @WrapOperation(
            method = "updateHeldItems",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;areEqual(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z"),
            require = 0)
    private boolean swa$sameItemNoReequip(ItemStack a, ItemStack b, Operation<Boolean> original) {
        boolean r = original.call(a, b);
        if (!r && SwingLogic.activeForMainHand() && a != null && b != null && ItemStack.areItemsEqual(a, b)) return true;
        return r;
    }
}
