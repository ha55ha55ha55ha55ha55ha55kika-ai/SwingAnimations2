package com.swinganimations.input;

import com.swinganimations.config.ConfigManager;
import com.swinganimations.config.Presets;
import com.swinganimations.config.SwingConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class KeyBinds {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(net.minecraft.util.Identifier.of("swinganimations", "main"));
    public static KeyBinding OPEN;
    public static KeyBinding TOGGLE;
    public static KeyBinding NEXT_PRESET;
    public static KeyBinding LOCK;
    public static KeyBinding CROSSHAIR_STYLE;
    public static KeyBinding CROSSHAIR_COLOR;
    public static KeyBinding FREE_LOOK;
    public static KeyBinding FRIEND;
    public static KeyBinding EVENT_PAGE;
    public static KeyBinding EVENT_SEARCH;
    public static KeyBinding MUSIC_OPEN;
    public static KeyBinding MUSIC_PAUSE;
    public static KeyBinding MUSIC_NEXT;
    public static KeyBinding MUSIC_PREV;
    private static boolean freeLookDown;

    private KeyBinds() {}

    public static void register() {
        OPEN = reg("open", GLFW.GLFW_KEY_RIGHT_SHIFT);
        TOGGLE = reg("toggle", GLFW.GLFW_KEY_UNKNOWN);       // по умолчанию не назначена, чтобы не конфликтовать
        NEXT_PRESET = reg("next_preset", GLFW.GLFW_KEY_UNKNOWN);
        LOCK = reg("lock_slot", GLFW.GLFW_KEY_L);
        CROSSHAIR_STYLE = reg("crosshair_style", GLFW.GLFW_KEY_UNKNOWN); // назначается игроком в «Управлении»
        CROSSHAIR_COLOR = reg("crosshair_color", GLFW.GLFW_KEY_UNKNOWN);
        FRIEND = reg("friend", GLFW.GLFW_KEY_UNKNOWN);
        EVENT_PAGE = reg("event_page", GLFW.GLFW_KEY_UNKNOWN);
        EVENT_SEARCH = reg("event_search", GLFW.GLFW_KEY_K);
        MUSIC_OPEN = reg("music_open", GLFW.GLFW_KEY_M);
        MUSIC_PAUSE = reg("music_pause", GLFW.GLFW_KEY_UNKNOWN);
        MUSIC_NEXT = reg("music_next", GLFW.GLFW_KEY_UNKNOWN);
        MUSIC_PREV = reg("music_prev", GLFW.GLFW_KEY_UNKNOWN);
        FREE_LOOK = reg("free_look", GLFW.GLFW_KEY_UNKNOWN);  // обычная клавиша управления: работает и на Android (Pojav)
        ClientTickEvents.END_CLIENT_TICK.register(KeyBinds::tick);
    }

    private static KeyBinding reg(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.swinganimations." + id, InputUtil.Type.KEYSYM, key, CATEGORY));
    }

    private static void tick(MinecraftClient mc) {
        while (OPEN.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new com.swinganimations.gui.ClickGuiScreen(null));
        }
        crosshairKeys(mc);
        freeLookKey(mc);
        friendKey(mc);
        while (EVENT_PAGE.wasPressed()) {
            com.swinganimations.module.EventHudModule ev = com.swinganimations.module.ModuleManager.eventHud;
            if (ev != null) ev.nextPage();
        }
        while (EVENT_SEARCH.wasPressed()) {
            com.swinganimations.module.EventHudModule ev = com.swinganimations.module.ModuleManager.eventHud;
            if (ev != null && mc.currentScreen == null) ev.openSearch();
        }
        musicKeys(mc);
        SwingConfig cfg = ConfigManager.get();
        while (TOGGLE.wasPressed()) {
            cfg.enabled = !cfg.enabled;
            ConfigManager.save();
            message(mc, Text.translatable("swinganimations.msg.toggle",
                    Text.translatable(cfg.enabled ? "swinganimations.on" : "swinganimations.off")));
        }
        while (NEXT_PRESET.wasPressed()) {
            Presets.next(cfg);
            ConfigManager.save();
            message(mc, Text.translatable("swinganimations.msg.preset", cfg.activePreset));
        }
    }

    /**
     * FreeLook через обычную клавишу управления (опрос состояния делает сама игра по событиям клавиатуры).
     * Если у модуля есть свой бинд из ClickGUI, им занимается ModuleManager, а здесь только сбрасываем очередь нажатий.
     */
    private static void freeLookKey(MinecraftClient mc) {
        com.swinganimations.module.FreeLookModule fl = com.swinganimations.module.ModuleManager.freeLook;
        boolean down = FREE_LOOK.isPressed();
        if (fl == null || fl.bindCode >= 0) {
            while (FREE_LOOK.wasPressed()) { /* очередь нажатий */ }
            freeLookDown = down;
            return;
        }
        if (fl.holdBind()) {
            while (FREE_LOOK.wasPressed()) { /* очередь нажатий */ }
            if (down && !freeLookDown && mc.currentScreen == null && mc.player != null) fl.setEnabled(true);
            else if (!down && freeLookDown) fl.setEnabled(false);
        } else {
            while (FREE_LOOK.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) fl.toggle();
            }
        }
        freeLookDown = down;
    }

    /** Добавить или убрать друга: игрок в прицеле. */
    private static void friendKey(MinecraftClient mc) {
        while (FRIEND.wasPressed()) {
            com.swinganimations.module.EntityEspModule esp = com.swinganimations.module.ModuleManager.entityEsp;
            if (esp == null || mc.player == null) continue;
            if (mc.targetedEntity instanceof net.minecraft.entity.player.PlayerEntity p) {
                String n = p.getName().getString();
                boolean now = esp.toggleFriend(n);
                message(mc, Text.translatableWithFallback(now ? "swinganimations.msg.friend_added" : "swinganimations.msg.friend_removed",
                        now ? "Friend added: %s" : "Friend removed: %s", n));
            } else {
                message(mc, Text.translatableWithFallback("swinganimations.msg.friend_aim", "Aim at a player"));
            }
        }
    }

    private static void crosshairKeys(MinecraftClient mc) {
        com.swinganimations.module.CrosshairModule c = com.swinganimations.module.ModuleManager.crosshair;
        while (CROSSHAIR_STYLE.wasPressed()) {
            if (c == null) continue;
            if (!c.isEnabled()) c.setEnabled(true);
            int s = c.nextStyle();
            message(mc, Text.literal("Crosshair: " + s + "/" + com.swinganimations.module.CrosshairModule.STYLES));
        }
        while (CROSSHAIR_COLOR.wasPressed()) {
            if (c == null) continue;
            if (!c.isEnabled()) c.setEnabled(true);
            message(mc, Text.literal("Crosshair color: " + c.nextColor()));
        }
    }

    private static void musicKeys(MinecraftClient mc) {
        com.swinganimations.module.MusicModule mm = com.swinganimations.module.ModuleManager.music;
        while (MUSIC_OPEN.wasPressed()) {
            if (mm != null && mc.currentScreen == null) mm.openBrowser();
        }
        while (MUSIC_PAUSE.wasPressed()) {
            com.swinganimations.music.MusicPlayer.togglePause();
            nowPlaying(mc);
        }
        while (MUSIC_NEXT.wasPressed()) {
            com.swinganimations.music.MusicPlayer.next();
            nowPlaying(mc);
        }
        while (MUSIC_PREV.wasPressed()) {
            com.swinganimations.music.MusicPlayer.prev();
            nowPlaying(mc);
        }
    }

    private static void nowPlaying(MinecraftClient mc) {
        com.swinganimations.music.Track t = com.swinganimations.music.MusicPlayer.current();
        if (t != null) message(mc, Text.literal("Music: " + t.title() + (t.artist().isEmpty() ? "" : " - " + t.artist())));
    }

    private static void message(MinecraftClient mc, Text t) {
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(t, false); // локально, пакетов нет
    }
}
