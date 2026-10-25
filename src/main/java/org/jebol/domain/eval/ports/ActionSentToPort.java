package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.DeclaresParameters;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ParameterKind;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One action on its way to the actor of the port it was called on.
 *
 * <p>Every action reaches the actor but MAKE, TO and REFLECT, with the
 * arguments and refinements the caller gave, and an action the actor has no
 * function for is {@code no-port-action}. An actor written in REBOL declares
 * its own parameters, so the arguments are laid out again against them,
 * matching each refinement by name; an actor the host serves answers for
 * itself, and a port with no actor answers none.
 */
public record ActionSentToPort(
        NativeValue action, List<Value> arguments, Set<String> refinements) {

    /**
     * What the actor of the port in front of {@code arguments} answers, or
     * nothing when this call does not reach an actor at all.
     */
    public static Optional<Value> answer(NativeValue action, List<Value> arguments,
            Set<String> refinements, Evaluator evaluator) {

        if (arguments.isEmpty() || !(arguments.getFirst() instanceof PortValue port)
                || !action.reachesPortActor()) {
            return Optional.empty();
        }
        return new ActionSentToPort(action, arguments, refinements)
                .answerFromActor(port, evaluator);
    }

    /**
     * The same, for a call made on a file, url, block or word that named the
     * port instead of being one, which the C answers by putting the port it
     * made in the first argument's place.
     */
    public static Optional<Value> answerOnPort(PortValue port, NativeValue action,
            List<Value> arguments, Set<String> refinements, Evaluator evaluator) {

        List<Value> onThePort = new ArrayList<>(arguments);
        onThePort.set(0, port);
        return answer(action, onThePort, refinements, evaluator);
    }

    /**
     * The arguments as the actor's own function declares them: each of its
     * refinements true or false as the caller asked, and each argument the
     * one this call holds for the same refinement, or none where the caller
     * asked for nothing there.
     */
    public List<Value> layOut(DeclaresParameters actorsFunction) {
        List<Value> laidOut = new ArrayList<>();
        Map<Optional<String>, Integer> reached = new HashMap<>();
        for (Parameter parameter : actorsFunction.parameters()) {
            if (parameter.kind() == ParameterKind.REFINEMENT) {
                laidOut.add(LogicValue.of(refinements.contains(parameter.name())));
                continue;
            }
            int which = reached.merge(parameter.owningRefinement(), 1, Integer::sum) - 1;
            laidOut.add(findArgumentAt(parameter.owningRefinement(), which)
                    .orElseGet(NoneValue::none));
        }
        return laidOut;
    }

    private Optional<Value> findArgumentAt(Optional<String> owningRefinement, int which) {
        if (!isAsked(owningRefinement)) {
            return Optional.empty();
        }
        int at = 0;
        int seenOfThisRefinement = 0;
        for (Parameter parameter : action.parameters()) {
            if (!parameter.consumesArgument() || !isAsked(parameter.owningRefinement())) {
                continue;
            }
            if (parameter.owningRefinement().equals(owningRefinement)
                    && seenOfThisRefinement++ == which) {
                return at < arguments.size() ? Optional.of(arguments.get(at)) : Optional.empty();
            }
            at++;
        }
        return Optional.empty();
    }

    private boolean isAsked(Optional<String> owningRefinement) {
        return owningRefinement.map(refinements::contains).orElse(true);
    }

    private Optional<Value> answerFromActor(PortValue port, Evaluator evaluator) {
        port.requireObjectSpec();
        port.requireNativeOrObjectActor();
        if (port.hasNoActor()) {
            return Optional.of(NoneValue.none());
        }
        Optional<ObjectValue> writtenInRebol = port.actorWrittenInRebol();
        if (writtenInRebol.isPresent()) {
            return Optional.of(evaluator.askActor(writtenInRebol.orElseThrow(), this));
        }
        return port.readFieldValue("actor") instanceof ServesPortActions servedByTheHost
                ? servedByTheHost.answers(action.nativeName(), arguments, refinements, evaluator)
                : Optional.empty();
    }
}
