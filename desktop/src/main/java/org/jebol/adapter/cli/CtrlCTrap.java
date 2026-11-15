package org.jebol.adapter.cli;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class CtrlCTrap {

    private static final String SIGNAL = "sun.misc.Signal";
    private static final String HANDLER = "sun.misc.SignalHandler";
    private static final String INTERRUPT = "INT";

    private CtrlCTrap() {
    }

    static boolean onInterrupt(Runnable escape) {
        try {
            Class<?> signal = Class.forName(SIGNAL);
            Class<?> handler = Class.forName(HANDLER);
            Object interrupt = signal.getConstructor(String.class).newInstance(INTERRUPT);
            Object handling = Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[] {handler},
                    (proxy, method, arguments) -> answerFor(proxy, method, arguments, escape));
            signal.getMethod("handle", signal, handler).invoke(null, interrupt, handling);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException unavailable) {
            return false;
        }
    }

    private static Object answerFor(Object proxy, Method method, Object[] arguments, Runnable escape)
            throws InvocationTargetException {
        return switch (method.getName()) {
            case "handle" -> {
                escape.run();
                yield null;
            }
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "the command line's Ctrl-C";
            default -> throw new InvocationTargetException(
                    new UnsupportedOperationException(method.getName()));
        };
    }
}
