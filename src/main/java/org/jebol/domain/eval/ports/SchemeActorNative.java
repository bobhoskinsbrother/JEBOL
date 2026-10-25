package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The actor of a scheme this build serves itself.
 *
 * <p>It answers nothing: the work is done by the action's own behaviour. What
 * it does is declare which verbs its scheme serves, so that one this scheme
 * does not is refused by name rather than running an ordinary series path on a
 * port. Each list is the {@code case A_} list of that scheme's actor in the C.
 */
public final class SchemeActorNative extends DefaultNative implements ServesPortActions {

    private static final String ARGUMENT_NOTHING_CAN_FILL = "internal";

    private static final Set<String> WHAT_AN_EVENT_QUEUE_SERVES = Set.of(
            "update", "poke", "insert", "append", "pick", "clear", "length?",
            "open", "close");

    private static final Set<String> WHAT_A_TRANSPORT_SERVES = Set.of(
            "update", "read", "write", "pick", "query", "open?", "close",
            "length?", "open", "delete");

    private static final Map<String, Set<String>> WHAT_EACH_SCHEME_SERVES = Map.ofEntries(
            Map.entry("file", Set.of(
                    "read", "write", "append", "open", "copy", "open?", "close",
                    "delete", "rename", "create", "query", "modify", "index?",
                    "indexz?", "length?", "head", "tail", "next", "back", "skip",
                    "at", "atz", "head?", "tail?", "past?", "clear")),
            Map.entry("dir", Set.of(
                    "read", "create", "rename", "delete", "open", "open?", "close",
                    "query", "length?", "tail?")),
            Map.entry("tcp", WHAT_A_TRANSPORT_SERVES),
            Map.entry("udp", WHAT_A_TRANSPORT_SERVES),
            Map.entry("dns", Set.of("read", "pick", "open", "close", "open?", "update")),
            Map.entry("console", Set.of(
                    "read", "write", "update", "open", "close", "open?", "modify",
                    "query", "flush")),
            Map.entry("event", WHAT_AN_EVENT_QUEUE_SERVES),
            Map.entry("system", WHAT_AN_EVENT_QUEUE_SERVES),
            Map.entry("callback", WHAT_AN_EVENT_QUEUE_SERVES),
            Map.entry("checksum", Set.of("write", "read", "update", "open", "close", "open?")),
            Map.entry("crypt", Set.of(
                    "open", "write", "take", "read", "update", "close", "open?", "modify")),
            Map.entry("clipboard", Set.of("update", "read", "write", "open", "close", "open?")),
            Map.entry("bundled", Set.of("read", "open", "close", "open?", "query")));

    private static final Map<String, Set<String>> WHAT_EACH_SCHEME_SERVES_WHILE_CLOSED = Map.of(
            "tcp", Set.of("open", "close", "open?", "update"),
            "udp", Set.of("open", "close", "open?", "update"),
            "crypt", Set.of("open"));

    private static final Map<String, Set<String>> WHAT_EACH_SCHEME_NEEDS_OPEN_FOR = Map.of(
            "dns", Set.of("pick"));

    private final String scheme;

    public SchemeActorNative(String scheme) {
        this.scheme = scheme;
        declare(BlockValue.of(List.of(NoneValue.none(), WordValue.of(ARGUMENT_NOTHING_CAN_FILL))),
                parametersAsWritten());
    }

    @Override
    public String nativeName() {
        return "the " + scheme + " actor";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of(ARGUMENT_NOTHING_CAN_FILL, Set.of(EndValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw Raised.of(EvaluationFailure.CANNOT_USE, nativeName());
        };
    }

    @Override
    public Optional<Value> answers(String action, List<Value> arguments,
            Set<String> refinements, Evaluator evaluator) {

        if (!(arguments.getFirst() instanceof PortValue port)) {
            return Optional.empty();
        }
        refuseWhenClosed(action, port);
        if (!WHAT_EACH_SCHEME_SERVES.getOrDefault(scheme, Set.of()).contains(action)) {
            throw Raised.of(EvaluationFailure.NO_PORT_ACTION, SetWordValue.of(action));
        }
        return Optional.empty();
    }

    private void refuseWhenClosed(String action, PortValue port) {
        if (port.isOpen() || !needsOpenPort(action)) {
            return;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN, port.reference());
    }

    private boolean needsOpenPort(String action) {
        Set<String> servedWhileClosed = WHAT_EACH_SCHEME_SERVES_WHILE_CLOSED.get(scheme);
        return servedWhileClosed == null
                ? WHAT_EACH_SCHEME_NEEDS_OPEN_FOR.getOrDefault(scheme, Set.of()).contains(action)
                : !servedWhileClosed.contains(action);
    }
}
