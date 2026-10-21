package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ReduceNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "reduce";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("block"),
                Parameter.belongTo("into", "target", TypesetValue.ANY_BLOCK.members()),
                Parameter.belongTo("only", "words", Set.of()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("into", "only", "no-set");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value source = arguments.getFirst();
            Optional<Value> target = refinementArgument("into", 0, arguments, refinements);
            Optional<AnyBlockValue> reduced = reduceFrom(source, evaluator,
                    refinementArgument("only", 0, arguments, refinements), refinements);
            if (reduced.isEmpty() && target.isEmpty()) {
                return source;
            }
            AnyBlockValue results = reduced.orElseGet(() -> BlockValue.of(List.of(source)));
            if (target.isEmpty() || !(target.get() instanceof AnyBlockValue into)) {
                return source instanceof ParenValue
                        ? ParenValue.of(results.remaining())
                        : results.asBlock();
            }
            List<Value> items = results.remaining();
            into.storage().spliceInAt(into.index(), items, results.storage(), results.index());
            return into.atIndex(into.index() + items.size());
        };
    }

    private Optional<AnyBlockValue> reduceFrom(Value source, Evaluator evaluator,
                                               Optional<Value> exceptions, Set<String> refinements) {
        if (!(source instanceof AnyBlockValue toReduce)
                || !(toReduce instanceof BlockValue || toReduce instanceof ParenValue)) {
            return Optional.empty();
        }
        if (refinements.contains("no-set")) {
            return Optional.of(BlockValue.of(evaluator.reduceLeavingSetWords(toReduce)));
        }
        if (refinements.contains("only")) {
            return Optional.of(BlockValue.of(reduceOnlyWords(toReduce, exceptions)));
        }
        return Optional.of(evaluator.evaluateEachKeepingLineShape(
                toReduce, evaluator.systemContext()));
    }

    private List<Value> reduceOnlyWords(AnyBlockValue block, Optional<Value> exceptions) {
        Set<String> kept = exceptions
                .filter(AnyBlockValue.class::isInstance)
                .map(excepted -> ((AnyBlockValue) excepted).remaining().stream()
                        .filter(AnyWordValue.class::isInstance)
                        .map(word -> ((AnyWordValue) word).canonical())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
        List<Value> results = new ArrayList<>();
        for (Value item : block.remaining()) {
            if (item instanceof WordValue word
                    && !kept.contains(word.canonical())) {
                Value held = word.findBoundSlot().value();
                if (held instanceof UnsetValue) {
                    throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
                }
                results.add(held);
            } else {
                results.add(item);
            }
        }
        return results;
    }
}
