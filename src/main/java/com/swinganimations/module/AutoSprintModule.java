package com.swinganimations.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Автоспринт: пока зажата «вперёд», удерживает клавишу спринта нажатой. Все ограничения ванили
 * (голод, слепота, использование предмета, приседание) остаются, потому что решение о спринте
 * принимает сама игра, а не мод.
 */
public final class AutoSprintModule extends Module {
    public AutoSprintModule() {
        super("auto_sprint", "AutoSprint", "movement");
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.currentScreen != null) return;
        if (mc.options.forwardKey.isPressed()) mc.options.sprintKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        // вернуть клавишу в реальное физическое состояние
        KeyBinding.updatePressedStates();
    }
}
