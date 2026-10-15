package org.jebol.domain.eval;

import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

public final class Postbox {

    public interface CallbackAnswer {

        void answered(Value value);

        void failed(RuntimeException failure);
    }

    public record PortEvent(PortValue port, String eventType) {
    }

    public record Callback(ObjectValue target, String word, List<Value> arguments, CallbackAnswer answer) {
    }

    private final BlockingQueue<PortEvent> events = new LinkedBlockingDeque<>();

    private final BlockingQueue<Callback> callbacks = new LinkedBlockingDeque<>();

    public void post(PortValue port, String eventType) {
        events.add(new PortEvent(port, eventType));
    }

    public void callBack(ObjectValue target, String word, List<Value> arguments, CallbackAnswer answer) {
        callbacks.add(new Callback(target, word, List.copyOf(arguments), answer));
    }

    void deliverEachEvent(Consumer<PortEvent> delivering) {
        PortEvent next;
        while ((next = events.poll()) != null) {
            delivering.accept(next);
        }
    }

    void deliverEachCallback(Consumer<Callback> delivering) {
        Callback next;
        while ((next = callbacks.poll()) != null) {
            delivering.accept(next);
        }
    }
}
