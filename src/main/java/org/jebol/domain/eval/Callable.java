package org.jebol.domain.eval;

import org.jebol.domain.value.Context;
import org.jebol.domain.value.Value;

import java.util.List;

/**
 * What a native does when it is called.
 *
 * <p>Kept apart from {@code NativeValue} so the value model stays data: a
 * value knows its interface, and the registry knows its behaviour.
 */
@FunctionalInterface
public interface Callable {

    /**
     * Runs the native.
     *
     * @param arguments the gathered arguments, in spec order
     * @param evaluator the evaluator, for natives such as IF and DO that
     *     evaluate blocks of their own
     * @param context where the call is being evaluated. FUNC closes over it,
     *     MAKE OBJECT! hangs the new object beneath it, and anything
     *     evaluating a block passes it on.
     * @return the value produced, never null; use {@code UnsetValue.unset()}
     *     for a native that returns nothing
     */
    Value call(List<Value> arguments, Evaluator evaluator, Context context);
}
