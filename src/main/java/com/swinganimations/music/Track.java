package com.swinganimations.music;

/** Трек из поиска. streamUrl: прямая ссылка на MP3-поток (для Deezer это 30-секундный превью-клип). */
public record Track(String id, String title, String artist, int durationSec, String streamUrl, String source) {}
