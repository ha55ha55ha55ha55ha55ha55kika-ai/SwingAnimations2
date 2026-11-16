package com.swinganimations.mixin;

import com.swinganimations.input.KeyBinds;
import com.swinganimations.module.ItemScrollerModule;
import com.swinganimations.module.LockSlotModule;
import com.swinganimations.module.ModuleManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LockSlot (блокировка слотов) и ItemScroller (shift-проводка по слотам). Все хуки с require = 0 и try/catch. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected Slot focusedSlot;

    @Shadow protected abstract Slot getSlotAt(double x, double y);

    @Shadow protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$lock(Slot slot, int slotId, int button, SlotActionType type, CallbackInfo ci) {
        try {
            LockSlotModule m = ModuleManager.lockSlot;
            if (m == null || !m.isEnabled() || type == SlotActionType.CLONE) return;
            if (type == SlotActionType.SWAP && (button >= 0 && button < 9 || button == 40) && m.isLocked(button)) {
                ci.cancel();
                return;
            }
            if (slot != null && slot.inventory instanceof PlayerInventory && m.isLocked(slot.getIndex())) ci.cancel();
        } catch (Throwable ignored) {
            // косметика/удобство: при сбое просто не блокируем
        }
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyInput;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$lockKey(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        try {
            LockSlotModule m = ModuleManager.lockSlot;
            if (m == null || !m.isEnabled() || KeyBinds.LOCK == null || !KeyBinds.LOCK.matchesKey(input)) return;
            Slot s = focusedSlot;
            if (s != null && s.inventory instanceof PlayerInventory) {
                m.toggleLock(s.getIndex());
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
            // не критично
        }
    }

    @Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V",
            at = @At("TAIL"), require = 0)
    private void swa$drawLock(DrawContext ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        try {
            LockSlotModule m = ModuleManager.lockSlot;
            if (m == null || !m.isEnabled() || !(slot.inventory instanceof PlayerInventory) || !m.isLocked(slot.getIndex())) return;
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x55FF3030);
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, 0xCCFF5050);
            ctx.fill(slot.x, slot.y + 15, slot.x + 16, slot.y + 16, 0xCCFF5050);
            ctx.fill(slot.x, slot.y, slot.x + 1, slot.y + 16, 0xCCFF5050);
            ctx.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 16, 0xCCFF5050);
        } catch (Throwable ignored) {
            // не критично
        }
    }

    @Inject(method = "mouseDragged(Lnet/minecraft/client/gui/Click;DD)Z", at = @At("HEAD"), require = 0)
    private void swa$scroll(Click click, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        try {
            double mx = click.x(), my = click.y();
            int button = click.button();
            ItemScrollerModule m = ModuleManager.itemScroller;
            if (m == null || !m.isEnabled() || button != 0 || !Screen.hasShiftDown()) return;
            Slot s = getSlotAt(mx, my);
            if (s == null || !s.hasStack() || s == m.lastSlot) return;
            if (Screen.hasControlDown()) {
                if (m.dragItem == null) m.dragItem = s.getStack().getItem();
                else if (s.getStack().getItem() != m.dragItem) return;
            }
            long now = System.currentTimeMillis();
            if (now - m.lastTime < m.delayMs()) return;
            m.lastSlot = s;
            m.lastTime = now;
            onMouseClick(s, s.id, 0, SlotActionType.QUICK_MOVE);
        } catch (Throwable ignored) {
            // не критично
        }
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/gui/Click;)Z", at = @At("HEAD"), require = 0)
    private void swa$scrollEnd(Click click, CallbackInfoReturnable<Boolean> cir) {
        ItemScrollerModule m = ModuleManager.itemScroller;
        if (m != null) {
            m.lastSlot = null;
            m.dragItem = null;
        }
    }

    /** ItemScroller: колесо над слотом переносит предметы между инвентарями. */
    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$itemScroll(double mx, double my, double h, double v, CallbackInfoReturnable<Boolean> cir) {
        try {
            ItemScrollerModule m = ModuleManager.itemScroller;
            if (m == null || !m.isEnabled() || (Object) this instanceof CreativeInventoryScreen) return;
            Slot s = focusedSlot;
            if (s != null && m.scroll((HandledScreen<?>) (Object) this, s, v)) cir.setReturnValue(true);
        } catch (Throwable ignored) {
            // удобство: при сбое колесо работает как в ванили
        }
    }

    /** ItemScroller: Ctrl + Shift + ЛКМ переносит все одинаковые предметы. */
    @Inject(method = "mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$massMove(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        try {
            double mx = click.x(), my = click.y();
            int button = click.button();
            ItemScrollerModule m = ModuleManager.itemScroller;
            if (m == null || !m.isEnabled() || button != 0 || (Object) this instanceof CreativeInventoryScreen) return;
            if (!Screen.hasControlDown() || !Screen.hasShiftDown()) return;
            Slot s = getSlotAt(mx, my);
            if (s != null && m.moveAllMatching((HandledScreen<?>) (Object) this, s)) {
                m.lastSlot = s;
                m.lastTime = System.currentTimeMillis();
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
            // не критично
        }
    }

    /** ItemScroller: Ctrl + Shift + Q выбрасывает все одинаковые предметы. */
    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyInput;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void swa$dropMatching(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        try {
            ItemScrollerModule m = ModuleManager.itemScroller;
            if (m == null || !m.isEnabled() || (Object) this instanceof CreativeInventoryScreen) return;
            if (!Screen.hasControlDown() || !Screen.hasShiftDown()) return;
            if (!MinecraftClient.getInstance().options.dropKey.matchesKey(input)) return;
            Slot s = focusedSlot;
            if (s != null && m.dropAllMatching((HandledScreen<?>) (Object) this, s)) cir.setReturnValue(true);
        } catch (Throwable ignored) {
            // не критично
        }
    }
}
