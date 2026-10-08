package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.SchemeActorNative;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class SetSchemeNative extends DefaultNative {

    private static final Set<String> SCHEMES_SERVED_NATIVELY = Set.of(
            "console", "tcp", "dns", "event", "checksum", "file", "dir", "crypt",
            "clipboard", "udp", "midi", "system", "callback", "bundled");

    private static final int WHERE_THE_STANDARD_SCHEME_KEEPS_ITS_NAME = 0;

    private static final int WHERE_THE_STANDARD_SCHEME_KEEPS_ITS_ACTOR = 4;

    @Override
    public String nativeName() {
        return "set-scheme";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("scheme", Set.of(Datatype.OBJECT)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            List<ContextSlot> fields = ((ObjectValue) arguments.getFirst()).context().slots().stream()
                    .filter(slot -> !slot.canonical().equals("self"))
                    .toList();
            if (fields.size() <= WHERE_THE_STANDARD_SCHEME_KEEPS_ITS_ACTOR
                    || !(fields.get(WHERE_THE_STANDARD_SCHEME_KEEPS_ITS_NAME).value() instanceof WordValue named)
                    || named.datatype() != Datatype.WORD
                    || !SCHEMES_SERVED_NATIVELY.contains(named.canonical())) {
                return NoneValue.none();
            }
            fields.get(WHERE_THE_STANDARD_SCHEME_KEEPS_ITS_ACTOR)
                    .setBeneathAnyProtection(theNativeActorFor(named));
            return LogicValue.of(true);
        };
    }

    private Value theNativeActorFor(WordValue named) {
        return new SchemeActorNative(named.canonical());
    }
}
