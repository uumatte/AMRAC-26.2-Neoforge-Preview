package amrac.platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Event<T> {
    private final List<T> listeners = new CopyOnWriteArrayList<>();

    public void register(T listener) {
        listeners.add(listener);
    }

    public void fire(Consumer<? super T> invoker) {
        for (T listener : listeners) {
            invoker.accept(listener);
        }
    }
}
