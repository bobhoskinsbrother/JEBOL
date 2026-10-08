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
        return List.of(Parameter.required("block"),
                Parameter.belongingTo("into", "target", Typeset.ANY_BLOCK.members()),
                Parameter.belongingTo("only", "words", Set.of()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("into", "only", "no-set");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value source = arguments.getFirst();
            Optional<Value> target = argumentOf("into", 0, arguments, refinements);
            Optional<BlockValue> reduced = reducedFrom(source, evaluator,
                    argumentOf("only", 0, arguments, refinements), refinements);
            if (reduced.isEmpty() && target.isEmpty()) {
                return source;
            }
            BlockValue results = reduced.orElseGet(() -> BlockValue.block(List.of(source)));
            if (target.isEmpty() || !(target.get() instanceof BlockValue into)) {
                return results.as(source.datatype() == Datatype.PAREN
                        ? Datatype.PAREN
                        : Datatype.BLOCK);
            }
            List<Value> items = results.remaining();
            into.storage().spliceInAt(into.index(), items, results.storage(), results.index());
            return into.atIndex(into.index() + items.size());
        };
    }

    private Optional<BlockValue> reducedFrom(Value source, Evaluator evaluator,
            Optional<Value> exceptions, Set<String> refinements) {
        if (!(source instanceof BlockValue toReduce)
                || !(toReduce.datatype() == Datatype.BLOCK
                        || toReduce.datatype() == Datatype.PAREN)) {
            return Optional.empty();
        }
        if (refinements.contains("no-set")) {
            return Optional.of(BlockValue.block(evaluator.reducedLeavingSetWords(toReduce)));
        }
        if (refinements.contains("only")) {
            return Optional.of(BlockValue.block(reducedOnlyWords(toReduce, exceptions)));
        }
        return Optional.of(evaluator.evaluateEachKeepingTheLineShape(
                toReduce, evaluator.systemContext()));
    }

    private List<Value> reducedOnlyWords(BlockValue block, Optional<Value> exceptions) {
        Set<String> kept = exceptions
                .filter(BlockValue.class::isInstance)
                .map(excepted -> ((BlockValue) excepted).remaining().stream()
                        .filter(AnyWordValue.class::isInstance)
                        .map(word -> ((AnyWordValue) word).canonical())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
        List<Value> results = new ArrayList<>();
        for (Value item : block.remaining()) {
            if (item instanceof AnyWordValue word && word.datatype() == Datatype.WORD
                    && !kept.contains(word.canonical())) {
                Value held = word.boundSlot().value();
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
