package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SetSchemeNative extends DefaultNative {

    private static final Set<String> SCHEMES_SERVED_NATIVELY = Set.of(
            "console", "tcp", "dns", "event", "checksum", "file", "dir", "crypt",
            "clipboard", "udp", "midi", "system", "callback", "bundled");

    private static final int WHERE_THE_STANDARD_SCHEME_KEEPS_ITS_NAME = 0;

    private static final int WHERE_THE_STANDARD_SCHEME_KEEPS_ITS_ACTOR = 4;

    private static final String THE_ARGUMENT_NOTHING_CAN_FILL = "internal";

    @Override
    public String name() {
        return "set-scheme";
    }

    @Override
    public List<Parameter> parameters() {
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
        return new NativeValue(
                "the " + named.canonical() + " actor",
                List.of(Parameter.required(THE_ARGUMENT_NOTHING_CAN_FILL, Set.of(Datatype.END))),
                Set.of(),
                Set.of(),
                Optional.of(BlockValue.block(List.of(
                        NoneValue.none(), WordValue.of(THE_ARGUMENT_NOTHING_CAN_FILL)))));
    }
}
