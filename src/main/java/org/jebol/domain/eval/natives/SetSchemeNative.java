package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ports.MidiSchemeActor;
import org.jebol.domain.eval.ports.SchemeActorNative;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class SetSchemeNative extends DefaultNative {

    private static final Set<String> SCHEMES_SERVED_NATIVELY = Set.of(
            "console", "tcp", "dns", "event", "checksum", "file", "dir", "crypt",
            "clipboard", "udp", "midi", "system", "callback", "bundled");

    private static final int WHERE_STANDARD_SCHEME_KEEPS_ITS_NAME = 0;

    private static final int WHERE_STANDARD_SCHEME_KEEPS_ITS_ACTOR = 4;

    private final GrantedServices granted;

    public SetSchemeNative(GrantedServices granted) {
        this.granted = granted;
    }

    @Override
    public String nativeName() {
        return "set-scheme";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("scheme", Set.of(ObjectValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            List<ContextSlot> fields = ((ObjectValue) arguments.getFirst()).context().slots();
            if (fields.size() <= WHERE_STANDARD_SCHEME_KEEPS_ITS_ACTOR
                    || !(fields.get(WHERE_STANDARD_SCHEME_KEEPS_ITS_NAME).value() instanceof WordValue named)
                    || !SCHEMES_SERVED_NATIVELY.contains(named.canonical())) {
                return NoneValue.none();
            }
            fields.get(WHERE_STANDARD_SCHEME_KEEPS_ITS_ACTOR)
                    .setBeneathAnyProtection(makeNativeActorFor(named));
            return LogicValue.of(true);
        };
    }

    private Value makeNativeActorFor(AnyWordValue named) {
        if (named.canonical().equals("midi")) {
            return new MidiSchemeActor(granted);
        }
        return new SchemeActorNative(named.canonical());
    }
}
