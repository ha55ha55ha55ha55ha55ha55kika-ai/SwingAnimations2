package com.swinganimations.module;

import com.swinganimations.gui.Hud;
import com.swinganimations.gui.HudLayout;
import com.swinganimations.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Список предметов, у которых идёт перезарядка (жемчуг, хорус, щит, яблоки и т.д.). Справа сверху.
 * Инвентарь сканируется раз в 2 тика, в кадре только считается прогресс у уже найденных предметов.
 */
public final class CooldownsModule extends Module {
    private static final class Entry {
        ItemStack stack;
        float progress;
    }

    private final Setting.Num maxRows = add(new Setting.Num("rows", "Max rows", 6f, 2f, 10f, 1f));

    private final HudLayout.Win win = HudLayout.win("cooldowns");
    private final List<ItemStack> active = new ArrayList<>();
    private final List<Entry> rows = new ArrayList<>();
    private int timer;

    public CooldownsModule() {
        super("cooldowns", "Cooldowns", "hud");
    }

    @Override
    public void onDisable() {
        active.clear();
        rows.clear();
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (++timer < 2) return;
        timer = 0;
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        ItemCooldownManager cm = p.getItemCooldownManager();
        PlayerInventory inv = p.getInventory();
        active.clear();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            Item item = st.getItem();
            boolean dup = false;
            for (int k = 0; k < active.size(); k++) {
                if (active.get(k).getItem() == item) {
                    dup = true;
                    break;
                }
            }
            if (dup) continue;
            if (cm.getCooldownProgress(st, 0f) > 0f) active.add(st);
        }
    }

    @Override
    public void renderHud(DrawContext ctx, float tickDelta) {
        boolean edit = HudLayout.editing();
        if (active.isEmpty() && !edit) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden) return;
        ItemCooldownManager cm = p.getItemCooldownManager();
        rows.clear();
        for (int i = 0; i < active.size(); i++) {
            ItemStack st = active.get(i);
            float pr = cm.getCooldownProgress(st, tickDelta);
            if (pr <= 0f) continue;
            Entry e = new Entry();
            e.stack = st;
            e.progress = pr;
            rows.add(e);
        }
        if (rows.isEmpty()) {
            if (!edit) return;
            rows.add(sample(Items.ENDER_PEARL, 0.65f));
            rows.add(sample(Items.GOLDEN_APPLE, 0.30f));
        }
        rows.sort((a, b) -> Float.compare(b.progress, a.progress));
        int n = Math.min(rows.size(), (int) maxRows.value);

        int w = 104;
        int rowH = 16;
        int h = 16 + n * rowH + 2;
        int x = ctx.getScaledWindowWidth() - w - 8;
        int y = 8;
        HudLayout.push(ctx, win);
        Hud.panel(ctx, x, y, w, h);
        Hud.text(ctx, "Cooldowns", x + 6, y + 5, Ui.ACCENT);
        ctx.fill(x + 5, y + 15, x + w - 5, y + 16, Ui.SEP);
        org.joml.Matrix3x2fStack m = ctx.getMatrices();
        for (int i = 0; i < n; i++) {
            Entry e = rows.get(i);
            int ry = y + 18 + i * rowH;
            m.pushMatrix();
            m.translate(x + 6, ry);
            m.scale(0.75f, 0.75f);
            ctx.drawItem(e.stack, 0, 0);
            m.popMatrix();
            String name = Ui.tr().trimToWidth(e.stack.getName().getString(), w - 52);
            Hud.text(ctx, name, x + 22, ry + 1, Ui.TEXT);
            Hud.textRight(ctx, (int) Math.ceil(e.progress * 100f) + "%", x + w - 6, ry + 1, Ui.TEXT_DIM);
            Hud.bar(ctx, x + 22, ry + 11, w - 28, 2, e.progress, Ui.ACCENT);
        }
        HudLayout.pop(ctx, win, x, y, w, h);
    }

    private static Entry sample(net.minecraft.item.Item item, float progress) {
        Entry e = new Entry();
        e.stack = new ItemStack(item);
        e.progress = progress;
        return e;
    }
}
