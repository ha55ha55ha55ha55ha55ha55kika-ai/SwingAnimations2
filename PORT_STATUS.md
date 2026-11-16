# Порт на 1.21.11: статус (не компилировалось, нужна проверка через CI)

## Сделано
- Сборка: Loom 1.14 (net.fabricmc.fabric-loom-remap), Gradle 9.2.1, yarn 1.21.11+build.4, loader 0.18.4, Fabric API 0.141.1, fabric.mod.json
- render/Batch.java: переписан на RenderPipeline + RenderPass (сигнатуры strip/triangles/v/restart/flush/begin/end сохранены).
  Новое: Batch.beginScreen(additive) для HUD/GUI (ортопроекция, GUI-координаты). Использует PainDicationModule.
- Ввод экранов: Click / KeyInput / CharInput в MusicScreen, EventSearchScreen, SettingsScreen, ClickGuiScreen, MainMenuScreen, HandledScreenMixin.
- KeyBinds: KeyBinding.Category (+ ключ key.category.swinganimations.main в lang).
- DrawContext: Matrix3x2fStack (pushMatrix/popMatrix/translate(x,y)/scale(x,y)) в 10 файлах.
- getTickDelta -> getTickProgress.
- WorldRendererMixin: сигнатура render(...) 1.21.11, хук ModifyVariable с ordinal = 0.
- HeldItemRendererMixin: OrderedRenderCommandQueue вместо VertexConsumerProvider.
- WorldRendererSkyMixin: цель переведена на SkyRendering.renderCelestialBodies (хук без аргументов).
- swinganimations.mixins.json: defaultRequire = 0 (несовпавший хук не роняет игру, фича просто не работает).

- Проверено по javadoc yarn 1.21.11: Camera.getPos() -> getCameraPos(), Camera.update(World, ...), drawTexture(RenderPipelines.GUI_TEXTURED, ...),
  Entity.getWorld() -> getEntityWorld(), опрос клавиш через GLFW.glfwGetKey (вместо InputUtil.isKeyPressed).
- InGameHudMixin.renderCrosshair: сигнатура не изменилась.

## Заглушки / не работает на 1.21.11
- GlowHand: рука портирована на OrderedRenderCommandQueue.submitModelPart (с цветом); свечение предметов в руке отключено. TintProvider удалён.
- Огонь на экране: InGameOverlayRendererMixin переведён на renderFireOverlay(MatrixStack, VertexConsumerProvider, Sprite).
- Возможно не сработают (цели не проверены): GameRendererMixin (tiltViewWhenHurt), InGameOverlayRendererMixin (огонь),
  InGameHudMixin (renderCrosshair), HeartTypeMixin, ItemEntityRendererMixin, LivingEntityRendererMixin (ghost alpha), getSlotAt в HandledScreenMixin.

## Неизвестные имена yarn (угадано, проверить по ошибкам компиляции)
- Framebuffer.getColorAttachmentView()/getDepthAttachmentView(), RenderPipelines.POSITION_COLOR_SNIPPET,
  RenderSystem.restoreProjectionMatrix(), BuiltBuffer.DrawParameters.format()/vertexCount()/indexCount(),
  net.minecraft.client.render.state.CameraRenderState, net.minecraft.client.input.KeyInput/CharInput.
