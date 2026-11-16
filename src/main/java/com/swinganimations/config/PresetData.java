package com.swinganimations.config;

public final class PresetData {
    public HandProfile main = new HandProfile();
    public HandProfile off = new HandProfile();

    public PresetData() {}

    public PresetData(HandProfile main, HandProfile off) {
        this.main = main;
        this.off = off;
    }
}
