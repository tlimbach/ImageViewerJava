package service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {
    private static final EventBus INSTANCE = new EventBus();

    public static EventBus get() {
        return INSTANCE;
    }

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    private EventBus() {
    }

    /**
     * Einen Listener für einen Event-Typ registrieren
     */
    public <E> void register(Class<E> eventType, Consumer<E> listener) {
        listeners.computeIfAbsent(eventType, t -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Listener wieder abmelden
     */
    public <E> void unregister(Class<E> eventType, Consumer<E> listener) {
        List<Consumer<?>> list = listeners.get(eventType);
        if (list != null) list.remove(listener);
    }

    /**
     * Ein Event veröffentlichen
     */
//    @SuppressWarnings("unchecked")
//    public <E> void publish(E event) {
//        List<Consumer<?>> list = listeners.get(event.getClass());
//        if (list != null) {
//            for (Consumer<?> raw : list) {
//                ((Consumer<E>) raw).accept(event);
//            }
//        }
//    }

    public <E> void publish(E event) {
        List<Consumer<?>> list = listeners.get(event.getClass());
        if (list != null) {
            for (Consumer<?> raw : list) {
                Consumer<E> listener = (Consumer<E>) raw;
                Controller.getInstance().getExecutorService().submit(() -> {
                    try {
                        listener.accept(event);
                    } catch (Exception ex) {
                        System.err.println("Event listener error: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
            }
        }
    }

    /**
     * Nur zu Debug-Zwecken: Wer horcht gerade auf welchen Typ?
     */
    public Map<Class<?>, List<Consumer<?>>> dumpRegistrations() {
        return Collections.unmodifiableMap(listeners);
    }
}