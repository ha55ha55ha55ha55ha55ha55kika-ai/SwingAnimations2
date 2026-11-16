package com.swinganimations.gui;

import net.minecraft.client.input.KeyInput;
import com.swinganimations.animation.Curve;
import com.swinganimations.animation.SwingLogic;
import com.swinganimations.animation.SwingMode;
import com.swinganimations.config.ConfigManager;
import com.swinganimations.config.HandProfile;
import com.swinganimations.config.ItemFilter;
import com.swinganimations.config.Presets;
import com.swinganimations.config.SwingConfig;
import com.swinganimations.input.KeyBinds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Меню в стиле тёмного ClickGUI (без логотипа). Слева, чтобы справа внизу оставалась видна рука:
 * кнопка «Тест свинга» зацикливает свинг в реальном first-person рендере (фон не блюрится).
 */
public class SettingsScreen extends Screen {
    private enum Page { GENERAL, MAIN, OFF, FINE, PRESETS }

    private static final int PANEL_L = 6;
    private static final int PANEL_R = 324;
    private static final int LX = 14;
    private static final int RX = 170;
    private static final int W = 148;
    private static final int STEP = 24;

    private final Screen parent;
    private final SwingConfig cfg = ConfigManager.get();
    private Page page = Page.GENERAL;
    private Hand fineHand = Hand.MAIN_HAND;

    public SettingsScreen(Screen parent) {
        super(Text.translatable("swinganimations.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            Page p = pages[i];
            addDrawableChild(new UiButton(12 + i * 61, 8, 59, 18, UiButton.Kind.TAB,
                    () -> Text.translatable("swinganimations.tab." + p.name().toLowerCase(Locale.ROOT)),
                    null, () -> p == page, () -> switchPage(p)));
        }
        switch (page) {
            case GENERAL -> buildGeneral();
            case MAIN -> buildProfile(Hand.MAIN_HAND, cfg.mainHand);
            case OFF -> buildProfile(Hand.OFF_HAND, cfg.offHand);
            case FINE -> buildFine();
            case PRESETS -> buildPresets();
        }
        addDrawableChild(new UiButton(RX, height - 26, W, 18, UiButton.Kind.PLAIN,
                () -> Text.translatable("gui.done"), null, null, this::close));
    }

    private void switchPage(Page p) {
        page = p;
        SwingLogic.previewHand = null;
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        init();
    }

    // ---------- pages ----------

    private void buildGeneral() {
        int y = 36;
        addDrawableChild(toggle(LX, y, 304, "swinganimations.ui.enabled", () -> cfg.enabled, v -> cfg.enabled = v));
        y += STEP;
        addDrawableChild(cycle(LX, y, 304, "swinganimations.ui.filter", "swinganimations.filter.",
                ItemFilter.values(), () -> cfg.filter, v -> cfg.filter = v));
        y += STEP + 14;

        TextFieldWidget field = new TextFieldWidget(textRenderer, LX, y, 304, 18, Text.empty());
        field.setMaxLength(2048);
        field.setPlaceholder(Text.translatable("swinganimations.option.custom_hint"));
        field.setText(String.join(", ", cfg.customItems));
        field.setChangedListener(s -> {
            List<String> list = new ArrayList<>();
            for (String part : s.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) list.add(t);
            }
            cfg.customItems = list;
            cfg.rebuildCaches();
        });
        addDrawableChild(field);
    }

    private void buildProfile(Hand hand, HandProfile hp) {
        int y = 36;
        addDrawableChild(cycle(LX, y, W, "swinganimations.ui.mode", "swinganimations.mode.",
                SwingMode.values(), () -> hp.mode, v -> hp.mode = v));
        y += STEP;
        slider(LX, y, "swinganimations.ui.speed", 0.3f, 3f, hp.speed, "%.2fx", v -> hp.speed = v);
        y += STEP;
        addDrawableChild(cycle(LX, y, W, "swinganimations.ui.curve", "swinganimations.curve.",
                Curve.values(), () -> hp.curve, v -> hp.curve = v));
        y += STEP;
        slider(LX, y, "swinganimations.ui.strength", 0f, 1f, hp.curveStrength, "%.2f", v -> hp.curveStrength = v);
        y += STEP;
        slider(LX, y, "swinganimations.ui.x", -1f, 1f, hp.x, "%.2f", v -> hp.x = v);
        y += STEP;
        slider(LX, y, "swinganimations.ui.y", -1f, 1f, hp.y, "%.2f", v -> hp.y = v);
        y += STEP;
        slider(LX, y, "swinganimations.ui.z", -1f, 1f, hp.z, "%.2f", v -> hp.z = v);
        y += STEP;
        slider(LX, y, "swinganimations.ui.intensity", 0.1f, 3f, hp.intensity, "%.2f", v -> hp.intensity = v);

        y = 36;
        slider(RX, y, "swinganimations.ui.pitch", -180f, 180f, hp.pitch, "%.0f\u00B0", v -> hp.pitch = v);
        y += STEP;
        slider(RX, y, "swinganimations.ui.yaw", -180f, 180f, hp.yaw, "%.0f\u00B0", v -> hp.yaw = v);
        y += STEP;
        slider(RX, y, "swinganimations.ui.roll", -180f, 180f, hp.roll, "%.0f\u00B0", v -> hp.roll = v);
        y += STEP;
        slider(RX, y, "swinganimations.ui.scale", 0.3f, 1.5f, hp.scale, "%.2f", v -> hp.scale = v);
        y += STEP;
        addDrawableChild(toggle(RX, y, W, "swinganimations.ui.walk", () -> hp.walkSway, v -> hp.walkSway = v));
        y += STEP;
        addDrawableChild(toggle(RX, y, W, "swinganimations.ui.crit", () -> hp.critAnimation, v -> hp.critAnimation = v));
        y += STEP;
        addDrawableChild(toggle(RX, y, W, "swinganimations.ui.test", () -> SwingLogic.previewHand == hand,
                v -> SwingLogic.previewHand = v ? hand : null));
    }

