package com.swinganimations.mixin;

import com.swinganimations.module.CustomWorldModule;
import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.RemovalsModule;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CustomWorld: подмена времени суток и погоды только для клиентского мира.
 * ServerWorld (встроенный сервер) не затрагивается. require = 0: при другой сигнатуре просто ничего не меняется.
 */
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true, require = 0)
    private void swa$timeOfDay(CallbackInfoReturnable<Long> cir) {
        swa$time(cir);
    }

    @Inject(method = "getLunarTime", at = @At("RETURN"), cancellable = true, require = 0)
    private void swa$lunarTime(CallbackInfoReturnable<Long> cir) {
        swa$time(cir);
    }

    private void swa$time(CallbackInfoReturnable<Long> cir) {
        try {
            CustomWorldModule m = ModuleManager.customWorld;
            if (m == null || !((Object) this instanceof ClientWorld)) return;
            long t = m.forcedTime();
            if (t >= 0L) cir.setReturnValue(t);
        } catch (Throwable ignored) {
            // косметика
        }
    }

    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true, require = 0)
    private void swa$rain(float delta, CallbackInfoReturnable<Float> cir) {
        try {
            CustomWorldModule m = ModuleManager.customWorld;
            RemovalsModule r = ModuleManager.removals;
            boolean off = (m != null && m.clearWeatherOn()) || (r != null && r.rainOff());
            if (off && (Object) this instanceof ClientWorld) cir.setReturnValue(0f);
        } catch (Throwable ignored) {
            // косметика
        }
    }

    @Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true, require = 0)
    private void swa$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        try {
            CustomWorldModule m = ModuleManager.customWorld;
            RemovalsModule r = ModuleManager.removals;
            boolean off = (m != null && m.clearWeatherOn()) || (r != null && r.rainOff());
            if (off && (Object) this instanceof ClientWorld) cir.setReturnValue(0f);
        } catch (Throwable ignored) {
            // косметика
        }
    }
}
