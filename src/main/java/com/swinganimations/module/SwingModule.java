package com.swinganimations.module;

import com.swinganimations.config.ConfigManager;

/** Включатель анимаций удара. Сами настройки открываются кнопкой в панели. */
public final class SwingModule extends Module {
    public SwingModule() {
        super("swing", "SwingAnimations", "animations");
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().enabled;
    }

    @Override
    public void setEnabled(boolean v) {
        ConfigManager.get().enabled = v;
        ConfigManager.save();
    }

    @Override
    public boolean hasExtraButton() {
        return true;
    }
}
