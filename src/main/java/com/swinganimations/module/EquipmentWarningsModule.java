package com.swinganimations.module;

import com.swinganimations.event.EventBus;
import com.swinganimations.event.NotificationEvent;
import com.swinganimations.gui.Hud;
import com.swinganimations.gui.HudLayout;
import com.swinganimations.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Предупреждение о низкой прочности брони (и элитр): надпись по центру и одноразовое уведомление. */
public final class EquipmentWarningsModule extends Module {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Setting.Num threshold = add(new Setting.Num("threshold", "Warn below (%)", 25f, 5f, 75f, 5f));
    private final Setting.Bool notify = add(new Setting.Bool("notify", "Notification", true));
    private final Setting.Bool onScreen = add(new Setting.Bool("on_screen", "Warning on screen", true));

    private final HudLayout.Win win = HudLayout.win("equipment_warnings");
    private final boolean[] warned = new boolean[4];
    private final List<ItemStack> low = new ArrayList<>();
    private final List<Integer> lowPct = new ArrayList<>();

    public EquipmentWarningsModule() {
        super("equipment_warnings", "EquipmentWarnings", "hud");
    }

    @Override
    public void onDisable() {
        java.util.Arrays.fill(warned, false);
        low.clear();
        lowPct.clear();
    }

    private static int percent(ItemStack st) {
        if (st.isEmpty() || !st.isDamageable() || st.getMaxDamage() <= 0) return 100;
        return (int) Math.floor((st.getMaxDamage() - st.getDamage()) * 100f / st.getMaxDamage());
    }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        low.clear();
        lowPct.clear();
        for (int i = 0; i < ARMOR.length; i++) {
            ItemStack st = p.getEquippedStack(ARMOR[i]);
            int pct = percent(st);
            boolean isLow = pct <= threshold.value;
            if (isLow) {
                low.add(st);
                lowPct.add(pct);
            }
            if (isLow && !warned[i]) {
                warned[i] = true;
                if (notify.value) {
                    EventBus.post(new NotificationEvent(st.getName().getString(), pct + "% durability", 0xFFB347));
                }
            } else if (!isLow) {
                warned[i] = false;
            }
        }
    }

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        if (!onScreen.value) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        List<ItemStack> items = low;
        List<Integer> pcts = lowPct;
        if (items.isEmpty()) {
            if (!HudLayout.editing()) return;
            items = List.of(new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.NETHERITE_HELMET));
            pcts = List.of(18, 9);
        }
        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 220.0);
        int col = ((int) (pulse * 255f) << 24) | 0xFF6B6B;
        int rowH = 18;
        int w = 128;
        int h = items.size() * rowH + 6;
        int x = (ctx.getScaledWindowWidth() - w) / 2;
        int y = ctx.getScaledWindowHeight() / 2 - 70 - h;
        HudLayout.push(ctx, win);
        Hud.panel(ctx, x, y, w, h);
        for (int i = 0; i < items.size(); i++) {
            ItemStack st = items.get(i);
            int ry = y + 3 + i * rowH;
            ctx.drawItem(st, x + 5, ry);
            String name = Ui.tr().trimToWidth(st.getName().getString(), w - 60);
            Hud.text(ctx, name, x + 25, ry + 5, Ui.TEXT);
            Hud.textRight(ctx, pcts.get(i) + "%", x + w - 6, ry + 5, col);
        }
        HudLayout.pop(ctx, win, x, y, w, h);
    }
}
