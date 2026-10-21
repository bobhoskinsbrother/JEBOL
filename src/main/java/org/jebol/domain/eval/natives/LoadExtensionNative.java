package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.eval.security.PolicyClass;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.DispatcherHandle;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LoadExtensionNative extends DefaultNative {

    private static final String WHY_NATIVE_CODE_CANNOT_BE_LOADED = "never portable";

    @Override
    public String nativeName() {
        return "load-extension";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("name", Set.of(FileValue.TYPE, BinaryValue.TYPE)),
                Parameter.belongTo("dispatch", "function", Set.of(HandleValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("dispatch");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value name = arguments.getFirst();
            Optional<Value> dispatcher = refinementArgument("dispatch", 0, arguments, refinements);
            if (dispatcher.isEmpty()) {
                throw refuseNativeCode(name, evaluator);
            }
            return makeExtensionHolding(makeFreshExtensionHandleFor((HandleValue) dispatcher.get(), evaluator),
                    readSourceIn(name), evaluator);
        };
    }

    private HandleValue makeFreshExtensionHandleFor(HandleValue given, Evaluator evaluator) {
        Value carried = new DispatcherHandle(given).findTheDispatcher()
                .<Value>map(JavaObjectValue::of)
                .orElseGet(NoneValue::none);
        return HandleValue.wrapFunction(DispatcherHandle.KIND_DISPATCHER_IS,
                evaluator.numberNextExtension(), carried);
    }

    private Raised refuseNativeCode(Value name, Evaluator evaluator) {
        if (!(name instanceof FileValue library)) {
            return Raised.of(EvaluationFailure.INVALID_ARG, name);
        }
        evaluator.policies().require(PolicyClass.EXTENSION, PolicyAccess.EXECUTE, Optional.of(library));
        return Raised.of(EvaluationFailure.NO_EXTENSION, library, StringValue.of(WHY_NATIVE_CODE_CANNOT_BE_LOADED));
    }

    private BinaryValue readSourceIn(Value name) {
        return name instanceof BinaryValue source
                ? source
                : BinaryValue.ofBytes(((AnyStringValue) name).text().getBytes(StandardCharsets.UTF_8));
    }

    private ObjectValue makeExtensionHolding(HandleValue handle, BinaryValue source, Evaluator evaluator) {
        Context fields = Context.root();
        if (evaluator.systemContext().readValueAt("system", "standard", "extension") instanceof ObjectValue(Context standard)) {
            standard.slots().forEach(slot -> fields.register(slot.spelling(), NoneValue.none()));
        }
        fields.register("lib-base", handle);
        fields.register("lib-boot", source);
        ObjectValue made = new ObjectValue(fields);
        fields.pointItsOwnSelfAt(made);
        return made;
    }
}
