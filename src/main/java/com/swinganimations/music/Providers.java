package com.swinganimations.music;

import java.util.List;

public final class Providers {
    public static final List<MusicProvider> ALL = List.of(new DeezerProvider(), new JamendoProvider(), new AudiusProvider());

    private Providers() {}

    public static MusicProvider get(int index) {
        return ALL.get(Math.max(0, Math.min(ALL.size() - 1, index)));
    }
}
