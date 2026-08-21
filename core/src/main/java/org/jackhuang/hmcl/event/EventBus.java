package org.jackhuang.hmcl.event;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Android 兼容版本：用 ConcurrentHashMap 替代不可用的 java.lang.ClassValue。
 *
 * @author huangyuhui
 */
public final class EventBus {

    public static final EventBus EVENT_BUS = new EventBus();

    private final ConcurrentHashMap<Class<?>, EventManager<?>> cache = new ConcurrentHashMap<>();

    private EventBus() {
    }

    protected EventManager<?> computeValue(@NotNull Class<?> type) {
        return new EventManager<>();
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> EventManager<T> channel(Class<T> clazz) {
        return (EventManager<T>) cache.computeIfAbsent(clazz, this::computeValue);
    }

    @SuppressWarnings("unchecked")
    public Event.Result fireEvent(Event obj) {
        LOG.info(obj + " gets fired");

        return ((EventManager<Event>) cache.computeIfAbsent(obj.getClass(), this::computeValue)).fireEvent(obj);
    }
}
