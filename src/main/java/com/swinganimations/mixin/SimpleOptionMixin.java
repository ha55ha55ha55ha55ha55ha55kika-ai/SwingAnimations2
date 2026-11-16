package com.swinganimations.mixin;

import com.swinganimations.module.CustomWorldModule;
import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.RemovalsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CustomWorld: fullbright. Карта освещения читает яркость (gamma) через SimpleOption.getValue,
 * поэтому пока fullbright включён, для неё возвращается максимум. Сохранённая в настройках яркость не меняется.
 */
@Mixin(SimpleOption.class)
public abstract class SimpleOptionMixin {
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, require = 0)
    private void swa$fullbright(CallbackInfoReturnable<Object> cir) {
        CustomWorldModule m = ModuleManager.customWorld;
        if (m == null || !m.fullbrightOn()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.options != null && (Object) this == mc.options.getGamma()) cir.setReturnValue(15.0);
        } catch (Throwable ignored) {
            // косметика
        }
    }

    /**
     * Removals: тени сущностей и искажение (тошнота, портал) читаются через соответствующие опции.
     * Пока модуль включён, игра видит «выключено», а сохранённые в настройках значения не меняются.
     */
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, require = 0)
    private void swa$removalsOptions(CallbackInfoReturnable<Object> cir) {
        RemovalsModule r = ModuleManager.removals;
        if (r == null || !r.isEnabled()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.options == null) return;
            Object self = this;
            if (r.shadowsOff() && self == mc.options.getEntityShadows()) cir.setReturnValue(Boolean.FALSE);
            else if (r.nauseaOff() && self == mc.options.getDistortionEffectScale()) cir.setReturnValue(0.0);
        } catch (Throwable ignored) {
            // косметика
        }
    }
}
