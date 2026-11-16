package com.swinganimations.mixin;

import com.swinganimations.module.LockSlotModule;
import com.swinganimations.module.ModuleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LockSlot: предмет в заблокированном слоте хотбара нельзя выбросить клавишей Q. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$noDropLocked(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        try {
            LockSlotModule m = ModuleManager.lockSlot;
            ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
            if (m != null && m.isLocked(p.getInventory().selectedSlot)) cir.setReturnValue(false);
        } catch (Throwable ignored) {
            // не критично
        }
    }
}
