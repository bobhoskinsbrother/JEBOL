package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.LocalFileConvention;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class EnlineNative extends DefaultNative {

    private final LocalFileConvention convention;

    public EnlineNative(LocalFileConvention convention) {
        this.convention = convention;
    }

    @Override
    public String nativeName() {
        return "enline";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("series", TypesetValue.ANY_STRING.membersAnd(BlockValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case AnyStringValue text -> convention == LocalFileConvention.WINDOWS
                    ? text.withCarriageReturnBeforeEachLineFeed()
                    : text.withOneLineFeedPerEnding();
            case Value lines -> throw Raised.of(EvaluationFailure.NOT_DONE);
        };
    }
}
