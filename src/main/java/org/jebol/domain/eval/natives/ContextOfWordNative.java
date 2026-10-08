package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class ContextOfWordNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "context?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("word", Typeset.ANY_WORD.members()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            WordValue word = (WordValue) arguments.getFirst();
            Context binding = word.binding();
            if (!word.isBound() || binding.isALoopFrame()) {
                return NoneValue.none();
            }
            if (binding.functionOwningThisFrame() == null) {
                return new ObjectValue(binding);
            }
            return binding.callHasEnded()
                    ? whatDoIsInLibrary(evaluator.systemContext())
                    : binding.functionOwningThisFrame();
        };
    }

    private Value whatDoIsInLibrary(Context library) {
        return library.knows("do") ? library.slotFor("do").value() : NoneValue.none();
    }
}
