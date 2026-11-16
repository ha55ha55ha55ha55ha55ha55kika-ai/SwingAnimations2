package com.swinganimations.animation;

import com.swinganimations.config.HandProfile;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;

/**
 * Один режим удара. Вызывается ПОСЛЕ ванильного equip-offset, внутри уже существующего push/pop
 * ванильного renderFirstPersonItem. progress: 0..1 (уже пропущен через скорость и кривую).
 * На progress == 0 и progress == 1 режим обязан давать единичную трансформацию.
 */
public interface SwingAnimation {
    void apply(MatrixStack matrices, float progress, Arm arm, HandProfile settings);
}
