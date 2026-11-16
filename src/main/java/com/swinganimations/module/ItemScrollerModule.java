package com.swinganimations.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * ItemScroller. Всё работает на обычных кликах по слотам (то же, что делает игрок мышью), поэтому
 * сервер видит их как ручные действия, а блокировки LockSlot учитываются.
 *
 * <ul>
 *   <li>Колесо над слотом: вверх переносит 1 предмет в другой инвентарь, вниз подтягивает 1 такой же
 *       из другого инвентаря. С Shift переносится весь стак.</li>
 *   <li>Shift + ЛКМ и проводка по слотам: быстро переносит стаки.</li>
 *   <li>Ctrl + Shift + ЛКМ: переносит все одинаковые предметы из этого инвентаря. Проводка с Ctrl
 *       переносит только предметы того же вида, что и первый слот.</li>
 *   <li>Ctrl + Shift + Q (клавиша выброса): выбрасывает все одинаковые предметы из этого инвентаря.</li>
 * </ul>
 * «Другой инвентарь»: в контейнере (сундук, бочка, шалкер и т.п.) это пара «игрок ↔ контейнер»,
 * в обычном инвентаре пара «хотбар ↔ основной инвентарь».
 */
public final class ItemScrollerModule extends Module {
    private final Setting.Num delay = add(new Setting.Num("delay", "Delay (ms)", 40f, 0f, 300f, 10f));
    private final Setting.Bool scroll = add(new Setting.Bool("scroll", "Mouse wheel move", true));
    private final Setting.Bool invert = add(new Setting.Bool("invert_scroll", "Invert wheel", false).<Setting.Bool>visibleWhen(() -> this.scroll.value));
    private final Setting.Bool massMove = add(new Setting.Bool("mass_move", "Ctrl+Shift+Click: move all same", true));
    private final Setting.Bool dropMatching = add(new Setting.Bool("drop_matching", "Ctrl+Shift+Q: drop all same", true));

    /** Состояние текущего перетаскивания (используется миксином HandledScreen). */
    public Slot lastSlot;
    public long lastTime;
    /** Вид предмета первого слота при проводке с Ctrl. */
    public Item dragItem;
    private long scrollTime;

    public ItemScrollerModule() {
        super("item_scroller", "ItemScroller", "player");
    }

    public long delayMs() { return (long) delay.value; }

    public boolean massMoveEnabled() { return massMove.value; }

    public boolean dropMatchingEnabled() { return dropMatching.value; }

    @Override
    public void onDisable() {
        lastSlot = null;
        dragItem = null;
    }

    // ---------------- группы слотов ----------------

    private static boolean hasContainer(ScreenHandler h) {
        if (h instanceof PlayerScreenHandler) return false;
        for (Slot s : h.slots) if (!(s.inventory instanceof PlayerInventory)) return true;
        return false;
    }

    /** 0 = игрок (или хотбар), 1 = основной инвентарь (без контейнера), 2 = контейнер, -1 = не трогаем. */
    private static int side(Slot s, boolean hc) {
        if (!(s.inventory instanceof PlayerInventory)) return hc ? 2 : -1;
        int i = s.getIndex();
        if (i >= 36) return -1; // броня и вторая рука
        return hc ? 0 : (i < 9 ? 0 : 1);
    }

    private static int other(int side, boolean hc) {
        if (side < 0) return -1;
        if (hc) return side == 2 ? 0 : 2;
        return side == 0 ? 1 : 0;
    }

    private static boolean locked(Slot s) {
        LockSlotModule l = ModuleManager.lockSlot;
        return l != null && s.inventory instanceof PlayerInventory && l.isLocked(s.getIndex());
    }

    private static void click(ScreenHandler h, Slot s, int button, SlotActionType type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(h.syncId, s.id, button, type, mc.player);
    }

    // ---------------- колесо ----------------