    private void buildFine() {
        HandProfile hp = fineHand == Hand.MAIN_HAND ? cfg.mainHand : cfg.offHand;
        Hand hand = fineHand;
        addDrawableChild(new UiButton(LX, 36, 304, 20, UiButton.Kind.CHIP,
                () -> Text.translatable("swinganimations.ui.hand"),
                () -> Text.translatable(hand == Hand.MAIN_HAND ? "swinganimations.hand.main" : "swinganimations.hand.off"),
                null, () -> {
            SwingLogic.previewHand = null;
            fineHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
            rebuild();
        }));
        int y = 64;
        slider(LX, y, "swinganimations.ui.amplitude", 0.1f, 1.5f, hp.amplitude, "%.2f", v -> hp.amplitude = v);
        slider(RX, y, "swinganimations.ui.ease_in", 0f, 1f, hp.easeIn, "%.2f", v -> hp.easeIn = v);
        y += STEP;
        slider(LX, y, "swinganimations.ui.windup", 0f, 1f, hp.windup, "%.2f", v -> hp.windup = v);
        slider(RX, y, "swinganimations.ui.ease_out", 0f, 1f, hp.easeOut, "%.2f", v -> hp.easeOut = v);
        y += STEP;
        slider(LX, y, "swinganimations.ui.hold", 0f, 1f, hp.hold, "%.2f", v -> hp.hold = v);
        slider(RX, y, "swinganimations.ui.sway", 0f, 1f, hp.sway, "%.2f", v -> hp.sway = v);
        y += STEP;
        slider(LX, y, "swinganimations.ui.thrust", 0f, 1f, hp.thrust, "%.2f", v -> hp.thrust = v);
        addDrawableChild(toggle(RX, y, W, "swinganimations.ui.test", () -> SwingLogic.previewHand == hand,
                v -> SwingLogic.previewHand = v ? hand : null));
    }

    private void buildPresets() {
        List<String> names = Presets.names(cfg);
        for (int i = 0; i < names.size() && i < 16; i++) {
            String n = names.get(i);
            boolean active = n.equals(cfg.activePreset);
            addDrawableChild(new UiButton(LX + (i % 2) * 156, 36 + (i / 2) * STEP, W, 20, UiButton.Kind.CHIP,
                    () -> Text.literal(n), () -> active ? Text.literal("\u25CF") : Text.empty(), null, () -> {
                Presets.apply(cfg, n);
                ConfigManager.save();
                rebuild();
            }));
        }
        addDrawableChild(new UiButton(LX, height - 26, W, 18, UiButton.Kind.PLAIN,
                () -> Text.translatable("swinganimations.preset.save"), null, null, () -> {
            Presets.saveCurrent(cfg);
            ConfigManager.save();
            rebuild();
        }));
    }

    // ---------- widget helpers ----------

    private void slider(int x, int y, String labelKey, float min, float max, float cur, String fmt, ValueSlider.Setter s) {
        addDrawableChild(new ValueSlider(x, y, W, 22, Text.translatable(labelKey), min, max, cur, fmt, s));
    }

    private UiButton toggle(int x, int y, int w, String labelKey, BooleanSupplier get, Consumer<Boolean> set) {
        return new UiButton(x, y, w, 20, UiButton.Kind.TOGGLE, () -> Text.translatable(labelKey), null, get,
                () -> set.accept(!get.getAsBoolean()));
    }

    private <E extends Enum<E>> UiButton cycle(int x, int y, int w, String labelKey, String prefix,
                                               E[] values, Supplier<E> get, Consumer<E> set) {
        return new UiButton(x, y, w, 20, UiButton.Kind.CHIP, () -> Text.translatable(labelKey),
                () -> Text.translatable(prefix + get.get().name().toLowerCase(Locale.ROOT)), null,
                () -> set.accept(values[(get.get().ordinal() + 1) % values.length]));
    }

    // ---------- screen plumbing ----------

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Намеренно пусто: без блюра/затемнения мир и рука видны позади меню (нужно для «Теста»).
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int pw = PANEL_R - PANEL_L, ph = height - 6;
        Ui.rect(ctx, PANEL_L, 3, pw, ph, 6, Ui.BG);
        Ui.outline(ctx, PANEL_L, 3, pw, ph, 6, Ui.OUTLINE);
        ctx.fill(PANEL_L + 6, 29, PANEL_R - 6, 30, Ui.SEP);
        if (page == Page.MAIN || page == Page.OFF) {
            ctx.fill(164, 36, 165, height - 32, Ui.SEP);
        }
        super.render(ctx, mouseX, mouseY, delta);
        if (page == Page.FINE) {
            Ui.text(ctx, Text.translatable("swinganimations.ui.fine_hint"), LX + 2, 166, Ui.TEXT_DIM);
        }
        if (page == Page.GENERAL) {
            Ui.text(ctx, Text.translatable("swinganimations.option.custom"), LX + 2, 94, Ui.TEXT_DIM);
            Ui.text(ctx, Text.translatable("swinganimations.hint.1"), LX + 2, 140, Ui.TEXT_DIM);
            Ui.text(ctx, Text.translatable("swinganimations.hint.2"), LX + 2, 152, Ui.TEXT_DIM);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (KeyBinds.OPEN != null && KeyBinds.OPEN.matchesKey(keyCode, scanCode)
                && !(getFocused() instanceof TextFieldWidget)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        SwingLogic.previewHand = null;
        ConfigManager.save();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
