package com.swinganimations.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.swinganimations.SwingAnimationsClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ChatScreen;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.ActionResult;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final List<Module> MODULES = new ArrayList<>();
    public static SeeInvisiblesModule seeInvisibles;
    public static ThemeModule theme;
    public static LockSlotModule lockSlot;
    public static ItemScrollerModule itemScroller;
    public static NoJumpDelayModule noJumpDelay;
    public static PerformanceModule performance;
    public static ItemPhysicsModule itemPhysics;
    public static CrosshairModule crosshair;
    public static CustomWorldModule customWorld;
    public static FreeLookModule freeLook;
    public static BlockOutlineModule blockOutline;
    public static RemovalsModule removals;
    public static KillEffectModule killEffect;
    public static EntityEspModule entityEsp;
    public static GlowHandModule glowHand;
    public static EventHudModule eventHud;
    public static MusicModule music;
    public static ClientLinkModule clientLink;
    private static final Map<String, Integer> RENDER_ERRORS = new LinkedHashMap<>();
    private static final int MAX_RENDER_ERRORS = 5;

    private ModuleManager() {}

    /** Сбрасывает счётчик ошибок рендера модуля (при повторном включении). */
    static void clearRenderErrors(String id) { RENDER_ERRORS.remove(id); }

    /** Сохранённое состояние одного модуля. */
    private static final class Saved {
        boolean enabled;
        int bind = -1;
        boolean mouse;
        Map<String, String> values = new LinkedHashMap<>();
    }

    public static void init() {
        performance = new PerformanceModule();
        MODULES.add(performance);
        MODULES.add(new TargetEspModule());
        entityEsp = new EntityEspModule();
        MODULES.add(entityEsp);
        glowHand = new GlowHandModule();
        MODULES.add(glowHand);
        eventHud = new EventHudModule();
        MODULES.add(eventHud);
        music = new MusicModule();
        MODULES.add(music);
        clientLink = new ClientLinkModule();
        MODULES.add(clientLink);
        com.swinganimations.link.ClientLink.init();
        MODULES.add(new TrailsModule());
        MODULES.add(new JumpCircleModule());
        itemPhysics = new ItemPhysicsModule();
        MODULES.add(itemPhysics);
        crosshair = new CrosshairModule();
        MODULES.add(crosshair);
        MODULES.add(new ParticlesModule());
        blockOutline = new BlockOutlineModule();
        MODULES.add(blockOutline);
        removals = new RemovalsModule();
        MODULES.add(removals);
        MODULES.add(new PainDicationModule());
        killEffect = new KillEffectModule();
        MODULES.add(killEffect);
        MODULES.add(new PredictionModule());
        customWorld = new CustomWorldModule();
        MODULES.add(customWorld);
        MODULES.add(new HeartShowModule());
        seeInvisibles = new SeeInvisiblesModule();
        MODULES.add(seeInvisibles);
        MODULES.add(new InterfaceModule());
        MODULES.add(new TargetHudModule());
        MODULES.add(new CooldownsModule());
        MODULES.add(new NotificationsModule());
        MODULES.add(new EquipmentWarningsModule());
        MODULES.add(new TntTimerModule());
        theme = new ThemeModule();
        MODULES.add(theme);
        MODULES.add(new AutoSprintModule());
        freeLook = new FreeLookModule();
        MODULES.add(freeLook);
        noJumpDelay = new NoJumpDelayModule();
        MODULES.add(noJumpDelay);
        lockSlot = new LockSlotModule();
        MODULES.add(lockSlot);
        itemScroller = new ItemScrollerModule();
        MODULES.add(itemScroller);
        MODULES.add(new SwingModule());
        com.swinganimations.gui.HudLayout.load();
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            com.swinganimations.gui.HudLayout.begin();
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, td) -> com.swinganimations.gui.HudLayout.onChatRender(ctx, mx, my));
            ScreenEvents.remove(screen).register(s -> com.swinganimations.gui.HudLayout.end());
        });
        load();
        ClientTickEvents.END_CLIENT_TICK.register(ModuleManager::tick);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> renderHud(ctx, tickCounter.getTickProgress(false)));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) com.swinganimations.event.EventBus.post(new com.swinganimations.event.AttackEvent(entity));
            return ActionResult.PASS;
        });
    }

    private static void tick(MinecraftClient mc) {
        boolean noScreen = mc.currentScreen == null;
        long handle = mc.getWindow() != null ? mc.getWindow().getHandle() : 0L;
        for (Module m : MODULES) {
            if (m.bindCode >= 0 && handle != 0L) {
                boolean down = m.bindMouse
                        ? GLFW.glfwGetMouseButton(handle, m.bindCode) == GLFW.GLFW_PRESS
                        : GLFW.glfwGetKey(handle, m.bindCode) == GLFW.GLFW_PRESS;
                if (m.holdBind()) {
                    if (down && !m.bindDown && noScreen) m.setEnabled(true);
                    else if (!down && m.bindDown) m.setEnabled(false);
                } else if (down && !m.bindDown && noScreen) {
                    m.toggle();
                    save();
                }
                m.bindDown = down;
            }
            if (mc.player != null && mc.world != null && m.isEnabled()) {
                try {
                    m.tick(mc);
                } catch (Throwable t) {
                    SwingAnimationsClient.LOGGER.warn("{}: tick error {}", m.id, t.toString());
                }
            }
        }
    }

    private static void renderHud(net.minecraft.client.gui.DrawContext ctx, float tickDelta) {
        ThemeModule.sync();
        com.swinganimations.gui.HudLayout.nextFrame();
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.renderHud(ctx, tickDelta);
            } catch (Throwable t) {
                SwingAnimationsClient.LOGGER.warn("{}: ошибка HUD {}", m.id, t.toString());
            }
        }
    }

    /** Вызывается из WorldRendererMixin в конце рендера мира. */
    public static void renderWorld(Matrix4f positionMatrix, Matrix4f projectionMatrix, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        boolean anyEnabled = false;
        boolean anyWorld = false;
        for (Module m : MODULES) {
            if (!m.isEnabled() || "swing".equals(m.id)) continue;
            anyEnabled = true;
            if (m.wantsWorldRender()) {
                anyWorld = true;
                break;
            }
        }
        if (!anyEnabled) return;
        com.swinganimations.render.Projection.capture(positionMatrix, projectionMatrix, camera.getCameraPos());
        // Нечего рисовать в мире: не создаём MatrixStack/RenderContext (HUD-модулям нужна только проекция).
        if (!anyWorld) return;
        try {
            MatrixStack ms = new MatrixStack();
            ms.multiplyPositionMatrix(positionMatrix);
            ms.translate(-camera.getCameraPos().x, -camera.getCameraPos().y, -camera.getCameraPos().z);
            RenderContext ctx = new RenderContext(ms, camera, tickDelta, System.currentTimeMillis(), mc);
            for (Module m : MODULES) {
                if (!m.isEnabled() || !m.wantsWorldRender()) continue;
                if (RENDER_ERRORS.getOrDefault(m.id, 0) >= MAX_RENDER_ERRORS) continue;
                try {
                    m.render(ctx);
                } catch (Throwable t) {
                    // Ошибка одного модуля не должна гасить остальные: сбрасываем состояние рендера и идём дальше.
                    com.swinganimations.render.Batch.end();
                    int n = RENDER_ERRORS.merge(m.id, 1, Integer::sum);
                    SwingAnimationsClient.LOGGER.error("{}: ошибка мирового рендера ({}/{})", m.id, n, MAX_RENDER_ERRORS, t);
                }
            }
        } catch (Throwable t) {
            SwingAnimationsClient.LOGGER.error("Ошибка подготовки мирового рендера", t);
        }
    }

    // ---------------- сохранение ----------------

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("swinganimations_modules.json");
    }

    public static void save() {
        try {
            Map<String, Saved> out = new LinkedHashMap<>();
            for (Module m : MODULES) {
                Saved s = new Saved();
                s.enabled = m.isEnabled();
                s.bind = m.bindCode;
                s.mouse = m.bindMouse;
                for (Setting st : m.settings) s.values.put(st.id, st.serialize());
                out.put(m.id, s);
            }
            Path p = path();
            Path tmp = p.resolveSibling("swinganimations_modules.json.tmp");
            Files.createDirectories(p.getParent());
            Files.writeString(tmp, GSON.toJson(out), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            SwingAnimationsClient.LOGGER.warn("Не удалось сохранить модули: {}", e.toString());
        }
    }

    private static void load() {
        Path p = path();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Saved>>() { }.getType();
            Map<String, Saved> in = GSON.fromJson(r, type);
            if (in == null) return;
            for (Module m : MODULES) {
                Saved s = in.get(m.id);
                if (s == null) continue;
                m.bindCode = s.bind;
                m.bindMouse = s.mouse;
                if (s.values != null) {
                    for (Setting st : m.settings) {
                        String v = s.values.get(st.id);
                        if (v != null) st.deserialize(v);
                    }
                }
                if (!"swing".equals(m.id) && s.enabled && m.restoreOnLoad()) m.setEnabled(true);
            }
        } catch (Exception e) {
            SwingAnimationsClient.LOGGER.warn("Повреждён swinganimations_modules.json ({}), использую значения по умолчанию", e.toString());
            try {
                Files.move(p, p.resolveSibling("swinganimations_modules.json.broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // не критично
            }
        }
    }
}
