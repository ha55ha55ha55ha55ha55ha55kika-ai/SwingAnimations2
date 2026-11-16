package com.swinganimations.event;

import com.swinganimations.SwingAnimationsClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Лёгкая шина событий: миксины и колбэки Fabric только публикуют событие, а модули сами на него
 * подписываются. Ошибка одного подписчика не мешает остальным.
 */
public final class EventBus {
    private static final Map<Class<?>, List<Consumer<Object>>> HANDLERS = new HashMap<>();

    private EventBus() {}

    @SuppressWarnings("unchecked")
    public static <T> void on(Class<T> type, Consumer<? super T> handler) {
        HANDLERS.computeIfAbsent(type, k -> new ArrayList<>()).add(o -> handler.accept((T) o));
    }

    public static void post(Object event) {
        List<Consumer<Object>> list = HANDLERS.get(event.getClass());
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            try {
                list.get(i).accept(event);
            } catch (Throwable t) {
                SwingAnimationsClient.LOGGER.warn("Ошибка обработчика {}: {}", event.getClass().getSimpleName(), t.toString());
            }
        }
    }
}
