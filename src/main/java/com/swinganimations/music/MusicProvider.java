package com.swinganimations.music;

import java.io.IOException;
import java.util.List;

/** Источник музыки. search() вызывается в фоновом потоке, не в потоке рендера. */
public interface MusicProvider {
    String name();

    /** Подпись ключа (например «client_id») или null, если источнику ключ не нужен. */
    String keyLabel();

    /** true: без ключа поиск невозможен. */
    boolean keyRequired();

    /** Короткая инструкция, где взять ключ (показывается в окне). */
    String keyHelp();

    /** Короткое описание источника под вкладками. */
    String note();

    List<Track> search(String query, String key) throws IOException;
}
