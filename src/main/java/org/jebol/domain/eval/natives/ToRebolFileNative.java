package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.LocalFileConvention;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class ToRebolFileNative extends DefaultNative {

    private final LocalFileConvention convention;

    public ToRebolFileNative(LocalFileConvention convention) {
        this.convention = convention;
    }

    @Override
    public String nativeName() {
        return "to-rebol-file";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("path", Set.of(FileValue.TYPE, StringValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value path = arguments.getFirst();
            return FileValue.of(convention.readLocalPath(((AnyStringValue) path).text())
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG, path)));
        };
    }
}