    /** true: событие обработано (колесо не должно листать ничего другого). */
    public boolean scroll(HandledScreen<?> screen, Slot hovered, double amount) {
        if (!scroll.value || amount == 0.0 || !hovered.hasStack()) return false;
        ScreenHandler h = screen.getScreenHandler();
        boolean hc = hasContainer(h);
        int sd = side(hovered, hc);
        if (sd < 0) return false;
        long now = System.currentTimeMillis();
        if (now - scrollTime < 25L) return true;
        scrollTime = now;

        boolean up = (amount > 0.0) != invert.value;
        boolean shift = Screen.hasShiftDown();
        ItemStack st = hovered.getStack();
        if (up) {
            if (locked(hovered)) return true;
            if (shift) {
                click(h, hovered, 0, SlotActionType.QUICK_MOVE);
                return true;
            }
            Slot to = findTarget(h, other(sd, hc), hc, st, hovered);
            if (to != null) moveOne(h, hovered, to);
        } else {
            Slot from = findSource(h, other(sd, hc), hc, st);
            if (from == null) return true;
            if (shift) {
                click(h, from, 0, SlotActionType.QUICK_MOVE);
                return true;
            }
            Slot to = findTarget(h, sd, hc, st, from);
            if (to != null) moveOne(h, from, to);
        }
        return true;
    }

    /** Слот, куда можно положить предмет: сначала недобитый стак такого же вида, потом пустой. */
    private static Slot findTarget(ScreenHandler h, int side, boolean hc, ItemStack st, Slot not) {
        if (side < 0) return null;
        Slot empty = null;
        for (Slot s : h.slots) {
            if (s == not || side(s, hc) != side || locked(s) || !s.canInsert(st)) continue;
            if (s.hasStack()) {
                ItemStack cur = s.getStack();
                if (ItemStack.areItemsAndComponentsEqual(cur, st) && cur.getCount() < cur.getMaxCount()) return s;
            } else if (empty == null) {
                empty = s;
            }
        }
        return empty;
    }

    private static Slot findSource(ScreenHandler h, int side, boolean hc, ItemStack st) {
        if (side < 0) return null;
        for (Slot s : h.slots) {
            if (side(s, hc) != side || locked(s) || !s.hasStack()) continue;
            if (ItemStack.areItemsAndComponentsEqual(s.getStack(), st)) return s;
        }
        return null;
    }

    /** Перенос одного предмета: взять стак, ПКМ в цель (кладёт один), вернуть остаток. */
    private static void moveOne(ScreenHandler h, Slot from, Slot to) {
        click(h, from, 0, SlotActionType.PICKUP);
        if (h.getCursorStack().isEmpty()) return;
        click(h, to, 1, SlotActionType.PICKUP);
        if (!h.getCursorStack().isEmpty()) click(h, from, 0, SlotActionType.PICKUP);
    }

    // ---------------- массовые действия ----------------

    /** Ctrl + Shift + ЛКМ: перенести все предметы такого же вида из этого инвентаря. */
    public boolean moveAllMatching(HandledScreen<?> screen, Slot hovered) {
        if (!massMove.value || !hovered.hasStack()) return false;
        ScreenHandler h = screen.getScreenHandler();
        boolean hc = hasContainer(h);
        int sd = side(hovered, hc);
        if (sd < 0) return false;
        ItemStack proto = hovered.getStack().copy();
        for (Slot s : h.slots) {
            if (side(s, hc) != sd || locked(s) || !s.hasStack()) continue;
            if (ItemStack.areItemsAndComponentsEqual(s.getStack(), proto)) click(h, s, 0, SlotActionType.QUICK_MOVE);
        }
        return true;
    }

    /** Ctrl + Shift + Q: выбросить все предметы такого же вида из этого инвентаря. */
    public boolean dropAllMatching(HandledScreen<?> screen, Slot hovered) {
        if (!dropMatching.value || !hovered.hasStack()) return false;
        ScreenHandler h = screen.getScreenHandler();
        boolean hc = hasContainer(h);
        int sd = side(hovered, hc);
        if (sd < 0) return false;
        ItemStack proto = hovered.getStack().copy();
        for (Slot s : h.slots) {
            if (side(s, hc) != sd || locked(s) || !s.hasStack()) continue;
            if (ItemStack.areItemsAndComponentsEqual(s.getStack(), proto)) click(h, s, 1, SlotActionType.THROW);
        }
        return true;
    }
}
