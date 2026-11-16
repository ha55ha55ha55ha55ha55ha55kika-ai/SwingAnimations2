package com.swinganimations.mixin;

import com.swinganimations.gui.MainMenuScreen;
import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.RemovalsModule;
import com.swinganimations.module.ThemeModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removals: тени от блоков (ambient occlusion). Действует при следующей перестройке чанков (модуль делает её сам).
 * Главное меню: ванильный TitleScreen заменяется на MainMenuScreen (если включено в Theme). require = 0: при другой сигнатуре
 * просто остаётся ванильное меню.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "isAmbientOcclusionEnabled()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void swa$noAo(CallbackInfoReturnable<Boolean> cir) {
        RemovalsModule r = ModuleManager.removals;
        if (r != null && r.aoOff()) cir.setReturnValue(false);
    }

    @ModifyVariable(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen swa$mainMenu(Screen screen) {
        try {
            if (screen != null && screen.getClass() == TitleScreen.class && !MainMenuScreen.failed && ThemeModule.mainMenu()) {
                return new MainMenuScreen();
            }
        } catch (Throwable t) {
            MainMenuScreen.failed = true;
        }
        return screen;
    }
}
