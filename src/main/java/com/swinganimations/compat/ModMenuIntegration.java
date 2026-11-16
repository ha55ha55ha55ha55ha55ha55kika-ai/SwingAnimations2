package com.swinganimations.compat;

import com.swinganimations.gui.ClickGuiScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Загружается только если ModMenu установлен (entrypoint "modmenu" вызывает сам ModMenu). */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClickGuiScreen::new;
    }
}
