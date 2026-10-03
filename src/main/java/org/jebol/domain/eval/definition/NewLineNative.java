package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public class NewLineNative extends DefaultNative {

    private static final int WHERE_THE_SKIP_SIZE_ARRIVES = 2;

    @Override
    public String name() {
        return "new-line";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("position", Set.of(Datatype.BLOCK, Datatype.PAREN)),
                Parameter.required("value"),
                Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("all", "skip");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BlockValue block = (BlockValue) arguments.getFirst();
            boolean wanted = arguments.get(1).isTruthy();
            OptionalInt stride = theStride(arguments, refinements);
            if (stride.isEmpty()) {
                if (!block.atTail()) {
                    block.storage().setLineBreakAt(block.index(), wanted);
                }
                return block;
            }
            for (int step = 0; block.index() + step <= block.storageLength(); step++) {
                block.storage().setLineBreakAt(block.index() + step,
                        wanted ^ (step % stride.getAsInt() != 0));
            }
            return block;
        };
    }

    private static OptionalInt theStride(List<Value> arguments, Set<String> refinements) {
        if (refinements.contains("skip") && arguments.size() > WHERE_THE_SKIP_SIZE_ARRIVES
                && arguments.get(WHERE_THE_SKIP_SIZE_ARRIVES) instanceof IntegerValue size) {
            if (size.magnitude() < 1) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Molder.mold(size));
            }
            return OptionalInt.of((int) size.magnitude());
        }
        return refinements.contains("all") ? OptionalInt.of(1) : OptionalInt.empty();
    }
}
