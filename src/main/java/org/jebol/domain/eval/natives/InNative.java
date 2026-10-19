package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class InNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "in";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("object", TypesetValue.ANY_OBJECT.membersAnd(BlockValue.TYPE)),
                Parameter.of("word",
                        TypesetValue.ANY_WORD.membersAnd(BlockValue.TYPE, ParenValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (arguments.getFirst() instanceof AnyBlockValue searched
                    && !(searched instanceof PathValue)) {
                return findTheFirstHolderIn(searched, arguments.get(1), evaluator, context);
            }
            Context frame = findTheContextOf(arguments.getFirst());
            if (arguments.get(1) instanceof AnyBlockValue body) {
                return Binder.bindInPlace(body, frame);
            }
            AnyWordValue word = (AnyWordValue) arguments.get(1);
            return frame.holds(word.canonical()) ? word.bindTo(frame) : NoneValue.none();
        };
    }

    private Context findTheContextOf(Value value) {
        return switch (value) {
            case ObjectValue object -> object.context();
            case PortValue port -> port.context();
            case ModuleValue module -> module.context();
            case ErrorValue raised -> readTheFieldsAnErrorHolds(raised);
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "in wanted an object, an error, a port or a block, not "
                            + value.datatype().literalSpelling());
        };
    }

    private Context readTheFieldsAnErrorHolds(ErrorValue raised) {
        Context fields = Context.root();
        for (String name : ErrorValue.FIELDS) {
            raised.readTheField(name).ifPresent(value -> fields.register(name, value));
        }
        return fields;
    }

    private Value findTheFirstHolderIn(
            AnyBlockValue searched, Value wanted, Evaluator evaluator, Context context) {

        if (!(wanted instanceof AnyWordValue word)) {
            return refuseTheArgument(wanted, "word");
        }
        for (Value item : searched.remaining()) {
            Value resolved = item instanceof AnyWordValue bound && bound.isBound()
                    ? evaluator.evaluateOrRaise(BlockValue.of(List.of(bound)), context)
                    : item;
            if (resolved instanceof ObjectValue(Context holder)
                    && holder.holds(word.canonical())) {
                return word.bindTo(holder);
            }
        }
        return NoneValue.none();
    }
}
