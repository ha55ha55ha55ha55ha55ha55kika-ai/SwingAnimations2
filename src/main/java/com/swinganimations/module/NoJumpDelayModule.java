package com.swinganimations.module;

/** Убирает паузу между прыжками при зажатом пробеле (логику сама игра, мод только обнуляет таймер на клиенте). */
public final class NoJumpDelayModule extends Module {
    public NoJumpDelayModule() {
        super("no_jump_delay", "NoJumpDelay", "movement");
    }
}
