package com.swinganimations.module;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Блокировка слотов инвентаря: заблокированный слот нельзя переложить, выбросить или обменять.
 * Слот блокируется клавишей «Lock slot» (по умолчанию L) при наведении на него в инвентаре.
 * Индексы как в PlayerInventory: 0–8 хотбар, 9–35 основной, 36–39 броня, 40 вторая рука.
 */
public final class LockSlotModule extends Module {
    private final Setting.Str locked = add(new Setting.Str("locked", "Locked slots", ""));
    private String cacheSrc = null;
    private Set<Integer> cache = new LinkedHashSet<>();

    public LockSlotModule() {
        super("lock_slot", "LockSlot", "player");
    }

    private Set<Integer> set() {
        if (!locked.value.equals(cacheSrc)) {
            cacheSrc = locked.value;
            cache = new LinkedHashSet<>();
            for (String p : locked.value.split(",")) {
                try {
                    if (!p.isBlank()) cache.add(Integer.parseInt(p.trim()));
                } catch (NumberFormatException ignored) { }
            }
        }
        return cache;
    }

    public boolean isLocked(int invIndex) {
        return isEnabled() && set().contains(invIndex);
    }

    public void toggleLock(int invIndex) {
        Set<Integer> s = new LinkedHashSet<>(set());
        if (!s.remove(invIndex)) s.add(invIndex);
        StringBuilder sb = new StringBuilder();
        for (int i : s) {
            if (sb.length() > 0) sb.append(',');
            sb.append(i);
        }
        locked.value = sb.toString();
        ModuleManager.save();
    }
}
