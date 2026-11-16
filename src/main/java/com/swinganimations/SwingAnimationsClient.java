package com.swinganimations;

import com.swinganimations.config.ConfigManager;
import com.swinganimations.input.KeyBinds;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwingAnimationsClient implements ClientModInitializer {
    public static final String MOD_ID = "swinganimations";
    public static final Logger LOGGER = LoggerFactory.getLogger("SwingAnimations");

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        com.swinganimations.module.ModuleManager.init();
        KeyBinds.register();
        LOGGER.info("SwingAnimations initialized (client-only, visual-only).");
    }
}
