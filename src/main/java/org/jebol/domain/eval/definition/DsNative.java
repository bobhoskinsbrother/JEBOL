package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class DsNative extends DefaultNative {

    private static final String FRAME_LINE = "%nSTACK[%d] %s[%d] %s";

    private static final String SLOT_LINE = "\t%s: %s";

    private static final int SLOT_MOLD_LIMIT = 72;

    private static final String NO_NAME = "?";

    @Override
    public String nativeName() {
        return "ds";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            printTheFrameStack(evaluator);
            return UnsetValue.unset();
        };
    }

    private void printTheFrameStack(Evaluator evaluator) {
        List<Evaluator.OpenCall> open = evaluator.callsInProgress();
        int slotsInUse = (open.size() + 1) * StackNative.FRAME_VALUE_UNITS;
        evaluator.output().writeLine(String.format(FRAME_LINE,
                slotsInUse, nativeName(), 0, Datatype.NATIVE.literalSpelling()));
        slotsInUse -= StackNative.FRAME_VALUE_UNITS;
        for (Evaluator.OpenCall call : open) {
            printOneFrame(evaluator, call, slotsInUse);
            slotsInUse -= StackNative.FRAME_VALUE_UNITS;
        }
    }

    private void printOneFrame(Evaluator evaluator, Evaluator.OpenCall call, int slotsInUse) {
        List<String> slots = call.slotNames();
        evaluator.output().writeLine(String.format(FRAME_LINE,
                slotsInUse,
                call.name().isEmpty() ? NO_NAME : call.name(),
                slots.size(),
                call.function().datatype().literalSpelling()));
        for (String slot : slots) {
            evaluator.output().writeLine(String.format(SLOT_LINE, slot,
                    moldedWithinTheLimit(call.locals().slotFor(Context.canonicalise(slot)).value())));
        }
    }

    private String moldedWithinTheLimit(Value value) {
        String written = Molder.mold(value);
        return written.length() <= SLOT_MOLD_LIMIT ? written : written.substring(0, SLOT_MOLD_LIMIT);
    }
}
