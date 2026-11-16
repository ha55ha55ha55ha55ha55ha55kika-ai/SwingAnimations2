package com.swinganimations.util;

import com.swinganimations.SwingAnimationsClient;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Смена ника на время сессии (оффлайн-режим): создаёт новую Session и подставляет её в MinecraftClient.
 * Всё через рефлексию по типам, без обращений к именам из маппингов, поэтому сборку и запуск это не ломает.
 * Для лицензионных серверов (онлайн-режим) не подходит: подпись и проверка сессии останутся старыми.
 */
public final class NickChanger {
    private NickChanger() {}

    /** Возвращает null при успехе или короткий текст ошибки. */
    public static String apply(String name) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Object old = mc.getSession();
            Class<?> sc = old.getClass();
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));

            Constructor<?> ctor = null;
            for (Constructor<?> c : sc.getDeclaredConstructors()) {
                Class<?>[] t = c.getParameterTypes();
                if (t.length >= 3 && t[0] == String.class && t[1] == UUID.class) {
                    ctor = c;
                    break;
                }
            }
            if (ctor == null) return "unsupported";

            Class<?>[] pt = ctor.getParameterTypes();
            Object[] args = new Object[pt.length];
            int strings = 0;
            for (int i = 0; i < pt.length; i++) {
                Class<?> t = pt[i];
                if (t == String.class) {
                    args[i] = strings++ == 0 ? name : "0";
                } else if (t == UUID.class) {
                    args[i] = uuid;
                } else if (t == Optional.class) {
                    args[i] = Optional.empty();
                } else if (t.isEnum()) {
                    Object[] consts = t.getEnumConstants();
                    Object pick = consts[0];
                    for (Object o : consts) if ("LEGACY".equals(((Enum<?>) o).name())) pick = o;
                    args[i] = pick;
                } else if (t == boolean.class) {
                    args[i] = false;
                }
            }
            ctor.setAccessible(true);
            Object fresh = ctor.newInstance(args);

            Field target = null;
            for (Field f : MinecraftClient.class.getDeclaredFields()) {
                if (f.getType() == sc && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    target = f;
                    break;
                }
            }
            if (target == null) return "unsupported";
            target.setAccessible(true);
            target.set(mc, fresh);
            return null;
        } catch (Throwable t) {
            SwingAnimationsClient.LOGGER.warn("Смена ника не удалась: {}", t.toString());
            return t.getClass().getSimpleName();
        }
    }
}
