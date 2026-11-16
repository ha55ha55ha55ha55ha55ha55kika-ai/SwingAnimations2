package com.swinganimations.module;

import com.swinganimations.SwingAnimationsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

/**
 * FreeLook: камера вращается на 360°, а сам игрок (направление взгляда, движение, удары) не поворачивается.
 * Назначьте бинд в ClickGUI. Режим Hold: активно, пока клавиша зажата. Режим Toggle: вкл/выкл по нажатию.
 * Чисто визуально: на сервер уходит прежний поворот игрока.
 */
public final class FreeLookModule extends Module {
    private final Setting.Mode mode = add(new Setting.Mode("mode", "Mode", "Hold", "Toggle"));
    private final Setting.Bool thirdPerson = add(new Setting.Bool("third_person", "Third person view", true));
    private final Setting.Num sensitivity = add(new Setting.Num("sensitivity", "Sensitivity", 1f, 0.2f, 2f, 0.1f));

    private float yaw;
    private float pitch;
    private Perspective previous;
    private int activeTicks;
    private int rotateCalls;

    public FreeLookModule() {
        super("free_look", "FreeLook", "player");
    }

    @Override public boolean holdBind() { return mode.is("Hold"); }

    @Override public boolean restoreOnLoad() { return false; }

    @Override
    public void onEnable() {
        activeTicks = 0;
        rotateCalls = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
        }
        if (thirdPerson.value && mc.options != null) {
            previous = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (previous != null && mc.options != null) mc.options.setPerspective(previous);
        previous = null;
    }

    /** Активна ли свободная камера прямо сейчас (камера смотрит от лица самого игрока, не наблюдателя). */
    public boolean isActive() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return enabled && mc.player != null && mc.getCameraEntity() == mc.player;
    }

    @Override
    public void tick(MinecraftClient mc) {
        // Диагностика: 2 секунды включён, а мышь ни разу не дошла до хука поворота. Тогда в логе будет эта строка.
        if (++activeTicks == 40 && rotateCalls == 0) {
            SwingAnimationsClient.LOGGER.warn("FreeLook: включён, но движение мыши не доходит до хука Entity.changeLookDirection. "
                    + "Если камера не крутится, пришлите этот лог.");
        }
    }

    /** Движение мыши вместо поворота игрока. Формула та же, что у ванили (0.15 на пиксель). */
    public void rotate(double dx, double dy) {
        rotateCalls++;
        float k = 0.15f * sensitivity.value;
        yaw += (float) dx * k;
        pitch = MathHelper.clamp(pitch + (float) dy * k, -90f, 90f);
    }

    public float cameraYaw() { return yaw; }

    public float cameraPitch() { return pitch; }
}
