package org.jebol.domain.eval;

import java.util.List;
import java.util.Set;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Value;

/**
 * What a native does when it is called, told which refinements were used.
 *
 * <p>Most natives have no refinements and are written against
 * {@link Callable}, which this wraps. The ones that do had been registered
 * once per refinement, so {@code copy/part} was a separate native from
 * {@code copy}. That worked while there were three of them and stopped
 * working at nine: FIND takes {@code /any}, {@code /tail}, {@code /last},
 * {@code /same}, {@code /part}, {@code /skip}, {@code /case},
 * {@code /reverse} and {@code /only}, and a caller may combine them, so one
 * entry per combination is five hundred entries for one native.
 *
 * <p>A user function has always declared its refinements as parameters and
 * been told which arrived. This is the same arrangement for natives.
 */
@FunctionalInterface
public interface RefinedCallable {

    /**
     * Runs the native.
     *
     * @param arguments the gathered arguments, in spec order
     * @param evaluator the evaluator, for natives that evaluate blocks
     * @param context where the call is being evaluated
     * @param refinements the refinements the caller asked for, canonical and
     *     without their slashes. Empty for a plain call.
     * @return the value produced, never null
     */
    Value call(List<Value> arguments, Evaluator evaluator, Context context,
            Set<String> refinements);
}
