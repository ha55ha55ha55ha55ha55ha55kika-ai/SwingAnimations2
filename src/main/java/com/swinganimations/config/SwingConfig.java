package com.swinganimations.config;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SwingConfig {
    public boolean enabled = true;
    public ItemFilter filter = ItemFilter.SWORDS;
    public List<String> customItems = new ArrayList<>();
    public HandProfile mainHand = new HandProfile();
    public HandProfile offHand = new HandProfile();
    public String activePreset = "Smooth";
    public Map<String, PresetData> customPresets = new LinkedHashMap<>();

    /** Кэш для быстрой проверки в рендере; в JSON не пишется. */
    private transient Set<Identifier> customSet = Set.of();

    public boolean isCustom(Identifier id) {
        return customSet.contains(id);
    }

    public void rebuildCaches() {
        Set<Identifier> s = new HashSet<>();
        if (customItems != null) {
            for (String str : customItems) {
                if (str == null) continue;
                Identifier id = Identifier.tryParse(str.trim());
                if (id != null) s.add(id);
            }
        }
        customSet = s;
    }

    /** Чинит null и выход за границы после чтения чужого/повреждённого JSON. */
    public void sanitize() {
        if (filter == null) filter = ItemFilter.SWORDS;
        if (customItems == null) customItems = new ArrayList<>();
        if (mainHand == null) mainHand = new HandProfile();
        if (offHand == null) offHand = new HandProfile();
        if (activePreset == null) activePreset = "";
        if (customPresets == null) customPresets = new LinkedHashMap<>();
        mainHand.sanitize();
        offHand.sanitize();
        customPresets.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null
                || e.getValue().main == null || e.getValue().off == null);
        for (PresetData d : customPresets.values()) {
            d.main.sanitize();
            d.off.sanitize();
        }
        rebuildCaches();
    }
}
