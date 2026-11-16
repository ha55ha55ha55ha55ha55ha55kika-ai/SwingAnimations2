package com.swinganimations.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Невидимые игроки рисуются полупрозрачными, а не пропадают. Чисто визуально: меняется только
 * то, как клиент рисует модель, сервер и хитбоксы не затрагиваются.
 */
public final class SeeInvisiblesModule extends Module {
    private final Setting.Num opacity = add(new Setting.Num("opacity", "Opacity", 0.7f, 0.15f, 1f, 0.05f));

    public SeeInvisiblesModule() {
        super("see_invisibles", "SeeInvisibles", "render");
    }

    public boolean shouldShow(PlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return isEnabled() && p != null && p != mc.player && p.isInvisible();
    }

    /** ARGB-цвет для модели: белый с выбранной прозрачностью. */
    public int ghostColor() {
        return (Math.round(opacity.value * 255f) << 24) | 0xFFFFFF;
    }
}
